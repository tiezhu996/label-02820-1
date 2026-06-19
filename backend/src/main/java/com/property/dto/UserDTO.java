package com.property.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import java.util.List;

@Data
public class UserDTO {
    private Long id;
    
    @NotBlank(message = "用户名不能为空")
    @Size(min = 3, max = 50, message = "用户名长度3-50个字符")
    private String username;
    
    @Size(min = 6, max = 50, message = "密码长度6-50个字符")
    private String password;
    
    @Size(max = 50, message = "真实姓名最多50个字符")
    private String realName;
    
    private String role;
    
    private Integer status;
    
    private List<String> permissions;
}
