package com.property.dto;

import lombok.Data;
import java.util.List;

@Data
public class LoginResponse {
    private String token;
    private UserInfo user;
    private List<String> permissions;
    
    @Data
    public static class UserInfo {
        private Long id;
        private String username;
        private String realName;
        private String role;
    }
}
