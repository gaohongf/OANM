package com.github.gaohongf.ai.entity.dto;

import org.springframework.context.annotation.Description;

/**
 * @author gaohongf
 * 用于决定AI的输出类型
 * @param type 类型
 */
public record RoutingResponse(

    // 这段话会随 JSON Schema 一起进到判定的提示里（.entity() 用的就是它），
    // 所以它是提示词的一部分、不是普通注释 —— 措辞要和 AiAssistantServiceImpl 的
    // userPrompt【第一步 判定】保持一致，两边说法不同会把模型拉向"可以推断就给选项"，
    // 结果就是缺时间地点时照样给选项。
    @Description("""
            判定结果，只能是 OPTIONS 或 PROBLEM。
            OPTIONS: 时间和地点都已经说清楚，这一轮给出意图选项。
            PROBLEM: 还缺时间或缺地点，或者与运维无关，这一轮只向用户提问。
            """)
    Type type
) {


    public enum Type {
        OPTIONS, // 选项
        PROBLEM // 问题
    }

}
