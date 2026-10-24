package com.github.gaohongf.software.entity.req;

import java.util.Optional;

import org.hibernate.validator.constraints.Length;
import org.springframework.util.StringUtils;

import com.github.gaohongf.software.entity.po.SoftwarePlatform;
import com.github.gaohongf.software.rsm.SoftwareRsm;
import com.lingyun.base.rsm.R;
import com.lingyun.base.rsm.validation.BaseValidationRsm;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 新增 / 编辑软件版本。
 *
 * <h2>文件字段是可选的"一整组"</h2>
 * 编辑时不重新上传文件是常态，所以 fileUrl 为空表示"保留原文件"。
 * 但只要给了一个文件字段，四个就必须齐全 —— 半组数据会让下载时拿不到文件名或地址。
 *
 * <h2>platform 收 String 而不是枚举</h2>
 * 直接绑成枚举时，非法值会变成框架的报文解析错误。收字符串再解析，才能给出
 * "平台只能是 WINDOWS、MACOS、LINUX 或 COMMON"这种直接可读的提示。
 */
@Data
public class SaveSoftwareVersionCommand {

    @NotBlank(message = BaseValidationRsm.JAKARTA_VALIDATION_CONSTRAINTS_NOTBLANK_MESSAGE)
    @Length(min = 1, max = 50, message = BaseValidationRsm.ORG_HIBERNATE_VALIDATOR_CONSTRAINTS_LENGTH_MESSAGE)
    private String versionNo;

    @NotBlank(message = BaseValidationRsm.JAKARTA_VALIDATION_CONSTRAINTS_NOTBLANK_MESSAGE)
    @Length(max = 16, message = BaseValidationRsm.ORG_HIBERNATE_VALIDATOR_CONSTRAINTS_LENGTH_MESSAGE)
    private String platform;

    @Length(max = 4000, message = BaseValidationRsm.ORG_HIBERNATE_VALIDATOR_CONSTRAINTS_LENGTH_MESSAGE)
    private String releaseNote;

    @Length(max = 64, message = BaseValidationRsm.ORG_HIBERNATE_VALIDATOR_CONSTRAINTS_LENGTH_MESSAGE)
    private String fileId;

    @Length(max = 512, message = BaseValidationRsm.ORG_HIBERNATE_VALIDATOR_CONSTRAINTS_LENGTH_MESSAGE)
    private String fileUrl;

    @Length(max = 256, message = BaseValidationRsm.ORG_HIBERNATE_VALIDATOR_CONSTRAINTS_LENGTH_MESSAGE)
    private String fileName;

    @Min(value = 0, message = "文件大小不能为负数")
    private Long fileSize;

    /** 编辑时 null 表示保持原值；新建时 null 视为可下载 */
    private Boolean enabled;

    /** 解析平台，非法值直接抛业务错误 */
    public SoftwarePlatform toPlatform() {
        Optional<SoftwarePlatform> parsed = SoftwarePlatform.parse(platform);
        if (parsed.isEmpty()) {
            // 独立语句而不是 orElseThrow: R.error 的泛型会被推成 Throwable，要求假的方法签名
            R.error(SoftwareRsm.SOFTWARE_PLATFORM_INVALID);
        }
        return parsed.get();
    }

    /** 新建版本时必须带齐文件 */
    public void requireFileOnCreate() {
        if (!hasCompleteFile()) {
            R.error(SoftwareRsm.SOFTWARE_FILE_INVALID);
        }
        validateFileUrl(fileUrl);
    }

    /**
     * 编辑版本时校验"没传就保留，传了就齐全"。
     *
     * @return true 表示这次请求要替换文件
     */
    public boolean shouldReplaceFile() {
        boolean anyFileField = StringUtils.hasText(fileId)
                || StringUtils.hasText(fileUrl)
                || StringUtils.hasText(fileName)
                || fileSize != null;
        if (!anyFileField) {
            return false;
        }
        if (!hasCompleteFile()) {
            R.error(SoftwareRsm.SOFTWARE_FILE_INVALID);
        }
        validateFileUrl(fileUrl);
        return true;
    }

    private boolean hasCompleteFile() {
        return StringUtils.hasText(fileId)
                && StringUtils.hasText(fileUrl)
                && StringUtils.hasText(fileName)
                && fileSize != null;
    }

    /**
     * 只接受同源的 /file/ 地址。
     * <p>
     * 前端用 &lt;a download&gt; 触发保存，该属性只对同源地址生效；允许外部地址会把
     * "下载"变成一次开放跳转。地址形状由 service-files 决定，这里收紧而不是放行。
     */
    private static void validateFileUrl(String url) {
        if (!StringUtils.hasText(url) || !url.startsWith("/file/")) {
            R.error(SoftwareRsm.SOFTWARE_FILE_INVALID);
        }
    }
}
