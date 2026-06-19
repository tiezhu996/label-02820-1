package com.property.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("sys_permission")
public class SysPermission {
    @TableId(type = IdType.AUTO)
    private Long id;
    
    private Long userId;
    
    private String permissionCode;
    
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
