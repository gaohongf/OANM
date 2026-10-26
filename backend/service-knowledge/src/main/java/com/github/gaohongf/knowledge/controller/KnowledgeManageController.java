package com.github.gaohongf.knowledge.controller;

import java.util.List;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.github.gaohongf.knowledge.entity.req.ChangeStatusCommand;
import com.github.gaohongf.knowledge.entity.req.SaveArticleCommand;
import com.github.gaohongf.knowledge.entity.req.SaveCategoryCommand;
import com.github.gaohongf.knowledge.entity.res.CreatedRes;
import com.github.gaohongf.knowledge.entity.res.KnowledgeManageArticleRes;
import com.github.gaohongf.knowledge.entity.res.KnowledgeManageDetailRes;
import com.github.gaohongf.knowledge.entity.res.KnowledgeRevisionRes;
import com.github.gaohongf.knowledge.service.KnowledgeArticleService;
import com.github.gaohongf.knowledge.service.KnowledgeCategoryService;
import com.github.gaohongf.model.PageRes;
import com.lingyun.base.rsm.GenericRsm;
import com.lingyun.base.rsm.annotation.ExecutionFailed;
import com.lingyun.base.rsm.annotation.ExecutionSuccess;

import jakarta.validation.Valid;
import lombok.AllArgsConstructor;

/**
 * 知识管理（挂在「系统管理」下）。
 *
 * <h2>这个控制器里没有 {@code @LoginOnly}</h2>
 * 每个端点都会被启动扫描登记成权限行（{@code GET:/api/knowledge/manage/articles} 等），
 * 再由管理员授给角色。公开读取在 {@link KnowledgePublicController}，两边路径不重叠。
 *
 * <h2>发布状态单独一个端点</h2>
 * 列表上的一次发布/取消发布不该走"读取整篇正文 → 提交整篇正文"那条路，
 * 否则两个管理员同时编辑时，后保存的人会把前一个人的内容一并覆盖。
 */
@AllArgsConstructor
@RestController
@RequestMapping("/api/knowledge")
public class KnowledgeManageController {

    private final KnowledgeArticleService articleService;
    private final KnowledgeCategoryService categoryService;

    @GetMapping("/manage/articles")
    @ExecutionSuccess(GenericRsm.QUERY_SUCCESS)
    @ExecutionFailed(GenericRsm.QUERY_FAILED)
    public PageRes<KnowledgeManageArticleRes> page(
            @RequestParam(name = "current", defaultValue = "1") long current,
            @RequestParam(name = "size", defaultValue = "10") long size,
            @RequestParam(name = "categoryId", required = false) Long categoryId,
            @RequestParam(name = "status", required = false) String status,
            @RequestParam(name = "keyword", required = false) String keyword) {
        return articleService.pageManage(current, size, categoryId, status, keyword);
    }

    @GetMapping("/manage/articles/{id}")
    @ExecutionSuccess(GenericRsm.QUERY_SUCCESS)
    @ExecutionFailed(GenericRsm.QUERY_FAILED)
    public KnowledgeManageDetailRes detail(@PathVariable("id") Long id) {
        return articleService.findManageDetail(id);
    }

    @PostMapping("/articles")
    @ExecutionSuccess(GenericRsm.CREATE_SUCCESS)
    @ExecutionFailed(GenericRsm.CREATE_FAILED)
    public CreatedRes create(@RequestBody @Valid SaveArticleCommand command) {
        return new CreatedRes(String.valueOf(articleService.create(command)));
    }

    @PutMapping("/articles/{id}")
    @ExecutionSuccess(GenericRsm.UPDATE_SUCCESS)
    @ExecutionFailed(GenericRsm.UPDATE_FAILED)
    public void update(@PathVariable("id") Long id,
                       @RequestBody @Valid SaveArticleCommand command) {
        articleService.update(id, command);
    }

    @PutMapping("/articles/{id}/status")
    @ExecutionSuccess(GenericRsm.UPDATE_SUCCESS)
    @ExecutionFailed(GenericRsm.UPDATE_FAILED)
    public void changeStatus(@PathVariable("id") Long id,
                             @RequestBody @Valid ChangeStatusCommand command) {
        articleService.changeStatus(id, command.getStatus());
    }

    @DeleteMapping("/articles/{id}")
    @ExecutionSuccess(GenericRsm.DELETE_SUCCESS)
    @ExecutionFailed(GenericRsm.DELETE_FAILED)
    public void delete(@PathVariable("id") Long id) {
        articleService.delete(id);
    }

    /** 异步重建索引：接口只返回成功受理，失败原因看列表里的索引状态 */
    @PostMapping("/articles/{id}/reindex")
    @ExecutionSuccess(GenericRsm.UPDATE_SUCCESS)
    @ExecutionFailed(GenericRsm.UPDATE_FAILED)
    public void reindex(@PathVariable("id") Long id) {
        articleService.reindex(id);
    }

    @GetMapping("/manage/articles/{id}/revisions")
    @ExecutionSuccess(GenericRsm.QUERY_SUCCESS)
    @ExecutionFailed(GenericRsm.QUERY_FAILED)
    public List<KnowledgeRevisionRes> revisions(@PathVariable("id") Long id) {
        return articleService.listRevisions(id);
    }

    @GetMapping("/manage/articles/{id}/revisions/{revisionId}")
    @ExecutionSuccess(GenericRsm.QUERY_SUCCESS)
    @ExecutionFailed(GenericRsm.QUERY_FAILED)
    public KnowledgeRevisionRes revision(@PathVariable("id") Long id,
                                         @PathVariable("revisionId") Long revisionId) {
        return articleService.findRevision(id, revisionId);
    }

    @PostMapping("/articles/{id}/revisions/{revisionId}/restore")
    @ExecutionSuccess(GenericRsm.UPDATE_SUCCESS)
    @ExecutionFailed(GenericRsm.UPDATE_FAILED)
    public void restore(@PathVariable("id") Long id,
                        @PathVariable("revisionId") Long revisionId) {
        articleService.restore(id, revisionId);
    }

    @PostMapping("/categories")
    @ExecutionSuccess(GenericRsm.CREATE_SUCCESS)
    @ExecutionFailed(GenericRsm.CREATE_FAILED)
    public CreatedRes createCategory(@RequestBody @Valid SaveCategoryCommand command) {
        return new CreatedRes(String.valueOf(categoryService.create(command)));
    }

    @PutMapping("/categories/{id}")
    @ExecutionSuccess(GenericRsm.UPDATE_SUCCESS)
    @ExecutionFailed(GenericRsm.UPDATE_FAILED)
    public void updateCategory(@PathVariable("id") Long id,
                               @RequestBody @Valid SaveCategoryCommand command) {
        categoryService.update(id, command);
    }

    @DeleteMapping("/categories/{id}")
    @ExecutionSuccess(GenericRsm.DELETE_SUCCESS)
    @ExecutionFailed(GenericRsm.DELETE_FAILED)
    public void deleteCategory(@PathVariable("id") Long id) {
        categoryService.delete(id);
    }
}
