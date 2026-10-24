package com.github.gaohongf.software.controller;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.github.gaohongf.model.PageRes;
import com.github.gaohongf.software.entity.req.SaveSoftwareCategoryCommand;
import com.github.gaohongf.software.entity.req.SaveSoftwareCommand;
import com.github.gaohongf.software.entity.req.SaveSoftwareVersionCommand;
import com.github.gaohongf.software.entity.req.ToggleEnabledCommand;
import com.github.gaohongf.software.entity.res.CreatedRes;
import com.github.gaohongf.software.entity.res.SoftwareManageDetailRes;
import com.github.gaohongf.software.entity.res.SoftwareManageRes;
import com.github.gaohongf.software.service.SoftwareCategoryService;
import com.github.gaohongf.software.service.SoftwareService;
import com.lingyun.base.rsm.GenericRsm;
import com.lingyun.base.rsm.annotation.ExecutionFailed;
import com.lingyun.base.rsm.annotation.ExecutionSuccess;

import jakarta.validation.Valid;
import lombok.AllArgsConstructor;

/**
 * 软件管理（挂在「系统管理」下）。
 *
 * <h2>这个控制器里没有 {@code @LoginOnly}</h2>
 * 每个端点都会被启动扫描登记成权限行（{@code GET:/api/software/manage} 等），
 * 再由管理员授给角色。公开读取在 {@link SoftwarePublicController}，两边路径不重叠。
 *
 * <h2>启用/停用单独一个端点</h2>
 * 不把 {@code enabled} 塞在编辑表单里顺手改：列表上的一次开关不该走"读取整行 → 提交整行"
 * 那条路，否则两个管理员同时编辑时，后保存的人会把前一个人的字段一并覆盖。
 */
@AllArgsConstructor
@RestController
@RequestMapping("/api/software")
public class SoftwareManageController {

    private final SoftwareService softwareService;
    private final SoftwareCategoryService categoryService;

    @GetMapping("/manage")
    @ExecutionSuccess(GenericRsm.QUERY_SUCCESS)
    @ExecutionFailed(GenericRsm.QUERY_FAILED)
    public PageRes<SoftwareManageRes> page(
            @RequestParam(name = "current", defaultValue = "1") long current,
            @RequestParam(name = "size", defaultValue = "10") long size,
            @RequestParam(name = "keyword", required = false) String keyword,
            @RequestParam(name = "categoryId", required = false) Long categoryId,
            @RequestParam(name = "platform", required = false) String platform,
            @RequestParam(name = "enabled", required = false) Boolean enabled) {
        return softwareService.pageManage(current, size, keyword, categoryId, platform, enabled);
    }

    @GetMapping("/manage/{id}")
    @ExecutionSuccess(GenericRsm.QUERY_SUCCESS)
    @ExecutionFailed(GenericRsm.QUERY_FAILED)
    public SoftwareManageDetailRes detail(@PathVariable("id") Long id) {
        return softwareService.findManageDetail(id);
    }

    @PostMapping
    @ExecutionSuccess(GenericRsm.CREATE_SUCCESS)
    @ExecutionFailed(GenericRsm.CREATE_FAILED)
    public CreatedRes create(@RequestBody @Valid SaveSoftwareCommand command) {
        return new CreatedRes(String.valueOf(softwareService.create(command)));
    }

    @PutMapping("/{id}")
    @ExecutionSuccess(GenericRsm.UPDATE_SUCCESS)
    @ExecutionFailed(GenericRsm.UPDATE_FAILED)
    public void update(@PathVariable("id") Long id,
                       @RequestBody @Valid SaveSoftwareCommand command) {
        softwareService.update(id, command);
    }

    @PutMapping("/{id}/enabled")
    @ExecutionSuccess(GenericRsm.UPDATE_SUCCESS)
    @ExecutionFailed(GenericRsm.UPDATE_FAILED)
    public void setEnabled(@PathVariable("id") Long id,
                           @RequestBody @Valid ToggleEnabledCommand command) {
        softwareService.setEnabled(id, command.getEnabled());
    }

    @DeleteMapping("/{id}")
    @ExecutionSuccess(GenericRsm.DELETE_SUCCESS)
    @ExecutionFailed(GenericRsm.DELETE_FAILED)
    public void delete(@PathVariable("id") Long id) {
        softwareService.delete(id);
    }

    @PostMapping("/{id}/versions")
    @ExecutionSuccess(GenericRsm.CREATE_SUCCESS)
    @ExecutionFailed(GenericRsm.CREATE_FAILED)
    public CreatedRes createVersion(@PathVariable("id") Long id,
                                    @RequestBody @Valid SaveSoftwareVersionCommand command) {
        return new CreatedRes(String.valueOf(softwareService.createVersion(id, command)));
    }

    @PutMapping("/versions/{versionId}")
    @ExecutionSuccess(GenericRsm.UPDATE_SUCCESS)
    @ExecutionFailed(GenericRsm.UPDATE_FAILED)
    public void updateVersion(@PathVariable("versionId") Long versionId,
                              @RequestBody @Valid SaveSoftwareVersionCommand command) {
        softwareService.updateVersion(versionId, command);
    }

    @PutMapping("/versions/{versionId}/enabled")
    @ExecutionSuccess(GenericRsm.UPDATE_SUCCESS)
    @ExecutionFailed(GenericRsm.UPDATE_FAILED)
    public void setVersionEnabled(@PathVariable("versionId") Long versionId,
                                  @RequestBody @Valid ToggleEnabledCommand command) {
        softwareService.setVersionEnabled(versionId, command.getEnabled());
    }

    @DeleteMapping("/versions/{versionId}")
    @ExecutionSuccess(GenericRsm.DELETE_SUCCESS)
    @ExecutionFailed(GenericRsm.DELETE_FAILED)
    public void deleteVersion(@PathVariable("versionId") Long versionId) {
        softwareService.deleteVersion(versionId);
    }

    @PostMapping("/categories")
    @ExecutionSuccess(GenericRsm.CREATE_SUCCESS)
    @ExecutionFailed(GenericRsm.CREATE_FAILED)
    public CreatedRes createCategory(@RequestBody @Valid SaveSoftwareCategoryCommand command) {
        return new CreatedRes(String.valueOf(categoryService.create(command)));
    }

    @PutMapping("/categories/{id}")
    @ExecutionSuccess(GenericRsm.UPDATE_SUCCESS)
    @ExecutionFailed(GenericRsm.UPDATE_FAILED)
    public void updateCategory(@PathVariable("id") Long id,
                               @RequestBody @Valid SaveSoftwareCategoryCommand command) {
        categoryService.update(id, command);
    }

    @DeleteMapping("/categories/{id}")
    @ExecutionSuccess(GenericRsm.DELETE_SUCCESS)
    @ExecutionFailed(GenericRsm.DELETE_FAILED)
    public void deleteCategory(@PathVariable("id") Long id) {
        categoryService.delete(id);
    }
}
