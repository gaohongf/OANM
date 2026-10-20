package com.github.gaohongf.files.entity.po;

import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.github.gaohongf.files.dao.handler.JsonbTypeHandler;

import cn.hutool.core.lang.Dict;
import lombok.Data;

/**
 * 文件上传记录, 对应 {@code file_detail} 表。
 *
 * <h2>为什么不继承 {@code BaseEntity}</h2>
 * 这张表是 X File Storage 的记录表, 列由那个库的约定决定, 只有 {@code create_time},
 * 没有 {@code create_by / update_* / deleted / version}。继承 {@code BaseEntity} 会让
 * MyBatis-Plus 在每条 SQL 里拼上这些不存在的列。
 *
 * <h2>id 为什么是 String</h2>
 * 列是 {@code varchar(32)}, X File Storage 的记录 id 是 32 位 UUID, 不是本项目的雪花 id。
 * 用 {@link IdType#ASSIGN_UUID} 由 MyBatis-Plus 在插入时生成, 正好也是 32 位。
 */
@Data
@TableName(value = "file_detail", autoResultMap = true)
public class FileDetailEntity {

    @TableId(type = IdType.ASSIGN_UUID)
    private String id;

    /** 文件访问地址 */
    private String url;

    /** 文件大小, 单位字节 */
    private Long size;

    /** 存储文件名 */
    private String filename;

    /** 用户上传时的原始文件名 */
    private String originalFilename;

    /** 基础路径 */
    private String basePath;

    /** 相对路径 */
    private String path;

    /** 文件扩展名 */
    private String ext;

    /** MIME 类型 */
    private String contentType;

    /** 存储平台标识, 对应配置文件里的 platform */
    private String platform;

    /** 缩略图访问地址 */
    private String thUrl;

    /** 缩略图文件名 */
    private String thFilename;

    /** 缩略图大小 */
    private Long thSize;

    /** 缩略图 MIME 类型 */
    private String thContentType;

    /** 对象存储的 objectId */
    private String objectId;

    /** 对象存储的 objectType */
    private String objectType;

    /**
     * 附加属性, 库里是 {@code jsonb}。
     * <p>
     * 必须显式指定 {@link JsonbTypeHandler}: MyBatis 自带的 JSON 处理器用
     * {@code setString} 绑定参数, PostgreSQL 不接受 varchar → jsonb 的隐式转换,
     * 插入时会报"列 attr 的类型是 jsonb, 表达式却是 character varying"。
     */
    @TableField(typeHandler = JsonbTypeHandler.class)
    private Dict attr;

    /** 记录创建时间, 由库的 DEFAULT CURRENT_TIMESTAMP 填充 */
    private LocalDateTime createTime;
}
