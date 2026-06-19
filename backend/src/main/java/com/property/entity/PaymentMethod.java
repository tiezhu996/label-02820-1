package com.property.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("t_payment_method")
public class PaymentMethod {
    
    @TableId(type = IdType.AUTO)
    private Long id;
    
    private Long accountSetId;
    
    private String methodName;
    
    private Integer status;
    
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
