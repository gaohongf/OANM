package com.github.gaohongf.knowledge.rsm;

import org.springframework.http.HttpStatus;

import com.lingyun.base.rsm.RsmManager;
import com.lingyun.base.rsm.annotation.RsmInfo;

/**
 * 知识库的消息键。
 *
 * <p>
 * 由 {@code KnowledgeRsmConfiguration} 显式注册。没注册过的键解析不出模板，
 * 会退回默认消息 —— 拒绝被渲染成"成功"，而且不抛错、不告警。
 */
public class KnowledgeRsm implements RsmManager {

    /** 按 id 查文章但不存在（已删除或从未创建），或文章尚未发布 */
    @RsmInfo(template = "知识库文章不存在", status = HttpStatus.NOT_FOUND)
    public static final String KNOWLEDGE_ARTICLE_NOT_FOUND = "Knowledge_KNOWLEDGE_ARTICLE_NOT_FOUND";

    /** 新建/编辑文章时指定的分类不存在 */
    @RsmInfo(template = "知识库分类不存在", status = HttpStatus.BAD_REQUEST)
    public static final String KNOWLEDGE_CATEGORY_NOT_FOUND = "Knowledge_KNOWLEDGE_CATEGORY_NOT_FOUND";

    /** 分类名称重复 */
    @RsmInfo(template = "分类名称已存在", status = HttpStatus.CONFLICT)
    public static final String KNOWLEDGE_CATEGORY_EXISTS = "Knowledge_KNOWLEDGE_CATEGORY_EXISTS";

    /** 分类下还有未删除的文章，不能删除 */
    @RsmInfo(template = "该分类下还有文章，不能删除", status = HttpStatus.CONFLICT)
    public static final String KNOWLEDGE_CATEGORY_IN_USE = "Knowledge_KNOWLEDGE_CATEGORY_IN_USE";

    /** 同一分类下已存在同名文章 */
    @RsmInfo(template = "该分类下已存在同名文章", status = HttpStatus.CONFLICT)
    public static final String KNOWLEDGE_ARTICLE_TITLE_EXISTS = "Knowledge_KNOWLEDGE_ARTICLE_TITLE_EXISTS";

    /** 状态取值非法 */
    @RsmInfo(template = "状态只能是 DRAFT 或 PUBLISHED", status = HttpStatus.BAD_REQUEST)
    public static final String KNOWLEDGE_STATUS_INVALID = "Knowledge_KNOWLEDGE_STATUS_INVALID";

    /** 按 id 查修订但不存在，或该修订不属于这篇文章 */
    @RsmInfo(template = "修订版本不存在", status = HttpStatus.NOT_FOUND)
    public static final String KNOWLEDGE_REVISION_NOT_FOUND = "Knowledge_KNOWLEDGE_REVISION_NOT_FOUND";

    /** 编辑/回滚时被他人抢先修改（乐观锁或修订号冲突） */
    @RsmInfo(template = "文章已被他人修改，请刷新后重试", status = HttpStatus.CONFLICT)
    public static final String KNOWLEDGE_CONCURRENT_MODIFIED = "Knowledge_KNOWLEDGE_CONCURRENT_MODIFIED";

    /**
     * 内部检索接口嵌入失败。
     * <p>
     * 返回 503 而不是空列表：调用方需要能区分"确实没有相关内容"和"检索服务挂了"。
     */
    @RsmInfo(template = "知识库检索暂不可用", status = HttpStatus.SERVICE_UNAVAILABLE)
    public static final String KNOWLEDGE_RETRIEVE_UNAVAILABLE = "Knowledge_KNOWLEDGE_RETRIEVE_UNAVAILABLE";

    /** 检索内容为空 */
    @RsmInfo(template = "检索内容不能为空", status = HttpStatus.BAD_REQUEST)
    public static final String KNOWLEDGE_QUERY_REQUIRED = "Knowledge_KNOWLEDGE_QUERY_REQUIRED";
}
