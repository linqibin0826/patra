-- ============================================================
-- Patra Catalog 数据库 - MeSH 体系表 DDL
-- ============================================================
-- 设计阶段: 阶段 3 - SQL DDL 生成
-- 创建日期: 2025-01-18
-- 设计范围: MeSH (医学主题词表) 体系（8张表）
-- 作者: Patra Lin
-- MySQL 版本: 8.0+
-- 字符集: utf8mb4 (支持完整Unicode)
-- 排序规则: utf8mb4_0900_ai_ci (支持多语言准确排序)
-- ============================================================

-- ============================================================
-- 表清单与依赖关系
-- ============================================================
-- MeSH 体系 (8张表):
--   1. cat_mesh_descriptor (MeSH 主题词表) - 无依赖
--   2. cat_mesh_qualifier (MeSH 限定词表) - 无依赖
--   3. cat_mesh_tree_number (树形编号表) - 依赖 cat_mesh_descriptor
--   4. cat_mesh_entry_term (入口术语表) - 依赖 cat_mesh_descriptor
--   5. cat_mesh_concept (MeSH 概念表) - 依赖 cat_mesh_descriptor
--   6. cat_mesh_concept_relation (概念关系表) - 依赖 cat_mesh_descriptor
--   7. cat_mesh_entry_combination (组合条目表) - 依赖 cat_mesh_descriptor
--   8. cat_publication_mesh (文献-MeSH关联表) - 依赖 cat_publication, cat_mesh_descriptor, cat_mesh_qualifier
-- ============================================================


-- ============================================================
-- 表 1: cat_mesh_descriptor (MeSH 主题词表)
-- ============================================================
-- 表说明: 存储 NLM MeSH(医学主题词表)主题词的核心信息,是医学文献标引的权威词表
-- 记录数预估: 初始 3.5万 / 年增长 500 / 5年规模 3.75万
-- 主要查询场景:
--   1. 按 MeSH UI 精确查询(>2000次/天,高频)
--   2. 按主题词名称查询(>1000次/天,高频)
--   3. 全文检索 scope_note(100-500次/天,中频)
--   4. 按版本筛选有效主题词(>500次/天,中频)
-- ============================================================


