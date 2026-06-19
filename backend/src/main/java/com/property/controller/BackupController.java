package com.property.controller;

import com.property.aspect.OperationLog;
import com.property.common.BusinessException;
import com.property.common.ErrorCode;
import com.property.common.Result;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.*;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@RestController
@RequestMapping("/backup")
@RequiredArgsConstructor
public class BackupController {
    
    private final JdbcTemplate jdbcTemplate;
    
    @Value("${spring.datasource.url}")
    private String dbUrl;
    
    @Value("${spring.datasource.username}")
    private String dbUsername;
    
    @Value("${spring.datasource.password}")
    private String dbPassword;
    
    @PostMapping("/export")
    @PreAuthorize("hasRole('ADMIN')")
    @OperationLog(operation = "数据备份")
    public void export(HttpServletResponse response) throws IOException {
        StringBuilder sql = new StringBuilder();
        sql.append("-- Property Management System Backup\n");
        sql.append("-- Generated at: ").append(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))).append("\n\n");
        
        // 导出所有业务表数据
        String[] tables = {"sys_account_set", "sys_user", "sys_permission", "sys_config", 
                          "t_owner", "t_parking", "t_fee_standard", "t_bill", "t_payment", 
                          "t_receivable", "t_template", "sys_operation_log"};
        
        for (String table : tables) {
            try {
                sql.append("-- Table: ").append(table).append("\n");
                sql.append("DELETE FROM ").append(table).append(";\n");
                
                List<String> insertStatements = generateInsertStatements(table);
                for (String insert : insertStatements) {
                    sql.append(insert).append("\n");
                }
                sql.append("\n");
            } catch (Exception e) {
                sql.append("-- Error exporting table ").append(table).append(": ").append(e.getMessage()).append("\n\n");
            }
        }
        
        String filename = "backup_" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")) + ".sql";
        response.setContentType("application/sql");
        response.setHeader("Content-Disposition", "attachment; filename=" + URLEncoder.encode(filename, StandardCharsets.UTF_8));
        response.getWriter().write(sql.toString());
    }
    
    private List<String> generateInsertStatements(String tableName) {
        String query = "SELECT * FROM " + tableName;
        return jdbcTemplate.query(query, (rs, rowNum) -> {
            StringBuilder insert = new StringBuilder("INSERT INTO ").append(tableName).append(" VALUES (");
            int columnCount = rs.getMetaData().getColumnCount();
            for (int i = 1; i <= columnCount; i++) {
                if (i > 1) insert.append(", ");
                Object value = rs.getObject(i);
                if (value == null) {
                    insert.append("NULL");
                } else if (value instanceof Number) {
                    insert.append(value);
                } else {
                    insert.append("'").append(value.toString().replace("'", "''")).append("'");
                }
            }
            insert.append(");");
            return insert.toString();
        });
    }
    
    @PostMapping("/import")
    @PreAuthorize("hasRole('ADMIN')")
    @OperationLog(operation = "数据恢复")
    public Result<Void> importData(@RequestParam("file") MultipartFile file) {
        try {
            String sql = new String(file.getBytes(), StandardCharsets.UTF_8);
            String[] statements = sql.split(";");
            
            int successCount = 0;
            for (String statement : statements) {
                String trimmed = statement.trim();
                if (trimmed.isEmpty() || trimmed.startsWith("--")) continue;
                
                try {
                    jdbcTemplate.execute(trimmed);
                    successCount++;
                } catch (Exception e) {
                    // 跳过执行失败的语句，继续执行
                }
            }
            
            return Result.success();
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "数据恢复失败: " + e.getMessage());
        }
    }
    
    @PostMapping("/init")
    @PreAuthorize("hasRole('ADMIN')")
    @OperationLog(operation = "系统初始化")
    public Result<Void> init() {
        // 清除业务数据，保留系统配置和管理员账户
        String[] tables = {"t_bill", "t_payment", "t_receivable", "t_owner", "t_parking", "t_fee_standard", "t_template"};
        
        for (String table : tables) {
            try {
                jdbcTemplate.execute("DELETE FROM " + table);
            } catch (Exception e) {
                // 忽略错误继续
            }
        }
        
        return Result.success();
    }
}
