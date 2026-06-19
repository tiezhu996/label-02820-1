package com.property.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("t_receivable")
public class Receivable {
    @TableId(type = IdType.AUTO)
    private Long id;
    
    private Long accountSetId;
    
    private Long ownerId;
    
    private Long parkingId;
    
    private String feeType;
    
    private String periodMonth;
    
    private BigDecimal amount;
    
    private BigDecimal paidAmount;
    
    private Integer isLocked;
    
    private BigDecimal cumulativeAmount;
    
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
    
    @TableField(exist = false)
    private String ownerName;
    
    @TableField(exist = false)
    private String roomInfo;
}
