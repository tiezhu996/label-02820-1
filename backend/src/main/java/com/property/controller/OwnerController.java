package com.property.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.property.aspect.OperationLog;
import com.property.common.PageResult;
import com.property.common.Result;
import com.property.config.AccountSetContext;
import com.property.dto.OwnerDTO;
import com.property.entity.Bill;
import com.property.entity.Owner;
import com.property.mapper.BillMapper;
import com.property.service.OwnerService;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/owners")
@RequiredArgsConstructor
public class OwnerController {
    
    private final OwnerService ownerService;
    private final BillMapper billMapper;
    
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

    /**
     * 业主列表（带当前账期累计欠费）。
     * 与 GET /owners 共用查询条件，额外按 periodMonth(YYYY-MM) 或 periodStart/periodEnd 累加欠费金额。
     * 当 onlyArrears=true 时在分页前过滤无欠费业主，分页 total 反映过滤后的真实总数。
     * 仅返回当前账套数据，账套上下文通过 AccountSetContext 隔离。
     */
    @GetMapping("/with-arrears")
    public Result<PageResult<OwnerWithArrears>> listWithArrears(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String name,
            @RequestParam(required = false) String buildingNo,
            @RequestParam(required = false) String unitNo,
            @RequestParam(required = false) String roomNo,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String periodMonth,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate periodStart,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate periodEnd,
            @RequestParam(required = false) Boolean onlyArrears) {

        Long accountSetId = AccountSetContext.getCurrentAccountSetId();
        LambdaQueryWrapper<Owner> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Owner::getAccountSetId, accountSetId);
        if (name != null && !name.isBlank()) wrapper.like(Owner::getName, name);
        if (buildingNo != null && !buildingNo.isBlank()) wrapper.eq(Owner::getBuildingNo, buildingNo);
        if (unitNo != null && !unitNo.isBlank()) wrapper.eq(Owner::getUnitNo, unitNo);
        if (roomNo != null && !roomNo.isBlank()) wrapper.eq(Owner::getRoomNo, roomNo);
        if (status != null && !status.isBlank()) wrapper.eq(Owner::getStatus, status);
        wrapper.orderByAsc(Owner::getId);
        List<Owner> all = ownerMapper.selectList(wrapper);

        if (all.isEmpty()) {
            return Result.success(new PageResult<>(List.of(), 0L, (long) size, (long) page));
        }

        LocalDate effectiveStart = periodStart;
        LocalDate effectiveEnd = periodEnd;
        if (effectiveStart == null && periodMonth != null && !periodMonth.isBlank()) {
            LocalDate first = LocalDate.parse(periodMonth + "-01");
            effectiveStart = first;
            effectiveEnd = first.withDayOfMonth(first.lengthOfMonth());
        }
        // 未指定账期时默认按"当前月"计算累计欠费，避免汇总历史全部欠费
        if (effectiveStart == null && effectiveEnd == null) {
            LocalDate first = LocalDate.now().withDayOfMonth(1);
            effectiveStart = first;
            effectiveEnd = first.withDayOfMonth(first.lengthOfMonth());
        }

        Map<Long, BigDecimal> arrearsMap = aggregateArrears(
                all.stream().map(Owner::getId).collect(Collectors.toSet()),
                effectiveStart, effectiveEnd);

        List<OwnerWithArrears> rows = new ArrayList<>(all.size());
        for (Owner owner : all) {
            BigDecimal arrears = arrearsMap.getOrDefault(owner.getId(), BigDecimal.ZERO);
            if (Boolean.TRUE.equals(onlyArrears) && arrears.compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }
            OwnerWithArrears row = new OwnerWithArrears();
            row.setId(owner.getId());
            row.setAccountSetId(owner.getAccountSetId());
            row.setName(owner.getName());
            row.setBuildingNo(owner.getBuildingNo());
            row.setUnitNo(owner.getUnitNo());
            row.setRoomNo(owner.getRoomNo());
            row.setPhone(owner.getPhone());
            row.setArea(owner.getArea());
            row.setMoveInDate(owner.getMoveInDate());
            row.setStatus(owner.getStatus());
            row.setCumulativeArrears(arrears);
            rows.add(row);
        }

        long total = rows.size();
        int safeSize = size <= 0 ? 10 : size;
        int safePage = page <= 0 ? 1 : page;
        int from = Math.min((safePage - 1) * safeSize, rows.size());
        int to = Math.min(from + safeSize, rows.size());
        List<OwnerWithArrears> pageRows = rows.subList(from, to);

        return Result.success(new PageResult<>(pageRows, total, (long) safeSize, (long) safePage));
    }

    private Map<Long, BigDecimal> aggregateArrears(Set<Long> ownerIds, LocalDate periodStart, LocalDate periodEnd) {
        Map<Long, BigDecimal> result = new HashMap<>();
        if (ownerIds.isEmpty()) return result;
        Long accountSetId = AccountSetContext.getCurrentAccountSetId();
        LambdaQueryWrapper<Bill> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Bill::getAccountSetId, accountSetId)
               .in(Bill::getOwnerId, ownerIds)
               .in(Bill::getStatus, "UNPAID", "OVERDUE");
        if (periodStart != null) wrapper.ge(Bill::getPeriodStart, periodStart);
        if (periodEnd != null) wrapper.le(Bill::getPeriodEnd, periodEnd);

        for (Bill bill : billMapper.selectList(wrapper)) {
            BigDecimal arrears = bill.getAmount().subtract(bill.getPaidAmount());
            result.merge(bill.getOwnerId(), arrears, BigDecimal::add);
        }
        return result;
    }

    @Data
    public static class OwnerWithArrears {
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
        private BigDecimal cumulativeArrears;
    }
}
