package com.property.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.property.entity.SysPermission;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import java.util.List;

@Mapper
public interface SysPermissionMapper extends BaseMapper<SysPermission> {
    
    @Select("SELECT permission_code FROM sys_permission WHERE user_id = #{userId}")
    List<String> findPermissionsByUserId(Long userId);
}
