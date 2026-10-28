package com.github.gaohongf.ai.entity.dto;

import java.util.List;

import lombok.Data;
import lombok.NoArgsConstructor;

/** 知识库内部检索接口 {@code data} 字段的形状。 */
@Data
@NoArgsConstructor
public class KnowledgeRetrieveResult {

    private List<KnowledgeHit> hits;
}
