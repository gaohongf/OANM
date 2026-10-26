package com.github.gaohongf.knowledge.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.github.gaohongf.auth.annotation.LoginOnly;
import com.github.gaohongf.knowledge.entity.res.KnowledgeArticleDetailRes;
import com.github.gaohongf.knowledge.entity.res.KnowledgeArticleRes;
import com.github.gaohongf.knowledge.entity.res.KnowledgeCategoryRes;
import com.github.gaohongf.knowledge.entity.res.KnowledgeSearchRes;
import com.github.gaohongf.knowledge.service.KnowledgeArticleService;
import com.github.gaohongf.knowledge.service.KnowledgeCategoryService;
import com.github.gaohongf.knowledge.service.KnowledgeSearchService;
import com.github.gaohongf.model.PageRes;
import com.lingyun.base.rsm.GenericRsm;
import com.lingyun.base.rsm.annotation.ExecutionFailed;
import com.lingyun.base.rsm.annotation.ExecutionSuccess;

import lombok.AllArgsConstructor;

/**
 * 知识库浏览页（所有登录用户可见）。
 *
 * <h2>为什么标 {@code @LoginOnly} 而不是开放</h2>
 * 知识库是内网经验沉淀，登录即读，不需要管理员逐个授权；{@code @LoginOnly}
 * 不产生权限行，新人不会遇到"菜单在、点进去 403"。
 * 写入与索引维护在 {@link KnowledgeManageController}，按权限控制。
 */
@AllArgsConstructor
@RestController
@RequestMapping("/api/knowledge")
public class KnowledgePublicController {

    private final KnowledgeCategoryService categoryService;
    private final KnowledgeArticleService articleService;
    private final KnowledgeSearchService searchService;

    /** 分类下拉，带已发布文章数 */
    @LoginOnly
    @GetMapping("/categories")
    @ExecutionSuccess(GenericRsm.QUERY_SUCCESS)
    @ExecutionFailed(GenericRsm.QUERY_FAILED)
    public List<KnowledgeCategoryRes> categories() {
        return categoryService.list();
    }

    /** 分页浏览，只返回已发布文章；带 keyword 时退化为关键字筛选 */
    @LoginOnly
    @GetMapping("/articles")
    @ExecutionSuccess(GenericRsm.QUERY_SUCCESS)
    @ExecutionFailed(GenericRsm.QUERY_FAILED)
    public PageRes<KnowledgeArticleRes> page(
            @RequestParam(name = "current", defaultValue = "1") long current,
            @RequestParam(name = "size", defaultValue = "10") long size,
            @RequestParam(name = "categoryId", required = false) Long categoryId,
            @RequestParam(name = "keyword", required = false) String keyword) {
        return articleService.pagePublic(current, size, categoryId, keyword);
    }

    /**
     * 语义搜索（语义优先、关键字补足），不分页。
     * <p>
     * 字面量 {@code /articles/search} 比 {@code /articles/{id}} 更具体，
     * Spring 会优先匹配它。
     */
    @LoginOnly
    @GetMapping("/articles/search")
    @ExecutionSuccess(GenericRsm.QUERY_SUCCESS)
    @ExecutionFailed(GenericRsm.QUERY_FAILED)
    public KnowledgeSearchRes search(
            @RequestParam(name = "q") String q,
            @RequestParam(name = "limit", required = false) Integer limit,
            @RequestParam(name = "categoryId", required = false) Long categoryId) {
        return searchService.search(q, limit, categoryId);
    }

    /** 详情：只返回已发布文章，并原子递增浏览次数 */
    @LoginOnly
    @GetMapping("/articles/{id}")
    @ExecutionSuccess(GenericRsm.QUERY_SUCCESS)
    @ExecutionFailed(GenericRsm.QUERY_FAILED)
    public KnowledgeArticleDetailRes detail(@PathVariable("id") Long id) {
        return articleService.findPublicDetail(id);
    }
}
