package com.github.gaohongf.software.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.github.gaohongf.auth.annotation.LoginOnly;
import com.github.gaohongf.model.PageRes;
import com.github.gaohongf.software.entity.res.DownloadRes;
import com.github.gaohongf.software.entity.res.SoftwareCategoryRes;
import com.github.gaohongf.software.entity.res.SoftwareDetailRes;
import com.github.gaohongf.software.entity.res.SoftwareRes;
import com.github.gaohongf.software.rsm.SoftwareRsm;
import com.github.gaohongf.software.service.SoftwareCategoryService;
import com.github.gaohongf.software.service.SoftwareService;
import com.lingyun.base.rsm.GenericRsm;
import com.lingyun.base.rsm.annotation.ExecutionFailed;
import com.lingyun.base.rsm.annotation.ExecutionSuccess;

import lombok.AllArgsConstructor;

/**
 * 软件下载页（所有登录用户可见）。
 *
 * <h2>为什么这些端点标 {@code @LoginOnly} 而不是开放</h2>
 * 下载页是内网自助入口，登录即用，不需要管理员再逐个授权。{@code @LoginOnly} 不产生权限行，
 * 所以新员工不会遇到"菜单在、点进去 403"的情况。
 *
 * <h2>下载返回地址而不是直接流转发文件</h2>
 * 文件由 service-files 的 {@code /file/**} 静态映射提供，这里只做"计数 + 发地址"。
 * 把整个文件读进内存再写出去，会让网关和本服务都多背一份 IO 压力。
 */
@AllArgsConstructor
@RestController
@RequestMapping("/api/software")
public class SoftwarePublicController {

    private final SoftwareService softwareService;
    private final SoftwareCategoryService categoryService;

    /**
     * 分页列表。
     *
     * @param keyword    同时匹配软件名与厂商
     * @param categoryId 一级分类
     * @param platform   WINDOWS / MACOS / LINUX / COMMON；具体平台会同时匹配"通用"
     */
    @LoginOnly
    @GetMapping
    @ExecutionSuccess(GenericRsm.QUERY_SUCCESS)
    @ExecutionFailed(GenericRsm.QUERY_FAILED)
    public PageRes<SoftwareRes> page(
            @RequestParam(name = "current", defaultValue = "1") long current,
            @RequestParam(name = "size", defaultValue = "10") long size,
            @RequestParam(name = "keyword", required = false) String keyword,
            @RequestParam(name = "categoryId", required = false) Long categoryId,
            @RequestParam(name = "platform", required = false) String platform) {
        return softwareService.pagePublic(current, size, keyword, categoryId, platform);
    }

    /**
     * 分类下拉。
     * <p>
     * 字面量 {@code /categories} 比 {@code /{id}} 更具体，Spring 会优先匹配它。
     */
    @LoginOnly
    @GetMapping("/categories")
    @ExecutionSuccess(GenericRsm.QUERY_SUCCESS)
    @ExecutionFailed(GenericRsm.QUERY_FAILED)
    public List<SoftwareCategoryRes> categories() {
        return categoryService.list();
    }

    /** 详情：只包含启用中的版本，软件本身停用时返回 404。 */
    @LoginOnly
    @GetMapping("/{id}")
    @ExecutionSuccess(GenericRsm.QUERY_SUCCESS)
    @ExecutionFailed(GenericRsm.QUERY_FAILED)
    public SoftwareDetailRes detail(@PathVariable("id") Long id) {
        return softwareService.findPublicDetail(id);
    }

    /**
     * 下载计数，并返回本次下载地址。
     * <p>
     * 用 POST 是因为它改变了状态（计数 +1）。前端拿到 {@code {url, fileName}} 后自己触发
     * 一次同源下载；地址由后端校验为 {@code /file/} 开头，避免变成开放跳转。
     */
    @LoginOnly
    @PostMapping("/versions/{versionId}/download")
    @ExecutionSuccess(SoftwareRsm.DOWNLOAD_STARTED)
    @ExecutionFailed(GenericRsm.EXECUTION_FAILED)
    public DownloadRes download(@PathVariable("versionId") Long versionId) {
        return softwareService.download(versionId);
    }
}
