package com.github.gaohongf.software.entity.po;

import org.apache.ibatis.type.EnumTypeHandler;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.github.gaohongf.model.BaseEntity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/**
 * 软件的一个可下载版本。
 *
 * <h2>版本号字段叫 versionNo</h2>
 * {@link BaseEntity#getVersion()} 是 MyBatis-Plus 的乐观锁列, 这里不能再声明一个同名的
 * {@code version} —— 既是 Java 字段冲突, 也是数据库列冲突。版本号统一用 versionNo /
 * version_no。
 */
@EqualsAndHashCode(callSuper = false)
@Data
@AllArgsConstructor
@NoArgsConstructor
@TableName(value = "software_versions", autoResultMap = true)
public class SoftwareVersionEntity extends BaseEntity {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long softwareId;

    /** 业务版本号（如 1.2.3），不是乐观锁的 version */
    private String versionNo;

    @TableField(typeHandler = EnumTypeHandler.class)
    private SoftwarePlatform platform;

    /** 版本说明 / 更新日志 */
    private String releaseNote;

    /**
     * service-files 的文件记录 id。
     * <p>
     * 只用于追溯"这个版本指向哪次上传"，下载不依赖它。
     */
    private String fileId;

    /** 同源访问地址，形如 /file/2026/10/24/xxx.exe */
    private String fileUrl;

    /** 上传时的原始文件名，下载时用它还原文件名 */
    private String fileName;

    private Long fileSize;

    /**
     * 下载次数。
     * <p>
     * 只由下载接口原子自增；管理端的编辑请求里永远不接收这个字段。
     */
    private Long downloadCount;

    /** 是否可下载。停用后下载页看不到，但历史下载次数保留 */
    private Boolean enabled;
}
