package com.github.gaohongf.files.service.impl;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Set;

import org.dromara.x.file.storage.core.FileInfo;
import org.dromara.x.file.storage.core.FileStorageService;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import com.github.gaohongf.files.constant.UploadLimits;
import com.github.gaohongf.files.entity.res.FileUploadRes;
import com.github.gaohongf.files.rsm.FileRsm;
import com.github.gaohongf.files.service.FileService;
import com.lingyun.base.rsm.exception.RequestException;

import lombok.extern.slf4j.Slf4j;

/**
 * 文件上传与删除, 实际存储交给 X File Storage。
 *
 * <h2>两个上传入口, 限制不同</h2>
 * 图片接口面向所有登录用户, 只收常见图片格式、单张 10MB; 大文件接口需要单独授权,
 * 收任意类型、单个 1GB。multipart 的 servlet 级上限按 1GB 设置（见 application.yml）,
 * 所以这里的校验是<b>业务层</b>的最后一道判断, 不依赖容器配置。
 *
 * <h2>为什么按日期分目录</h2>
 * 不设 path 时所有文件都落在存储根目录下, 文件一多, 本地存储的目录操作（列目录、备份、
 * 资源管理器打开）都会变慢, 而且肉眼无法判断某个文件是什么时候传的。
 * 路径不带前导 {@code /}: 它会被拼进 url, 带了会变成 {@code /file//2026/...}。
 */
@Slf4j
@Service
public class FileServiceImpl implements FileService {

    /** 文件在存储根目录下的相对路径, 如 {@code 2026/10/19/} */
    private static final DateTimeFormatter DIRECTORY_FORMAT = DateTimeFormatter.ofPattern("yyyy/MM/dd");

    /** 对应 file_detail.original_filename 的 varchar(256) */
    private static final int MAX_NAME_LENGTH = 255;

    /** 对应 file_detail.object_type / object_id 的 varchar(32) */
    private static final int MAX_OBJECT_LENGTH = 32;

    /**
     * 图片接口接受的扩展名。
     * <p>
     * 只认扩展名和声明类型, 不解析文件内容 —— 这是"别把 PDF 当图片传上来"的业务约束,
     * 不是防恶意文件的安全扫描。存储地址是随机生成且不带可执行权限的静态资源,
     * 真要防内容伪装, 应该在存储或网关层做。
     */
    private static final Set<String> IMAGE_EXTENSIONS = Set.of("jpg", "jpeg", "png", "gif", "webp", "bmp");

    private final FileStorageService fileStorageService;

    public FileServiceImpl(FileStorageService fileStorageService) {
        this.fileStorageService = fileStorageService;
    }

    @Override
    public FileUploadRes uploadImage(MultipartFile file, String objectType, String objectId) {
        validateCommon(file, objectType, objectId);
        if (file.getSize() > UploadLimits.IMAGE_MAX_BYTES) {
            throw new RequestException(FileRsm.FILE_IMAGE_TOO_LARGE);
        }
        validateImageType(file);
        return store(file, objectType, objectId);
    }

    @Override
    public FileUploadRes uploadLarge(MultipartFile file, String objectType, String objectId) {
        validateCommon(file, objectType, objectId);
        if (file.getSize() > UploadLimits.LARGE_MAX_BYTES) {
            throw new RequestException(FileRsm.FILE_TOO_LARGE);
        }
        return store(file, objectType, objectId);
    }

    @Override
    public void delete(String url) {
        if (!StringUtils.hasText(url)) {
            throw new RequestException(FileRsm.FILE_URL_REQUIRED);
        }

        FileInfo info = fileStorageService.getFileInfoByUrl(url);
        if (info == null) {
            // 幂等: 已经不存在就视为删除成功。重复点删除、重试请求都不该报错。
            return;
        }

        try {
            if (!fileStorageService.delete(info)) {
                throw new RequestException(FileRsm.FILE_DELETE_FAILED);
            }
        } catch (RequestException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            log.error("文件删除失败: url={}", url, ex);
            throw new RequestException(ex, FileRsm.FILE_DELETE_FAILED);
        }
    }

