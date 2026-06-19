package com.property.dto;

import jakarta.validation.constraints.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class OwnerDTO {
    private Long id;
    
    @NotBlank(message = "业主姓名不能为空")
    @Size(max = 50, message = "业主姓名最多50个字符")
    private String name;
    
    @NotBlank(message = "楼栋号不能为空")
    @Size(max = 20, message = "楼栋号最多20个字符")
    private String buildingNo;
    
    @NotBlank(message = "单元号不能为空")
    @Size(max = 20, message = "单元号最多20个字符")
    private String unitNo;
    
    @NotBlank(message = "房间号不能为空")
    @Size(max = 20, message = "房间号最多20个字符")
    private String roomNo;
    
    @Pattern(regexp = "^$|^1[3-9]\\d{9}$", message = "电话格式不正确")
    private String phone;
    
    @NotNull(message = "房屋面积不能为空")
    @DecimalMin(value = "0.01", message = "房屋面积必须大于0")
    private BigDecimal area;
    
    private LocalDate moveInDate;
    
    private String status;
}
