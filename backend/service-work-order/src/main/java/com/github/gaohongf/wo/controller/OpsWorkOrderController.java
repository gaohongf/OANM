package com.github.gaohongf.wo.controller;

import org.antlr.runtime.tree.TreeFilter.fptr;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.prompt.SystemPromptTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.github.gaohongf.auth.annotation.IsOpen;
import com.github.gaohongf.wo.entity.ai.ProblemDescriptionSuggestions;
import com.github.gaohongf.wo.entity.res.WorkOrderRes;
import com.github.gaohongf.wo.util.ProblemDescriptionSuggestionsBuilder;
import com.lingyun.base.rsm.str.RString;

@RestController
@RequestMapping("/api/ops/work_order")
public class OpsWorkOrderController {
    private final ProblemDescriptionSuggestionsBuilder problemDescriptionSuggestionsBuilder;
    private final ChatClient chatClient;
    private final String systemPrompt = """
                你是一个资深的运维工程师。
                用户提交了故障描述，但往往描述不清。
                你的任务是：
                1. 将用户的模糊描述提炼为一句专业的标准描述。
                2. 推测 3-5 个具体的故障现象作为选项，供用户选择确认。

                必须以纯 JSON 格式返回，不要包含任何 Markdown 标记，格式如下：
                {
                    "clarifiedDescription": "标准描述内容",
                    "options": ["选项1", "选项2", "选项3"]
                }
            """;

    public OpsWorkOrderController(ChatClient.Builder chatClientBuilder) {
        this.problemDescriptionSuggestionsBuilder = new ProblemDescriptionSuggestionsBuilder();
        this.chatClient = chatClientBuilder.build();
    }

    /**
     * 演示 {@code @User} 的解析效果。
     *
     * @param userId 用来指定 createBy/updateBy 指向哪个用户, 省得为了联调改代码。
     *               默认 1, 需要先在 service-auth 里建出这个用户。
     */
    @GetMapping("/{id}")
    public WorkOrderRes getWorkOrder(
            @PathVariable("id") String id,
            @RequestParam(name = "userId", defaultValue = "1") Long userId) {
        WorkOrderRes res = new WorkOrderRes();
        res.setId(id);
        res.setName("测试工单");
        res.setCreateBy(userId);
        res.setUpdateBy(userId);
        return res;
    }

    @IsOpen
    @PostMapping("/chat")
    public ProblemDescriptionSuggestions chat(@RequestBody RString userInput) {
        return problemDescriptionSuggestionsBuilder.build(
                chatClient.prompt()
                        .system(systemPrompt)
                        .user(userInput.str()).call().content());
    }
}
