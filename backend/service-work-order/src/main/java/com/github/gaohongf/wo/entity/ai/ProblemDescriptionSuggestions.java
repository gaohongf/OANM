package com.github.gaohongf.wo.entity.ai;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data 
@AllArgsConstructor 
@NoArgsConstructor 
public class ProblemDescriptionSuggestions {
    private String clarifiedDescription;
    private List<String> options;
}
