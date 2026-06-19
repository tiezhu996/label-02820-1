package com.property.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.property.aspect.OperationLog;
import com.property.common.Result;
import com.property.entity.SysAccountSet;
import com.property.mapper.SysAccountSetMapper;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/account-sets")
@RequiredArgsConstructor
public class AccountSetController {
    
    private final SysAccountSetMapper accountSetMapper;
    
    @GetMapping
    public Result<List<SysAccountSet>> list() {
        LambdaQueryWrapper<SysAccountSet> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SysAccountSet::getStatus, 1);
        wrapper.orderByAsc(SysAccountSet::getId);
        return Result.success(accountSetMapper.selectList(wrapper));
    }
    
    @GetMapping("/{id}")
    public Result<SysAccountSet> getById(@PathVariable Long id) {
        return Result.success(accountSetMapper.selectById(id));
    }
    
    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    @OperationLog(operation = "创建账套")
    public Result<Void> create(@Valid @RequestBody AccountSetDTO dto) {
        SysAccountSet accountSet = new SysAccountSet();
        accountSet.setName(dto.getName());
        accountSet.setDescription(dto.getDescription());
        accountSet.setStatus(1);
        accountSetMapper.insert(accountSet);
        return Result.success();
    }
    
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @OperationLog(operation = "更新账套")
    public Result<Void> update(@PathVariable Long id, @Valid @RequestBody AccountSetDTO dto) {
        SysAccountSet accountSet = accountSetMapper.selectById(id);
        if (accountSet != null) {
            accountSet.setName(dto.getName());
            accountSet.setDescription(dto.getDescription());
            accountSetMapper.updateById(accountSet);
        }
        return Result.success();
    }
    
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @OperationLog(operation = "删除账套")
    public Result<Void> delete(@PathVariable Long id) {
        SysAccountSet accountSet = accountSetMapper.selectById(id);
        if (accountSet != null) {
            accountSet.setStatus(0);
            accountSetMapper.updateById(accountSet);
        }
        return Result.success();
    }
    
    @PostMapping("/{id}/switch")
    public Result<SysAccountSet> switchAccountSet(@PathVariable Long id) {
        SysAccountSet accountSet = accountSetMapper.selectById(id);
        if (accountSet == null || accountSet.getStatus() != 1) {
            return Result.success(null);
        }
        // 返回账套信息，前端通过Header方式传递账套ID
        return Result.success(accountSet);
    }
    
    @Data
    public static class AccountSetDTO {
        @NotBlank(message = "账套名称不能为空")
        private String name;
        private String description;
    }
}
