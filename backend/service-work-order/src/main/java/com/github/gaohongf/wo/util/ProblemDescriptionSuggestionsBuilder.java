package com.github.gaohongf.wo.util;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.gaohongf.wo.entity.ai.ProblemDescriptionSuggestions;

@Component
public class ProblemDescriptionSuggestionsBuilder {

    private ObjectMapper objectMapper = new ObjectMapper();

    public String wash(String aiOutput) {
        String cleaned = aiOutput.replaceAll("```json", "").replaceAll("```", "").trim();
        return cleaned;
    }

    public ProblemDescriptionSuggestions build(String aiOutput) {
        try {
            return objectMapper.readValue(wash(aiOutput), ProblemDescriptionSuggestions.class);
        } catch (Exception e) {
            ProblemDescriptionSuggestions suggestions = new ProblemDescriptionSuggestions();
            suggestions.setClarifiedDescription(wash(aiOutput));
            return suggestions;
        }
    }
}
