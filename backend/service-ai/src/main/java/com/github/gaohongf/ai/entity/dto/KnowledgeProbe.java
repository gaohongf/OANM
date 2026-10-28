package com.github.gaohongf.ai.entity.dto;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 知识库故障确认：模型看完命中的文章后，把它们各自对应的现象并列问出来。
 *
 * <p>
 * 知识库里常常同时有"卡纸"和"打印没反应"这类针对不同情况的文章。只问其中一篇，
 * 用户遇到的是另一篇时，那条知识就被错过了；所以这里让每篇文章给一句用户口径的
 * 回答，界面上摆成一组卡片，最后再补一项"都不是"。
 *
 * <p>
 * {@code question} 和 {@code items} 里的话都是给用户看的：前者是这一轮的提问，
 * 后者是卡片文字 —— 点卡片时直接把这句话作为用户发言发回后端，
 * 所以它们要能独立成句，并且带上具体故障（"是打印机卡纸了"），后续检索才用得上。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class KnowledgeProbe {

    /**
     * 这些文章值不值得问一句：同一类设备或系统、故障是可能的成因即可。
     * 用户还没说出具体现象不算不匹配 —— 那正是要问的。false 时这一轮不提问。
     */
    private boolean matched;

    /** 一句提问，把各项现象并列问出来；不提知识库、不提文章标题 */
    private String question;

    /** 每篇文章一项，最多 3 项；顺序即卡片顺序 */
    private List<ProbeItem> items;
}
