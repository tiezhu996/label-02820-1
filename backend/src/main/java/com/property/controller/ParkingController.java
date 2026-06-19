package com.property.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.property.aspect.OperationLog;
import com.property.common.BusinessException;
import com.property.common.ErrorCode;
import com.property.common.PageResult;
import com.property.common.Result;
import com.property.config.AccountSetContext;
import com.property.entity.Owner;
import com.property.entity.Parking;
import com.property.mapper.OwnerMapper;
import com.property.mapper.ParkingMapper;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/parkings")
@RequiredArgsConstructor
public class ParkingController {
    
    private final ParkingMapper parkingMapper;
    private final OwnerMapper ownerMapper;
    
    @GetMapping
    public Result<PageResult<Parking>> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String parkingNo,
            @RequestParam(required = false) String status) {
        Page<Parking> pageParam = new Page<>(page, size);
        LambdaQueryWrapper<Parking> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Parking::getAccountSetId, AccountSetContext.getCurrentAccountSetId());
        
        if (StringUtils.hasText(parkingNo)) {
            wrapper.like(Parking::getParkingNo, parkingNo);
        }
        if (StringUtils.hasText(status)) {
            wrapper.eq(Parking::getStatus, status);
        }
        wrapper.orderByAsc(Parking::getParkingNo);
        
        Page<Parking> result = parkingMapper.selectPage(pageParam, wrapper);
        
        result.getRecords().forEach(p -> {
            if (p.getOwnerId() != null) {
                Owner owner = ownerMapper.selectById(p.getOwnerId());
                if (owner != null) {
                    p.setOwnerName(owner.getName());
                }
            }
        });
        
        return Result.success(new PageResult<>(
                result.getRecords(),
                result.getTotal(),
                result.getSize(),
                result.getCurrent()
        ));
    }
    
    @GetMapping("/all")
    public Result<List<Parking>> getAll() {
        LambdaQueryWrapper<Parking> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Parking::getAccountSetId, AccountSetContext.getCurrentAccountSetId());
        return Result.success(parkingMapper.selectList(wrapper));
    }
    
    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    @OperationLog(operation = "创建车位")
    public Result<Void> create(@Valid @RequestBody ParkingDTO dto) {
        Long accountSetId = AccountSetContext.getCurrentAccountSetId();
        
        LambdaQueryWrapper<Parking> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Parking::getAccountSetId, accountSetId)
               .eq(Parking::getParkingNo, dto.getParkingNo());
        if (parkingMapper.selectCount(wrapper) > 0) {
            throw new BusinessException(ErrorCode.PARKING_NO_DUPLICATE);
        }
        
        Parking parking = new Parking();
        parking.setAccountSetId(accountSetId);
        parking.setParkingNo(dto.getParkingNo());
        parking.setStatus("VACANT");
        parkingMapper.insert(parking);
        
        return Result.success();
    }
    
    @PutMapping("/{id}/bind")
    @PreAuthorize("hasRole('ADMIN')")
    @OperationLog(operation = "绑定车位")
    public Result<Void> bind(@PathVariable Long id, @RequestBody BindDTO dto) {
        Parking parking = parkingMapper.selectById(id);
        if (parking == null) {
            throw new BusinessException(ErrorCode.DATA_NOT_FOUND, "车位不存在");
        }
        
        if (parking.getOwnerId() != null && !parking.getOwnerId().equals(dto.getOwnerId())) {
            throw new BusinessException(ErrorCode.PARKING_BINDIED);
        }
        
        parking.setOwnerId(dto.getOwnerId());
        parking.setStatus("USED");
        parkingMapper.updateById(parking);
        
        return Result.success();
    }
    
    @PutMapping("/{id}/unbind")
    @PreAuthorize("hasRole('ADMIN')")
    @OperationLog(operation = "解绑车位")
    public Result<Void> unbind(@PathVariable Long id) {
        Parking parking = parkingMapper.selectById(id);
        if (parking == null) {
            throw new BusinessException(ErrorCode.DATA_NOT_FOUND, "车位不存在");
        }
        
        LambdaUpdateWrapper<Parking> updateWrapper = new LambdaUpdateWrapper<>();
        updateWrapper.eq(Parking::getId, id)
                     .set(Parking::getOwnerId, null)
                     .set(Parking::getStatus, "VACANT");
        parkingMapper.update(null, updateWrapper);
        
        return Result.success();
    }
    
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @OperationLog(operation = "删除车位")
    public Result<Void> delete(@PathVariable Long id) {
        parkingMapper.deleteById(id);
        return Result.success();
    }
    
    @PostMapping("/import")
    @PreAuthorize("hasRole('ADMIN')")
    @OperationLog(operation = "导入车位")
    public Result<Map<String, Object>> importFromExcel(@RequestParam("file") MultipartFile file) {
        List<String> errors = new ArrayList<>();
        int successCount = 0;
        Long accountSetId = AccountSetContext.getCurrentAccountSetId();
        
        try (Workbook workbook = new XSSFWorkbook(file.getInputStream())) {
            Sheet sheet = workbook.getSheetAt(0);
            
            for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row == null) continue;
                
                try {
                    Cell cell = row.getCell(0);
                    String parkingNo = getCellStringValue(cell);
                    
                    if (!StringUtils.hasText(parkingNo)) {
                        errors.add("第" + (i + 1) + "行: 车位号不能为空");
                        continue;
                    }
                    
                    LambdaQueryWrapper<Parking> wrapper = new LambdaQueryWrapper<>();
                    wrapper.eq(Parking::getAccountSetId, accountSetId)
                           .eq(Parking::getParkingNo, parkingNo);
                    
                    Parking existing = parkingMapper.selectOne(wrapper);
                    if (existing != null) {
                        errors.add("第" + (i + 1) + "行: 车位号 " + parkingNo + " 已存在");
                        continue;
                    }
                    
                    Parking parking = new Parking();
                    parking.setAccountSetId(accountSetId);
                    parking.setParkingNo(parkingNo);
                    parking.setStatus("VACANT");
                    parkingMapper.insert(parking);
                    successCount++;
                } catch (Exception e) {
                    errors.add("第" + (i + 1) + "行: " + e.getMessage());
                }
            }
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.EXCEL_PARSE_ERROR, "Excel解析失败: " + e.getMessage());
        }
        
        Map<String, Object> result = new HashMap<>();
        result.put("successCount", successCount);
        result.put("errors", errors);
        return Result.success(result);
    }
    
    private String getCellStringValue(Cell cell) {
        if (cell == null) return "";
        return switch (cell.getCellType()) {
            case STRING -> cell.getStringCellValue().trim();
            case NUMERIC -> String.valueOf((int) cell.getNumericCellValue());
            default -> "";
        };
    }
    
    @GetMapping("/template")
    public void downloadTemplate(jakarta.servlet.http.HttpServletResponse response) throws Exception {
        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("车位导入模板");
            
            // 表头
            Row headerRow = sheet.createRow(0);
            CellStyle headerStyle = workbook.createCellStyle();
            Font headerFont = workbook.createFont();
            headerFont.setBold(true);
            headerStyle.setFont(headerFont);
            
            Cell headerCell = headerRow.createCell(0);
            headerCell.setCellValue("车位号");
            headerCell.setCellStyle(headerStyle);
            
            // 示例数据
            String[] examples = {"A-001", "A-002", "B-001"};
            for (int i = 0; i < examples.length; i++) {
                Row row = sheet.createRow(i + 1);
                row.createCell(0).setCellValue(examples[i]);
            }
            
            sheet.setColumnWidth(0, 20 * 256);
            
            response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
            response.setHeader("Content-Disposition", "attachment; filename=parking_template.xlsx");
            workbook.write(response.getOutputStream());
        }
    }
    
    @Data
    public static class ParkingDTO {
        @NotBlank(message = "车位号不能为空")
        private String parkingNo;
    }
    
    @Data
    public static class BindDTO {
        private Long ownerId;
    }
}
