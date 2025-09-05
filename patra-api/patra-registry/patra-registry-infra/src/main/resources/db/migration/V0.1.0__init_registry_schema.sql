-- =====================================================================
-- 注册中心:系统唯一真实数据来源（Registry）—— reg_*
-- =====================================================================
CREATE TABLE IF NOT EXISTS `reg_data_source`
(
    `id`              BIGINT UNSIGNED NOT NULL COMMENT 'PK · Snowflake',
    `name`            VARCHAR(100)    NOT NULL COMMENT '数据源名称；pubmed/epmc/openalex/crossref',
    `code`            VARCHAR(50)     NOT NULL COMMENT '数据源代码；简短标识符',

    -- BaseEntity
    `record_remarks`  json            NULL COMMENT 'json数组，备注/变更说明 [{"time":"2025-08-18 15:00:00","by":"王五","note":"xxx"}]',
    `created_at`      TIMESTAMP(6)    NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间',
    `created_by`      BIGINT UNSIGNED          DEFAULT NULL COMMENT '创建人ID',
    `created_by_name` VARCHAR(100)             DEFAULT NULL COMMENT '创建人姓名',
    `updated_at`      TIMESTAMP(6)    NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '更新时间',
    `updated_by`      BIGINT UNSIGNED          DEFAULT NULL COMMENT '更新人ID',
    `updated_by_name` VARCHAR(100)             DEFAULT NULL COMMENT '更新人姓名',
    `version`         BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    `ip_address`      VARBINARY(16)            DEFAULT NULL COMMENT '请求方 IP（二进制，支持 IPv4/IPv6）',
    `deleted`         TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '逻辑删除',

    PRIMARY KEY (`id`),
    UNIQUE KEY `uq_source_code` (`code`)
) ENGINE = InnoDB
  ROW_FORMAT = DYNAMIC
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci
    COMMENT ='（Registry 域）外部数据源字典';

CREATE TABLE IF NOT EXISTS `reg_data_source_config`
(
    `id`                        BIGINT UNSIGNED NOT NULL COMMENT '主键（雪花/发号器）',
    `data_source_id`            BIGINT UNSIGNED NOT NULL COMMENT '逻辑外键→reg_data_source.id（不建物理FK）',
    `is_active`                 TINYINT(1)      NOT NULL DEFAULT 1 COMMENT '是否当前生效：1=生效，0=历史/停用',

    -- 运行策略
    `timezone`                  VARCHAR(64)     NOT NULL DEFAULT 'UTC' COMMENT '窗口计算/展示的时区；写库仍用UTC',
    `retry_max`                 INT             NULL COMMENT '失败最大重试次数；NULL=使用应用默认',
    `backoff_ms`                INT             NULL COMMENT '重试退避毫秒；与 retry_max 配合',
    `rate_limit_per_sec`        INT             NULL COMMENT '每秒请求上限（令牌桶）；NULL=不限流或用应用默认',

    -- 行为策略（平台无关；NULL 表示“使用应用默认/Provider 默认”）
    `search_page_size`          INT             NULL COMMENT '列表/搜索页大小（ESearch/列表等）；NULL=用默认',
    `fetch_batch_size`          INT             NULL COMMENT '详情批大小（EFetch/works 批）；NULL=用默认',
    `max_search_ids_per_window` INT             NULL COMMENT '单窗口最多 UID（超出则二次切窗）；NULL=用默认',
    `overlap_days`              INT             NULL COMMENT '增量窗口重叠天数（迟到兜底）；NULL=用默认',
    `retry_jitter`              DOUBLE          NULL COMMENT '重试抖动系数（0~1）；NULL=用默认',

    `enable_access`             TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '是否写入 acc_*（当来源返回 OA/下载位时）',

    -- 请求层默认：默认增量日期字段（平台相关）
    `date_field_default`        VARCHAR(32)     NULL COMMENT '默认增量字段（如 PubMed: EDAT/PDAT/MHDA；Crossref: index-date 等）',

    -- HTTP 与参数映射（避免把秘钥明文存库，建议仅存“引用ID”）
    `api_url`                   VARCHAR(255)    NOT NULL COMMENT 'API 基址',
    `auth`                      JSON            NULL COMMENT '鉴权配置（建议仅存凭据引用ID/issuer，真正密钥放 Vault/KMS/ENV）',
    `headers`                   JSON            NULL COMMENT '默认 HTTP 头（如 User-Agent/From/email 等）',

    -- 备注与审计
    `record_remarks`            JSON            NULL COMMENT 'json数组，备注/变更说明 [{"time":"2025-08-18 15:00:00","by":"王五","note":"xxx"}]',
    `version`                   BIGINT          NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    `ip_address`                VARBINARY(16)            DEFAULT NULL COMMENT '请求方 IP（二进制，支持 IPv4/IPv6）',
    `created_at`                TIMESTAMP(6)    NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间（UTC）',
    `created_by`                BIGINT          NULL COMMENT '创建人ID',
    `created_by_name`           VARCHAR(64)     NULL COMMENT '创建人姓名',
    `updated_at`                TIMESTAMP(6)    NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '更新时间（UTC）',
    `updated_by`                BIGINT          NULL COMMENT '更新人ID',
    `updated_by_name`           VARCHAR(64)     NULL COMMENT '更新人姓名',
    `deleted`                   TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '软删除：0=未删，1=已删',


    PRIMARY KEY (`id`),
    KEY `idx_src_active` (`data_source_id`, `is_active`),
    KEY `idx_updated` (`updated_at`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci
  ROW_FORMAT = DYNAMIC
    COMMENT ='（Registry 域）数据源动态配置（调度/限流/参数映射）— 与 reg_data_source 1:1 对应';
