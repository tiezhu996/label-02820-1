package com.property.common;

import lombok.Getter;

@Getter
public enum ErrorCode {
    // 认证相关 401xx
    AUTH_FAILED(40101, "认证失败"),
    TOKEN_EXPIRED(40102, "Token已过期"),
    TOKEN_INVALID(40103, "Token无效"),
    
    // 权限相关 403xx
    ACCESS_DENIED(40301, "权限不足"),
    DATA_LOCKED(40302, "数据已锁定，无法修改"),
    FORBIDDEN(40303, "禁止访问"),
    
    // 参数校验 400xx
    PARAM_ERROR(40001, "参数错误"),
    PARAM_MISSING(40002, "缺少必填参数"),
    PARAM_FORMAT_ERROR(40003, "参数格式错误"),
    
    // 数据相关 404xx
    DATA_NOT_FOUND(40401, "数据不存在"),
    DATA_DUPLICATE(40402, "数据已存在"),
    
    // 业务相关 400xx
    BUSINESS_ERROR(40010, "业务处理失败"),
    PARKING_BINDIED(40011, "车位已被绑定"),
    PARKING_NO_DUPLICATE(40012, "车位号已存在"),
    OWNER_NOT_FOUND(40013, "业主不存在"),
    BILL_NOT_FOUND(40014, "账单不存在"),
    
    // 系统相关 500xx
    SYSTEM_ERROR(50001, "系统错误"),
    FILE_UPLOAD_ERROR(50002, "文件上传失败"),
    EXCEL_PARSE_ERROR(50003, "Excel解析失败"),
    BACKUP_ERROR(50004, "备份失败"),
    RESTORE_ERROR(50005, "恢复失败");
    
    private final int code;
    private final String message;
    
    ErrorCode(int code, String message) {
        this.code = code;
        this.message = message;
    }
}
