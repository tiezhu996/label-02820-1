package com.property.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.property.aspect.OperationLog;
import com.property.common.PageResult;
import com.property.common.Result;
import com.property.config.AccountSetContext;
import com.property.entity.*;
import com.property.mapper.*;
import jakarta.servlet.http.HttpServletResponse;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/bills")
@RequiredArgsConstructor
public class BillController {
    
    private final BillMapper billMapper;
    private final OwnerMapper ownerMapper;
    private final ParkingMapper parkingMapper;
    private final FeeStandardMapper feeStandardMapper;
    
    @GetMapping
    public Result<PageResult<Bill>> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String feeType,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Long ownerId,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate endDate) {
        
        Page<Bill> pageParam = new Page<>(page, size);
        LambdaQueryWrapper<Bill> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Bill::getAccountSetId, AccountSetContext.getCurrentAccountSetId());
        
        if (StringUtils.hasText(feeType)) {
            wrapper.eq(Bill::getFeeType, feeType);
        }
        if (StringUtils.hasText(status)) {
            wrapper.eq(Bill::getStatus, status);
        }
        if (ownerId != null) {
            wrapper.eq(Bill::getOwnerId, ownerId);
        }
        if (startDate != null) {
            wrapper.ge(Bill::getPeriodStart, startDate);
        }
        if (endDate != null) {
            wrapper.le(Bill::getPeriodEnd, endDate);
        }
        wrapper.orderByDesc(Bill::getCreateTime);
        
        Page<Bill> result = billMapper.selectPage(pageParam, wrapper);
        
        result.getRecords().forEach(bill -> {
            Owner owner = ownerMapper.selectById(bill.getOwnerId());
            if (owner != null) {
                bill.setOwnerName(owner.getName());
                bill.setRoomInfo(owner.getBuildingNo() + "-" + owner.getUnitNo() + "-" + owner.getRoomNo());
            }
        });
        
        return Result.success(new PageResult<>(
                result.getRecords(),
                result.getTotal(),
                result.getSize(),
                result.getCurrent()
        ));
    }
    
    @PostMapping("/generate")
    @PreAuthorize("hasRole('ADMIN')")
    @OperationLog(operation = "生成账单")
    public Result<Integer> generate(@RequestBody GenerateBillDTO dto) {
        Long accountSetId = AccountSetContext.getCurrentAccountSetId();
        int count = 0;
        
        LambdaQueryWrapper<FeeStandard> feeWrapper = new LambdaQueryWrapper<>();
        feeWrapper.eq(FeeStandard::getAccountSetId, accountSetId)
                  .eq(FeeStandard::getFeeType, dto.getFeeType())
                  .eq(FeeStandard::getStatus, 1);
        FeeStandard feeStandard = feeStandardMapper.selectOne(feeWrapper);
        
        if (feeStandard == null) {
            return Result.success(0);
        }
        
        // 计算账单周期月数（用于金额折算）
        int periodMonths = calculatePeriodMonths(dto.getPeriodStart(), dto.getPeriodEnd());
        
        if ("PROPERTY".equals(dto.getFeeType())) {
            LambdaQueryWrapper<Owner> ownerWrapper = new LambdaQueryWrapper<>();
            ownerWrapper.eq(Owner::getAccountSetId, accountSetId);
            List<Owner> owners = ownerMapper.selectList(ownerWrapper);
            
            for (Owner owner : owners) {
                // 检查是否已存在该周期账单
                if (billExists(accountSetId, owner.getId(), null, dto.getFeeType(), dto.getPeriodStart(), dto.getPeriodEnd())) {
                    continue;
                }
                Bill bill = new Bill();
                bill.setAccountSetId(accountSetId);
                bill.setBillNo(generateBillNo());
                bill.setOwnerId(owner.getId());
                bill.setFeeType(dto.getFeeType());
                bill.setFeeName(feeStandard.getFeeName());
                // 金额 = 面积 × 单价 × 周期月数
                bill.setAmount(owner.getArea().multiply(feeStandard.getAmount()).multiply(BigDecimal.valueOf(periodMonths)));
                bill.setPaidAmount(BigDecimal.ZERO);
                bill.setPeriodStart(dto.getPeriodStart());
                bill.setPeriodEnd(dto.getPeriodEnd());
                bill.setDueDate(dto.getDueDate());
                bill.setStatus("UNPAID");
                billMapper.insert(bill);
                count++;
            }
        } else if ("PARKING".equals(dto.getFeeType())) {
            LambdaQueryWrapper<Parking> parkingWrapper = new LambdaQueryWrapper<>();
            parkingWrapper.eq(Parking::getAccountSetId, accountSetId)
                          .isNotNull(Parking::getOwnerId);
            List<Parking> parkings = parkingMapper.selectList(parkingWrapper);
            
            for (Parking parking : parkings) {
                if (billExists(accountSetId, parking.getOwnerId(), parking.getId(), dto.getFeeType(), dto.getPeriodStart(), dto.getPeriodEnd())) {
                    continue;
                }
                Bill bill = new Bill();
                bill.setAccountSetId(accountSetId);
                bill.setBillNo(generateBillNo());
                bill.setOwnerId(parking.getOwnerId());
                bill.setParkingId(parking.getId());
                bill.setFeeType(dto.getFeeType());
                bill.setFeeName(feeStandard.getFeeName());
                // 金额 = 单价 × 周期月数
                bill.setAmount(feeStandard.getAmount().multiply(BigDecimal.valueOf(periodMonths)));
                bill.setPaidAmount(BigDecimal.ZERO);
                bill.setPeriodStart(dto.getPeriodStart());
                bill.setPeriodEnd(dto.getPeriodEnd());
                bill.setDueDate(dto.getDueDate());
                bill.setStatus("UNPAID");
                billMapper.insert(bill);
                count++;
            }
        } else {
            // 自定义收费项目：为所有业主生成账单
            LambdaQueryWrapper<Owner> ownerWrapper = new LambdaQueryWrapper<>();
            ownerWrapper.eq(Owner::getAccountSetId, accountSetId);
            List<Owner> owners = ownerMapper.selectList(ownerWrapper);
            
            for (Owner owner : owners) {
                if (billExists(accountSetId, owner.getId(), null, dto.getFeeType(), dto.getPeriodStart(), dto.getPeriodEnd())) {
                    continue;
                }
                Bill bill = new Bill();
                bill.setAccountSetId(accountSetId);
                bill.setBillNo(generateBillNo());
                bill.setOwnerId(owner.getId());
                bill.setFeeType(dto.getFeeType());
                bill.setFeeName(feeStandard.getFeeName());
                // 自定义收费：固定金额 × 周期月数
                bill.setAmount(feeStandard.getAmount().multiply(BigDecimal.valueOf(periodMonths)));
                bill.setPaidAmount(BigDecimal.ZERO);
                bill.setPeriodStart(dto.getPeriodStart());
                bill.setPeriodEnd(dto.getPeriodEnd());
                bill.setDueDate(dto.getDueDate());
                bill.setStatus("UNPAID");
                billMapper.insert(bill);
                count++;
            }
        }
        
        return Result.success(count);
    }
    
    /**
     * 计算账单周期月数
     */
    private int calculatePeriodMonths(LocalDate start, LocalDate end) {
        if (start == null || end == null) return 1;
        int months = (end.getYear() - start.getYear()) * 12 + (end.getMonthValue() - start.getMonthValue()) + 1;
        return Math.max(1, months);
    }
    
    /**
     * 检查账单是否已存在
     */
    private boolean billExists(Long accountSetId, Long ownerId, Long parkingId, String feeType, LocalDate periodStart, LocalDate periodEnd) {
        LambdaQueryWrapper<Bill> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Bill::getAccountSetId, accountSetId)
               .eq(Bill::getOwnerId, ownerId)
               .eq(Bill::getFeeType, feeType)
               .eq(Bill::getPeriodStart, periodStart)
               .eq(Bill::getPeriodEnd, periodEnd);
        if (parkingId != null) {
            wrapper.eq(Bill::getParkingId, parkingId);
        }
        return billMapper.selectCount(wrapper) > 0;
    }
    
    @GetMapping("/{id}")
    public Result<Bill> getById(@PathVariable Long id) {
        Bill bill = billMapper.selectById(id);
        if (bill != null) {
            Owner owner = ownerMapper.selectById(bill.getOwnerId());
            if (owner != null) {
                bill.setOwnerName(owner.getName());
                bill.setRoomInfo(owner.getBuildingNo() + "-" + owner.getUnitNo() + "-" + owner.getRoomNo());
            }
        }
        return Result.success(bill);
    }
    
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @OperationLog(operation = "删除账单")
    public Result<Void> delete(@PathVariable Long id) {
        billMapper.deleteById(id);
        return Result.success();
    }
    
    @GetMapping("/export")
    @OperationLog(operation = "导出账单")
    public void exportExcel(
            @RequestParam(required = false) String feeType,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate endDate,
            HttpServletResponse response) throws IOException {
        
        LambdaQueryWrapper<Bill> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Bill::getAccountSetId, AccountSetContext.getCurrentAccountSetId());
        if (StringUtils.hasText(feeType)) wrapper.eq(Bill::getFeeType, feeType);
        if (StringUtils.hasText(status)) wrapper.eq(Bill::getStatus, status);
        if (startDate != null) wrapper.ge(Bill::getPeriodStart, startDate);
        if (endDate != null) wrapper.le(Bill::getPeriodEnd, endDate);
        wrapper.orderByDesc(Bill::getCreateTime);
        
        List<Bill> bills = billMapper.selectList(wrapper);
        bills.forEach(bill -> {
            Owner owner = ownerMapper.selectById(bill.getOwnerId());
            if (owner != null) {
                bill.setOwnerName(owner.getName());
                bill.setRoomInfo(owner.getBuildingNo() + "-" + owner.getUnitNo() + "-" + owner.getRoomNo());
            }
        });
        
        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("账单列表");
            String[] headers = {"账单编号", "业主", "房间", "费用名称", "应收金额", "已缴金额", "账单周期", "截止日期", "状态"};
            Row headerRow = sheet.createRow(0);
            for (int i = 0; i < headers.length; i++) {
                headerRow.createCell(i).setCellValue(headers[i]);
            }
            
            int rowNum = 1;
            for (Bill bill : bills) {
                Row row = sheet.createRow(rowNum++);
                row.createCell(0).setCellValue(bill.getBillNo());
                row.createCell(1).setCellValue(bill.getOwnerName());
                row.createCell(2).setCellValue(bill.getRoomInfo());
                row.createCell(3).setCellValue(bill.getFeeName());
                row.createCell(4).setCellValue(bill.getAmount().doubleValue());
                row.createCell(5).setCellValue(bill.getPaidAmount().doubleValue());
                row.createCell(6).setCellValue(bill.getPeriodStart() + " ~ " + bill.getPeriodEnd());
                row.createCell(7).setCellValue(bill.getDueDate().toString());
                row.createCell(8).setCellValue(getStatusText(bill.getStatus()));
            }
            
            response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
            response.setHeader("Content-Disposition", "attachment; filename=" + 
                URLEncoder.encode("账单列表.xlsx", StandardCharsets.UTF_8));
            workbook.write(response.getOutputStream());
        }
    }
    
    @GetMapping("/{id}/print")
    public Result<String> getPrintHtml(@PathVariable Long id) {
        Bill bill = billMapper.selectById(id);
        if (bill == null) return Result.success("");
        
        Owner owner = ownerMapper.selectById(bill.getOwnerId());
        String ownerName = owner != null ? owner.getName() : "";
        String roomInfo = owner != null ? owner.getBuildingNo() + "-" + owner.getUnitNo() + "-" + owner.getRoomNo() : "";
        String phone = owner != null && owner.getPhone() != null ? owner.getPhone() : "";
        
        String html = """
            <!DOCTYPE html>
            <html>
            <head>
                <meta charset="UTF-8">
                <title>物业费账单</title>
                <style>
                    @page { size: A4; margin: 15mm 20mm; }
                    @media print { 
                        body { margin: 0; padding: 0; } 
                        .no-print { display: none; }
                    }
                    body { font-family: "SimSun", "宋体", serif; font-size: 12pt; line-height: 1.6; color: #000; }
                    .print-container { width: 170mm; margin: 0 auto; padding: 20mm 0; }
                    .print-title { text-align: center; font-size: 18pt; font-weight: bold; margin-bottom: 30px; padding-bottom: 10px; border-bottom: 2px solid #000; }
                    .print-table { width: 100%%; border-collapse: collapse; margin: 20px 0; }
                    .print-table td { padding: 12px 15px; border: 1px solid #000; }
                    .print-table .label { width: 30%%; background-color: #f5f5f5; font-weight: normal; }
                    .print-amount { font-size: 14pt; font-weight: bold; }
                    .print-signature { margin-top: 50px; text-align: right; }
                    .print-signature p { margin: 8px 0; }
                </style>
            </head>
            <body>
                <div class="print-container">
                    <h2 class="print-title">物业费账单</h2>
                    <table class="print-table">
                        <tr><td class="label">账单编号</td><td>%s</td></tr>
                        <tr><td class="label">业主姓名</td><td>%s</td></tr>
                        <tr><td class="label">联系电话</td><td>%s</td></tr>
                        <tr><td class="label">房间信息</td><td>%s</td></tr>
                        <tr><td class="label">费用名称</td><td>%s</td></tr>
                        <tr><td class="label">应收金额</td><td class="print-amount">￥%s</td></tr>
                        <tr><td class="label">已缴金额</td><td>￥%s</td></tr>
                        <tr><td class="label">待缴金额</td><td class="print-amount">￥%s</td></tr>
                        <tr><td class="label">账单周期</td><td>%s 至 %s</td></tr>
                        <tr><td class="label">缴费截止</td><td>%s</td></tr>
                        <tr><td class="label">账单状态</td><td>%s</td></tr>
                    </table>
                    <div class="print-signature">
                        <p>物业服务中心（盖章）</p>
                        <p>打印日期：%s</p>
                    </div>
                </div>
            </body>
            </html>
            """.formatted(
                bill.getBillNo(), ownerName, phone, roomInfo, bill.getFeeName(),
                bill.getAmount(), bill.getPaidAmount(), bill.getAmount().subtract(bill.getPaidAmount()),
                bill.getPeriodStart(), bill.getPeriodEnd(), bill.getDueDate(), 
                getStatusText(bill.getStatus()), LocalDate.now());
        
        return Result.success(html);
    }
    
    private String getStatusText(String status) {
        return switch (status) {
            case "UNPAID" -> "未缴";
            case "PAID" -> "已缴";
            case "OVERDUE" -> "欠费";
            default -> status;
        };
    }
    
    private String generateBillNo() {
        return "BILL" + LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd")) 
               + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }
    
    @Data
    public static class GenerateBillDTO {
        private String feeType;
        private LocalDate periodStart;
        private LocalDate periodEnd;
        private LocalDate dueDate;
    }
}
