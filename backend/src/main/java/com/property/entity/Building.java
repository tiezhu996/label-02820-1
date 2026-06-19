package com.property.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("t_building")
public class Building {
    
    @TableId(type = IdType.AUTO)
    private Long id;
    
    private Long accountSetId;
    
    private String buildingNo;
    
    private Integer unitCount;
    
    private Integer floorCount;
    
    private Integer roomsPerFloor;
    
    private Integer positionX;
    
    private Integer positionY;
    
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
