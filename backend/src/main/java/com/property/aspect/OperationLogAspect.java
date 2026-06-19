package com.property.aspect;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.property.entity.SysOperationLog;
import com.property.mapper.SysOperationLogMapper;
import com.property.security.SecurityUtil;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class OperationLogAspect {
    
    private final SysOperationLogMapper logMapper;
    private final ObjectMapper objectMapper;
    
    @Around("@annotation(operationLog)")
    public Object around(ProceedingJoinPoint point, OperationLog operationLog) throws Throwable {
        Object result = point.proceed();
        
        try {
            saveLog(point, operationLog);
        } catch (Exception e) {
            log.error("保存操作日志失败", e);
        }
        
        return result;
    }
    
    private void saveLog(ProceedingJoinPoint point, OperationLog operationLog) throws Exception {
        MethodSignature signature = (MethodSignature) point.getSignature();
        String methodName = signature.getDeclaringTypeName() + "." + signature.getName();
        
        SysOperationLog logEntity = new SysOperationLog();
        logEntity.setUserId(SecurityUtil.getCurrentUserId());
        logEntity.setUsername(SecurityUtil.getCurrentUsername());
        logEntity.setOperation(operationLog.operation());
        logEntity.setMethod(methodName);
        
        Object[] args = point.getArgs();
        if (args != null && args.length > 0) {
            String params = objectMapper.writeValueAsString(args);
            if (params.length() > 2000) {
                params = params.substring(0, 2000);
            }
            logEntity.setParams(params);
        }
        
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes != null) {
            HttpServletRequest request = attributes.getRequest();
            logEntity.setIp(getIpAddress(request));
        }
        
        logMapper.insert(logEntity);
    }
    
    private String getIpAddress(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("Proxy-Client-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("WL-Proxy-Client-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        if (ip != null && ip.contains(",")) {
            ip = ip.split(",")[0].trim();
        }
        return ip;
    }
}
