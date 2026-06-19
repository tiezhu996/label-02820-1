package com.property.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.property.aspect.OperationLog;
import com.property.common.PageResult;
import com.property.common.Result;
import com.property.dto.UserDTO;
import com.property.entity.SysUser;
import com.property.mapper.SysPermissionMapper;
import com.property.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/users")
@RequiredArgsConstructor
public class UserController {
    
    private final UserService userService;
    private final SysPermissionMapper permissionMapper;
    
    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public Result<PageResult<SysUser>> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String username,
            @RequestParam(required = false) String role) {
        Page<SysUser> pageResult = userService.getPage(page, size, username, role);
        PageResult<SysUser> result = new PageResult<>(
                pageResult.getRecords(),
                pageResult.getTotal(),
                pageResult.getSize(),
                pageResult.getCurrent()
        );
        return Result.success(result);
    }
    
    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<UserDTO> getById(@PathVariable Long id) {
        SysUser user = userService.getById(id);
        List<String> permissions = permissionMapper.findPermissionsByUserId(id);
        
        UserDTO dto = new UserDTO();
        dto.setId(user.getId());
        dto.setUsername(user.getUsername());
        dto.setRealName(user.getRealName());
        dto.setRole(user.getRole());
        dto.setStatus(user.getStatus());
        dto.setPermissions(permissions);
        
        return Result.success(dto);
    }
    
    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    @OperationLog(operation = "创建用户")
    public Result<Void> create(@Valid @RequestBody UserDTO dto) {
        userService.create(dto);
        return Result.success();
    }
    
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @OperationLog(operation = "更新用户")
    public Result<Void> update(@PathVariable Long id, @Valid @RequestBody UserDTO dto) {
        userService.update(id, dto);
        return Result.success();
    }
    
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @OperationLog(operation = "删除用户")
    public Result<Void> delete(@PathVariable Long id) {
        userService.delete(id);
        return Result.success();
    }
    
    @PutMapping("/{id}/permissions")
    @PreAuthorize("hasRole('ADMIN')")
    @OperationLog(operation = "更新用户权限")
    public Result<Void> updatePermissions(@PathVariable Long id, @RequestBody List<String> permissions) {
        userService.updatePermissions(id, permissions);
        return Result.success();
    }
    
    @PutMapping("/{id}/password")
    @PreAuthorize("hasRole('ADMIN')")
    @OperationLog(operation = "重置用户密码")
    public Result<Void> resetPassword(@PathVariable Long id, @RequestBody String newPassword) {
        userService.resetPassword(id, newPassword);
        return Result.success();
    }
}
