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
 * <h2>为什么上传只需要登录, 删除需要权限</h2>
 * 任何能建单的用户都要能传附件, 所以上传标 {@link LoginOnly}: 它不产生权限行,
 * 新用户不会因为"还没被授予文件权限"而传不了附件。
 * 删除是破坏性操作, 走自注册的 {@code DELETE:/api/files} 权限行, 由管理端显式授权。
 */
@AllArgsConstructor
@RestController
@RequestMapping("/api/files")
public class FileController {

    private final FileService fileService;

    /**
     * 上传一个文件。
     *
     * @param file       表单字段名必须是 {@code file}
     * @param objectType 文件所属对象类型, 可为空
     * @param objectId   文件所属对象 id, 可为空
     */
    @LoginOnly
    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ExecutionSuccess(GenericRsm.CREATE_SUCCESS)
    @ExecutionFailed(GenericRsm.CREATE_FAILED)
    public FileUploadRes upload(
            @RequestPart("file") MultipartFile file,
            @RequestParam(name = "objectType", required = false) String objectType,
            @RequestParam(name = "objectId", required = false) String objectId) {
        return fileService.upload(file, objectType, objectId);
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
