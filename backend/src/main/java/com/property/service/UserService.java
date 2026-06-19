package com.property.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.property.dto.UserDTO;
import com.property.entity.SysUser;

import java.util.List;

public interface UserService {
    Page<SysUser> getPage(int page, int size, String username, String role);
    SysUser getById(Long id);
    void create(UserDTO dto);
    void update(Long id, UserDTO dto);
    void delete(Long id);
    void updatePermissions(Long userId, List<String> permissions);
    void resetPassword(Long id, String newPassword);
}
