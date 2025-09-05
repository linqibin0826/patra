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

CREATE TABLE IF NOT EXISTS `reg_plat_field_dict`
(
    `id`               bigint unsigned                                                                     not null comment '主键（雪花/发号器）',
    `field_key`        varchar(64)                                                                         not null comment '平台统一字段键（小写蛇形，如 pub_date/title_abstract）',
    `data_type`        enum ('date','datetime','number','text','keyword','boolean','token')                not null comment '数据类型',
    `cardinality`      enum ('single','multi') default 'single'                                            not null comment '基数：单值/多值',
    `is_date`          tinyint(1)              default 0                                                   not null comment '是否日期字段（DateLens 判定用）',
    `datetype`         enum ('PDAT','EDAT','MHDA')                                                         null comment '仅日期类使用的 datetype 映射',

    -- 审计/运维
    `record_remarks`   json                                                                                null comment 'json 数组，备注/变更说明',
    `version`          bigint                  default 0                                                   not null comment '乐观锁版本号',
    `ip_address`       varbinary(16)                                                                       null comment '请求方 IP（二进制，支持 IPv4/IPv6）',
    `created_at`       TIMESTAMP(6)            default CURRENT_TIMESTAMP(6)                                not null comment '创建时间（UTC）',
    `created_by`       bigint                                                                              null comment '创建人ID',
    `created_by_name`  varchar(64)                                                                         null comment '创建人姓名',
    `updated_at`       TIMESTAMP(6)            default CURRENT_TIMESTAMP(6) on update CURRENT_TIMESTAMP(6) not null comment '更新时间（UTC）',
    `updated_by`       bigint                                                                              null comment '更新人ID',
    `updated_by_name`  varchar(64)                                                                         null comment '更新人姓名',
    `deleted`          tinyint(1)              default 0                                                   not null comment '软删除：0=未删，1=已删',

    -- 仅对未删除记录生效的唯一约束
    `active_field_key` varchar(64) as (case when (deleted = 0) then field_key else null end) comment '唯一约束辅助：仅未删除记录参与唯一',
    PRIMARY KEY (`id`),
    CONSTRAINT `uk_field_dict_active` UNIQUE (`active_field_key`)
)
    ENGINE = InnoDB
    ROW_FORMAT = DYNAMIC
    COMMENT ='（Registry 域）平台统一字段字典（UFK Registry）：Expr 可用字段的唯一事实来源';

CREATE TABLE IF NOT EXISTS `reg_source_api_param_mapping`
(
    `id`              bigint unsigned                           not null comment '主键（雪花/发号器）',
    `data_source_id`  bigint unsigned                           not null comment '逻辑外键→reg_data_source.id（不建物理FK）',
    `is_active`       tinyint(1)   default 1                    not null comment '是否当前生效：1=生效，0=历史/停用',

    `operation`       varchar(32)                               not null comment '操作名：search/fetch/lookup…',
    `std_key`         varchar(64)                               not null comment '标准键（统一内部语义键）',
    `provider_param`  varchar(64)                               not null comment '供应商参数名（如 term/retmax/retstart）',
    `transform`       varchar(64)                               null comment '可选转换函数（如 toExclusiveMinus1d）',
    `notes`           json                                      null comment '备注/补充说明',

    `record_remarks`  json                                      null comment 'json数组，备注/变更说明',
    `version`         bigint       default 0                    not null comment '乐观锁版本号',
    `ip_address`      varbinary(16)                             null comment '请求方 IP（二进制，支持 IPv4/IPv6）',
    `created_at`      TIMESTAMP(6) default CURRENT_TIMESTAMP(6) not null comment '创建时间（UTC）',
    `created_by`      bigint                                    null comment '创建人ID',
    `created_by_name` varchar(64)                               null comment '创建人姓名',
    `updated_at`      TIMESTAMP(6) default CURRENT_TIMESTAMP(6) not null on update CURRENT_TIMESTAMP(6) comment '更新时间（UTC）',
    `updated_by`      bigint                                    null comment '更新人ID',
    `updated_by_name` varchar(64)                               null comment '更新人姓名',
    `deleted`         tinyint(1)   default 0                    not null comment '软删除：0=未删，1=已删',

    `active_std_key`  varchar(64) as ((case
                                           when ((`is_active` = 1) and (`deleted` = 0)) then `std_key`
                                           else NULL end)) comment '唯一约束辅助：仅激活记录参与唯一',

    PRIMARY KEY (`id`),
    CONSTRAINT `uk_parammap_active` UNIQUE (`data_source_id`, `operation`, `active_std_key`),
    KEY `idx_parammap_src_active` (`data_source_id`, `is_active`),
    KEY `idx_parammap_lookup` (`data_source_id`, `operation`, `std_key`),
    KEY `idx_parammap_updated` (`updated_at`)
)
    ENGINE = InnoDB
    ROW_FORMAT = DYNAMIC
    COMMENT ='（Registry 域）供应商 API 参数映射表：标准键 → 供应商参数名（按 operation 区分；仅激活记录唯一）';

