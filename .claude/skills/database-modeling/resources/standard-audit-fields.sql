-- ============================================================
-- 标准审计字段 SQL 模板
-- ============================================================
-- 用途: 复制此模板到你的 CREATE TABLE 语句中
-- 所有业务表必须包含这些审计字段以支持数据追踪和合规要求
-- ============================================================

-- ==================== 审计字段 ====================
-- 变更追踪
-- `record_remarks`    JSON            NULL COMMENT 'JSON 数组, 备注/变更日志 [{"time":"2025-08-18 15:00:00","by":"John Doe","note":"xxx"}]',
-- `version`           BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
-- `ip_address`        VARBINARY(16)   NULL COMMENT '请求者IP (二进制, 支持 IPv4/IPv6)',

-- 创建信息
-- `created_at`        TIMESTAMP(6)    NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间 (UTC)',
-- `created_by`        BIGINT UNSIGNED NULL COMMENT '创建人ID',
-- `created_by_name`   VARCHAR(100)    NULL COMMENT '创建人姓名',

-- 更新信息
-- `updated_at`        TIMESTAMP(6)    NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '更新时间 (UTC)',
-- `updated_by`        BIGINT UNSIGNED NULL COMMENT '更新人ID',
-- `updated_by_name`   VARCHAR(100)    NULL COMMENT '更新人姓名',

-- 软删除
-- `deleted`           TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '软删除: 0=活动, 1=已删除',

-- ============================================================
-- 字段说明
-- ============================================================

-- 1. record_remarks (JSON)
-- ============================================================
-- 用途: 记录数据的变更历史和备注
-- 格式: JSON 数组
-- 示例:
--   [
--     {
--       "time": "2025-08-18 15:00:00",
--       "by": "John Doe",
--       "action": "UPDATE",
--       "note": "修正了标题拼写错误"
--     },
--     {
--       "time": "2025-08-19 10:30:00",
--       "by": "Jane Smith",
--       "action": "UPDATE",
--       "note": "更新了摘要内容"
--     }
--   ]
--
-- 应用层操作:
--   - 插入时: SET record_remarks = '[]'
--   - 更新时: JSON_ARRAY_APPEND(record_remarks, '$', JSON_OBJECT(...))

-- 2. version (BIGINT UNSIGNED)
-- ============================================================
-- 用途: 乐观锁版本控制，防止并发更新冲突
-- 机制: 每次更新时版本号 +1
-- MyBatis-Plus 集成: @Version 注解
--
-- 更新语句示例:
--   UPDATE user
--   SET username = 'new_name', version = version + 1
--   WHERE id = 123 AND version = 5;
--   -- 如果 version 已经不是 5，更新失败（被其他事务修改）

-- 3. ip_address (VARBINARY(16))
-- ============================================================
-- 用途: 记录操作者的 IP 地址，用于安全审计
-- 存储格式: 二进制（节省空间，支持 IPv4 和 IPv6）
--
-- IPv4 存储 (4 字节):
--   INSERT INTO user (ip_address, ...) VALUES (INET6_ATON('192.168.1.1'), ...);
--
-- IPv6 存储 (16 字节):
--   INSERT INTO user (ip_address, ...) VALUES (INET6_ATON('2001:0db8:85a3::8a2e:0370:7334'), ...);
--
-- 查询时转换回字符串:
--   SELECT INET6_NTOA(ip_address) AS ip FROM user WHERE id = 123;
--
-- 注意事项:
--   - 符合 GDPR 合规要求，建议设置 IP 地址自动清理策略（如 90 天后匿名化）
--   - 在隐私政策中声明 IP 收集目的