CREATE TABLE IF NOT EXISTS `cat_mesh_descriptor` (
    -- ========================================
    -- 业务字段
    -- ========================================
    `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键,雪花算法生成',
    `ui` VARCHAR(10) NOT NULL COMMENT 'MeSH 唯一标识符(格式:D000001-D999999)',
    `name` VARCHAR(255) NOT NULL COMMENT '主题词名称(首选术语,英文)',
    `descriptor_class` VARCHAR(50) NULL DEFAULT NULL COMMENT '主题词类型(枚举:1-Topical/2-PublicationType/3-Geographicals/4-CheckTag)',
    `scope_note` TEXT NULL DEFAULT NULL COMMENT '范围说明(定义和使用指南)',
    `annotation` TEXT NULL DEFAULT NULL COMMENT '注释(索引员使用的说明)',
    `previous_indexing` TEXT NULL DEFAULT NULL COMMENT '之前的索引方式(历史参考)',
    `public_mesh_note` TEXT NULL DEFAULT NULL COMMENT '公共 MeSH 注释(面向用户)',
    `consider_also` TEXT NULL DEFAULT NULL COMMENT '另请参考(相关主题词建议)',
    `history_note` TEXT NULL DEFAULT NULL COMMENT '历史说明(记录主题词的历史使用规则)',
    `online_note` TEXT NULL DEFAULT NULL COMMENT '在线检索说明(检索策略指南)',
    `nlm_classification_number` VARCHAR(50) NULL DEFAULT NULL COMMENT 'NLM分类号',
    `date_created` DATE NULL DEFAULT NULL COMMENT '创建日期',
    `date_revised` DATE NULL DEFAULT NULL COMMENT '修订日期',
    `date_established` DATE NULL DEFAULT NULL COMMENT '确立日期',
    `active_status` BOOLEAN NOT NULL DEFAULT 1 COMMENT '是否有效(0=已废弃,1=有效)',
    `mesh_version` VARCHAR(10) NULL DEFAULT NULL COMMENT 'MeSH 版本年份(如"2025")',
    `metadata` JSON NULL DEFAULT NULL COMMENT '其他元数据(扩展字段)',

    -- ========================================
    -- 审计字段（完整版）
    -- ========================================
    `record_remarks` JSON NULL DEFAULT NULL COMMENT 'JSON数组,备注/变更日志',
    `version` BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '乐观锁版本号(每次更新自增)',
    `ip_address` VARBINARY(16) NULL DEFAULT NULL COMMENT '请求者IP(二进制,支持IPv4/IPv6)',
    `created_at` TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间(UTC,微秒精度)',
    `created_by` BIGINT UNSIGNED NULL DEFAULT NULL COMMENT '创建人ID',
    `created_by_name` VARCHAR(100) NULL DEFAULT NULL COMMENT '创建人姓名(冗余-审计友好)',
    `updated_at` TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '更新时间(UTC,微秒精度)',
    `updated_by` BIGINT UNSIGNED NULL DEFAULT NULL COMMENT '更新人ID',
    `updated_by_name` VARCHAR(100) NULL DEFAULT NULL COMMENT '更新人姓名(冗余-审计友好)',

    -- ========================================
    -- 主键和索引
    -- ========================================
    PRIMARY KEY (`id`) COMMENT '主键聚簇索引',

    -- 唯一索引
    UNIQUE INDEX `uk_mesh_ui` (`ui`) COMMENT 'MeSH UI 唯一索引,支持高频精确查询(<5ms)',

    -- 普通索引
    INDEX `idx_name` (`name`) COMMENT '主题词名称索引,支持按名称查询',

    -- 复合索引
    INDEX `idx_active_version` (`active_status`, `mesh_version`) COMMENT '有效状态+版本复合索引,筛选某版本的有效主题词'

) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
COMMENT='MeSH 主题词表:存储 NLM MeSH 主题词核心信息,医学文献标引权威词表';


-- ============================================================
-- 表 2: cat_mesh_qualifier (MeSH 限定词表)
-- ============================================================
-- 表说明: 存储 MeSH 限定词,用于修饰主题词(如"immunology"限定"Antibodies")
-- 记录数预估: 初始 100 / 年增长 5 / 5年规模 125
-- 主要查询场景:
--   1. 按限定词 UI 精确查询(>500次/天,中频)
--   2. 按限定词名称查询(100-500次/天,中频)
--   3. 按缩写查询(<100次/天,低频)
-- ============================================================


CREATE TABLE IF NOT EXISTS `cat_mesh_qualifier` (
    -- ========================================
    -- 业务字段
    -- ========================================
    `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键,雪花算法生成',
    `ui` VARCHAR(10) NOT NULL COMMENT '限定词唯一标识符(格式:Q000001-Q999999)',
    `name` VARCHAR(100) NOT NULL COMMENT '限定词名称(英文)',
    `abbreviation` VARCHAR(10) NULL DEFAULT NULL COMMENT '限定词缩写(如 DI, GE, IM)',
    `annotation` TEXT NULL DEFAULT NULL COMMENT '注释说明',
    `date_created` DATE NULL DEFAULT NULL COMMENT '创建日期',
    `date_revised` DATE NULL DEFAULT NULL COMMENT '修订日期',
    `date_established` DATE NULL DEFAULT NULL COMMENT '确立日期',
    `active_status` BOOLEAN NOT NULL DEFAULT 1 COMMENT '是否有效(0=已废弃,1=有效)',
    `mesh_version` VARCHAR(10) NULL DEFAULT NULL COMMENT 'MeSH 版本年份(如"2025")',
    `history_note` TEXT NULL DEFAULT NULL COMMENT '历史说明(记录限定词的历史使用规则)',
    `online_note` TEXT NULL DEFAULT NULL COMMENT '在线检索说明(检索策略指南)',
    `tree_numbers` JSON NULL DEFAULT NULL COMMENT '树形编号列表(JSON数组,限定词在MeSH层级树中的位置)',

    -- ========================================
    -- 审计字段（完整版）
    -- ========================================
    `record_remarks` JSON NULL DEFAULT NULL COMMENT 'JSON数组,备注/变更日志',
    `version` BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '乐观锁版本号(每次更新自增)',
    `ip_address` VARBINARY(16) NULL DEFAULT NULL COMMENT '请求者IP(二进制,支持IPv4/IPv6)',
    `created_at` TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间(UTC,微秒精度)',
    `created_by` BIGINT UNSIGNED NULL DEFAULT NULL COMMENT '创建人ID',
    `created_by_name` VARCHAR(100) NULL DEFAULT NULL COMMENT '创建人姓名(冗余-审计友好)',
    `updated_at` TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '更新时间(UTC,微秒精度)',
    `updated_by` BIGINT UNSIGNED NULL DEFAULT NULL COMMENT '更新人ID',
    `updated_by_name` VARCHAR(100) NULL DEFAULT NULL COMMENT '更新人姓名(冗余-审计友好)',

    -- ========================================
    -- 主键和索引
    -- ========================================
    PRIMARY KEY (`id`) COMMENT '主键聚簇索引',

    -- 唯一索引
    UNIQUE INDEX `uk_qualifier_ui` (`ui`) COMMENT '限定词 UI 唯一索引,支持精确查询(<5ms)',

    -- 普通索引
    INDEX `idx_name` (`name`) COMMENT '限定词名称索引,支持按名称查询'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
COMMENT='MeSH 限定词表:存储 MeSH 限定词,用于修饰主题词';


-- ============================================================
-- 表 3: cat_mesh_tree_number (MeSH 树形编号表)
-- ============================================================
-- 表说明: 存储 MeSH 主题词的树形编号,支持多位置和层次查询(一个主题词平均 2.3 个位置)
-- 记录数预估: 初始 8万 / 年增长 1000 / 5年规模 8.5万
-- 主要查询场景:
--   1. 按 descriptor_ui 查询某主题词的所有位置(>1000次/天,高频)
--   2. 按树形编号前缀查询某分支下的所有主题词(>500次/天,中频)
--   3. 按层级深度查询(100-500次/天,中频)
-- ============================================================


CREATE TABLE IF NOT EXISTS `cat_mesh_tree_number` (
    -- ========================================
    -- 业务字段
    -- ========================================
    `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键,雪花算法生成',
    `descriptor_ui` VARCHAR(10) NOT NULL COMMENT '主题词UI(关联:cat_mesh_descriptor.ui,格式:D000001)',
    `tree_number` VARCHAR(100) NOT NULL COMMENT '树形编号(如 C04.557.337.428，最多15层约59字符)',
    `tree_level` TINYINT NOT NULL COMMENT '层级深度(1-15,自动计算)',
    `is_primary` BOOLEAN NOT NULL DEFAULT 1 COMMENT '是否主要位置(0=次要,1=主要)',

    -- ========================================
    -- 主键和索引
    -- ========================================
    PRIMARY KEY (`id`) COMMENT '主键聚簇索引',

    -- 唯一索引
    UNIQUE INDEX `uk_tree_number` (`tree_number`) COMMENT '树形编号唯一索引,保证编号唯一性',

    -- 普通索引
    INDEX `idx_descriptor_ui` (`descriptor_ui`) COMMENT '主题词UI索引,支持查询某主题词的所有位置',

    -- 前缀索引(层次查询优化)
    INDEX `idx_tree_prefix` (`tree_number`(20)) COMMENT '树形编号前缀索引,支持层次查询(LIKE "D12.%")',

    -- 复合索引
    INDEX `idx_tree_level` (`tree_level`, `descriptor_ui`) COMMENT '层级+主题词UI复合索引,支持按层级筛选'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
COMMENT='MeSH 树形编号表:存储主题词树形编号,支持多位置和层次查询';


-- ============================================================
-- 表 4: cat_mesh_entry_term (MeSH 入口术语表)
-- ============================================================
-- 表说明: 存储 MeSH 主题词的同义词和入口术语,支持模糊检索(如 "A-23187" → "Calcimycin")
-- 记录数预估: 初始 25万 / 年增长 1万 / 5年规模 30万
-- 主要查询场景:
--   1. 按 descriptor_ui 查询某主题词的所有同义词(>500次/天,中频)
--   2. 全文检索入口术语(>1000次/天,高频)
--   3. 按词法标记筛选(<100次/天,低频)
-- ============================================================


CREATE TABLE IF NOT EXISTS `cat_mesh_entry_term` (
    -- ========================================
    -- 业务字段
    -- ========================================
    `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键,雪花算法生成',
    `descriptor_ui` VARCHAR(10) NOT NULL COMMENT '主题词UI(关联:cat_mesh_descriptor.ui,格式:D000001)',
    `term_ui` VARCHAR(10) NULL DEFAULT NULL COMMENT '术语UI',
    `concept_ui` VARCHAR(10) NULL DEFAULT NULL COMMENT '所属概念UI',
    `term` VARCHAR(255) NOT NULL COMMENT '入口术语/同义词',
    `lexical_tag` VARCHAR(10) NULL DEFAULT NULL COMMENT '词法标记(枚举:NON/PEF/LAB/ABB/ACR/NAM)',
    `is_print_flag` BOOLEAN NOT NULL DEFAULT 1 COMMENT '是否打印(0=否,1=是)',
    `record_preferred` VARCHAR(10) NULL DEFAULT NULL COMMENT '记录首选(枚举:Y/N)',
    `is_permuted_term` BOOLEAN NOT NULL DEFAULT 0 COMMENT '是否排列术语(0=否,1=是)',
    `is_concept_preferred` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否概念首选术语(0=否,1=是)',
    `abbreviation` VARCHAR(50) NULL DEFAULT NULL COMMENT '术语缩写',
    `sort_version` VARCHAR(255) NULL DEFAULT NULL COMMENT '排序版本',
    `entry_version` VARCHAR(100) NULL DEFAULT NULL COMMENT '入口版本',
    `term_note` TEXT NULL DEFAULT NULL COMMENT '术语说明',
    `date_created` DATE NULL DEFAULT NULL COMMENT '创建日期',
    `thesaurus_ids` JSON NULL DEFAULT NULL COMMENT '来源词库ID列表(JSON数组)',

    -- ========================================
    -- 主键和索引
    -- ========================================
    PRIMARY KEY (`id`) COMMENT '主键聚簇索引',

    -- 普通索引
    INDEX `idx_descriptor_ui` (`descriptor_ui`) COMMENT '主题词UI索引,支持查询某主题词的所有入口术语',
    INDEX `idx_concept_ui` (`concept_ui`) COMMENT '概念UI索引,支持按概念查询入口术语'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
COMMENT='MeSH 入口术语表:存储主题词同义词和入口术语,支持模糊检索';


-- ============================================================
-- 表 5: cat_mesh_concept (MeSH 概念表)
-- ============================================================
-- 表说明: 存储 MeSH 主题词下的概念,支持概念级别的关联和检索
-- 记录数预估: 初始 18万 / 年增长 5000 / 5年规模 20.5万
-- 主要查询场景:
--   1. 按 descriptor_ui 查询某主题词的所有概念(>300次/天,中频)
--   2. 按 concept_ui 精确查询(<500次/天,中频)
--   3. 按 registry_number 查询(化学物质,<100次/天,低频)
-- ============================================================


CREATE TABLE IF NOT EXISTS `cat_mesh_concept` (
    -- ========================================
    -- 业务字段
    -- ========================================
    `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键,雪花算法生成',
    `descriptor_ui` VARCHAR(10) NOT NULL COMMENT '主题词UI(关联:cat_mesh_descriptor.ui,格式:D000001)',
    `concept_ui` VARCHAR(10) NOT NULL COMMENT '概念唯一标识符(格式:M000001-M999999)',
    `concept_name` VARCHAR(255) NOT NULL COMMENT '概念名称',
    `is_preferred` BOOLEAN NOT NULL DEFAULT 0 COMMENT '是否首选概念(0=否,1=是)',
    `casn1_name` TEXT NULL DEFAULT NULL COMMENT 'CAS 类型 1 名称(化学物质专用,IUPAC 命名可能很长)',
    `registry_numbers` JSON NULL DEFAULT NULL COMMENT '注册号列表(JSON数组,2025 DTD支持多个)',
    `related_registry_numbers` JSON NULL DEFAULT NULL COMMENT '相关注册号列表(JSON数组,RelatedRegistryNumberList)',
    `scope_note` TEXT NULL DEFAULT NULL COMMENT '范围说明',
    `translators_english_scope_note` TEXT NULL DEFAULT NULL COMMENT '翻译者英文范围说明',
    `translators_scope_note` TEXT NULL DEFAULT NULL COMMENT '翻译者范围说明',
    `concept_status` VARCHAR(10) NULL DEFAULT NULL COMMENT '概念状态(枚举值)',

    -- ========================================
    -- 主键和索引
    -- ========================================
    PRIMARY KEY (`id`) COMMENT '主键聚簇索引',

    -- 唯一索引
    UNIQUE INDEX `uk_concept_ui` (`concept_ui`) COMMENT '概念 UI 唯一索引,支持精确查询',

    -- 普通索引
    INDEX `idx_descriptor_ui` (`descriptor_ui`) COMMENT '主题词UI索引,支持查询某主题词的所有概念'

) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
COMMENT='MeSH 概念表:存储主题词下的概念,支持概念级别关联和检索';


-- ============================================================
-- 表 6: cat_mesh_concept_relation (MeSH 概念关系表)
-- ============================================================
-- 表说明: 存储概念关系(ConceptRelationList),记录同一主题词内不同概念之间的语义关系
-- 记录数预估: 初始 5万 / 年增长 2000 / 5年规模 6万
-- 主要查询场景:
--   1. 按 descriptor_ui 查询某主题词的所有概念关系(低频)
--   2. 按 concept_ui 查询某概念的所有关系(低频)
-- ============================================================


CREATE TABLE IF NOT EXISTS `cat_mesh_concept_relation` (
    -- ========================================
    -- 业务字段
    -- ========================================
    `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键,雪花算法生成',
    `descriptor_ui` VARCHAR(10) NOT NULL COMMENT '主题词UI(关联:cat_mesh_descriptor.ui,格式:D000001)',
    `concept_ui` VARCHAR(10) NOT NULL COMMENT '所属概念UI(拥有此关系列表的概念)',
    `is_preferred` BOOLEAN NOT NULL DEFAULT 0 COMMENT '所属概念是否为首选概念(0=否,1=是)',
    `relation_name` VARCHAR(10) NULL DEFAULT NULL COMMENT '关系类型(NRW=Narrower/BRD=Broader/REL=Related,DTD #IMPLIED可为null)',
    `concept1_ui` VARCHAR(10) NOT NULL COMMENT '概念1 UI(DTD定义总是首选概念)',
    `concept2_ui` VARCHAR(10) NOT NULL COMMENT '概念2 UI(关联概念)',

    -- ========================================
    -- 主键和索引
    -- ========================================
    PRIMARY KEY (`id`) COMMENT '主键聚簇索引',

    -- 普通索引
    INDEX `idx_descriptor_ui` (`descriptor_ui`) COMMENT '主题词UI索引,支持查询某主题词的所有概念关系',
    INDEX `idx_concept` (`concept_ui`) COMMENT '概念索引,支持查询某概念的所有关系'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
COMMENT='MeSH 概念关系表:存储概念关系(ConceptRelationList),记录同一主题词内不同概念之间的语义关系';


-- ============================================================
-- 表 7: cat_mesh_entry_combination (MeSH 组合条目表)
-- ============================================================
-- 表说明: 存储 MeSH 主题词的组合条目信息(EntryCombinationList)
-- 记录数预估: 初始 500 / 5年规模 600
-- 主要查询场景:
--   1. 按 descriptor_ui 查询某主题词的所有组合条目(低频)
--   2. 按 ecin_descriptor_ui 查询输入组合(低频)
-- ============================================================


CREATE TABLE IF NOT EXISTS `cat_mesh_entry_combination` (
    -- ========================================
    -- 业务字段
    -- ========================================
    `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键,雪花算法生成',
    `descriptor_ui` VARCHAR(10) NOT NULL COMMENT '主题词UI(关联:cat_mesh_descriptor.ui,格式:D000001)',
    `ecin_descriptor_ui` VARCHAR(10) NOT NULL COMMENT 'ECIN Descriptor UI(输入组合的主题词)',
    `ecin_qualifier_ui` VARCHAR(10) NOT NULL COMMENT 'ECIN Qualifier UI(输入组合的限定词)',
    `ecout_descriptor_ui` VARCHAR(10) NOT NULL COMMENT 'ECOUT Descriptor UI(输出组合的主题词)',
    `ecout_qualifier_ui` VARCHAR(10) NULL DEFAULT NULL COMMENT 'ECOUT Qualifier UI(输出组合的限定词,可选)',

    -- ========================================
    -- 主键和索引
    -- ========================================
    PRIMARY KEY (`id`) COMMENT '主键聚簇索引',

    -- 普通索引
    INDEX `idx_descriptor_ui` (`descriptor_ui`) COMMENT '主题词UI索引,支持查询某主题词的所有组合条目',
    INDEX `idx_ecin_descriptor` (`ecin_descriptor_ui`) COMMENT 'ECIN主题词索引',
    INDEX `idx_ecout_descriptor` (`ecout_descriptor_ui`) COMMENT 'ECOUT主题词索引'

) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
COMMENT='MeSH 组合条目表:存储主题词的组合条目信息(EntryCombinationList)';


-- ============================================================
-- 表 8: cat_publication_mesh (文献-MeSH 关联表)
-- ============================================================
-- 表说明: 存储文献的 MeSH 标引,关联文献、主题词、限定词,支持主/副主题标记
-- 记录数预估: 初始 2000万 / 年增长 1600万 / 5年规模 2.8亿
-- 主要查询场景:
--   1. 按 publication_id 查询某文献的所有 MeSH(>3000次/天,高频)
--   2. 按 descriptor_ui 查询某主题词的所有文献(>2000次/天,高频)
--   3. 筛选主要主题(is_major_topic=1)(>1000次/天,高频)
--   4. 按限定词筛选(>500次/天,中频)
-- ============================================================


CREATE TABLE IF NOT EXISTS `cat_publication_mesh` (
    -- ========================================
    -- 业务字段
    -- ========================================
    `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键,雪花算法生成',
    `publication_id` BIGINT UNSIGNED NOT NULL COMMENT '出版物ID(外键:cat_publication.id)',
    `descriptor_ui` VARCHAR(10) NOT NULL COMMENT '主题词UI(关联:cat_mesh_descriptor.ui,格式:D000001)',
    `qualifier_ui` VARCHAR(10) NULL DEFAULT NULL COMMENT '限定词UI(关联:cat_mesh_qualifier.ui,格式:Q000001,可选)',
    `is_major_topic` BOOLEAN NOT NULL DEFAULT 0 COMMENT '是否主要主题(0=副主题,1=主要主题,对应 MeSH 星号*)',
    `order_num` INT UNSIGNED NULL DEFAULT NULL COMMENT '顺序号(在同一文献内的排序)',
    `indexing_method` VARCHAR(50) NULL DEFAULT NULL COMMENT '标引方法(如 Manual/Automatic)',

    -- ========================================
    -- 审计字段（完整版）
    -- ========================================
    `record_remarks` JSON NULL DEFAULT NULL COMMENT 'JSON数组,备注/变更日志',
    `version` BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '乐观锁版本号(每次更新自增)',
    `ip_address` VARBINARY(16) NULL DEFAULT NULL COMMENT '请求者IP(二进制,支持IPv4/IPv6)',
    `created_at` TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间(UTC,微秒精度)',
    `created_by` BIGINT UNSIGNED NULL DEFAULT NULL COMMENT '创建人ID',
    `created_by_name` VARCHAR(100) NULL DEFAULT NULL COMMENT '创建人姓名(冗余-审计友好)',
    `updated_at` TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '更新时间(UTC,微秒精度)',
    `updated_by` BIGINT UNSIGNED NULL DEFAULT NULL COMMENT '更新人ID',
    `updated_by_name` VARCHAR(100) NULL DEFAULT NULL COMMENT '更新人姓名(冗余-审计友好)',

    -- ========================================
    -- 主键和索引
    -- ========================================
    PRIMARY KEY (`id`) COMMENT '主键聚簇索引',

    -- 复合索引(核心查询)
    INDEX `idx_pub_desc` (`publication_id`, `descriptor_ui`) COMMENT '文献+主题词UI复合索引,支持查询文献的MeSH(<20ms)',
    INDEX `idx_desc_pub` (`descriptor_ui`, `publication_id`) COMMENT '主题词UI+文献复合索引,支持查询MeSH的文献(<50ms)',
    INDEX `idx_major_topic` (`descriptor_ui`, `is_major_topic`) COMMENT '主题词UI+主/副主题复合索引,筛选主要主题文献',

    -- 普通索引
    INDEX `idx_qualifier_ui` (`qualifier_ui`) COMMENT '限定词UI索引,支持按限定词筛选文献'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
COMMENT='文献-MeSH 关联表:存储文献 MeSH 标引,支持主/副主题标记';


-- ============================================================
-- 全文索引（需要在表创建后单独执行）
-- ============================================================
-- 注意: 全文索引使用 ngram 解析器,支持中文分词(MySQL 5.7.6+)
-- ============================================================

-- cat_mesh_descriptor 表的全文索引
CREATE FULLTEXT INDEX `ft_name_note` ON `cat_mesh_descriptor` (`name`, `scope_note`)
    WITH PARSER ngram
    COMMENT '名称和范围说明全文索引,支持中英文混合检索';

-- cat_mesh_entry_term 表的全文索引
CREATE FULLTEXT INDEX `ft_term` ON `cat_mesh_entry_term` (`term`)
    WITH PARSER ngram
    COMMENT '入口术语全文索引,支持同义词模糊检索';
