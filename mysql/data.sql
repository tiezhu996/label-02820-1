-- 物业管理系统初始化数据
USE property_management;

-- 初始化管理员账户 (密码: 123456, BCrypt加密)
INSERT INTO sys_user (username, password, real_name, role, status) VALUES
('admin', '$2a$10$7JB720yubVSZvUI0rEqK/.VqGOZTH.ulu33dHOiBE8ByOhJIrdAu2', '系统管理员', 'ADMIN', 1)
ON DUPLICATE KEY UPDATE password = '$2a$10$7JB720yubVSZvUI0rEqK/.VqGOZTH.ulu33dHOiBE8ByOhJIrdAu2';

-- 初始化默认账套
INSERT INTO sys_account_set (name, description, status) VALUES
('默认账套', '系统默认账套', 1)
ON DUPLICATE KEY UPDATE name = name;

-- 初始化系统配置
INSERT INTO sys_config (config_key, config_value, description) VALUES
('company_name', '物业管理系统', '物业公司名称'),
('company_logo', NULL, '物业公司Logo路径')
ON DUPLICATE KEY UPDATE config_key = config_key;

-- 初始化默认缴费方式 (账套ID=1)
INSERT INTO t_payment_method (account_set_id, method_name, status) VALUES
(1, '现金', 1),
(1, '转账', 1),
(1, 'POS', 1),
(1, '银行转账', 1),
(1, '微信支付', 1),
(1, '支付宝', 1)
ON DUPLICATE KEY UPDATE method_name = method_name;

-- 初始化默认收费标准 (账套ID=1)
INSERT INTO t_fee_standard (account_set_id, fee_type, fee_name, amount, unit, frequency, status) VALUES
(1, 'PROPERTY', '物业管理费', 2.50, '元/平方米/月', 'PERIODIC', 1),
(1, 'PARKING', '车位管理费', 100.00, '元/个/月', 'PERIODIC', 1)
ON DUPLICATE KEY UPDATE fee_name = fee_name;
