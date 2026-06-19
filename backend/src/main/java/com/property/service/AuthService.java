package com.property.service;

import com.property.dto.LoginRequest;
import com.property.dto.LoginResponse;

public interface AuthService {
    LoginResponse login(LoginRequest request);
    void logout();
    LoginResponse getUserInfo();
}
