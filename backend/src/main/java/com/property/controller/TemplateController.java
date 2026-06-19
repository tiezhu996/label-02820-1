package com.property.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.property.aspect.OperationLog;
import com.property.common.BusinessException;
import com.property.common.ErrorCode;
import com.property.common.Result;
import com.property.config.AccountSetContext;
import com.property.entity.Owner;
import com.property.entity.Receivable;
import com.property.entity.Template;
import com.property.mapper.OwnerMapper;
import com.property.mapper.ReceivableMapper;
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

@RestController
@RequestMapping("/templates")
@RequiredArgsConstructor
public class TemplateController {
    
    private final TemplateMapper templateMapper;
    private final OwnerMapper ownerMapper;
    private final ReceivableMapper receivableMapper;
    
    @Value("${file.template-path}")
    private String templatePath;
    
    @GetMapping
    public Result<List<Template>> list() {
        Long accountSetId = AccountSetContext.getCurrentAccountSetId();
        LambdaQueryWrapper<Template> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Template::getAccountSetId, accountSetId);
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
        if (template == null) {
            throw new BusinessException(ErrorCode.DATA_NOT_FOUND, "模板不存在");
        }
        if (!template.getAccountSetId().equals(accountSetId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "无权操作其他账套的模板");
        }
        templateMapper.deleteById(id);
        return Result.success();
    }
    
    @GetMapping("/{id}/download")
    public void download(@PathVariable Long id, HttpServletResponse response) throws IOException {
        Template template = templateMapper.selectById(id);
        if (template == null) {
            throw new BusinessException(ErrorCode.DATA_NOT_FOUND, "模板不存在");
        }

        Long accountSetId = AccountSetContext.getCurrentAccountSetId();
        if (!template.getAccountSetId().equals(accountSetId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "无权访问其他账套的模板");
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
        if (template == null) {
            throw new BusinessException(ErrorCode.DATA_NOT_FOUND, "模板不存在");
        }
        if (!template.getAccountSetId().equals(accountSetId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "无权使用其他账套的模板");
        }
        
        Owner owner = ownerMapper.selectById(ownerId);
        if (owner == null) {
            throw new BusinessException(ErrorCode.DATA_NOT_FOUND, "业主不存在");
        }
        if (!owner.getAccountSetId().equals(accountSetId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "无权访问其他账套的业主");
        }

        String currentPeriod = resolveCurrentPeriodMonth(accountSetId);
        
        LambdaQueryWrapper<Receivable> recWrapper = new LambdaQueryWrapper<>();
        recWrapper.eq(Receivable::getAccountSetId, accountSetId)
                  .eq(Receivable::getOwnerId, ownerId)
                  .eq(Receivable::getPeriodMonth, currentPeriod);
        List<Receivable> receivables = receivableMapper.selectList(recWrapper);
        
        BigDecimal totalArrears = BigDecimal.ZERO;
        StringBuilder billDetails = new StringBuilder();
        for (Receivable r : receivables) {
            BigDecimal arrears = r.getAmount().subtract(r.getPaidAmount());
            if (arrears.compareTo(BigDecimal.ZERO) > 0) {
                totalArrears = totalArrears.add(arrears);
                billDetails.append(String.format("%s: ￥%s<br/>", resolveFeeName(r.getFeeType()), arrears));
            }
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

    @Data
    public static class BatchGenerateRequest {
        private List<Long> ownerIds;
        private Long templateId;
    }

    @PostMapping("/batch-generate")
    public Result<String> batchGenerate(@RequestBody BatchGenerateRequest request) {
        if (request.getOwnerIds() == null || request.getOwnerIds().isEmpty()) {
            return Result.fail("请选择业主");
        }
        if (request.getTemplateId() == null) {
            return Result.fail("请选择模板");
        }

        Long accountSetId = AccountSetContext.getCurrentAccountSetId();

        Template template = templateMapper.selectById(request.getTemplateId());
        if (template == null) {
            throw new BusinessException(ErrorCode.DATA_NOT_FOUND, "模板不存在");
        }
        if (!template.getAccountSetId().equals(accountSetId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "只能使用当前账套的模板");
        }

        String currentPeriod = resolveCurrentPeriodMonth(accountSetId);

        StringBuilder allHtml = new StringBuilder();
        allHtml.append("""
            <!DOCTYPE html>
            <html>
            <head>
                <meta charset="UTF-8">
                <title>批量催缴单</title>
                <style>
                    @page { size: A4; margin: 20mm; }
                    @media print { body { margin: 0; padding: 0; } }
                    body { font-family: SimSun, serif; }
                </style>
            </head>
            <body>
            """);

        for (Long ownerId : request.getOwnerIds()) {
            Owner owner = ownerMapper.selectById(ownerId);
            if (owner == null) continue;
            if (!owner.getAccountSetId().equals(accountSetId)) {
                throw new BusinessException(ErrorCode.FORBIDDEN, "业主 " + owner.getName() + " 不属于当前账套");
            }

            LambdaQueryWrapper<Receivable> recWrapper = new LambdaQueryWrapper<>();
            recWrapper.eq(Receivable::getAccountSetId, accountSetId)
                      .eq(Receivable::getOwnerId, ownerId)
                      .eq(Receivable::getPeriodMonth, currentPeriod);
            List<Receivable> receivables = receivableMapper.selectList(recWrapper);

            BigDecimal totalArrears = BigDecimal.ZERO;
            StringBuilder billDetails = new StringBuilder();
            for (Receivable r : receivables) {
                BigDecimal arrears = r.getAmount().subtract(r.getPaidAmount());
                if (arrears.compareTo(BigDecimal.ZERO) > 0) {
                    totalArrears = totalArrears.add(arrears);
                    billDetails.append(String.format("%s: ￥%s<br/>", resolveFeeName(r.getFeeType()), arrears));
                }
            }

            if (totalArrears.compareTo(BigDecimal.ZERO) > 0) {
                String noticeHtml = generateNoticeHtml(template.getTemplateType(), owner, totalArrears, billDetails.toString());
                allHtml.append(noticeHtml);
            }
        }

        allHtml.append("</body></html>");
        return Result.success(allHtml.toString());
    }

    private String resolveCurrentPeriodMonth(Long accountSetId) {
        LambdaQueryWrapper<Receivable> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Receivable::getAccountSetId, accountSetId)
               .orderByDesc(Receivable::getPeriodMonth)
               .last("LIMIT 1");
        Receivable latest = receivableMapper.selectOne(wrapper);
        if (latest != null) {
            return latest.getPeriodMonth();
        }
        return LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM"));
    }

    private String resolveFeeName(String feeType) {
        return switch (feeType) {
            case "PROPERTY" -> "物业费";
            case "PARKING" -> "车位费";
            case "WATER" -> "水费";
            case "ELECTRIC" -> "电费";
            case "GAS" -> "燃气费";
            case "HEATING" -> "暖气费";
            default -> feeType;
        };
    }
}
