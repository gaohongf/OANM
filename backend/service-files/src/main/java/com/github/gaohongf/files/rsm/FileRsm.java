package com.github.gaohongf.files.rsm;

import org.springframework.http.HttpStatus;

import com.lingyun.base.rsm.RsmManager;
import com.lingyun.base.rsm.annotation.RsmInfo;

/**
 * 文件服务相关的统一响应消息。
 *
 * <h2>不要给它加 @Component</h2>
 * 它由 {@code com.github.gaohongf.files.config.FileRsmConfiguration} 注册。
 * 没注册过的消息键解析不出模板时, RSM 会退回默认消息, 拒绝会被渲染成"成功" ——
 * 那正是 {@code WorkOrderRsm} 文档里记录过的问题。
 */
public class FileRsm implements RsmManager {

    /** 上传时没有带文件, 或者文件是空文件 */
    @RsmInfo(template = "上传的文件为空", status = HttpStatus.BAD_REQUEST)
    public static final String FILE_EMPTY = "File_FILE_EMPTY";

    /**
     * 原始文件名超出 {@code file_detail.original_filename} 的 varchar(256)。
     * <p>
     * 不提前拦的话, 插入会在 PostgreSQL 那一层报 "value too long", 前端只会看到一个 500。
     */
    @RsmInfo(template = "原始文件名过长（最多 255 个字符）", status = HttpStatus.BAD_REQUEST)
    public static final String FILE_NAME_TOO_LONG = "File_FILE_NAME_TOO_LONG";

    /** 存储失败: 库返回了 null, 或抛出未预期的异常 */
    @RsmInfo(template = "文件上传失败", status = HttpStatus.INTERNAL_SERVER_ERROR)
    public static final String FILE_UPLOAD_FAILED = "File_FILE_UPLOAD_FAILED";

    /** 删除时没带 url */
    @RsmInfo(template = "文件地址不可为空", status = HttpStatus.BAD_REQUEST)
    public static final String FILE_URL_REQUIRED = "File_FILE_URL_REQUIRED";

    /** 物理文件删除失败（记录仍然存在） */
    @RsmInfo(template = "文件删除失败", status = HttpStatus.INTERNAL_SERVER_ERROR)
    public static final String FILE_DELETE_FAILED = "File_FILE_DELETE_FAILED";

    /** objectType / objectId 超出 {@code file_detail} 的 varchar(32) */
    @RsmInfo(template = "文件关联对象标识过长（最多 32 个字符）", status = HttpStatus.BAD_REQUEST)
    public static final String FILE_OBJECT_INVALID = "File_FILE_OBJECT_INVALID";

    /** 图片上传接口: 文件超过 {@code UploadLimits.IMAGE_MAX_BYTES} */
    @RsmInfo(template = "图片不能超过 10MB", status = HttpStatus.PAYLOAD_TOO_LARGE)
    public static final String FILE_IMAGE_TOO_LARGE = "File_FILE_IMAGE_TOO_LARGE";

    /** 图片上传接口: 扩展名不在白名单内, 或声明的 Content-Type 不是图片 */
    @RsmInfo(template = "只支持 jpg、jpeg、png、gif、webp、bmp 格式的图片", status = HttpStatus.BAD_REQUEST)
    public static final String FILE_IMAGE_TYPE_INVALID = "File_FILE_IMAGE_TYPE_INVALID";

    /** 大文件上传接口: 文件超过 {@code UploadLimits.LARGE_MAX_BYTES} */
    @RsmInfo(template = "文件不能超过 1GB", status = HttpStatus.PAYLOAD_TOO_LARGE)
    public static final String FILE_TOO_LARGE = "File_FILE_TOO_LARGE";
}
