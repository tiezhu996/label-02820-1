package com.property.common;

import lombok.Data;
import java.util.List;

@Data
public class ValidationResult {
    private int code;
    private String message;
    private List<ValidationError> errors;
    private long timestamp;
    
    public ValidationResult() {
        this.timestamp = System.currentTimeMillis();
    }
    
    public static ValidationResult error(List<ValidationError> errors) {
        ValidationResult result = new ValidationResult();
        result.setCode(ErrorCode.PARAM_ERROR.getCode());
        result.setMessage("参数校验失败");
        result.setErrors(errors);
        return result;
    }
}