CREATE TABLE IF NOT EXISTS `reg_source_query_capability`
(
    `id`                          bigint unsigned                                                       not null comment '主键（雪花/发号器）',
    `data_source_id`              bigint unsigned                                                       not null comment '逻辑外键→reg_data_source.id（不建物理FK）',
    `is_active`                   tinyint(1)                               default 1                    not null comment '是否当前生效：1=生效，0=历史/停用',

    `field_key`                   varchar(64)                                                           not null comment '内部字段键，如 ti/ab/tiab/la/pt/dp/owner（自定义字典）',

    `ops`                         json                                                                  not null comment '允许的操作符集合：["TERM","IN","RANGE","EXISTS","TOKEN"]',
    `negatable_ops`               json                                                                  null comment '允许取反的操作符子集；NULL 表示与 ops 相同',
    `supports_not`                tinyint(1)                               default 1                    not null comment '该字段是否允许 NOT 取反（总开关）',

    `term_matches`                json                                                                  null comment 'TERM 允许的匹配策略：["PHRASE","EXACT","ANY"]',
    `term_case_sensitive_allowed` tinyint(1)                               default 0                    not null comment 'TERM 是否允许大小写敏感',
    `term_allow_blank`            tinyint(1)                               default 0                    not null comment 'TERM 是否允许空白',
    `term_min_len`                int                                      default 0                    not null comment 'TERM 最小长度；0 不限制',
    `term_max_len`                int                                      default 0                    not null comment 'TERM 最大长度；0 不限制',
    `term_pattern`                varchar(255)                                                          null comment 'TERM 值正则（可选）',

    `in_max_size`                 int                                      default 0                    not null comment 'IN 最大项数；0 不限制',
    `in_case_sensitive_allowed`   tinyint(1)                               default 0                    not null comment 'IN 是否允许大小写敏感',

    `range_kind`                  enum ('NONE','DATE','DATETIME','NUMBER') default 'NONE'               not null comment '范围类型',
    `range_allow_open_start`      tinyint(1)                               default 1                    not null comment '允许省略 from（-∞, x]',
    `range_allow_open_end`        tinyint(1)                               default 1                    not null comment '允许省略 to [x, +∞)',
    `range_allow_closed_at_infty` tinyint(1)                               default 0                    not null comment '允许无穷端闭区间（如 (-∞,x]）',

    `date_min`                    date                                                                  null comment '最小日期（DATE）',
    `date_max`                    date                                                                  null comment '最大日期（DATE）',
    `datetime_min`                TIMESTAMP(6)                                                          null comment '最小时间（DATETIME，UTC）',
    `datetime_max`                TIMESTAMP(6)                                                          null comment '最大时间（DATETIME，UTC）',
    `number_min`                  decimal(38, 12)                                                       null comment '最小数值（NUMBER）',
    `number_max`                  decimal(38, 12)                                                       null comment '最大数值（NUMBER）',

    `exists_supported`            tinyint(1)                               default 0                    not null comment '是否支持 EXISTS',
    `token_kinds`                 json                                                                  null comment '允许的 token 种类集合（小写），如 ["owner","pmcid"]',
    `token_value_pattern`         varchar(255)                                                          null comment 'token 值正则（可选）',

    `record_remarks`              json                                                                  null comment 'json数组，备注/变更说明',
    `version`                     bigint                                   default 0                    not null comment '乐观锁版本号',
    `ip_address`                  varbinary(16)                                                         null comment '请求方 IP（二进制，支持 IPv4/IPv6）',
    `created_at`                  TIMESTAMP(6)                             default CURRENT_TIMESTAMP(6) not null comment '创建时间（UTC）',
    `created_by`                  bigint                                                                null comment '创建人ID',
    `created_by_name`             varchar(64)                                                           null comment '创建人姓名',
    `updated_at`                  TIMESTAMP(6)                             default CURRENT_TIMESTAMP(6) not null on update CURRENT_TIMESTAMP(6) comment '更新时间（UTC）',
    `updated_by`                  bigint                                                                null comment '更新人ID',
    `updated_by_name`             varchar(64)                                                           null comment '更新人姓名',
    `deleted`                     tinyint(1)                               default 0                    not null comment '软删除：0=未删，1=已删',

    `active_field_key`            varchar(64) as ((case
                                                       when ((`is_active` = 1) and (`deleted` = 0)) then `field_key`
                                                       else NULL end)) comment '唯一约束辅助：仅激活记录参与唯一',
    PRIMARY KEY (`id`),
    CONSTRAINT `uk_capability_active_field` UNIQUE (`data_source_id`, `active_field_key`),
    KEY `idx_cap_src_active` (`data_source_id`, `is_active`),
    KEY `idx_cap_updated` (`updated_at`)
)
    ENGINE = InnoDB
    ROW_FORMAT = DYNAMIC
    COMMENT ='（Registry 域）字段能力规则（源敏感）：限定字段在该源上的可用操作/匹配/范围/NOT 等';

