-- 物业管理系统数据库建表语句
-- 数据库: property_management
-- 字符集: utf8mb4

CREATE DATABASE IF NOT EXISTS property_management DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE property_management;

-- =============================================
-- 系统表
-- =============================================

-- 用户表
CREATE TABLE IF NOT EXISTS sys_user (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
    username VARCHAR(50) NOT NULL UNIQUE COMMENT '用户名',
    password VARCHAR(255) NOT NULL COMMENT '密码(BCrypt加密)',
    real_name VARCHAR(50) COMMENT '真实姓名',
    role VARCHAR(20) NOT NULL DEFAULT 'USER' COMMENT '角色: ADMIN/USER',
    status TINYINT NOT NULL DEFAULT 1 COMMENT '状态: 0禁用/1启用',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX idx_username (username),
    INDEX idx_role (role)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户表';

-- 用户权限表
CREATE TABLE IF NOT EXISTS sys_permission (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
    user_id BIGINT NOT NULL COMMENT '用户ID',
    permission_code VARCHAR(100) NOT NULL COMMENT '权限编码',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    INDEX idx_user_id (user_id),
    UNIQUE KEY uk_user_permission (user_id, permission_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户权限表';

-- 操作日志表
CREATE TABLE IF NOT EXISTS sys_operation_log (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
    user_id BIGINT COMMENT '操作用户ID',
    username VARCHAR(50) COMMENT '操作用户名',
    operation VARCHAR(50) NOT NULL COMMENT '操作类型',
    method VARCHAR(200) COMMENT '请求方法',
    params TEXT COMMENT '请求参数',
    ip VARCHAR(50) COMMENT 'IP地址',
    account_set_id BIGINT COMMENT '账套ID',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '操作时间',
    INDEX idx_user_id (user_id),
    INDEX idx_create_time (create_time),
    INDEX idx_operation (operation)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='操作日志表';

-- 账套表
CREATE TABLE IF NOT EXISTS sys_account_set (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
    name VARCHAR(100) NOT NULL COMMENT '账套名称',
    description VARCHAR(500) COMMENT '描述',
    status TINYINT NOT NULL DEFAULT 1 COMMENT '状态: 0禁用/1启用',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='账套表';

-- 系统配置表
CREATE TABLE IF NOT EXISTS sys_config (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
    config_key VARCHAR(100) NOT NULL UNIQUE COMMENT '配置键',
    config_value TEXT COMMENT '配置值',
    description VARCHAR(500) COMMENT '描述',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='系统配置表';


-- =============================================
-- 业务表
-- =============================================

-- 业主表
CREATE TABLE IF NOT EXISTS t_owner (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
    account_set_id BIGINT NOT NULL COMMENT '账套ID',
    name VARCHAR(50) NOT NULL COMMENT '业主姓名',
    building_no VARCHAR(20) NOT NULL COMMENT '楼栋号',
    unit_no VARCHAR(20) NOT NULL COMMENT '单元号',
    room_no VARCHAR(20) NOT NULL COMMENT '房间号',
    phone VARCHAR(20) COMMENT '电话',
    area DECIMAL(10,2) NOT NULL DEFAULT 0 COMMENT '房屋面积(平方米)',
    move_in_date DATE COMMENT '入住时间',
    status VARCHAR(20) NOT NULL DEFAULT 'VACANT' COMMENT '房屋状态: OCCUPIED已入住/VACANT空置',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX idx_account_set_id (account_set_id),
    INDEX idx_building_no (building_no),
    INDEX idx_status (status),
    UNIQUE KEY uk_room (account_set_id, building_no, unit_no, room_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='业主表';

-- 车位表
CREATE TABLE IF NOT EXISTS t_parking (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
    account_set_id BIGINT NOT NULL COMMENT '账套ID',
    parking_no VARCHAR(50) NOT NULL COMMENT '车位号',
    owner_id BIGINT COMMENT '绑定业主ID',
    status VARCHAR(20) NOT NULL DEFAULT 'VACANT' COMMENT '状态: USED已使用/VACANT空置',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX idx_account_set_id (account_set_id),
    INDEX idx_owner_id (owner_id),
    INDEX idx_status (status),
    UNIQUE KEY uk_parking_no (account_set_id, parking_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='车位表';

-- 收费标准表
CREATE TABLE IF NOT EXISTS t_fee_standard (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
    account_set_id BIGINT NOT NULL COMMENT '账套ID',
    fee_type VARCHAR(50) NOT NULL COMMENT '费用类型: PROPERTY物业费/PARKING车位费/CUSTOM自定义',
    fee_name VARCHAR(100) NOT NULL COMMENT '费用名称',
    amount DECIMAL(10,2) NOT NULL DEFAULT 0 COMMENT '金额',
    unit VARCHAR(50) NOT NULL COMMENT '单位',
    frequency VARCHAR(20) NOT NULL DEFAULT 'MONTHLY' COMMENT '收费频次: MONTHLY按月/QUARTERLY按季度/YEARLY按年/ONETIME一次性',
    status TINYINT NOT NULL DEFAULT 1 COMMENT '状态: 0禁用/1启用',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX idx_account_set_id (account_set_id),
    INDEX idx_fee_type (fee_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='收费标准表';

-- 缴费方式表
CREATE TABLE IF NOT EXISTS t_payment_method (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
    account_set_id BIGINT NOT NULL COMMENT '账套ID',
    method_name VARCHAR(50) NOT NULL COMMENT '方式名称',
    status TINYINT NOT NULL DEFAULT 1 COMMENT '状态: 0禁用/1启用',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    INDEX idx_account_set_id (account_set_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='缴费方式表';

-- 账单表
CREATE TABLE IF NOT EXISTS t_bill (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
    account_set_id BIGINT NOT NULL COMMENT '账套ID',
    bill_no VARCHAR(50) NOT NULL COMMENT '账单编号',
    owner_id BIGINT NOT NULL COMMENT '业主ID',
    parking_id BIGINT COMMENT '车位ID',
    fee_type VARCHAR(50) NOT NULL COMMENT '费用类型',
    fee_name VARCHAR(100) NOT NULL COMMENT '费用名称',
    amount DECIMAL(10,2) NOT NULL DEFAULT 0 COMMENT '应收金额',
    paid_amount DECIMAL(10,2) NOT NULL DEFAULT 0 COMMENT '已缴金额',
    period_start DATE NOT NULL COMMENT '账单周期开始',
    period_end DATE NOT NULL COMMENT '账单周期结束',
    due_date DATE NOT NULL COMMENT '缴费截止日期',
    status VARCHAR(20) NOT NULL DEFAULT 'UNPAID' COMMENT '状态: UNPAID未缴/PAID已缴/OVERDUE欠费',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX idx_account_set_id (account_set_id),
    INDEX idx_owner_id (owner_id),
    INDEX idx_status (status),
    INDEX idx_period (period_start, period_end),
    UNIQUE KEY uk_bill_no (account_set_id, bill_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='账单表';

-- 账单定时任务表
CREATE TABLE IF NOT EXISTS t_bill_schedule (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
    account_set_id BIGINT NOT NULL COMMENT '账套ID',
    fee_type VARCHAR(50) NOT NULL COMMENT '费用类型',
    custom_fee_type VARCHAR(50) DEFAULT NULL COMMENT '自定义费用类型名称(fee_type为CUSTOM时使用)',
    generate_day INT NOT NULL DEFAULT 1 COMMENT '生成日期(每月几号)',
    period_type VARCHAR(20) NOT NULL DEFAULT 'MONTHLY' COMMENT '周期类型: MONTHLY/QUARTERLY/YEARLY',
    due_days INT NOT NULL DEFAULT 30 COMMENT '缴费期限(天)',
    status TINYINT NOT NULL DEFAULT 1 COMMENT '状态: 0禁用/1启用',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX idx_account_set_id (account_set_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='账单定时任务表';


-- 缴费记录表
CREATE TABLE IF NOT EXISTS t_payment (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
    account_set_id BIGINT NOT NULL COMMENT '账套ID',
    payment_no VARCHAR(50) NOT NULL COMMENT '缴费单号',
    bill_id BIGINT NOT NULL COMMENT '账单ID',
    owner_id BIGINT NOT NULL COMMENT '业主ID',
    amount DECIMAL(10,2) NOT NULL DEFAULT 0 COMMENT '缴费金额',
    discount_rate DECIMAL(5,2) NOT NULL DEFAULT 0 COMMENT '优惠比例(%)',
    discount_amount DECIMAL(10,2) NOT NULL DEFAULT 0 COMMENT '优惠金额',
    actual_amount DECIMAL(10,2) NOT NULL DEFAULT 0 COMMENT '实际缴费金额',
    payment_method VARCHAR(50) NOT NULL COMMENT '缴费方式',
    payment_period VARCHAR(20) COMMENT '缴费区间',
    operator_id BIGINT NOT NULL COMMENT '操作人ID',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '缴费时间',
    INDEX idx_account_set_id (account_set_id),
    INDEX idx_bill_id (bill_id),
    INDEX idx_owner_id (owner_id),
    INDEX idx_create_time (create_time),
    UNIQUE KEY uk_payment_no (account_set_id, payment_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='缴费记录表';

-- 应收账款表
CREATE TABLE IF NOT EXISTS t_receivable (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
    account_set_id BIGINT NOT NULL COMMENT '账套ID',
    owner_id BIGINT NOT NULL COMMENT '业主ID',
    parking_id BIGINT COMMENT '车位ID',
    fee_type VARCHAR(50) NOT NULL COMMENT '费用类型',
    period_month VARCHAR(10) NOT NULL COMMENT '账期月份(YYYY-MM)',
    amount DECIMAL(10,2) NOT NULL DEFAULT 0 COMMENT '应收金额',
    paid_amount DECIMAL(10,2) NOT NULL DEFAULT 0 COMMENT '已缴金额',
    is_locked TINYINT NOT NULL DEFAULT 0 COMMENT '是否锁定: 0否/1是',
    cumulative_amount DECIMAL(10,2) NOT NULL DEFAULT 0 COMMENT '累计应收',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX idx_account_set_id (account_set_id),
    INDEX idx_owner_id (owner_id),
    INDEX idx_period_month (period_month),
    INDEX idx_is_locked (is_locked)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='应收账款表';

-- 模板表
CREATE TABLE IF NOT EXISTS t_template (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
    account_set_id BIGINT NOT NULL COMMENT '账套ID',
    template_type VARCHAR(50) NOT NULL COMMENT '模板类型: REMINDER催缴/VIOLATION违规/NOTICE通知',
    template_name VARCHAR(100) NOT NULL COMMENT '模板名称',
    file_path VARCHAR(500) NOT NULL COMMENT '文件路径',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX idx_account_set_id (account_set_id),
    INDEX idx_template_type (template_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='模板表';

-- 楼栋配置表
CREATE TABLE IF NOT EXISTS t_building (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
    account_set_id BIGINT NOT NULL COMMENT '账套ID',
    building_no VARCHAR(20) NOT NULL COMMENT '楼栋号',
    unit_count INT NOT NULL DEFAULT 1 COMMENT '单元数量',
    floor_count INT NOT NULL DEFAULT 1 COMMENT '楼层数',
    rooms_per_floor INT NOT NULL DEFAULT 2 COMMENT '每层房间数',
    position_x INT NOT NULL DEFAULT 0 COMMENT '平面图X坐标',
    position_y INT NOT NULL DEFAULT 0 COMMENT '平面图Y坐标',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX idx_account_set_id (account_set_id),
    UNIQUE KEY uk_building_no (account_set_id, building_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='楼栋配置表';
