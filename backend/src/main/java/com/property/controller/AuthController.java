package com.property.controller;

import com.property.common.Result;
import com.property.dto.LoginRequest;
import com.property.dto.LoginResponse;
import com.property.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {
    
    private final AuthService authService;
    
    @PostMapping("/login")
    public Result<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        LoginResponse response = authService.login(request);
        return Result.success(response);
    }
    
    @PostMapping("/logout")
    public Result<Void> logout() {
        authService.logout();
        return Result.success();
    }
    
    @GetMapping("/info")
    public Result<LoginResponse> getUserInfo() {
        LoginResponse response = authService.getUserInfo();
        return Result.success(response);
    }
}
