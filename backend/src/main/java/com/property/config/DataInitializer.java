package com.property.config;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.property.entity.SysUser;
import com.property.mapper.SysUserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class DataInitializer implements CommandLineRunner {
    
    private final SysUserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    
    @Override
    public void run(String... args) {
        // 确保admin账户存在且密码正确
        LambdaQueryWrapper<SysUser> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SysUser::getUsername, "admin");
        SysUser admin = userMapper.selectOne(wrapper);
        
        if (admin == null) {
            // 创建admin账户
            admin = new SysUser();
            admin.setUsername("admin");
            admin.setPassword(passwordEncoder.encode("123456"));
            admin.setRealName("系统管理员");
            admin.setRole("ADMIN");
            admin.setStatus(1);
            userMapper.insert(admin);
            log.info("已创建默认管理员账户: admin / 123456");
        } else {
            // 更新密码确保正确
            admin.setPassword(passwordEncoder.encode("123456"));
            userMapper.updateById(admin);
            log.info("已更新管理员密码: admin / 123456");
        }
    }
}
