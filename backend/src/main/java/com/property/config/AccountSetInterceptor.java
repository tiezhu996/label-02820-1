package com.property.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class AccountSetInterceptor implements HandlerInterceptor {
    
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String accountSetId = request.getHeader("X-Account-Set-Id");
        if (StringUtils.hasText(accountSetId)) {
            try {
                AccountSetContext.setCurrentAccountSetId(Long.parseLong(accountSetId));
            } catch (NumberFormatException e) {
                AccountSetContext.setCurrentAccountSetId(1L);
            }
        } else {
            AccountSetContext.setCurrentAccountSetId(1L);
        }
        return true;
    }
    
    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, 
                                Object handler, Exception ex) {
        AccountSetContext.clear();
    }
}
