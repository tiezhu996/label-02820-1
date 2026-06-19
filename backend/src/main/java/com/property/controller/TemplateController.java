package com.property.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.property.aspect.OperationLog;
import com.property.common.BusinessException;
import com.property.common.ErrorCode;
import com.property.common.Result;
import com.property.config.AccountSetContext;
import com.property.dto.BatchReminderRequest;
import com.property.entity.Bill;
import com.property.entity.Owner;
import com.property.entity.Template;
import com.property.mapper.BillMapper;
import com.property.mapper.OwnerMapper;
import com.property.mapper.TemplateMapper;
import jakarta.servlet.http.HttpServletResponse;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.time.temporal.TemporalAdjusters;

@RestController
@RequestMapping("/templates")
@RequiredArgsConstructor
public class TemplateController {
    
    private final TemplateMapper templateMapper;
    private final OwnerMapper ownerMapper;
    private final BillMapper billMapper;
    
    @Value("${file.template-path}")
    private String templatePath;
    
    @GetMapping
    public Result<List<Template>> list() {
        LambdaQueryWrapper<Template> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Template::getAccountSetId, AccountSetContext.getCurrentAccountSetId());
        return Result.success(templateMapper.selectList(wrapper));
    }
    
    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    @OperationLog(operation = "上传模板")
    public Result<Void> upload(@RequestParam("file") MultipartFile file,
                               @RequestParam("templateType") String templateType) throws IOException {
        String filename = UUID.randomUUID() + "_" + file.getOriginalFilename();
        File dir = new File(templatePath);
        if (!dir.exists()) dir.mkdirs();
        File dest = new File(dir, filename);
        Files.copy(file.getInputStream(), dest.toPath());
        
        Template template = new Template();
        template.setAccountSetId(AccountSetContext.getCurrentAccountSetId());
        template.setTemplateType(templateType);
        template.setTemplateName(file.getOriginalFilename());
        template.setFilePath("/templates/" + filename);
        templateMapper.insert(template);
        
        return Result.success();
    }
    
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @OperationLog(operation = "删除模板")
    public Result<Void> delete(@PathVariable Long id) {
        Long accountSetId = AccountSetContext.getCurrentAccountSetId();
        Template template = templateMapper.selectById(id);
        if (template == null || !accountSetId.equals(template.getAccountSetId())) {
            throw new BusinessException(ErrorCode.DATA_NOT_FOUND, "模板不存在或无权访问");
        }
        templateMapper.deleteById(id);
        return Result.success();
    }
    
    @GetMapping("/{id}/download")
    public void download(@PathVariable Long id, HttpServletResponse response) throws IOException {
        Long accountSetId = AccountSetContext.getCurrentAccountSetId();
        Template template = templateMapper.selectById(id);
        if (template == null || !accountSetId.equals(template.getAccountSetId())) {
            throw new BusinessException(ErrorCode.DATA_NOT_FOUND, "模板不存在或无权访问");
        }
        
        String filename = template.getFilePath().substring(template.getFilePath().lastIndexOf("/") + 1);
        File file = new File(templatePath, filename);
        if (!file.exists()) {
            throw new BusinessException(ErrorCode.DATA_NOT_FOUND, "文件不存在");
        }
        
        response.setContentType("application/octet-stream");
        response.setHeader("Content-Disposition", "attachment; filename=" + 
            URLEncoder.encode(template.getTemplateName(), StandardCharsets.UTF_8));
        Files.copy(file.toPath(), response.getOutputStream());
    }
    
    @GetMapping("/{id}/preview")
    public Result<String> preview(@PathVariable Long id, @RequestParam Long ownerId) {
        Long accountSetId = AccountSetContext.getCurrentAccountSetId();
        
        Template template = templateMapper.selectById(id);
        if (template == null || !accountSetId.equals(template.getAccountSetId())) {
            throw new BusinessException(ErrorCode.DATA_NOT_FOUND, "模板不存在或无权访问");
        }
        
        Owner owner = ownerMapper.selectById(ownerId);
        if (owner == null || !accountSetId.equals(owner.getAccountSetId())) {
            throw new BusinessException(ErrorCode.DATA_NOT_FOUND, "业主不存在或无权访问");
        }
        
        LocalDate periodStart = LocalDate.now().with(TemporalAdjusters.firstDayOfMonth());
        LocalDate periodEnd = LocalDate.now().with(TemporalAdjusters.lastDayOfMonth());
        
        LambdaQueryWrapper<Bill> billWrapper = new LambdaQueryWrapper<>();
        billWrapper.eq(Bill::getAccountSetId, accountSetId)
                   .eq(Bill::getOwnerId, ownerId)
                   .eq(Bill::getPeriodStart, periodStart)
                   .eq(Bill::getPeriodEnd, periodEnd)
                   .in(Bill::getStatus, "UNPAID", "OVERDUE");
        List<Bill> bills = billMapper.selectList(billWrapper);
        
        BigDecimal totalArrears = BigDecimal.ZERO;
        StringBuilder billDetails = new StringBuilder();
        for (Bill bill : bills) {
            BigDecimal arrears = bill.getAmount().subtract(bill.getPaidAmount());
            totalArrears = totalArrears.add(arrears);
            billDetails.append(String.format("%s: ￥%s<br/>", bill.getFeeName(), arrears));
        }
        
        String html = generateNoticeHtml(template.getTemplateType(), owner, totalArrears, billDetails.toString());
        return Result.success(html);
    }
    
    private String generateNoticeHtml(String templateType, Owner owner, BigDecimal totalArrears, String billDetails) {
        String title = switch (templateType) {
            case "REMINDER" -> "催缴通知单";
            case "VIOLATION" -> "违规通知单";
            default -> "通知单";
        };
        
        String roomInfo = owner.getBuildingNo() + "栋" + owner.getUnitNo() + "单元" + owner.getRoomNo() + "室";
        String today = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy年MM月dd日"));
        
        return """
            <style>
                @page { size: A4; margin: 20mm; }
                @media print { body { margin: 0; padding: 0; } }
            </style>
            <div style="width: 210mm; min-height: 297mm; padding: 20mm; box-sizing: border-box; font-family: SimSun, serif; margin: 0 auto; page-break-after: always;">
                <h2 style="text-align: center; margin-bottom: 30px;">%s</h2>
                <p style="margin-bottom: 20px; font-size: 14px;">尊敬的 <strong>%s</strong> 业主：</p>
                <p style="text-indent: 2em; line-height: 2; font-size: 14px;">
                    您好！您位于 <strong>%s</strong> 的房产，截至目前存在以下费用未缴纳：
                </p>
                <div style="margin: 20px 40px; padding: 15px; background: #f5f5f5; border-left: 4px solid #1890ff;">
                    %s
                    <strong>合计：￥%s</strong>
                </div>
                <p style="text-indent: 2em; line-height: 2; font-size: 14px;">
                    请您于收到本通知后7日内前往物业服务中心缴纳相关费用，或通过线上渠道完成缴费。
                    如有疑问，请联系物业服务中心。
                </p>
                <p style="text-indent: 2em; line-height: 2; font-size: 14px;">感谢您的配合！</p>
                <div style="text-align: right; margin-top: 60px;">
                    <p>物业服务中心</p>
                    <p>%s</p>
                </div>
            </div>
            """.formatted(title, owner.getName(), roomInfo, billDetails, totalArrears, today);
    }
    
    @PostMapping("/batch-preview")
    public Result<BatchPreviewResult> batchPreview(@RequestBody BatchReminderRequest request) {
        Long accountSetId = AccountSetContext.getCurrentAccountSetId();
        
        Template template = templateMapper.selectById(request.getTemplateId());
        if (template == null || !accountSetId.equals(template.getAccountSetId())) {
            throw new BusinessException(ErrorCode.DATA_NOT_FOUND, "模板不存在或无权访问");
        }
        
        List<ReminderItem> items = new ArrayList<>();
        for (Long ownerId : request.getOwnerIds()) {
            Owner owner = ownerMapper.selectById(ownerId);
            if (owner == null || !accountSetId.equals(owner.getAccountSetId())) continue;
            
            LocalDate periodStart = LocalDate.now().with(TemporalAdjusters.firstDayOfMonth());
            LocalDate periodEnd = LocalDate.now().with(TemporalAdjusters.lastDayOfMonth());
            
            LambdaQueryWrapper<Bill> billWrapper = new LambdaQueryWrapper<>();
            billWrapper.eq(Bill::getAccountSetId, accountSetId)
                       .eq(Bill::getOwnerId, ownerId)
                       .eq(Bill::getPeriodStart, periodStart)
                       .eq(Bill::getPeriodEnd, periodEnd)
                       .in(Bill::getStatus, "UNPAID", "OVERDUE");
            List<Bill> bills = billMapper.selectList(billWrapper);
            
            BigDecimal totalArrears = BigDecimal.ZERO;
            StringBuilder billDetails = new StringBuilder();
            for (Bill bill : bills) {
                BigDecimal arrears = bill.getAmount().subtract(bill.getPaidAmount());
                totalArrears = totalArrears.add(arrears);
                billDetails.append(String.format("%s: ￥%s<br/>", bill.getFeeName(), arrears));
            }
            
            if (totalArrears.compareTo(BigDecimal.ZERO) <= 0) continue;
            
            ReminderItem item = new ReminderItem();
            item.setOwnerId(ownerId);
            item.setOwnerName(owner.getName());
            item.setRoomInfo(owner.getBuildingNo() + "-" + owner.getUnitNo() + "-" + owner.getRoomNo());
            item.setTotalArrears(totalArrears);
            item.setHtml(generateNoticeHtml(template.getTemplateType(), owner, totalArrears, billDetails.toString()));
            items.add(item);
        }
        
        BatchPreviewResult result = new BatchPreviewResult();
        result.setTemplateName(template.getTemplateName());
        result.setTemplateType(template.getTemplateType());
        result.setCount(items.size());
        result.setItems(items);
        
        return Result.success(result);
    }
    
    @PostMapping("/batch-generate")
    @OperationLog(operation = "批量生成催缴单")
    public void batchGenerate(@RequestBody BatchReminderRequest request, HttpServletResponse response) throws IOException {
        Long accountSetId = AccountSetContext.getCurrentAccountSetId();
        
        Template template = templateMapper.selectById(request.getTemplateId());
        if (template == null || !accountSetId.equals(template.getAccountSetId())) {
            throw new BusinessException(ErrorCode.DATA_NOT_FOUND, "模板不存在或无权访问");
        }
        
        StringBuilder allHtml = new StringBuilder();
        allHtml.append("""
            <!DOCTYPE html>
            <html>
            <head>
                <meta charset="UTF-8">
                <title>批量催缴单</title>
                <style>
                    body { margin: 0; padding: 0; }
                </style>
            </head>
            <body>
            """);
        
        LocalDate periodStart = LocalDate.now().with(TemporalAdjusters.firstDayOfMonth());
        LocalDate periodEnd = LocalDate.now().with(TemporalAdjusters.lastDayOfMonth());
        
        for (Long ownerId : request.getOwnerIds()) {
            Owner owner = ownerMapper.selectById(ownerId);
            if (owner == null || !accountSetId.equals(owner.getAccountSetId())) continue;
            
            LambdaQueryWrapper<Bill> billWrapper = new LambdaQueryWrapper<>();
            billWrapper.eq(Bill::getAccountSetId, accountSetId)
                       .eq(Bill::getOwnerId, ownerId)
                       .eq(Bill::getPeriodStart, periodStart)
                       .eq(Bill::getPeriodEnd, periodEnd)
                       .in(Bill::getStatus, "UNPAID", "OVERDUE");
            List<Bill> bills = billMapper.selectList(billWrapper);
            
            BigDecimal totalArrears = BigDecimal.ZERO;
            StringBuilder billDetails = new StringBuilder();
            for (Bill bill : bills) {
                BigDecimal arrears = bill.getAmount().subtract(bill.getPaidAmount());
                totalArrears = totalArrears.add(arrears);
                billDetails.append(String.format("%s: ￥%s<br/>", bill.getFeeName(), arrears));
            }
            
            if (totalArrears.compareTo(BigDecimal.ZERO) <= 0) continue;
            
            allHtml.append(generateNoticeHtml(template.getTemplateType(), owner, totalArrears, billDetails.toString()));
        }
        
        allHtml.append("""
            </body>
            </html>
            """);
        
        String filename = "批量催缴单_" + LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd")) + ".html";
        response.setContentType("text/html; charset=UTF-8");
        response.setHeader("Content-Disposition", "attachment; filename=" + 
            URLEncoder.encode(filename, StandardCharsets.UTF_8));
        response.getWriter().write(allHtml.toString());
    }
    
    @Data
    public static class BatchPreviewResult {
        private String templateName;
        private String templateType;
        private Integer count;
        private List<ReminderItem> items;
    }
    
    @Data
    public static class ReminderItem {
        private Long ownerId;
        private String ownerName;
        private String roomInfo;
        private BigDecimal totalArrears;
        private String html;
    }
}
