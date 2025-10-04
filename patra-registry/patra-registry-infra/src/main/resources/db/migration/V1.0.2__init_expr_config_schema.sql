/* ====================================================================
 * 1) 全局统一字段字典（无源敏感）
 *    - 统一系统内部字段的语义定义；例如：publish_date / ti / ab / tiab
 *    - 不区分来源；仅描述字段本体的数据类型/基数/是否对外暴露等
 * ==================================================================== */
CREATE TABLE IF NOT EXISTS `reg_expr_field_dict`
(
    `id`               BIGINT UNSIGNED NOT NULL COMMENT '主键（雪花/发号器或发号器），仅用于库内标识；跨环境迁移不依赖此值',
    `field_key`        VARCHAR(64)     NOT NULL COMMENT '统一内部字段键：小写蛇形或约定缩写，如 publish_date/ti/ab/tiab；全库唯一，作为配置与 GitOps 的稳定键',
    `display_name`     VARCHAR(128)    NULL COMMENT '字段的人类可读名称，用于控制台/可视化配置展示（可选）',
    `description`      VARCHAR(255)    NULL COMMENT '字段说明/使用约束/对外暴露注意事项等（可选）',

    `data_type_code`   VARCHAR(32)     NOT NULL COMMENT '数据类型 code（字典 reg_data_type）：DATE/DATETIME/NUMBER/TEXT/KEYWORD/BOOLEAN/TOKEN；用于校验与渲染分支',
    `cardinality_code` VARCHAR(16)     NOT NULL DEFAULT 'SINGLE' COMMENT '基数 code（字典 reg_cardinality）：SINGLE=单值，MULTI=多值；决定是否允许一个字段出现多次值',
    `exposable`        TINYINT(1)      NOT NULL DEFAULT 1 COMMENT '是否允许对外暴露/被使用的全局开关：1=可暴露，0=不可暴露；与来源能力层（capability）解耦',
    `is_date`          TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '冗余标记：1=日期类字段（便于 UI 及 DateLens 快速判断）；与 data_type_code=DATE/DATETIME 一般一致',

    `record_remarks`   JSON            NULL COMMENT '审计备注：JSON 数组，记录修订说明/评审意见/运维备注等',
    `version`          BIGINT          NOT NULL DEFAULT 0 COMMENT '乐观锁版本号：配合应用层 CAS 更新，避免并发覆盖',
    `ip_address`       VARBINARY(16)   NULL COMMENT '最后一次写入来源 IP（二进制，兼容 IPv4/IPv6），用于审计与风控',
    `created_at`       TIMESTAMP(6)    NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间（UTC，微秒精度）',
    `created_by`       BIGINT          NULL COMMENT '创建人 ID（逻辑外键，通常指用户/系统账号）',
    `created_by_name`  VARCHAR(64)     NULL COMMENT '创建人名称/登录名快照',
    `updated_at`       TIMESTAMP(6)    NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '最后更新时间（UTC，微秒精度）',
    `updated_by`       BIGINT          NULL COMMENT '最后更新人 ID（逻辑外键）',
    `updated_by_name`  VARCHAR(64)     NULL COMMENT '最后更新人名称/登录名快照',
    `deleted`          TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '软删除标记：0=未删，1=已删；读侧统一过滤 deleted=0',

    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_expr_field_key` (`field_key`) COMMENT '确保统一字段键在全局唯一'
) ENGINE = InnoDB
  ROW_FORMAT = DYNAMIC
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci
    COMMENT ='（Registry·Expr）统一内部字段字典（无源敏感；统一语义的唯一事实来源）';


/* ====================================================================
 * 2) 源敏感：API 参数名映射（标准键 → 供应商参数名）
 *    - 把统一语义键（std_key，如 from/to/ti）映射为具体平台的 HTTP 参数名（如 mindate/maxdate/term）
 *    - 仅负责“键名层”的映射；不负责 query 模板；值级转换仅通过 transform_code 声明
 *    - 维度唯一 + 时间片：[from,to)；读侧按 NOW 命中，按 from DESC 取一条
 * ==================================================================== */
CREATE TABLE IF NOT EXISTS `reg_prov_api_param_map`
(
    `id`                  BIGINT UNSIGNED NOT NULL COMMENT '主键（雪花/发号器），库内标识',
    `provenance_id`       BIGINT UNSIGNED NOT NULL COMMENT '来源 ID（逻辑外键 → reg_provenance.id），区分不同数据源/供应商',

    `task_type`           VARCHAR(32)     NULL COMMENT '任务类型（可空）：例如 HARVEST/UPDATE/BACKFILL/SANDBOX；用于 TASK 级灰度',
    `task_type_key`       VARCHAR(16) GENERATED ALWAYS AS (IFNULL(CAST(`task_type` AS CHAR), 'ALL')) STORED COMMENT '生成列：将 NULL 归一化为 ALL，稳定维度 Join/唯一键',

    `operation_code`      VARCHAR(32)     NOT NULL COMMENT '端点操作 code（字典 reg_operation）：SEARCH/DETAIL/LOOKUP...，与 reg_prov 的端点执行合同一致',
    `std_key`             VARCHAR(64)     NOT NULL COMMENT '标准键（统一内部语义键）：如 from/to/ti/ab；通常来源于渲染阶段产出的标准参数名',
    `provider_param_name` VARCHAR(64)     NOT NULL COMMENT '供应商参数名：具体平台 HTTP 参数名，如 mindate/maxdate/term/retmax',
    `transform_code`      VARCHAR(64)     NULL COMMENT '可选：值级转换 code（字典 reg_transform），如 TO_EXCLUSIVE_MINUS_1D 表示将结束日闭区间转开区间',
    `notes`               JSON            NULL COMMENT '补充说明：JSON 对象，可记录平台差异/边界条件等',

    `effective_from`      TIMESTAMP(6)    NOT NULL COMMENT '生效起（含）；UTC；时间片开始边界',
    `effective_to`        TIMESTAMP(6)    NULL COMMENT '生效止（不含）；UTC；可空表示“当前仍生效”',
    CONSTRAINT `ck_param_map_range` CHECK (`effective_to` IS NULL OR `effective_to` > `effective_from`),

    `record_remarks`      JSON            NULL COMMENT '审计备注：JSON 数组',
    `version`             BIGINT          NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    `ip_address`          VARBINARY(16)   NULL COMMENT '最后一次写入来源 IP（二进制），IPv4/IPv6',
    `created_at`          TIMESTAMP(6)    NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间（UTC）',
    `created_by`          BIGINT          NULL COMMENT '创建人 ID',
    `created_by_name`     VARCHAR(64)     NULL COMMENT '创建人名称/登录名快照',
    `updated_at`          TIMESTAMP(6)    NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '最后更新时间（UTC）',
    `updated_by`          BIGINT          NULL COMMENT '更新人 ID',
    `updated_by_name`     VARCHAR(64)     NULL COMMENT '更新人名称/登录名快照',
    `deleted`             TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '软删除：0=未删，1=已删；读侧统一过滤',

    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_param_map__dim_from`
        (`provenance_id`, `task_type_key`, `operation_code`, `std_key`,
         `effective_from`) COMMENT '维度唯一 + 起始时间，保证任一时刻命中至多一条'
) ENGINE = InnoDB
  ROW_FORMAT = DYNAMIC
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci
    COMMENT ='（Registry·Expr）API 参数映射：标准键 → 供应商参数名（键名层；时间片生效；使用 code）';


