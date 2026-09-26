package com.github.gaohongf.auth.res;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@AllArgsConstructor 
@NoArgsConstructor 
@Data 
public class UserRes {
    private Long id;
    private String username;
    private String nickname;
    private Boolean locked;
}