    /** 两个上传入口共用的校验: 非空、文件名和关联对象不超列宽 */
    private static void validateCommon(MultipartFile file, String objectType, String objectId) {
        if (file == null || file.isEmpty()) {
            throw new RequestException(FileRsm.FILE_EMPTY);
        }
        String originalFilename = file.getOriginalFilename();
        if (originalFilename != null && originalFilename.length() > MAX_NAME_LENGTH) {
            throw new RequestException(FileRsm.FILE_NAME_TOO_LONG);
        }
        if (tooLong(objectType) || tooLong(objectId)) {
            throw new RequestException(FileRsm.FILE_OBJECT_INVALID);
        }
    }

    /**
     * 图片接口的格式校验: 扩展名必须在白名单里, 且声明的 Content-Type 不能是别的类型。
     * <p>
     * {@code application/octet-stream} 放行: 有些 HTTP 客户端（不是浏览器）不会为
     * 上传的文件推断具体类型, 只统一发这个值。真正起约束作用的是扩展名白名单。
     */
    private static void validateImageType(MultipartFile file) {
        if (!IMAGE_EXTENSIONS.contains(extensionOf(file.getOriginalFilename()))) {
            throw new RequestException(FileRsm.FILE_IMAGE_TYPE_INVALID);
        }
        String contentType = file.getContentType();
        if (contentType == null || contentType.isBlank()
                || MediaType.APPLICATION_OCTET_STREAM_VALUE.equalsIgnoreCase(contentType)) {
            return;
        }
        if (!contentType.toLowerCase(Locale.ROOT).startsWith("image/")) {
            throw new RequestException(FileRsm.FILE_IMAGE_TYPE_INVALID);
        }
    }

    /** 上传到存储平台并转换成响应模型 */
    private FileUploadRes store(MultipartFile file, String objectType, String objectId) {
        String originalFilename = file.getOriginalFilename();
        FileInfo info;
        try {
            info = fileStorageService.of(file)
                    .setPath(LocalDate.now().format(DIRECTORY_FORMAT) + "/")
                    .setObjectType(normalize(objectType))
                    .setObjectId(normalize(objectId))
                    .upload();
        } catch (RuntimeException ex) {
            // 存储平台找不到、磁盘不可写、数据库插入失败都会走到这里。
            // 原始异常只在日志里保留, 响应统一成注册过的消息键 —— 未注册的字符串
            // 会被 RSM 当成找不到模板, 退回默认消息, 拒绝会显示成"成功"。
            log.error("文件上传失败: originalFilename={}", originalFilename, ex);
            throw new RequestException(ex, FileRsm.FILE_UPLOAD_FAILED);
        }

        if (info == null) {
            log.error("文件上传失败: 存储平台返回了 null, originalFilename={}", originalFilename);
            throw new RequestException(FileRsm.FILE_UPLOAD_FAILED);
        }

        return new FileUploadRes(
                info.getId(),
                info.getUrl(),
                info.getFilename(),
                info.getOriginalFilename(),
                info.getSize(),
                info.getContentType());
    }

    /** 取小写扩展名（不含点）; 没有扩展名时返回空串, 交给白名单判断 */
    private static String extensionOf(String filename) {
        if (!StringUtils.hasText(filename)) {
            return "";
        }
        int dot = filename.lastIndexOf('.');
        if (dot < 0 || dot == filename.length() - 1) {
            return "";
        }
        return filename.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    private static boolean tooLong(String value) {
        return value != null && value.length() > MAX_OBJECT_LENGTH;
    }

    /** 空白字符串按"未提供"处理, 存进库里是 NULL 而不是空串 */
    private static String normalize(String value) {
        return StringUtils.hasText(value) ? value : null;
    }
}
