package com.property.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("t_parking")
public class Parking {
    @TableId(type = IdType.AUTO)
    private Long id;
    
    private Long accountSetId;
    
    private String parkingNo;
    
    private Long ownerId;
    
    private String status;
    
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
    
    @TableField(exist = false)
    private String ownerName;
}
