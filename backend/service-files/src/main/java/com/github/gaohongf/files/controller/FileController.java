package com.github.gaohongf.files.controller;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.github.gaohongf.auth.annotation.LoginOnly;
import com.github.gaohongf.files.entity.res.FileUploadRes;
import com.github.gaohongf.files.service.FileService;
import com.lingyun.base.rsm.GenericRsm;
import com.lingyun.base.rsm.annotation.ExecutionFailed;
import com.lingyun.base.rsm.annotation.ExecutionSuccess;

import lombok.AllArgsConstructor;

/**
 * 文件上传与删除。
 *
 * <h2>读取文件不走这里</h2>
 * 上传后返回的 url 是存储平台的相对地址（如 {@code /file/2026/10/19/xxx.jpg}）,
 * 由网关的 {@code /file/**} 路由转发到本服务, 再由 X File Storage 自动注册的静态资源
 * 映射直接返回文件内容 —— 不需要一个把整个文件读进内存再写出去的下载接口。
 *
 * <h2>两个上传入口, 权限和上限绑在一起</h2>
 * <ul>
 *   <li>{@code /upload/image}: 普通用户传图片, 标 {@link LoginOnly}。任何登录用户都能用
 *       （提单要传截图）, 但只收常见图片格式、单张 10MB。</li>
 *   <li>{@code /upload/large}: 管理员/运维传大文件（打印机驱动、安装包）, 不标
 *       {@link LoginOnly}, 因此会自注册出 {@code POST:/api/files/upload/large} 权限行,
 *       由管理端显式授权。收任意类型、单个 1GB。</li>
 * </ul>
 * 不合成一个接口的原因就在上面: 权限和大小上限是一体的。合成一个之后, 要么所有人都能传
 * 1GB 任意文件, 要么传一张截图也得先被授权。
 *
 * <h2>为什么删除也需要权限</h2>
 * 删除是破坏性操作, 走自注册的 {@code DELETE:/api/files} 权限行。
 */
@AllArgsConstructor
@RestController
@RequestMapping("/api/files")
public class FileController {

    private final FileService fileService;

    /**
     * 上传一张图片（登录即可）。
     *
     * @param file       表单字段名必须是 {@code file}
     * @param objectType 文件所属对象类型, 可为空
     * @param objectId   文件所属对象 id, 可为空
     */
    @LoginOnly
    @PostMapping(value = "/upload/image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ExecutionSuccess(GenericRsm.CREATE_SUCCESS)
    @ExecutionFailed(GenericRsm.CREATE_FAILED)
    public FileUploadRes uploadImage(
            @RequestPart("file") MultipartFile file,
            @RequestParam(name = "objectType", required = false) String objectType,
            @RequestParam(name = "objectId", required = false) String objectId) {
        return fileService.uploadImage(file, objectType, objectId);
    }

    /**
     * 上传一个大文件（需要 {@code POST:/api/files/upload/large} 权限）。
     *
     * @param file       表单字段名必须是 {@code file}
     * @param objectType 文件所属对象类型, 可为空
     * @param objectId   文件所属对象 id, 可为空
     */
    @PostMapping(value = "/upload/large", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ExecutionSuccess(GenericRsm.CREATE_SUCCESS)
    @ExecutionFailed(GenericRsm.CREATE_FAILED)
    public FileUploadRes uploadLarge(
            @RequestPart("file") MultipartFile file,
            @RequestParam(name = "objectType", required = false) String objectType,
            @RequestParam(name = "objectId", required = false) String objectId) {
        return fileService.uploadLarge(file, objectType, objectId);
    }

    /**
     * 按访问地址删除文件。
     * <p>
     * 幂等: 记录已经不存在时同样返回成功。
     *
     * @param url 上传接口返回的 url（相对地址）
     */
    @DeleteMapping
    @ExecutionSuccess(GenericRsm.DELETE_SUCCESS)
    @ExecutionFailed(GenericRsm.DELETE_FAILED)
    public void delete(@RequestParam("url") String url) {
        fileService.delete(url);
    }
}
