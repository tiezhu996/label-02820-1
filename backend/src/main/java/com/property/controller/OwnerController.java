package com.property.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.property.aspect.OperationLog;
import com.property.common.PageResult;
import com.property.common.Result;
import com.property.dto.OwnerDTO;
import com.property.entity.Owner;
import com.property.service.OwnerService;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/owners")
@RequiredArgsConstructor
public class OwnerController {
    
    private final OwnerService ownerService;
    
    @GetMapping
    public Result<PageResult<Owner>> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String name,
            @RequestParam(required = false) String buildingNo,
            @RequestParam(required = false) String unitNo,
            @RequestParam(required = false) String roomNo,
            @RequestParam(required = false) String status) {
        Page<Owner> pageResult = ownerService.getPage(page, size, name, buildingNo, unitNo, roomNo, status);
        return Result.success(new PageResult<>(
                pageResult.getRecords(),
                pageResult.getTotal(),
                pageResult.getSize(),
                pageResult.getCurrent()
        ));
    }
    
    @GetMapping("/all")
    public Result<List<Owner>> getAll() {
        return Result.success(ownerService.getAll());
    }
    
    @GetMapping("/{id}")
    public Result<Owner> getById(@PathVariable Long id) {
        return Result.success(ownerService.getById(id));
    }
    
    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    @OperationLog(operation = "创建业主")
    public Result<Void> create(@Valid @RequestBody OwnerDTO dto) {
        ownerService.create(dto);
        return Result.success();
    }
    
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @OperationLog(operation = "更新业主")
    public Result<Void> update(@PathVariable Long id, @Valid @RequestBody OwnerDTO dto) {
        ownerService.update(id, dto);
        return Result.success();
    }
    
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @OperationLog(operation = "删除业主")
    public Result<Void> delete(@PathVariable Long id) {
        ownerService.delete(id);
        return Result.success();
    }
    
    @PostMapping("/import")
    @PreAuthorize("hasRole('ADMIN')")
    @OperationLog(operation = "导入业主")
    public Result<Map<String, Object>> importFromExcel(@RequestParam("file") MultipartFile file) {
        Map<String, Object> result = ownerService.importFromExcel(file);
        return Result.success(result);
    }
    
    @GetMapping("/template")
    public void downloadTemplate(HttpServletResponse response) throws IOException {
        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("业主导入模板");
            
            // 创建表头样式
            CellStyle headerStyle = workbook.createCellStyle();
            headerStyle.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            Font headerFont = workbook.createFont();
            headerFont.setBold(true);
            headerStyle.setFont(headerFont);
            
            // 创建表头
            String[] headers = {"业主姓名", "楼栋号", "单元号", "房间号", "电话", "面积(㎡)", "入住日期(yyyy-MM-dd)", "状态(OCCUPIED/VACANT)"};
            Row headerRow = sheet.createRow(0);
            for (int i = 0; i < headers.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers[i]);
                cell.setCellStyle(headerStyle);
                sheet.setColumnWidth(i, 20 * 256);
            }
            
            // 添加示例数据
            Row exampleRow = sheet.createRow(1);
            exampleRow.createCell(0).setCellValue("张三");
            exampleRow.createCell(1).setCellValue("1");
            exampleRow.createCell(2).setCellValue("1单元");
            exampleRow.createCell(3).setCellValue("101");
            exampleRow.createCell(4).setCellValue("13800001001");
            exampleRow.createCell(5).setCellValue(89.5);
            exampleRow.createCell(6).setCellValue("2024-01-15");
            exampleRow.createCell(7).setCellValue("OCCUPIED");
            
            response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
            response.setHeader("Content-Disposition", "attachment; filename=" + 
                URLEncoder.encode("业主导入模板.xlsx", StandardCharsets.UTF_8));
            workbook.write(response.getOutputStream());
        }
    }
}
