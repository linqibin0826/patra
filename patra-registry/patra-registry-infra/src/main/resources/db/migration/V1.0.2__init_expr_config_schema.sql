/* ====================================================================
 * 1) 全局统一字段字典 (数据源无关)
 *    - 定义内部字段的统一语义; 例如 publish_date / ti / ab / tiab
 *    - 数据源无关; 仅描述字段数据类型/基数/可暴露性等
 * ==================================================================== */
CREATE TABLE IF NOT EXISTS `reg_expr_field_dict`
(
    `id`               BIGINT UNSIGNED NOT NULL COMMENT '主键 (雪花ID/序列); 仅内部标识符; 迁移不依赖此值',
    `field_key`        VARCHAR(64)     NOT NULL COMMENT '统一内部字段键: 小写下划线或约定缩写, 例如 publish_date/ti/ab/tiab; 全局唯一,配置/GitOps 稳定',
    `display_name`     VARCHAR(128)    NULL COMMENT '人类可读字段名用于控制台/可视化配置 (可选)',
    `description`      VARCHAR(255)    NULL COMMENT '字段描述/约束/暴露说明 (可选)',

    `data_type_code`   VARCHAR(32)     NOT NULL COMMENT '数据类型编码 (字典 reg_data_type): DATE/DATETIME/NUMBER/TEXT/KEYWORD/BOOLEAN/TOKEN; 用于验证/渲染分支',
    `cardinality_code` VARCHAR(16)     NOT NULL DEFAULT 'SINGLE' COMMENT '基数编码 (字典 reg_cardinality): SINGLE/MULTI; 是否允许多值',
    `exposable`        TINYINT(1)      NOT NULL DEFAULT 1 COMMENT '是否允许全局暴露/使用: 1=可暴露, 0=隐藏; 与数据源级能力解耦',
    `is_date`          TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '冗余标志: 1=类日期 (帮助UI/DateLens); 通常与 DATE/DATETIME 类型一致',

    `record_remarks`   JSON            NULL COMMENT '审计备注: JSON数组,记录变更备注/审查/运维备注',
    `version`          BIGINT          NOT NULL DEFAULT 0 COMMENT '乐观锁版本 (CAS) 避免并发覆盖',
    `ip_address`       VARBINARY(16)   NULL COMMENT '最后写入来源IP (二进制, IPv4/IPv6) 用于审计/风控',
    `created_at`       TIMESTAMP(6)    NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间 (UTC, 微秒精度)',
    `created_by`       BIGINT          NULL COMMENT '创建人ID (逻辑外键; 用户/系统账号)',
    `created_by_name`  VARCHAR(64)     NULL COMMENT '创建人姓名/登录名快照',
    `updated_at`       TIMESTAMP(6)    NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '最后更新时间 (UTC, 微秒精度)',
    `updated_by`       BIGINT          NULL COMMENT '最后更新人ID (逻辑外键)',
    `updated_by_name`  VARCHAR(64)     NULL COMMENT '最后更新人姓名/登录名快照',
    `deleted`          TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '软删除标志: 0=活动, 1=已删除; 读侧过滤 deleted=0',

    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_expr_field_key` (`field_key`) COMMENT '确保统一字段键全局唯一'
) ENGINE = InnoDB
  ROW_FORMAT = DYNAMIC
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci
    COMMENT ='(Registry - Expr) 统一内部字段字典 (数据源无关; 字段语义的单一事实来源)';


/* ====================================================================
 * 2) 数据源相关: API参数名称映射 (std_key -> provider param)
 *    - 将统一语义键 (std_key, 例如 from/to/ti) 映射到提供商HTTP参数名 (例如 mindate/maxdate/term)
 *    - 仅负责键名映射; 非请求模板; 值级转换仅通过 transform_code 声明
 *    - 维度唯一性 + 时间切片: [from,to); 读取按 NOW 选择,取 from DESC 一行
 * ==================================================================== */
CREATE TABLE IF NOT EXISTS `reg_prov_api_param_map`
(
    `id`                  BIGINT UNSIGNED NOT NULL COMMENT '主键 (雪花ID/序列); 内部标识符',
    `provenance_id`       BIGINT UNSIGNED NOT NULL COMMENT '数据源ID (逻辑外键 -> reg_provenance.id) 区分提供商',

    `operation_type`      VARCHAR(32)     NOT NULL DEFAULT 'ALL' COMMENT '任务类型: HARVEST/UPDATE/BACKFILL/SANDBOX/ALL; 用于任务级灰度发布',
    `lifecycle_status_code` VARCHAR(32)   NOT NULL DEFAULT 'ACTIVE' COMMENT '字典编码(type=lifecycle_status): 生命周期; 读侧仅使用ACTIVE',

    `endpoint_name`       VARCHAR(64)     NULL COMMENT '端点名称: 此映射应用的具体端点; NULL表示应用于所有端点',
    `std_key`             VARCHAR(64)     NOT NULL COMMENT '标准键 (统一内部语义键): 例如 from/to/ti/ab; 通常在渲染时产生',
    `provider_param_name` VARCHAR(64)     NOT NULL COMMENT '提供商参数名: 具体HTTP参数, 例如 mindate/maxdate/term/retmax',
    `transform_code`      VARCHAR(64)     NULL COMMENT '可选: 值级转换代码 (字典 reg_transform), 例如 TO_EXCLUSIVE_MINUS_1D',
    `notes`               JSON            NULL COMMENT '附加备注: JSON对象,记录平台差异/边界',

    `effective_from`      TIMESTAMP(6)    NOT NULL COMMENT '生效开始时间 (包含); UTC; 时间切片的起始',
    `effective_to`        TIMESTAMP(6)    NULL COMMENT '生效结束时间 (不包含); UTC; NULL表示"仍然有效"',
    CONSTRAINT `ck_param_map_range` CHECK (`effective_to` IS NULL OR `effective_to` > `effective_from`),

    `record_remarks`      JSON            NULL COMMENT '审计备注: JSON数组',
    `version`             BIGINT          NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
    `ip_address`          VARBINARY(16)   NULL COMMENT '最后写入来源IP (二进制), IPv4/IPv6',
    `created_at`          TIMESTAMP(6)    NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间 (UTC)',
    `created_by`          BIGINT          NULL COMMENT '创建人ID',
    `created_by_name`     VARCHAR(64)     NULL COMMENT '创建人姓名/登录名快照',
    `updated_at`          TIMESTAMP(6)    NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '最后更新时间 (UTC)',
    `updated_by`          BIGINT          NULL COMMENT '最后更新人ID',
    `updated_by_name`     VARCHAR(64)     NULL COMMENT '最后更新人姓名/登录名快照',
    `deleted`             TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '软删除: 0=活动, 1=已删除; 读侧过滤',

    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_param_map__dim_from`
        (`provenance_id`, `operation_type`, `endpoint_name`, `std_key`,
         `effective_from`) COMMENT '维度唯一性 + 开始时间确保任意时间最多一个匹配'
) ENGINE = InnoDB
  ROW_FORMAT = DYNAMIC
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci
    COMMENT ='(Registry - Expr) API参数映射: std_key -> provider parameter (键名级别; 时间维度)';


