package com.github.gaohongf.ai.graph.wo;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.AsyncNodeAction;
import com.github.gaohongf.ai.entity.dto.WorkOrderProblemDraft;

public class ValidateNode implements AsyncNodeAction {

    @Override
    public CompletableFuture<Map<String, Object>> apply(OverAllState state) {
        return AsyncNodeAction.node_async((s) -> {
            System.out.println("================{validate}================");
            WorkOrderProblemDraft draft = s.value("draft", WorkOrderProblemDraft.class).orElse(null);
            return Map.of("missing", findMissingRequired(draft));
        }).apply(state);
    }

    private List<String> findMissingRequired(WorkOrderProblemDraft draft) {
        if (draft == null) {
            return List.of("时间描述", "地点描述");
        }
        List<String> missing = new ArrayList<>();
        if (isBlank(draft.getUserLocationDescription())) {
            missing.add("地点描述");
        }
        if (isBlank(draft.getUserTimeDescription())) {
            missing.add("时间描述");
        }
        return missing;
    }

    private boolean isBlank(String str) {
        return str == null || str.isBlank();
    }
}
