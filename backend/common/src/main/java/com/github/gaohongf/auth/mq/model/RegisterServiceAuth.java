package com.github.gaohongf.auth.mq.model;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data 
@AllArgsConstructor 
@NoArgsConstructor 
public final class RegisterServiceAuth {
    private String instanceId;
    private List<String> auths;
}