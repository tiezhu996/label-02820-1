package com.property.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.property.aspect.OperationLog;
import com.property.common.BusinessException;
import com.property.common.ErrorCode;
import com.property.common.PageResult;
import com.property.common.Result;
import com.property.config.AccountSetContext;
import com.property.entity.Owner;
import com.property.entity.Receivable;
import com.property.mapper.OwnerMapper;
import com.property.mapper.ReceivableMapper;
import com.property.security.SecurityUtil;
import jakarta.servlet.http.HttpServletResponse;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

@RestController
@RequestMapping("/receivables")
@RequiredArgsConstructor
public class ReceivableController {
    
    private final ReceivableMapper receivableMapper;
    private final OwnerMapper ownerMapper;
    
    @GetMapping
    public Result<PageResult<Receivable>> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String periodMonth,
            @RequestParam(required = false) String feeType) {
        Page<Receivable> pageParam = new Page<>(page, size);
        LambdaQueryWrapper<Receivable> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Receivable::getAccountSetId, AccountSetContext.getCurrentAccountSetId());
        
        if (StringUtils.hasText(periodMonth)) {
            wrapper.eq(Receivable::getPeriodMonth, periodMonth);
        }
        if (StringUtils.hasText(feeType)) {
            wrapper.eq(Receivable::getFeeType, feeType);
        }
        wrapper.orderByDesc(Receivable::getPeriodMonth);
        
        Page<Receivable> result = receivableMapper.selectPage(pageParam, wrapper);
        
        result.getRecords().forEach(r -> {
            Owner owner = ownerMapper.selectById(r.getOwnerId());
            if (owner != null) {
                r.setOwnerName(owner.getName());
                r.setRoomInfo(owner.getBuildingNo() + "-" + owner.getUnitNo() + "-" + owner.getRoomNo());
            }
        });
        
        return Result.success(new PageResult<>(
                result.getRecords(),
                result.getTotal(),
                result.getSize(),
                result.getCurrent()
        ));
    }
    
    @PutMapping("/{id}/lock")
    @OperationLog(operation = "锁定应收账款")
    public Result<Void> lock(@PathVariable Long id) {
        Receivable receivable = receivableMapper.selectById(id);
        if (receivable == null) {
            throw new BusinessException(ErrorCode.DATA_NOT_FOUND, "应收账款不存在");
        }
        receivable.setIsLocked(1);
        receivableMapper.updateById(receivable);
        return Result.success();
    }
    
    @PutMapping("/{id}/unlock")
    @OperationLog(operation = "解锁应收账款")
    public Result<Void> unlock(@PathVariable Long id) {
        // 只有管理员可以解锁
        if (!SecurityUtil.isAdmin()) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "只有管理员可以解锁");
        }
        
        Receivable receivable = receivableMapper.selectById(id);
        if (receivable == null) {
            throw new BusinessException(ErrorCode.DATA_NOT_FOUND, "应收账款不存在");
        }
        receivable.setIsLocked(0);
        receivableMapper.updateById(receivable);
        return Result.success();
    }
    
    @PutMapping("/{id}")
    @OperationLog(operation = "修改应收账款")
    public Result<Void> update(@PathVariable Long id, @RequestBody UpdateReceivableDTO dto) {
        // 只有管理员可以修改
        if (!SecurityUtil.isAdmin()) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "只有管理员可以修改应收账款");
        }
        
        Receivable receivable = receivableMapper.selectById(id);
        if (receivable == null) {
            throw new BusinessException(ErrorCode.DATA_NOT_FOUND, "应收账款不存在");
        }
        
        if (receivable.getIsLocked() == 1) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "已锁定的应收账款不能修改");
        }
        
        if (dto.getAmount() != null) {
            receivable.setAmount(dto.getAmount());
        }
        if (dto.getPaidAmount() != null) {
            receivable.setPaidAmount(dto.getPaidAmount());
        }
        
        receivableMapper.updateById(receivable);
        return Result.success();
    }
    
    @GetMapping("/export")
    @OperationLog(operation = "导出应收账款")
    public void exportExcel(
            @RequestParam(required = false) String periodMonth,
            @RequestParam(required = false) String feeType,
            HttpServletResponse response) throws IOException {
        
        LambdaQueryWrapper<Receivable> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Receivable::getAccountSetId, AccountSetContext.getCurrentAccountSetId());
        if (StringUtils.hasText(periodMonth)) wrapper.eq(Receivable::getPeriodMonth, periodMonth);
        if (StringUtils.hasText(feeType)) wrapper.eq(Receivable::getFeeType, feeType);
        wrapper.orderByDesc(Receivable::getPeriodMonth);
        
        List<Receivable> receivables = receivableMapper.selectList(wrapper);
        receivables.forEach(r -> {
            Owner owner = ownerMapper.selectById(r.getOwnerId());
            if (owner != null) {
                r.setOwnerName(owner.getName());
                r.setRoomInfo(owner.getBuildingNo() + "-" + owner.getUnitNo() + "-" + owner.getRoomNo());
            }
        });
        
        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("应收账款");
            String[] headers = {"业主", "房间", "费用类型", "账期", "应收金额", "已缴金额", "累计应收", "状态"};
            Row headerRow = sheet.createRow(0);
            for (int i = 0; i < headers.length; i++) {
                headerRow.createCell(i).setCellValue(headers[i]);
            }
            
            int rowNum = 1;
            for (Receivable r : receivables) {
                Row row = sheet.createRow(rowNum++);
                row.createCell(0).setCellValue(r.getOwnerName());
                row.createCell(1).setCellValue(r.getRoomInfo());
                row.createCell(2).setCellValue(r.getFeeType());
                row.createCell(3).setCellValue(r.getPeriodMonth());
                row.createCell(4).setCellValue(r.getAmount().doubleValue());
                row.createCell(5).setCellValue(r.getPaidAmount().doubleValue());
                row.createCell(6).setCellValue(r.getCumulativeAmount().doubleValue());
                row.createCell(7).setCellValue(r.getIsLocked() == 1 ? "已锁定" : "未锁定");
            }
            
            response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
            response.setHeader("Content-Disposition", "attachment; filename=" + 
                URLEncoder.encode("应收账款.xlsx", StandardCharsets.UTF_8));
            workbook.write(response.getOutputStream());
        }
    }
    
    @Data
    public static class UpdateReceivableDTO {
        private BigDecimal amount;
        private BigDecimal paidAmount;
    }
    
    @GetMapping("/{id}/notice")
    public Result<String> generateNotice(@PathVariable Long id) {
        Receivable receivable = receivableMapper.selectById(id);
        if (receivable == null) {
            throw new BusinessException(ErrorCode.DATA_NOT_FOUND, "应收账款不存在");
        }
        
        Owner owner = ownerMapper.selectById(receivable.getOwnerId());
        if (owner == null) {
            throw new BusinessException(ErrorCode.DATA_NOT_FOUND, "业主不存在");
        }
        
        BigDecimal arrears = receivable.getAmount().subtract(receivable.getPaidAmount());
        String roomInfo = owner.getBuildingNo() + "栋" + owner.getUnitNo() + "单元" + owner.getRoomNo() + "室";
        String today = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy年MM月dd日"));
        String feeTypeName = switch (receivable.getFeeType()) {
            case "PROPERTY" -> "物业费";
            case "PARKING" -> "车位费";
            default -> receivable.getFeeType();
        };
        
        String html = """
            <style>
                @page { size: A4; margin: 20mm; }
                @media print { body { margin: 0; padding: 0; } }
            </style>
            <div style="width: 210mm; min-height: 297mm; padding: 20mm; box-sizing: border-box; font-family: SimSun, serif; margin: 0 auto;">
                <h2 style="text-align: center; margin-bottom: 30px;">缴费通知单</h2>
                <p style="margin-bottom: 20px; font-size: 14px;">尊敬的 <strong>%s</strong> 业主：</p>
                <p style="text-indent: 2em; line-height: 2; font-size: 14px;">
                    您好！您位于 <strong>%s</strong> 的房产，%s账期（%s）的费用如下：
                </p>
                <div style="margin: 20px 40px; padding: 15px; background: #f5f5f5; border-left: 4px solid #1890ff;">
                    <p>%s：￥%s</p>
                    <p>已缴金额：￥%s</p>
                    <strong>待缴金额：￥%s</strong>
                </div>
                <p style="text-indent: 2em; line-height: 2; font-size: 14px;">
                    请您于收到本通知后15日内前往物业服务中心缴纳相关费用，或通过线上渠道完成缴费。
                    如有疑问，请联系物业服务中心。
                </p>
                <p style="text-indent: 2em; line-height: 2; font-size: 14px;">感谢您的配合！</p>
                <div style="text-align: right; margin-top: 60px;">
                    <p>物业服务中心</p>
                    <p>%s</p>
                </div>
            </div>
            """.formatted(owner.getName(), roomInfo, feeTypeName, receivable.getPeriodMonth(),
                         feeTypeName, receivable.getAmount(), receivable.getPaidAmount(), arrears, today);
        
        return Result.success(html);
    }
}