/* ====================================================================
 * 3) 数据源相关: 字段能力 (允许的操作/约束)
 *    - 声明允许的表达式操作 (ops) 和每个操作的约束 (长度/大小写/范围类型/边界)
 *    - 为渲染和验证提供先验知识
 *    - 维度唯一性 + 时间切片: [from,to)
 * ==================================================================== */
CREATE TABLE IF NOT EXISTS `reg_prov_expr_capability`
(
    `id`                          BIGINT UNSIGNED NOT NULL COMMENT '主键 (雪花ID/序列); 内部标识符',
    `provenance_id`               BIGINT UNSIGNED NOT NULL COMMENT '数据源ID (逻辑外键 -> reg_provenance.id)',

    `operation_type`              VARCHAR(32)     NOT NULL DEFAULT 'ALL' COMMENT '任务类型: HARVEST/UPDATE/BACKFILL/ALL',
    `lifecycle_status_code`       VARCHAR(32)     NOT NULL DEFAULT 'ACTIVE' COMMENT '字典编码(type=lifecycle_status): 生命周期; 读侧仅使用ACTIVE',

    `field_key`                   VARCHAR(64)     NOT NULL COMMENT '统一内部字段键 (逻辑外键 -> reg_expr_field_dict.field_key)',

    `effective_from`              TIMESTAMP(6)    NOT NULL COMMENT '生效开始时间 (包含); UTC',
    `effective_to`                TIMESTAMP(6)    NULL COMMENT '生效结束时间 (不包含); UTC; NULL=开放式',
    CONSTRAINT `ck_cap_range` CHECK (`effective_to` IS NULL OR `effective_to` > `effective_from`),

    `ops`                         JSON            NOT NULL COMMENT '允许的操作集合 (大写编码数组, 例如 ["TERM","IN","RANGE","EXISTS","TOKEN"])',
    `negatable_ops`               JSON            NULL COMMENT '允许NOT的操作子集; NULL表示与ops相同; 例如仅TERM允许NOT',
    `supports_not`                TINYINT(1)      NOT NULL DEFAULT 1 COMMENT 'NOT是否全局允许: 1=允许, 0=禁止',

    `term_matches`                JSON            NULL COMMENT 'TERM匹配策略 (大写编码): ["PHRASE","EXACT","ANY"]',
    `term_case_sensitive_allowed` TINYINT(1)      NOT NULL DEFAULT 0 COMMENT 'TERM大小写敏感支持: 1=是, 0=否',
    `term_allow_blank`            TINYINT(1)      NOT NULL DEFAULT 0 COMMENT 'TERM是否允许空白/空字符串',
    `term_min_len`                INT             NOT NULL DEFAULT 0 COMMENT 'TERM最小长度; 0表示无限制',
    `term_max_len`                INT             NOT NULL DEFAULT 0 COMMENT 'TERM最大长度; 0表示无限制',
    `term_pattern`                VARCHAR(255)    NULL COMMENT 'TERM值正则表达式 (可选) 约束字符集/格式',

    `in_max_size`                 INT             NOT NULL DEFAULT 0 COMMENT 'IN集合最大元素数; 0表示无限制',
    `in_case_sensitive_allowed`   TINYINT(1)      NOT NULL DEFAULT 0 COMMENT 'IN大小写敏感支持: 1=是, 0=否',

    `range_kind_code`             VARCHAR(16)     NOT NULL DEFAULT 'NONE' COMMENT '范围类型 (字典 reg_range_kind): NONE/DATE/DATETIME/NUMBER; 决定RANGE值类型',
    `range_allow_open_start`      TINYINT(1)      NOT NULL DEFAULT 1 COMMENT '允许开放起始 (-inf, x]: 1=允许',
    `range_allow_open_end`        TINYINT(1)      NOT NULL DEFAULT 1 COMMENT '允许开放结束 [x, +inf): 1=允许',
    `range_allow_closed_at_infty` TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '允许无穷处闭区间 (例如 (-inf, x]): 1=允许; 通常为0',

    `date_min`                    DATE            NULL COMMENT '最小 DATE 边界 (UTC)',
    `date_max`                    DATE            NULL COMMENT '最大 DATE 边界 (UTC)',
    `datetime_min`                TIMESTAMP(6)    NULL COMMENT '最小 DATETIME 边界 (UTC, 微秒精度)',
    `datetime_max`                TIMESTAMP(6)    NULL COMMENT '最大 DATETIME 边界 (UTC, 微秒精度)',
    `number_min`                  DECIMAL(38, 12) NULL COMMENT '最小 NUMBER 边界 (高精度)',
    `number_max`                  DECIMAL(38, 12) NULL COMMENT '最大 NUMBER 边界 (高精度)',

    `exists_supported`            TINYINT(1)      NOT NULL DEFAULT 0 COMMENT 'EXISTS操作符是否支持: 1=支持, 0=不支持',
    `token_kinds`                 JSON            NULL COMMENT '允许的令牌类型 (小写字符串数组, 例如 ["owner","pmcid"])',
    `token_value_pattern`         VARCHAR(255)    NULL COMMENT '令牌值的正则约束 (可选)',

    `record_remarks`              JSON            NULL COMMENT '审计备注: JSON数组',
    `version`                     BIGINT          NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
    `ip_address`                  VARBINARY(16)   NULL COMMENT '最后写入来源IP (二进制)',
    `created_at`                  TIMESTAMP(6)    NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间 (UTC)',
    `created_by`                  BIGINT          NULL COMMENT '创建人ID',
    `created_by_name`             VARCHAR(64)     NULL COMMENT '创建人姓名',
    `updated_at`                  TIMESTAMP(6)    NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '最后更新时间 (UTC)',
    `updated_by`                  BIGINT          NULL COMMENT '最后更新人ID',
    `updated_by_name`             VARCHAR(64)     NULL COMMENT '最后更新人姓名',
    `deleted`                     TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '软删除: 0=活动, 1=已删除',

    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_cap__dim_from`
        (`provenance_id`, `operation_type`, `field_key`,
         `effective_from`) COMMENT '维度唯一性 + 开始时间确保任意时间唯一匹配'
) ENGINE = InnoDB
  ROW_FORMAT = DYNAMIC
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci
    COMMENT ='(Registry - Expr) 字段能力 (数据源相关): 用于验证/渲染的允许操作和约束';


/* ====================================================================
 * 4) 数据源相关: 渲染规则 (Expr.Atom -> query fragment or params)
 *    - 将表达式原子 (字段 + 操作 + 匹配/否定 + 值类型) 渲染为查询片段或参数
 *    - 与API参数命名 (param_map) 解耦: 这里仅产生标准键/模板变量
 *    - 维度唯一性 + 时间切片: [from,to); 规范化生成列消除NULL歧义
 * ==================================================================== */
CREATE TABLE IF NOT EXISTS `reg_prov_expr_render_rule`
(
    `id`              BIGINT UNSIGNED NOT NULL COMMENT '主键 (雪花ID/序列); 内部标识符',
    `provenance_id`   BIGINT UNSIGNED NOT NULL COMMENT '数据源ID (逻辑外键 -> reg_provenance.id)',

    `operation_type`  VARCHAR(32)     NOT NULL DEFAULT 'ALL' COMMENT '任务类型: HARVEST/UPDATE/BACKFILL/ALL',
    `lifecycle_status_code` VARCHAR(32) NOT NULL DEFAULT 'ACTIVE' COMMENT '字典编码(type=lifecycle_status): 生命周期; 读侧仅使用ACTIVE',

    `field_key`       VARCHAR(64)     NOT NULL COMMENT '统一内部字段键 (逻辑外键 -> reg_expr_field_dict.field_key)',
    `op_code`         VARCHAR(16)     NOT NULL COMMENT '表达式操作符编码 (字典 reg_expr_op): TERM/IN/RANGE/EXISTS/TOKEN',
    `match_type_code` VARCHAR(16)     NULL COMMENT '匹配类型编码 (字典 reg_match_type; 仅TERM): PHRASE/EXACT/ANY; NULL=无关',
    `negated`         TINYINT(1)      NULL COMMENT '否定标志: 1=NOT, 0=非NOT; NULL=无关 (参与规范化键)',
    `value_type_code` VARCHAR(16)     NULL COMMENT '值类型编码 (用于RANGE等): STRING/DATE/DATETIME/NUMBER; NULL=无关',
    `emit_type_code`  VARCHAR(8)      NOT NULL DEFAULT 'QUERY' COMMENT '发射类型 (字典 reg_emit_type): QUERY=发射查询片段; PARAMS=发射标准参数',

    `match_type_key`  VARCHAR(16) GENERATED ALWAYS AS (IFNULL(`match_type_code`, 'ANY')) STORED COMMENT '规范化: NULL -> ANY 用于 match_type_code',
    `negated_key`     CHAR(3) GENERATED ALWAYS AS (IFNULL(IF(`negated` = 1, 'T', 'F'), 'ANY')) STORED COMMENT '规范化: NULL -> ANY 用于 negated (T/F/ANY)',
    `value_type_key`  VARCHAR(16) GENERATED ALWAYS AS (IFNULL(`value_type_code`, 'ANY')) STORED COMMENT '规范化: NULL -> ANY 用于 value_type_code',

    `effective_from`  TIMESTAMP(6)    NOT NULL COMMENT '生效开始时间 (包含); UTC',
    `effective_to`    TIMESTAMP(6)    NULL COMMENT '生效结束时间 (不包含); UTC; NULL=开放式',
    CONSTRAINT `ck_render_range` CHECK (`effective_to` IS NULL OR `effective_to` > `effective_from`),

    `template`        TEXT            NULL COMMENT '当 emit=QUERY 时: 渲染查询片段的模板; 支持助手 (例如 {{q v}}/{{lower ...}})',
    `item_template`   TEXT            NULL COMMENT '当 emit=QUERY 且 op=IN 时: 每个项的模板 (可选)',
    `joiner`          VARCHAR(32)     NULL COMMENT '当 emit=QUERY 且 op=IN 时: 项的连接符 (例如 " OR " / " AND ")',
    `wrap_group`      TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '当 emit=QUERY 且 op=IN 时: 是否用括号包裹整个组',

    `params`          JSON            NULL COMMENT '当 emit=PARAMS 时: 标准键/模板变量的 JSON (不使用提供商参数名); 例如 {"from":"from","to":"to"}',
    `fn_code`         VARCHAR(64)     NULL COMMENT '模板级渲染函数代码 (reg_transform 的子集/扩展); 例如 PUBMED_DATETYPE (非值级转换)',

    `record_remarks`  JSON            NULL COMMENT '审计备注: JSON数组',
    `version`         BIGINT          NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
    `ip_address`      VARBINARY(16)   NULL COMMENT '最后写入来源IP (二进制)',
    `created_at`      TIMESTAMP(6)    NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间 (UTC)',
    `created_by`      BIGINT          NULL COMMENT '创建人ID',
    `created_by_name` VARCHAR(64)     NULL COMMENT '创建人姓名',
    `updated_at`      TIMESTAMP(6)    NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '最后更新时间 (UTC)',
    `updated_by`      BIGINT          NULL COMMENT '最后更新人ID',
    `updated_by_name` VARCHAR(64)     NULL COMMENT '最后更新人姓名',
    `deleted`         TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '软删除: 0=活动, 1=已删除',

    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_render__dim_from`
        (`provenance_id`, `operation_type`, `field_key`, `op_code`, `match_type_key`, `negated_key`,
         `value_type_key`, `emit_type_code`,
         `effective_from`) COMMENT '维度唯一性 + 开始时间; 规范化键消除NULL歧义'
) ENGINE = InnoDB
  ROW_FORMAT = DYNAMIC
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci
    COMMENT ='(Registry - Expr) 渲染规则 (数据源相关): Expr.Atom -> query fragment or params; 与参数命名解耦; 时间维度'
