package com.property.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.property.aspect.OperationLog;
import com.property.common.Result;
import com.property.entity.SysConfig;
import com.property.mapper.SysConfigMapper;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/config")
@RequiredArgsConstructor
public class ConfigController {
    
    private final SysConfigMapper configMapper;
    
    @Value("${file.upload-path:./uploads/}")
    private String uploadPath;
    
    @GetMapping
    public Result<Map<String, String>> getConfig() {
        Map<String, String> config = new HashMap<>();
        config.put("companyName", getConfigValue("company_name", "物业管理系统"));
        config.put("logoUrl", getConfigValue("company_logo", ""));
        config.put("defaultDueDays", getConfigValue("default_due_days", "15"));
        config.put("arrearsThreshold", getConfigValue("arrears_threshold", "20"));
        return Result.success(config);
    }
    
    @PutMapping
    @PreAuthorize("hasRole('ADMIN')")
    @OperationLog(operation = "更新系统配置")
    public Result<Void> updateConfig(@RequestBody ConfigDTO dto) {
        if (dto.getCompanyName() != null) {
            updateConfigValue("company_name", dto.getCompanyName());
        }
        if (dto.getDefaultDueDays() != null) {
            updateConfigValue("default_due_days", String.valueOf(dto.getDefaultDueDays()));
        }
        if (dto.getArrearsThreshold() != null) {
            updateConfigValue("arrears_threshold", String.valueOf(dto.getArrearsThreshold()));
        }
        return Result.success();
    }
    
    @PostMapping("/logo")
    @PreAuthorize("hasRole('ADMIN')")
    @OperationLog(operation = "上传Logo")
    public Result<String> uploadLogo(@RequestParam("file") MultipartFile file) {
        if (file.isEmpty()) {
            return Result.fail("请选择要上传的文件");
        }
        
        try {
            // 获取原始文件名和扩展名
            String originalFilename = file.getOriginalFilename();
            String extension = "";
            if (originalFilename != null && originalFilename.contains(".")) {
                extension = originalFilename.substring(originalFilename.lastIndexOf("."));
            }
            
            // 生成新文件名
            String filename = UUID.randomUUID().toString() + extension;
            
            // 确保上传目录存在
            Path uploadDir = Paths.get(uploadPath).toAbsolutePath().normalize();
            Files.createDirectories(uploadDir);
            
            // 保存文件
            Path targetPath = uploadDir.resolve(filename);
            Files.copy(file.getInputStream(), targetPath, StandardCopyOption.REPLACE_EXISTING);
            
            log.info("Logo上传成功: {}", targetPath);
            
            // 保存到配置 - 需要加上 /api 前缀因为有 context-path
            String logoUrl = "/api/uploads/" + filename;
            updateConfigValue("company_logo", logoUrl);
            
            return Result.success(logoUrl);
        } catch (IOException e) {
            log.error("Logo上传失败", e);
            return Result.fail("文件上传失败: " + e.getMessage());
        }
    }
    
    private String getConfigValue(String key, String defaultValue) {
        String value = configMapper.getValueByKey(key);
        return value != null ? value : defaultValue;
    }
    
    private void updateConfigValue(String key, String value) {
        LambdaQueryWrapper<SysConfig> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SysConfig::getConfigKey, key);
        SysConfig config = configMapper.selectOne(wrapper);
        
        if (config != null) {
            config.setConfigValue(value);
            configMapper.updateById(config);
        } else {
            config = new SysConfig();
            config.setConfigKey(key);
            config.setConfigValue(value);
            configMapper.insert(config);
        }
    }
    
    @Data
    public static class ConfigDTO {
        private String companyName;
        private Integer defaultDueDays;
        private Integer arrearsThreshold;
    }
}
