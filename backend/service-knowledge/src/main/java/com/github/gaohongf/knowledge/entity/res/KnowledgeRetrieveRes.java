package com.github.gaohongf.knowledge.entity.res;

import java.util.List;

/** 内部检索结果 */
public record KnowledgeRetrieveRes(List<KnowledgeHitRes> hits) {
}