CREATE TABLE IF NOT EXISTS `reg_source_query_render_rule`
(
    `id`              bigint unsigned                                      not null comment '主键（雪花/发号器）',
    `data_source_id`  bigint unsigned                                      not null comment '逻辑外键→reg_data_source.id（不建物理FK）',
    `is_active`       tinyint(1)              default 1                    not null comment '是否当前生效：1=生效，0=历史/停用',

    `field_key`       varchar(64)                                          not null comment '内部字段键，如 ti/ab/tiab/la/pt/dp/owner',
    `op`              enum ('term','in','range','exists','token')          not null comment '操作符',
    `match_type`      enum ('phrase','exact','any')                        null comment '匹配策略（TERM 专用；NULL=不区分）',
    `negated`         tinyint(1)                                           null comment '是否针对取反情形的规则；NULL=不区分',
    `value_type`      enum ('string','date','datetime','number')           null comment 'RANGE 值类型；其他 OP 可为空',

    `emit`            enum ('query','params') default 'query'              not null comment '渲染输出到 query 或 params',
    `priority`        int                     default 0                    not null comment '优先级（值越大越优先）',

    -- query 渲染
    `template`        text                                                 null comment 'query 模板（支持 helper：{{q v}}/{{lower ...}} 等）',
    `item_template`   text                                                 null comment 'IN/集合展开的单项模板（可选）',
    `joiner`          varchar(32)                                          null comment '集合项连接符（如 " OR "）',
    `wrap_group`      tinyint(1)              default 0                    not null comment '集合是否用括号包裹',

    -- params 渲染
    `params`          json                                                 null comment '参数映射（标准键→供应商参数名），如 {"from->mindate":"mindate"}',
    `fn`              varchar(64)                                          null comment '可选渲染函数名（如 pubmedDatetypeRenderer）',

    `record_remarks`  json                                                 null comment 'json数组，备注/变更说明',
    `version`         bigint                  default 0                    not null comment '乐观锁版本号',
    `ip_address`      varbinary(16)                                        null comment '请求方 IP（二进制，支持 IPv4/IPv6）',
    `created_at`      TIMESTAMP(6)            default CURRENT_TIMESTAMP(6) not null comment '创建时间（UTC）',
    `created_by`      bigint                                               null comment '创建人ID',
    `created_by_name` varchar(64)                                          null comment '创建人姓名',
    `updated_at`      TIMESTAMP(6)            default CURRENT_TIMESTAMP(6) not null on update CURRENT_TIMESTAMP(6) comment '更新时间（UTC）',
    `updated_by`      bigint                                               null comment '更新人ID',
    `updated_by_name` varchar(64)                                          null comment '更新人姓名',
    `deleted`         tinyint(1)              default 0                    not null comment '软删除：0=未删，1=已删',
    PRIMARY KEY (`id`),
    KEY `idx_rr_src_active` (`data_source_id`, `is_active`),
    KEY `idx_rr_lookup` (`data_source_id`, `is_active`, `field_key`, `op`, `match_type`, `negated`, `value_type`,
                         `priority`),
    KEY `idx_rr_updated` (`updated_at`)
)
    ENGINE = InnoDB
    ROW_FORMAT = DYNAMIC
    COMMENT ='（Registry 域）渲染规则（源敏感）：将 Expr.Atom 渲染为 query 片段或 params；多条并存按 priority 命中';
-- =====================================================================
-- 结束
-- =====================================================================
