package com.property.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.property.common.Result;
import com.property.config.AccountSetContext;
import com.property.entity.BillSchedule;
import com.property.mapper.BillScheduleMapper;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/bill-schedules")
@RequiredArgsConstructor
public class BillScheduleController {
    
    private final BillScheduleMapper billScheduleMapper;
    
    @GetMapping
    public Result<List<BillSchedule>> list() {
        LambdaQueryWrapper<BillSchedule> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(BillSchedule::getAccountSetId, AccountSetContext.getCurrentAccountSetId());
        return Result.success(billScheduleMapper.selectList(wrapper));
    }
    
    @GetMapping("/{id}")
    public Result<BillSchedule> getById(@PathVariable Long id) {
        return Result.success(billScheduleMapper.selectById(id));
    }
    
    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public Result<Void> create(@RequestBody BillScheduleDTO dto) {
        BillSchedule schedule = new BillSchedule();
        schedule.setAccountSetId(AccountSetContext.getCurrentAccountSetId());
        schedule.setFeeType(dto.getFeeType());
        schedule.setCustomFeeType("CUSTOM".equals(dto.getFeeType()) ? dto.getCustomFeeType() : null);
        schedule.setGenerateDay(dto.getGenerateDay());
        schedule.setPeriodType(dto.getPeriodType());
        schedule.setDueDays(dto.getDueDays());
        schedule.setStatus(dto.getStatus() != null ? dto.getStatus() : 1);
        billScheduleMapper.insert(schedule);
        return Result.success();
    }
    
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<Void> update(@PathVariable Long id, @RequestBody BillScheduleDTO dto) {
        BillSchedule schedule = billScheduleMapper.selectById(id);
        if (schedule != null) {
            schedule.setFeeType(dto.getFeeType());
            schedule.setCustomFeeType("CUSTOM".equals(dto.getFeeType()) ? dto.getCustomFeeType() : null);
            schedule.setGenerateDay(dto.getGenerateDay());
            schedule.setPeriodType(dto.getPeriodType());
            schedule.setDueDays(dto.getDueDays());
            if (dto.getStatus() != null) schedule.setStatus(dto.getStatus());
            billScheduleMapper.updateById(schedule);
        }
        return Result.success();
    }
    
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<Void> delete(@PathVariable Long id) {
        billScheduleMapper.deleteById(id);
        return Result.success();
    }
    
    @Data
    public static class BillScheduleDTO {
        private String feeType;
        private String customFeeType;
        private Integer generateDay;
        private String periodType;
        private Integer dueDays;
        private Integer status;
    }
}
