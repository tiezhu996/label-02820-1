package com.property.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.property.common.BusinessException;
import com.property.common.ErrorCode;
import com.property.dto.UserDTO;
import com.property.entity.SysPermission;
import com.property.entity.SysUser;
import com.property.mapper.SysPermissionMapper;
import com.property.mapper.SysUserMapper;
import com.property.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;

@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {
    
    private final SysUserMapper userMapper;
    private final SysPermissionMapper permissionMapper;
    private final PasswordEncoder passwordEncoder;
    
    @Override
    public Page<SysUser> getPage(int page, int size, String username, String role) {
        Page<SysUser> pageParam = new Page<>(page, size);
        LambdaQueryWrapper<SysUser> wrapper = new LambdaQueryWrapper<>();
        
        if (StringUtils.hasText(username)) {
            wrapper.like(SysUser::getUsername, username);
        }
        if (StringUtils.hasText(role)) {
            wrapper.eq(SysUser::getRole, role);
        }
        wrapper.orderByDesc(SysUser::getCreateTime);
        
        return userMapper.selectPage(pageParam, wrapper);
    }
    
    @Override
    public SysUser getById(Long id) {
        SysUser user = userMapper.selectById(id);
        if (user == null) {
            throw new BusinessException(ErrorCode.DATA_NOT_FOUND, "用户不存在");
        }
        return user;
    }
    
    @Override
    @Transactional
    public void create(UserDTO dto) {
        SysUser existing = userMapper.findByUsername(dto.getUsername());
        if (existing != null) {
            throw new BusinessException(ErrorCode.DATA_DUPLICATE, "用户名已存在");
        }
        
        SysUser user = new SysUser();
        user.setUsername(dto.getUsername());
        user.setPassword(passwordEncoder.encode(dto.getPassword()));
        user.setRealName(dto.getRealName());
        user.setRole(dto.getRole() != null ? dto.getRole() : "USER");
        user.setStatus(dto.getStatus() != null ? dto.getStatus() : 1);
        
        userMapper.insert(user);
        
        if (dto.getPermissions() != null && !dto.getPermissions().isEmpty()) {
            updatePermissions(user.getId(), dto.getPermissions());
        }
    }
    
    @Override
    @Transactional
    public void update(Long id, UserDTO dto) {
        SysUser user = getById(id);
        
        if (StringUtils.hasText(dto.getRealName())) {
            user.setRealName(dto.getRealName());
        }
        if (dto.getRole() != null) {
            user.setRole(dto.getRole());
        }
        if (dto.getStatus() != null) {
            user.setStatus(dto.getStatus());
        }
        
        userMapper.updateById(user);
        
        if (dto.getPermissions() != null) {
            updatePermissions(id, dto.getPermissions());
        }
    }
    
    @Override
    @Transactional
    public void delete(Long id) {
        SysUser user = getById(id);
        if ("admin".equals(user.getUsername())) {
            throw new BusinessException("不能删除管理员账户");
        }
        
        userMapper.deleteById(id);
        permissionMapper.delete(new LambdaQueryWrapper<SysPermission>()
                .eq(SysPermission::getUserId, id));
    }
    
    @Override
    @Transactional
    public void updatePermissions(Long userId, List<String> permissions) {
        permissionMapper.delete(new LambdaQueryWrapper<SysPermission>()
                .eq(SysPermission::getUserId, userId));
        
        for (String permissionCode : permissions) {
            SysPermission permission = new SysPermission();
            permission.setUserId(userId);
            permission.setPermissionCode(permissionCode);
            permissionMapper.insert(permission);
        }
    }
    
    @Override
    public void resetPassword(Long id, String newPassword) {
        SysUser user = getById(id);
        user.setPassword(passwordEncoder.encode(newPassword));
        userMapper.updateById(user);
    }
}
