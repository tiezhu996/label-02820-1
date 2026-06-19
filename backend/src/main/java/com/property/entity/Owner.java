package com.property.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("t_owner")
public class Owner {
    @TableId(type = IdType.AUTO)
    private Long id;
    
    private Long accountSetId;
    
    private String name;
    
    private String buildingNo;
    
    private String unitNo;
    
    private String roomNo;
    
    private String phone;
    
    private BigDecimal area;
    
    private LocalDate moveInDate;
    
    private String status;
    
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
    
    @TableField(exist = false)
    private BigDecimal cumulativeArrears;
}
