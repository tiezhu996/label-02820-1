package com.property.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("t_fee_standard")
public class FeeStandard {
    @TableId(type = IdType.AUTO)
    private Long id;
    
    private Long accountSetId;
    
    private String feeType;
    
    private String feeName;
    
    private BigDecimal amount;
    
    private String unit;
    
    private String frequency;
    
    private Integer status;
    
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