-- 4. created_at / updated_at (TIMESTAMP(6))
-- ============================================================
-- 用途: 记录记录的创建和更新时间
-- 精度: 微秒级（6 位小数）
-- 时区: 统一存储 UTC 时间
--
-- 创建时自动填充:
--   DEFAULT CURRENT_TIMESTAMP(6)
--
-- 更新时自动更新:
--   DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6)
--
-- MyBatis-Plus 集成:
--   @TableField(value = "created_at", fill = FieldFill.INSERT)
--   private LocalDateTime createdAt;
--
-- 时区处理:
--   - 数据库服务器时区: SET GLOBAL time_zone = '+00:00';
--   - JVM 时区: -Duser.timezone=UTC
--   - API 响应格式: ISO 8601 (2025-11-17T10:30:00.000Z)

-- 5. created_by / updated_by (BIGINT UNSIGNED)
-- ============================================================
-- 用途: 记录操作者的用户 ID
-- 允许 NULL: 系统自动导入的数据可以为 NULL
--
-- MyBatis-Plus 自动填充:
--   @TableField(value = "created_by", fill = FieldFill.INSERT)
--   private Long createdBy;
--
-- 实现方式:
--   @Component
--   public class AuditMetaObjectHandler implements MetaObjectHandler {
--       @Override
--       public void insertFill(MetaObject metaObject) {
--           Long userId = SecurityContextHolder.getCurrentUserId();
--           this.strictInsertFill(metaObject, "createdBy", Long.class, userId);
--       }
--   }

-- 6. created_by_name / updated_by_name (VARCHAR(100))
-- ============================================================
-- 用途: 记录操作者的姓名（冗余字段，用于快速显示）
-- 设计决策: 存储时点姓名（不跟随用户改名更新，适合审计需求）
--
-- 替代方案（仅存储 ID）:
--   - 优点: 符合范式，无数据冗余
--   - 缺点: 每次显示需要关联查询 user 表
--
-- 当前方案（同时存储 ID 和姓名）:
--   - 优点: 查询性能高，保留历史操作者姓名
--   - 缺点: 数据冗余，如果用户改名，历史记录显示旧名字

-- 7. deleted (TINYINT(1))
-- ============================================================
-- 用途: 软删除标志
-- 取值: 0 = 活动记录, 1 = 已删除
--
-- MyBatis-Plus 逻辑删除:
--   @TableLogic
--   @TableField("deleted")
--   private Boolean deleted;
--
-- 索引设计注意事项:
--   - deleted 字段区分度极低（< 0.1），不应该单独建索引
--   - 如需同时过滤 deleted=0 和其他条件，将 deleted 放在组合索引末尾:
--     KEY `idx_username_deleted` (`username`, `deleted`)

-- ============================================================
-- 完整示例：用户表
-- ============================================================

CREATE TABLE `user` (
  -- 主键
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',

  -- 业务字段
  `username` VARCHAR(50) NOT NULL COMMENT '用户名',
  `email` VARCHAR(100) NOT NULL COMMENT '邮箱',
  `password_hash` VARCHAR(255) NOT NULL COMMENT '密码哈希',
  `status` TINYINT NOT NULL DEFAULT 1 COMMENT '状态: 0=禁用, 1=启用',

  -- 审计字段（复制自上方模板）
  `record_remarks` JSON NULL COMMENT 'JSON 数组, 备注/变更日志',
  `version` BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
  `ip_address` VARBINARY(16) NULL COMMENT '请求者IP (二进制, 支持 IPv4/IPv6)',
  `created_at` TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间 (UTC)',
  `created_by` BIGINT UNSIGNED NULL COMMENT '创建人ID',
  `created_by_name` VARCHAR(100) NULL COMMENT '创建人姓名',
  `updated_at` TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '更新时间 (UTC)',
  `updated_by` BIGINT UNSIGNED NULL COMMENT '更新人ID',
  `updated_by_name` VARCHAR(100) NULL COMMENT '更新人姓名',
  `deleted` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '软删除: 0=活动, 1=已删除',

  -- 索引
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_username` (`username`),
  UNIQUE KEY `uk_email` (`email`),
  KEY `idx_status` (`status`),
  KEY `idx_created_at` (`created_at`)
  -- 注意: deleted 字段区分度低，不建立单独索引
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户表';
