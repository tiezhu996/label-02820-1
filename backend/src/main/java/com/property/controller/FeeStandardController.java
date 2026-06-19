package com.property.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.property.aspect.OperationLog;
import com.property.common.Result;
import com.property.config.AccountSetContext;
import com.property.entity.FeeStandard;
import com.property.entity.PaymentMethod;
import com.property.mapper.FeeStandardMapper;
import com.property.mapper.PaymentMethodMapper;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;

@RestController
@RequestMapping("/fee-standards")
@RequiredArgsConstructor
public class FeeStandardController {
    
    private final FeeStandardMapper feeStandardMapper;
    private final PaymentMethodMapper paymentMethodMapper;
    
    @GetMapping
    public Result<List<FeeStandard>> list(@RequestParam(required = false) String feeType) {
        LambdaQueryWrapper<FeeStandard> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(FeeStandard::getAccountSetId, AccountSetContext.getCurrentAccountSetId());
        if (StringUtils.hasText(feeType)) {
            wrapper.eq(FeeStandard::getFeeType, feeType);
        }
        wrapper.orderByAsc(FeeStandard::getFeeType, FeeStandard::getCreateTime);
        return Result.success(feeStandardMapper.selectList(wrapper));
    }
    
    @GetMapping("/{id}")
    public Result<FeeStandard> getById(@PathVariable Long id) {
        return Result.success(feeStandardMapper.selectById(id));
    }
    
    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    @OperationLog(operation = "创建收费标准")
    public Result<Void> create(@Valid @RequestBody FeeStandardDTO dto) {
        FeeStandard feeStandard = new FeeStandard();
        feeStandard.setAccountSetId(AccountSetContext.getCurrentAccountSetId());
        feeStandard.setFeeType(dto.getFeeType());
        feeStandard.setFeeName(dto.getFeeName());
        feeStandard.setAmount(dto.getAmount());
        feeStandard.setUnit(dto.getUnit());
        feeStandard.setFrequency(dto.getFrequency() != null ? dto.getFrequency() : "MONTHLY");
        feeStandard.setStatus(1);
        feeStandardMapper.insert(feeStandard);
        return Result.success();
    }
    
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @OperationLog(operation = "更新收费标准")
    public Result<Void> update(@PathVariable Long id, @Valid @RequestBody FeeStandardDTO dto) {
        FeeStandard feeStandard = feeStandardMapper.selectById(id);
        if (feeStandard != null) {
            feeStandard.setFeeName(dto.getFeeName());
            feeStandard.setAmount(dto.getAmount());
            feeStandard.setUnit(dto.getUnit());
            feeStandard.setFrequency(dto.getFrequency());
            feeStandardMapper.updateById(feeStandard);
        }
        return Result.success();
    }
    
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @OperationLog(operation = "删除收费标准")
    public Result<Void> delete(@PathVariable Long id) {
        feeStandardMapper.deleteById(id);
        return Result.success();
    }
    
    // ========== 缴费方式管理 ==========
    
    @GetMapping("/payment-methods")
    public Result<List<PaymentMethod>> listPaymentMethods() {
        LambdaQueryWrapper<PaymentMethod> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(PaymentMethod::getAccountSetId, AccountSetContext.getCurrentAccountSetId())
               .eq(PaymentMethod::getStatus, 1);
        return Result.success(paymentMethodMapper.selectList(wrapper));
    }
    
    @PostMapping("/payment-methods")
    @PreAuthorize("hasRole('ADMIN')")
    @OperationLog(operation = "创建缴费方式")
    public Result<Void> createPaymentMethod(@RequestBody PaymentMethodDTO dto) {
        PaymentMethod method = new PaymentMethod();
        method.setAccountSetId(AccountSetContext.getCurrentAccountSetId());
        method.setMethodName(dto.getMethodName());
        method.setStatus(1);
        paymentMethodMapper.insert(method);
        return Result.success();
    }
    
    @DeleteMapping("/payment-methods/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @OperationLog(operation = "删除缴费方式")
    public Result<Void> deletePaymentMethod(@PathVariable Long id) {
        paymentMethodMapper.deleteById(id);
        return Result.success();
    }
    
    @Data
    public static class PaymentMethodDTO {
        @NotBlank(message = "方式名称不能为空")
        private String methodName;
    }
    
    @Data
    public static class FeeStandardDTO {
        @NotBlank(message = "费用类型不能为空")
        private String feeType;
        
        @NotBlank(message = "费用名称不能为空")
        private String feeName;
        
        @NotNull(message = "金额不能为空")
        private BigDecimal amount;
        
        @NotBlank(message = "单位不能为空")
        private String unit;
        
        private String frequency;
    }
}