/* ====================================================================
 * 3) 源敏感：字段能力（该来源在特定字段上的可用操作/限制）
 *    - 声明允许的表达式操作（ops）及各操作的约束（长度/大小写/范围类型与边界等）
 *    - 为渲染与校验提供“可/不可”的先验信息
 *    - 维度唯一 + 时间片：[from,to)
 * ==================================================================== */
CREATE TABLE IF NOT EXISTS `reg_prov_expr_capability`
(
    `id`                          BIGINT UNSIGNED NOT NULL COMMENT '主键（雪花/发号器），库内标识',
    `provenance_id`               BIGINT UNSIGNED NOT NULL COMMENT '来源 ID（逻辑外键 → reg_provenance.id）',

    `task_type`                   VARCHAR(32)     NULL COMMENT '任务类型（可空）：HARVEST/UPDATE/BACKFILL...',
    `task_type_key`               VARCHAR(16) GENERATED ALWAYS AS (IFNULL(CAST(`task_type` AS CHAR), 'ALL')) STORED COMMENT '生成列：将 NULL 归一为 ALL，稳定维度',

    `field_key`                   VARCHAR(64)     NOT NULL COMMENT '统一内部字段键（逻辑外键 → reg_expr_field_dict.field_key）；决定本条能力约束作用对象',

    `effective_from`              TIMESTAMP(6)    NOT NULL COMMENT '生效起（含）；UTC',
    `effective_to`                TIMESTAMP(6)    NULL COMMENT '生效止（不含）；UTC；NULL=当前仍生效',
    CONSTRAINT `ck_cap_range` CHECK (`effective_to` IS NULL OR `effective_to` > `effective_from`),

    `ops`                         JSON            NOT NULL COMMENT '允许的表达式操作集合（大写 code 数组，如 ["TERM","IN","RANGE","EXISTS","TOKEN"]）',
    `negatable_ops`               JSON            NULL COMMENT '允许取反的操作子集；NULL 表示与 ops 相同；例如仅对 TERM 允许 NOT',
    `supports_not`                TINYINT(1)      NOT NULL DEFAULT 1 COMMENT '是否全局允许 NOT：1=允许，0=不允许（总开关）',

    `term_matches`                JSON            NULL COMMENT 'TERM 可用匹配策略（大写 code 数组）：["PHRASE","EXACT","ANY"]',
    `term_case_sensitive_allowed` TINYINT(1)      NOT NULL DEFAULT 0 COMMENT 'TERM 是否允许大小写敏感：1=允许，0=不允许',
    `term_allow_blank`            TINYINT(1)      NOT NULL DEFAULT 0 COMMENT 'TERM 是否允许空白（例如某些平台无法接受空串）',
    `term_min_len`                INT             NOT NULL DEFAULT 0 COMMENT 'TERM 最小长度；0 表示不限制',
    `term_max_len`                INT             NOT NULL DEFAULT 0 COMMENT 'TERM 最大长度；0 表示不限制',
    `term_pattern`                VARCHAR(255)    NULL COMMENT 'TERM 值正则（可选）：用于限定字符集/格式',

    `in_max_size`                 INT             NOT NULL DEFAULT 0 COMMENT 'IN 集合最大项数；0 表示不限制；为 0 以外值时用于防御过长的 IN 列表',
    `in_case_sensitive_allowed`   TINYINT(1)      NOT NULL DEFAULT 0 COMMENT 'IN 是否允许大小写敏感：1=允许，0=不允许',

    `range_kind_code`             VARCHAR(16)     NOT NULL DEFAULT 'NONE' COMMENT '范围类型 code（字典 reg_range_kind）：NONE/DATE/DATETIME/NUMBER；决定 RANG E 值的类型',
    `range_allow_open_start`      TINYINT(1)      NOT NULL DEFAULT 1 COMMENT '是否允许省略起始值（-∞, x]）：1=允许',
    `range_allow_open_end`        TINYINT(1)      NOT NULL DEFAULT 1 COMMENT '是否允许省略结束值（[x, +∞)）：1=允许',
    `range_allow_closed_at_infty` TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '是否允许无穷端闭区间（如 (-∞, x]）：1=允许；一般设置为 0',

    `date_min`                    DATE            NULL COMMENT 'DATE 范围的最小日期（UTC）',
    `date_max`                    DATE            NULL COMMENT 'DATE 范围的最大日期（UTC）',
    `datetime_min`                TIMESTAMP(6)    NULL COMMENT 'DATETIME 范围的最小时间（UTC，微秒精度）',
    `datetime_max`                TIMESTAMP(6)    NULL COMMENT 'DATETIME 范围的最大时间（UTC，微秒精度）',
    `number_min`                  DECIMAL(38, 12) NULL COMMENT 'NUMBER 范围的最小数值（高精度）',
    `number_max`                  DECIMAL(38, 12) NULL COMMENT 'NUMBER 范围的最大数值（高精度）',

    `exists_supported`            TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '是否支持 EXISTS 操作：1=支持，0=不支持',
    `token_kinds`                 JSON            NULL COMMENT '允许的 token 种类（小写字符串数组，如 ["owner","pmcid"]）',
    `token_value_pattern`         VARCHAR(255)    NULL COMMENT 'token 值的正则约束（可选）',

    `record_remarks`              JSON            NULL COMMENT '审计备注：JSON 数组',
    `version`                     BIGINT          NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    `ip_address`                  VARBINARY(16)   NULL COMMENT '最后一次写入来源 IP（二进制）',
    `created_at`                  TIMESTAMP(6)    NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间（UTC）',
    `created_by`                  BIGINT          NULL COMMENT '创建人 ID',
    `created_by_name`             VARCHAR(64)     NULL COMMENT '创建人名称',
    `updated_at`                  TIMESTAMP(6)    NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '最后更新时间（UTC）',
    `updated_by`                  BIGINT          NULL COMMENT '更新人 ID',
    `updated_by_name`             VARCHAR(64)     NULL COMMENT '更新人名称',
    `deleted`                     TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '软删除：0=未删，1=已删',

    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_cap__dim_from`
        (`provenance_id`, `task_type_key`, `field_key`,
         `effective_from`) COMMENT '维度唯一 + 起始时间，保证同一时刻命中唯一配置'
) ENGINE = InnoDB
  ROW_FORMAT = DYNAMIC
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci
    COMMENT ='（Registry·Expr）字段能力（源敏感）：允许的操作与限制声明；用于校验与渲染前置判断';


/* ====================================================================
 * 4) 源敏感：渲染规则（Expr.Atom → query 片段或 params）
 *    - 将表达式原子（字段 + 操作 + 匹配/取反 + 值类型）渲染为 query 片段或 params
 *    - 与 API 参数名映射（param_map）解耦：此处不写具体的供应商参数名，仅产出“标准键/模板变量”
 *    - 维度唯一 + 时间片：[from,to)；引入归一化生成列消除 NULL 歧义
 * ==================================================================== */
CREATE TABLE IF NOT EXISTS `reg_prov_expr_render_rule`
(
    `id`              BIGINT UNSIGNED NOT NULL COMMENT '主键（雪花/发号器），库内标识',
    `provenance_id`   BIGINT UNSIGNED NOT NULL COMMENT '来源 ID（逻辑外键 → reg_provenance.id）',

    `task_type`       VARCHAR(32)     NULL COMMENT '任务类型（可空）：HARVEST/UPDATE/BACKFILL...',
    `task_type_key`   VARCHAR(16) GENERATED ALWAYS AS (IFNULL(CAST(`task_type` AS CHAR), 'ALL')) STORED COMMENT '生成列：将 NULL 归一化为 ALL',

    `field_key`       VARCHAR(64)     NOT NULL COMMENT '统一内部字段键（逻辑外键 → reg_expr_field_dict.field_key）',
    `op_code`         VARCHAR(16)     NOT NULL COMMENT '表达式操作符 code（字典 reg_expr_op）：TERM/IN/RANGE/EXISTS/TOKEN',
    `match_type_code` VARCHAR(16)     NULL COMMENT '匹配策略 code（字典 reg_match_type；TERM 专用）：PHRASE/EXACT/ANY；NULL 表示不区分',
    `negated`         TINYINT(1)      NULL COMMENT '是否取反：1=NOT，0=非 NOT；NULL=不区分（参与归一化生成列）',
    `value_type_code` VARCHAR(16)     NULL COMMENT '值类型 code（用于 RANGE 等）：STRING/DATE/DATETIME/NUMBER；NULL=不区分',
    `emit_type_code`  VARCHAR(8)      NOT NULL DEFAULT 'QUERY' COMMENT '渲染产出类型（字典 reg_emit_type）：QUERY=拼接 query 片段；PARAMS=生成标准参数集合',

    `match_type_key`  VARCHAR(16) GENERATED ALWAYS AS (IFNULL(`match_type_code`, 'ANY')) STORED COMMENT '归一化：将 match_type_code 的 NULL 归一化为 ANY，用于维度唯一与查询',
    `negated_key`     CHAR(3) GENERATED ALWAYS AS (IFNULL(IF(`negated` = 1, 'T', 'F'), 'ANY')) STORED COMMENT '归一化：将 negated 的 NULL 归一化为 ANY（T/F/ANY）',
    `value_type_key`  VARCHAR(16) GENERATED ALWAYS AS (IFNULL(`value_type_code`, 'ANY')) STORED COMMENT '归一化：将 value_type_code 的 NULL 归一化为 ANY',

    `effective_from`  TIMESTAMP(6)    NOT NULL COMMENT '生效起（含）；UTC',
    `effective_to`    TIMESTAMP(6)    NULL COMMENT '生效止（不含）；UTC；NULL=当前仍生效',
    CONSTRAINT `ck_render_range` CHECK (`effective_to` IS NULL OR `effective_to` > `effective_from`),

    `template`        TEXT            NULL COMMENT '当 emit=QUERY：用于渲染 query 片段的模板；支持 helper（如 {{q v}}/{{lower ...}}）',
    `item_template`   TEXT            NULL COMMENT '当 emit=QUERY 且 IN/集合：集合项的单项模板（可选）',
    `joiner`          VARCHAR(32)     NULL COMMENT '当 emit=QUERY 且 IN/集合：集合项连接符（如 " OR " / " AND "）',
    `wrap_group`      TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '当 emit=QUERY 且 IN/集合：是否使用括号包裹整个集合表达式',

    `params`          JSON            NULL COMMENT '当 emit=PARAMS：渲染生成的“标准键/模板变量”JSON（不直接写供应商参数名）；例如 {"from":"from","to":"to"}',
    `fn_code`         VARCHAR(64)     NULL COMMENT '模板级渲染函数 code（字典 reg_transform 的子集或扩展）：如 PUBMED_DATETYPE 等（不做值级转换）',

    `record_remarks`  JSON            NULL COMMENT '审计备注：JSON 数组',
    `version`         BIGINT          NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    `ip_address`      VARBINARY(16)   NULL COMMENT '最后一次写入来源 IP（二进制）',
    `created_at`      TIMESTAMP(6)    NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间（UTC）',
    `created_by`      BIGINT          NULL COMMENT '创建人 ID',
    `created_by_name` VARCHAR(64)     NULL COMMENT '创建人名称',
    `updated_at`      TIMESTAMP(6)    NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '最后更新时间（UTC）',
    `updated_by`      BIGINT          NULL COMMENT '更新人 ID',
    `updated_by_name` VARCHAR(64)     NULL COMMENT '更新人名称',
    `deleted`         TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '软删除：0=未删，1=已删',

    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_render__dim_from`
        (`provenance_id`, `task_type_key`, `field_key`, `op_code`, `match_type_key`, `negated_key`,
         `value_type_key`, `emit_type_code`, `effective_from`) COMMENT '维度唯一 + 起始时间；通过归一化列消除 NULL 歧义'
) ENGINE = InnoDB
  ROW_FORMAT = DYNAMIC
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci
    COMMENT ='（Registry·Expr）渲染规则（源敏感）：Expr.Atom → query 片段或 params；与参数名映射解耦；时间片生效';

