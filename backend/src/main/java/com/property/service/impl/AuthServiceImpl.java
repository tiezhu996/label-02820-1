package com.property.service.impl;

import com.property.common.BusinessException;
import com.property.common.ErrorCode;
import com.property.dto.LoginRequest;
import com.property.dto.LoginResponse;
import com.property.entity.SysUser;
import com.property.mapper.SysPermissionMapper;
import com.property.mapper.SysUserMapper;
import com.property.security.SecurityUtil;
import com.property.service.AuthService;
import com.property.util.JwtUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {
    
    private final SysUserMapper userMapper;
    private final SysPermissionMapper permissionMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    
    @Override
    public LoginResponse login(LoginRequest request) {
        SysUser user = userMapper.findByUsername(request.getUsername());
        
        if (user == null) {
            throw new BusinessException(ErrorCode.AUTH_FAILED, "用户名或密码错误");
        }
        
        if (user.getStatus() != 1) {
            throw new BusinessException(ErrorCode.AUTH_FAILED, "账户已被禁用");
        }
        
        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new BusinessException(ErrorCode.AUTH_FAILED, "用户名或密码错误");
        }
        
        String token = jwtUtil.generateToken(user.getId(), user.getUsername(), user.getRole());
        List<String> permissions = permissionMapper.findPermissionsByUserId(user.getId());
        
        LoginResponse response = new LoginResponse();
        response.setToken(token);
        response.setPermissions(permissions);
        
        LoginResponse.UserInfo userInfo = new LoginResponse.UserInfo();
        userInfo.setId(user.getId());
        userInfo.setUsername(user.getUsername());
        userInfo.setRealName(user.getRealName());
        userInfo.setRole(user.getRole());
        response.setUser(userInfo);
        
        return response;
    }
    
    @Override
    public void logout() {
        // JWT无状态，客户端删除token即可
    }
    
    @Override
    public LoginResponse getUserInfo() {
        Long userId = SecurityUtil.getCurrentUserId();
        if (userId == null) {
            throw new BusinessException(ErrorCode.AUTH_FAILED);
        }
        
        SysUser user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ErrorCode.AUTH_FAILED);
        }
        
        List<String> permissions = permissionMapper.findPermissionsByUserId(userId);
        
        LoginResponse response = new LoginResponse();
        response.setPermissions(permissions);
        
        LoginResponse.UserInfo userInfo = new LoginResponse.UserInfo();
        userInfo.setId(user.getId());
        userInfo.setUsername(user.getUsername());
        userInfo.setRealName(user.getRealName());
        userInfo.setRole(user.getRole());
        response.setUser(userInfo);
        
        return response;
    }
}
