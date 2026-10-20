package com.github.gaohongf.files.service;

import org.springframework.web.multipart.MultipartFile;

import com.github.gaohongf.files.entity.res.FileUploadRes;

/**
 * 文件上传与删除。
 */
public interface FileService {

    /**
     * 上传一个文件。
     *
     * @param objectType 文件所属对象类型, 可为空
     * @param objectId   文件所属对象 id, 可为空
     * @throws com.lingyun.base.rsm.exception.RequestException 文件为空、字段超长或存储失败时
     */
    FileUploadRes upload(MultipartFile file, String objectType, String objectId);

    /**
     * 按访问地址删除文件, 连同 {@code file_detail} 里的记录。
     * <p>
     * 地址对应的记录不存在时视为已经删掉（幂等）, 不报错。
     *
     * @throws com.lingyun.base.rsm.exception.RequestException url 为空或物理删除失败时
     */
    void delete(String url);
}
