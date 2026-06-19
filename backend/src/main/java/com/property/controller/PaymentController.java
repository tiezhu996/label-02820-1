package com.property.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.property.aspect.OperationLog;
import com.property.common.BusinessException;
import com.property.common.ErrorCode;
import com.property.common.PageResult;
import com.property.common.Result;
import com.property.config.AccountSetContext;
import com.property.entity.*;
import com.property.mapper.*;
import com.property.security.SecurityUtil;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

@RestController
@RequestMapping("/payments")
@RequiredArgsConstructor
public class PaymentController {
    
    private final PaymentMapper paymentMapper;
    private final BillMapper billMapper;
    private final OwnerMapper ownerMapper;
    private final SysUserMapper userMapper;
    
    @GetMapping
    public Result<PageResult<Payment>> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) Long ownerId,
            @RequestParam(required = false) String paymentMethod,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate endDate) {
        
        Page<Payment> pageParam = new Page<>(page, size);
        LambdaQueryWrapper<Payment> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Payment::getAccountSetId, AccountSetContext.getCurrentAccountSetId());
        
        if (ownerId != null) {
            wrapper.eq(Payment::getOwnerId, ownerId);
        }
        if (StringUtils.hasText(paymentMethod)) {
            wrapper.eq(Payment::getPaymentMethod, paymentMethod);
        }
        if (startDate != null) {
            wrapper.ge(Payment::getCreateTime, startDate.atStartOfDay());
        }
        if (endDate != null) {
            wrapper.le(Payment::getCreateTime, endDate.plusDays(1).atStartOfDay());
        }
        wrapper.orderByDesc(Payment::getCreateTime);
        
        Page<Payment> result = paymentMapper.selectPage(pageParam, wrapper);
        
        result.getRecords().forEach(payment -> {
            Owner owner = ownerMapper.selectById(payment.getOwnerId());
            if (owner != null) {
                payment.setOwnerName(owner.getName());
            }
            SysUser user = userMapper.selectById(payment.getOperatorId());
            if (user != null) {
                payment.setOperatorName(user.getRealName());
            }
        });
        
        return Result.success(new PageResult<>(
                result.getRecords(),
                result.getTotal(),
                result.getSize(),
                result.getCurrent()
        ));
    }
    
    @PostMapping
    @OperationLog(operation = "缴费")
    public Result<Void> create(@Valid @RequestBody PaymentDTO dto) {
        Bill bill = billMapper.selectById(dto.getBillId());
        if (bill == null) {
            throw new BusinessException(ErrorCode.BILL_NOT_FOUND);
        }
        
        BigDecimal amount = dto.getAmount().setScale(2, RoundingMode.HALF_UP);
        BigDecimal discountRate = dto.getDiscountRate() != null ? dto.getDiscountRate() : BigDecimal.ZERO;
        BigDecimal discountAmount = amount.multiply(discountRate).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
        BigDecimal actualAmount = amount.subtract(discountAmount).setScale(2, RoundingMode.HALF_UP);
        
        Payment payment = new Payment();
        // 使用账单的accountSetId，确保数据一致性
        payment.setAccountSetId(bill.getAccountSetId());
        payment.setPaymentNo(generatePaymentNo());
        payment.setBillId(dto.getBillId());
        payment.setOwnerId(bill.getOwnerId());
        payment.setAmount(amount);
        payment.setDiscountRate(discountRate);
        payment.setDiscountAmount(discountAmount);
        payment.setActualAmount(actualAmount);
        payment.setPaymentMethod(dto.getPaymentMethod());
        payment.setPaymentPeriod(dto.getPaymentPeriod());
        payment.setOperatorId(SecurityUtil.getCurrentUserId());
        
        paymentMapper.insert(payment);
        
        bill.setPaidAmount(bill.getPaidAmount().add(actualAmount));
        if (bill.getPaidAmount().compareTo(bill.getAmount()) >= 0) {
            bill.setStatus("PAID");
        }
        billMapper.updateById(bill);
        
        return Result.success();
    }
    
    private String generatePaymentNo() {
        return "PAY" + LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"))
               + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }
    
    @Data
    public static class PaymentDTO {
        @NotNull(message = "账单ID不能为空")
        private Long billId;
        
        @NotNull(message = "缴费金额不能为空")
        private BigDecimal amount;
        
        private BigDecimal discountRate;
        
        private String paymentMethod;
        
        private String paymentPeriod; // 缴费区间: MONTHLY/QUARTERLY/YEARLY
    }
}
