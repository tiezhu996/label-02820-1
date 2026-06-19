package com.property.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("t_bill_schedule")
public class BillSchedule {
    
    @TableId(type = IdType.AUTO)
    private Long id;
    
    private Long accountSetId;
    
    private String feeType;

    private String customFeeType;

    private Integer generateDay;
    
    private String periodType;
    
    private Integer dueDays;
    
    private Integer status;
    
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
