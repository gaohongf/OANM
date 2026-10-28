package com.github.gaohongf.ai.graph.wo.v2;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.github.gaohongf.ai.entity.dto.WorkOrderProblemDraft;

/** 纯规则判断：时间、地点是否已经齐全。不调用模型。 */
public class ValidateNode extends WorkflowNode {

    @Override
    protected Map<String, Object> execute(OverAllState state) {
        WorkOrderProblemDraft draft = state.value("draft", WorkOrderProblemDraft.class).orElse(null);
        return Map.of("missing", findMissing(draft));
    }

    private List<String> findMissing(WorkOrderProblemDraft draft) {
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

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
