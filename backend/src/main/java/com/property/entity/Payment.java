package com.property.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("t_payment")
public class Payment {
    @TableId(type = IdType.AUTO)
    private Long id;
    
    private Long accountSetId;
    
    private String paymentNo;
    
    private Long billId;
    
    private Long ownerId;
    
    private BigDecimal amount;
    
    private BigDecimal discountRate;
    
    private BigDecimal discountAmount;
    
    private BigDecimal actualAmount;
    
    private String paymentMethod;
    
    private String paymentPeriod;
    
    private Long operatorId;
    
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    
    @TableField(exist = false)
    private String ownerName;
    
    @TableField(exist = false)
    private String operatorName;
}
