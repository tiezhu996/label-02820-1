package com.property.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("t_bill")
public class Bill {
    @TableId(type = IdType.AUTO)
    private Long id;
    
    private Long accountSetId;
    
    private String billNo;
    
    private Long ownerId;
    
    private Long parkingId;
    
    private String feeType;
    
    private String feeName;
    
    private BigDecimal amount;
    
    private BigDecimal paidAmount;
    
    private LocalDate periodStart;
    
    private LocalDate periodEnd;
    
    private LocalDate dueDate;
    
    private String status;
    
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
    
    @TableField(exist = false)
    private String ownerName;
    
    @TableField(exist = false)
    private String roomInfo;
}
