package com.github.gaohongf.ai.entity.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 自助建议引用的一篇知识库文章。
 *
 * <p>
 * 模型只给得出标题，{@code articleId} 是拿标题回检索结果里对出来的：
 * 对上了前端就能点开原文，对不上（模型改了措辞）就只剩标题可看。
 * 所以它允许为 null —— 有出处总比没有强，只是不能跳转。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class KnowledgeSource {

    private String articleId;

    private String title;
}
