-- mesh 子域：MeSH 主题词、限定词、树形编号、入口术语、概念、补充概念记录（SCR）
-- 引用关系由应用保证，不建外键

-- ------------------------------------------------------------
-- cat_mesh_descriptor
-- ------------------------------------------------------------
CREATE TABLE cat_mesh_descriptor
(
    id BIGINT NOT NULL PRIMARY KEY,
    ui VARCHAR(10) NOT NULL,
    name VARCHAR(255) NOT NULL,
    descriptor_class VARCHAR(50) NULL,
    scope_note TEXT NULL,
    annotation TEXT NULL,
    previous_indexing TEXT NULL,
    public_mesh_note TEXT NULL,
    consider_also TEXT NULL,
    history_note TEXT NULL,
    online_note TEXT NULL,
    nlm_classification_number VARCHAR(50) NULL,
    date_created DATE NULL,
    date_revised DATE NULL,
    date_established DATE NULL,
    active_status BOOLEAN NOT NULL DEFAULT true,
    mesh_version VARCHAR(10) NULL,
    metadata JSONB NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by BIGINT      NULL,
    updated_by BIGINT      NULL,
    ip_address INET        NULL,
    record_remarks JSONB       NULL,
    version BIGINT      NOT NULL DEFAULT 0
);
CREATE UNIQUE INDEX uk_cat_mesh_descriptor_ui ON cat_mesh_descriptor (ui);
CREATE INDEX idx_cat_mesh_descriptor_active_version ON cat_mesh_descriptor (active_status, mesh_version);
CREATE INDEX idx_cat_mesh_descriptor_name ON cat_mesh_descriptor (name);
CREATE INDEX idx_cat_mesh_descriptor_name_trgm ON cat_mesh_descriptor USING gin (name gin_trgm_ops) WHERE (((descriptor_class)::text = '1'::text) AND active_status);
CREATE TRIGGER trg_cat_mesh_descriptor_audit_times
    BEFORE INSERT OR UPDATE ON cat_mesh_descriptor
    FOR EACH ROW EXECUTE FUNCTION set_audit_times();

COMMENT ON TABLE cat_mesh_descriptor IS 'MeSH 主题词';
COMMENT ON COLUMN cat_mesh_descriptor.ui IS 'MeSH 唯一标识符（格式：D000001-D999999）';
COMMENT ON COLUMN cat_mesh_descriptor.name IS '主题词名称（首选术语，英文）';
COMMENT ON COLUMN cat_mesh_descriptor.descriptor_class IS '主题词类型（1-Topical/2-PublicationType/3-Geographicals/4-CheckTag）';
COMMENT ON COLUMN cat_mesh_descriptor.scope_note IS '范围说明（定义和使用指南）';
COMMENT ON COLUMN cat_mesh_descriptor.annotation IS '注释（索引员使用的说明）';
COMMENT ON COLUMN cat_mesh_descriptor.previous_indexing IS '之前的索引方式（历史参考）';
COMMENT ON COLUMN cat_mesh_descriptor.public_mesh_note IS '公共 MeSH 注释（面向用户）';
COMMENT ON COLUMN cat_mesh_descriptor.consider_also IS '另请参考（相关主题词建议）';
COMMENT ON COLUMN cat_mesh_descriptor.history_note IS '历史说明（记录主题词的历史使用规则）';
COMMENT ON COLUMN cat_mesh_descriptor.online_note IS '在线检索说明（检索策略指南）';
COMMENT ON COLUMN cat_mesh_descriptor.nlm_classification_number IS 'NLM 分类号';
COMMENT ON COLUMN cat_mesh_descriptor.date_created IS '创建日期';
COMMENT ON COLUMN cat_mesh_descriptor.date_revised IS '修订日期';
COMMENT ON COLUMN cat_mesh_descriptor.date_established IS '确立日期';
COMMENT ON COLUMN cat_mesh_descriptor.active_status IS '是否有效（false=已废弃，true=有效）';
COMMENT ON COLUMN cat_mesh_descriptor.mesh_version IS 'MeSH 版本年份（如 "2025"）';
COMMENT ON COLUMN cat_mesh_descriptor.metadata IS '其他元数据（JSON 扩展字段）';

-- ------------------------------------------------------------
-- cat_mesh_qualifier
-- ------------------------------------------------------------
CREATE TABLE cat_mesh_qualifier
(
    id BIGINT NOT NULL PRIMARY KEY,
    ui VARCHAR(10) NOT NULL,
    name VARCHAR(100) NOT NULL,
    abbreviation VARCHAR(10) NULL,
    annotation TEXT NULL,
    date_created DATE NULL,
    date_revised DATE NULL,
    date_established DATE NULL,
    active_status BOOLEAN NOT NULL DEFAULT true,
    mesh_version VARCHAR(10) NULL,
    history_note TEXT NULL,
    online_note TEXT NULL,
    tree_numbers JSONB NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by BIGINT      NULL,
    updated_by BIGINT      NULL,
    ip_address INET        NULL,
    record_remarks JSONB       NULL,
    version BIGINT      NOT NULL DEFAULT 0
);
CREATE UNIQUE INDEX uk_cat_mesh_qualifier_ui ON cat_mesh_qualifier (ui);
CREATE INDEX idx_cat_mesh_qualifier_name ON cat_mesh_qualifier (name);
CREATE TRIGGER trg_cat_mesh_qualifier_audit_times
    BEFORE INSERT OR UPDATE ON cat_mesh_qualifier
    FOR EACH ROW EXECUTE FUNCTION set_audit_times();

COMMENT ON TABLE cat_mesh_qualifier IS 'MeSH 限定词';
COMMENT ON COLUMN cat_mesh_qualifier.ui IS '限定词唯一标识符（格式：Q000001-Q999999）';
COMMENT ON COLUMN cat_mesh_qualifier.name IS '限定词名称（英文）';
COMMENT ON COLUMN cat_mesh_qualifier.abbreviation IS '限定词缩写（如 DI, GE, IM）';
COMMENT ON COLUMN cat_mesh_qualifier.annotation IS '注释说明';
COMMENT ON COLUMN cat_mesh_qualifier.date_created IS '创建日期';
COMMENT ON COLUMN cat_mesh_qualifier.date_revised IS '修订日期';
COMMENT ON COLUMN cat_mesh_qualifier.date_established IS '确立日期';
COMMENT ON COLUMN cat_mesh_qualifier.active_status IS '是否有效（false=已废弃，true=有效）';
COMMENT ON COLUMN cat_mesh_qualifier.mesh_version IS 'MeSH 版本年份（如 "2025"）';
COMMENT ON COLUMN cat_mesh_qualifier.history_note IS '历史说明（记录限定词的历史使用规则）';
COMMENT ON COLUMN cat_mesh_qualifier.online_note IS '在线检索说明（检索策略指南）';
COMMENT ON COLUMN cat_mesh_qualifier.tree_numbers IS '树形编号列表（限定词在 MeSH 层级树中的位置）';

-- ------------------------------------------------------------
-- cat_mesh_tree_number
-- ------------------------------------------------------------
CREATE TABLE cat_mesh_tree_number
(
    id BIGINT NOT NULL PRIMARY KEY,
    descriptor_ui VARCHAR(10) NOT NULL,
    tree_number VARCHAR(100) NOT NULL,
    tree_level SMALLINT NOT NULL,
    is_primary BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX uk_cat_mesh_tree_number_number_descriptor ON cat_mesh_tree_number (tree_number, descriptor_ui);
CREATE INDEX idx_cat_mesh_tree_number_descriptor ON cat_mesh_tree_number (descriptor_ui);
CREATE INDEX idx_cat_mesh_tree_number_level ON cat_mesh_tree_number (tree_level, descriptor_ui);
CREATE INDEX idx_cat_mesh_tree_number_prefix ON cat_mesh_tree_number (tree_number);
CREATE TRIGGER trg_cat_mesh_tree_number_audit_times
    BEFORE INSERT OR UPDATE ON cat_mesh_tree_number
    FOR EACH ROW EXECUTE FUNCTION set_audit_times();

COMMENT ON TABLE cat_mesh_tree_number IS 'MeSH 树形编号';
COMMENT ON COLUMN cat_mesh_tree_number.descriptor_ui IS '主题词 UI（关联：cat_mesh_descriptor.ui）';
COMMENT ON COLUMN cat_mesh_tree_number.tree_number IS '树形编号（如 C04.557.337.428，最多 15 层约 59 字符）';
COMMENT ON COLUMN cat_mesh_tree_number.tree_level IS '层级深度（1-15，自动计算）';
COMMENT ON COLUMN cat_mesh_tree_number.is_primary IS '是否主要位置（false=次要，true=主要）';

-- ------------------------------------------------------------
-- cat_mesh_entry_term
-- ------------------------------------------------------------
CREATE TABLE cat_mesh_entry_term
(
    id BIGINT NOT NULL PRIMARY KEY,
    record_type VARCHAR(20) NOT NULL DEFAULT 'DESCRIPTOR',
    owner_ui VARCHAR(10) NOT NULL,
    term_ui VARCHAR(10) NULL,
    concept_ui VARCHAR(10) NULL,
    term VARCHAR(255) NOT NULL,
    lexical_tag VARCHAR(10) NULL,
    is_print_flag BOOLEAN NOT NULL DEFAULT true,
    record_preferred VARCHAR(10) NULL,
    is_permuted_term BOOLEAN NOT NULL DEFAULT false,
    is_concept_preferred BOOLEAN NOT NULL DEFAULT false,
    abbreviation VARCHAR(50) NULL,
    sort_version VARCHAR(255) NULL,
    entry_version VARCHAR(100) NULL,
    term_note TEXT NULL,
    date_created DATE NULL,
    thesaurus_ids JSONB NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_cat_mesh_entry_term_concept ON cat_mesh_entry_term (concept_ui);
CREATE INDEX idx_cat_mesh_entry_term_owner ON cat_mesh_entry_term (owner_ui);
CREATE INDEX idx_cat_mesh_entry_term_record_type ON cat_mesh_entry_term (record_type, owner_ui);
CREATE INDEX idx_cat_mesh_entry_term_term_trgm ON cat_mesh_entry_term USING gin (term gin_trgm_ops) WHERE (((record_type)::text = 'DESCRIPTOR'::text) AND (is_permuted_term = false));
CREATE TRIGGER trg_cat_mesh_entry_term_audit_times
    BEFORE INSERT OR UPDATE ON cat_mesh_entry_term
    FOR EACH ROW EXECUTE FUNCTION set_audit_times();

COMMENT ON TABLE cat_mesh_entry_term IS 'MeSH 入口术语';
COMMENT ON COLUMN cat_mesh_entry_term.record_type IS '记录类型（DESCRIPTOR=主题词，SCR=补充概念）';
COMMENT ON COLUMN cat_mesh_entry_term.owner_ui IS '所有者 UI（Descriptor: D开头，SCR: C开头）';
COMMENT ON COLUMN cat_mesh_entry_term.term_ui IS '术语 UI';
COMMENT ON COLUMN cat_mesh_entry_term.concept_ui IS '所属概念 UI';
COMMENT ON COLUMN cat_mesh_entry_term.term IS '入口术语/同义词';
COMMENT ON COLUMN cat_mesh_entry_term.lexical_tag IS '词法标记（枚举：NON/PEF/LAB/ABB/ACR/NAM）';
COMMENT ON COLUMN cat_mesh_entry_term.is_print_flag IS '是否打印（false=否，true=是）';
COMMENT ON COLUMN cat_mesh_entry_term.record_preferred IS '记录首选（枚举：Y/N）';
COMMENT ON COLUMN cat_mesh_entry_term.is_permuted_term IS '是否排列术语（false=否，true=是）';
COMMENT ON COLUMN cat_mesh_entry_term.is_concept_preferred IS '是否概念首选术语（false=否，true=是）';
COMMENT ON COLUMN cat_mesh_entry_term.abbreviation IS '术语缩写';
COMMENT ON COLUMN cat_mesh_entry_term.sort_version IS '排序版本';
COMMENT ON COLUMN cat_mesh_entry_term.entry_version IS '入口版本';
COMMENT ON COLUMN cat_mesh_entry_term.term_note IS '术语说明';
COMMENT ON COLUMN cat_mesh_entry_term.date_created IS '创建日期';
COMMENT ON COLUMN cat_mesh_entry_term.thesaurus_ids IS '来源词库 ID 列表（JSON 数组）';

-- ------------------------------------------------------------
-- cat_mesh_concept
-- ------------------------------------------------------------
CREATE TABLE cat_mesh_concept
(
    id BIGINT NOT NULL PRIMARY KEY,
    record_type VARCHAR(20) NOT NULL DEFAULT 'DESCRIPTOR',
    owner_ui VARCHAR(10) NOT NULL,
    concept_ui VARCHAR(10) NOT NULL,
    concept_name VARCHAR(255) NOT NULL,
    is_preferred BOOLEAN NOT NULL DEFAULT false,
    casn1_name TEXT NULL,
    registry_numbers JSONB NULL,
    related_registry_numbers JSONB NULL,
    scope_note TEXT NULL,
    translators_english_scope_note TEXT NULL,
    translators_scope_note TEXT NULL,
    concept_status VARCHAR(10) NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX uk_cat_mesh_concept_ui ON cat_mesh_concept (concept_ui);
CREATE INDEX idx_cat_mesh_concept_owner ON cat_mesh_concept (owner_ui);
CREATE INDEX idx_cat_mesh_concept_record_type ON cat_mesh_concept (record_type, owner_ui);
CREATE TRIGGER trg_cat_mesh_concept_audit_times
    BEFORE INSERT OR UPDATE ON cat_mesh_concept
    FOR EACH ROW EXECUTE FUNCTION set_audit_times();

COMMENT ON TABLE cat_mesh_concept IS 'MeSH 概念';
COMMENT ON COLUMN cat_mesh_concept.record_type IS '记录类型（DESCRIPTOR=主题词，SCR=补充概念）';
COMMENT ON COLUMN cat_mesh_concept.owner_ui IS '所有者 UI（Descriptor: D开头，SCR: C开头）';
COMMENT ON COLUMN cat_mesh_concept.concept_ui IS '概念唯一标识符（格式：M000001-M999999）';
COMMENT ON COLUMN cat_mesh_concept.concept_name IS '概念名称';
COMMENT ON COLUMN cat_mesh_concept.is_preferred IS '是否首选概念（每个主题词只有一个）';
COMMENT ON COLUMN cat_mesh_concept.casn1_name IS 'CAS 类型 1 名称（化学物质专用，IUPAC 命名可能很长）';
COMMENT ON COLUMN cat_mesh_concept.registry_numbers IS '注册号列表（JSON 数组，2025 DTD 支持多个）';
COMMENT ON COLUMN cat_mesh_concept.related_registry_numbers IS '相关注册号列表（JSON 数组）';
COMMENT ON COLUMN cat_mesh_concept.scope_note IS '范围说明';
COMMENT ON COLUMN cat_mesh_concept.translators_english_scope_note IS '翻译者英文范围说明';
COMMENT ON COLUMN cat_mesh_concept.translators_scope_note IS '翻译者范围说明';
COMMENT ON COLUMN cat_mesh_concept.concept_status IS '概念状态（枚举值）';

-- ------------------------------------------------------------
-- cat_mesh_concept_relation
-- ------------------------------------------------------------
CREATE TABLE cat_mesh_concept_relation
(
    id BIGINT NOT NULL PRIMARY KEY,
    descriptor_ui VARCHAR(10) NOT NULL,
    concept_ui VARCHAR(10) NOT NULL,
    is_preferred BOOLEAN NOT NULL DEFAULT false,
    relation_name VARCHAR(10) NULL,
    concept1_ui VARCHAR(10) NOT NULL,
    concept2_ui VARCHAR(10) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_cat_mesh_concept_relation_concept ON cat_mesh_concept_relation (concept_ui);
CREATE INDEX idx_cat_mesh_concept_relation_descriptor ON cat_mesh_concept_relation (descriptor_ui);
CREATE TRIGGER trg_cat_mesh_concept_relation_audit_times
    BEFORE INSERT OR UPDATE ON cat_mesh_concept_relation
    FOR EACH ROW EXECUTE FUNCTION set_audit_times();

COMMENT ON TABLE cat_mesh_concept_relation IS 'MeSH 概念关系';
COMMENT ON COLUMN cat_mesh_concept_relation.descriptor_ui IS '主题词 UI（关联：cat_mesh_descriptor.ui）';
COMMENT ON COLUMN cat_mesh_concept_relation.concept_ui IS '所属概念 UI（拥有此关系列表的概念）';
COMMENT ON COLUMN cat_mesh_concept_relation.is_preferred IS '所属概念是否为首选概念';
COMMENT ON COLUMN cat_mesh_concept_relation.relation_name IS '关系类型（NRW=Narrower/BRD=Broader/REL=Related，DTD #IMPLIED 可为 null）';
COMMENT ON COLUMN cat_mesh_concept_relation.concept1_ui IS '概念 1 UI（DTD 定义总是首选概念）';
COMMENT ON COLUMN cat_mesh_concept_relation.concept2_ui IS '概念 2 UI（关联概念）';

-- ------------------------------------------------------------
-- cat_mesh_entry_combination
-- ------------------------------------------------------------
CREATE TABLE cat_mesh_entry_combination
(
    id BIGINT NOT NULL PRIMARY KEY,
    descriptor_ui VARCHAR(10) NOT NULL,
    ecin_descriptor_ui VARCHAR(10) NOT NULL,
    ecin_qualifier_ui VARCHAR(10) NOT NULL,
    ecout_descriptor_ui VARCHAR(10) NOT NULL,
    ecout_qualifier_ui VARCHAR(10) NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_cat_mesh_entry_combination_in_descriptor ON cat_mesh_entry_combination (ecin_descriptor_ui);
CREATE INDEX idx_cat_mesh_entry_combination_out_descriptor ON cat_mesh_entry_combination (ecout_descriptor_ui);
CREATE INDEX idx_cat_mesh_entry_combination_descriptor ON cat_mesh_entry_combination (descriptor_ui);
CREATE TRIGGER trg_cat_mesh_entry_combination_audit_times
    BEFORE INSERT OR UPDATE ON cat_mesh_entry_combination
    FOR EACH ROW EXECUTE FUNCTION set_audit_times();

COMMENT ON TABLE cat_mesh_entry_combination IS 'MeSH 组合条目';
COMMENT ON COLUMN cat_mesh_entry_combination.descriptor_ui IS '主题词 UI（关联：cat_mesh_descriptor.ui）';
COMMENT ON COLUMN cat_mesh_entry_combination.ecin_descriptor_ui IS 'ECIN Descriptor UI（输入组合的主题词）';
COMMENT ON COLUMN cat_mesh_entry_combination.ecin_qualifier_ui IS 'ECIN Qualifier UI（输入组合的限定词）';
COMMENT ON COLUMN cat_mesh_entry_combination.ecout_descriptor_ui IS 'ECOUT Descriptor UI（输出组合的主题词）';
COMMENT ON COLUMN cat_mesh_entry_combination.ecout_qualifier_ui IS 'ECOUT Qualifier UI（输出组合的限定词，可选）';

-- ------------------------------------------------------------
-- cat_mesh_scr
-- ------------------------------------------------------------
CREATE TABLE cat_mesh_scr
(
    id BIGINT NOT NULL PRIMARY KEY,
    ui VARCHAR(10) NOT NULL,
    name VARCHAR(500) NOT NULL,
    scr_class SMALLINT NOT NULL DEFAULT 1,
    note TEXT NULL,
    frequency VARCHAR(50) NULL,
    previous_indexing TEXT NULL,
    date_created DATE NULL,
    date_revised DATE NULL,
    active_status BOOLEAN NOT NULL DEFAULT true,
    mesh_version VARCHAR(10) NULL,
    metadata JSONB NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by BIGINT      NULL,
    updated_by BIGINT      NULL,
    ip_address INET        NULL,
    record_remarks JSONB       NULL,
    version BIGINT      NOT NULL DEFAULT 0
);
CREATE UNIQUE INDEX uk_cat_mesh_scr_ui ON cat_mesh_scr (ui);
CREATE INDEX idx_cat_mesh_scr_active_version ON cat_mesh_scr (active_status, mesh_version);
CREATE INDEX idx_cat_mesh_scr_class ON cat_mesh_scr (scr_class);
CREATE INDEX idx_cat_mesh_scr_name ON cat_mesh_scr (name);
CREATE INDEX idx_cat_mesh_scr_revised ON cat_mesh_scr (date_revised, mesh_version);
CREATE TRIGGER trg_cat_mesh_scr_audit_times
    BEFORE INSERT OR UPDATE ON cat_mesh_scr
    FOR EACH ROW EXECUTE FUNCTION set_audit_times();

COMMENT ON TABLE cat_mesh_scr IS 'MeSH SCR（补充概念记录）';
COMMENT ON COLUMN cat_mesh_scr.ui IS 'SCR 唯一标识符（格式：C000001-C999999）';
COMMENT ON COLUMN cat_mesh_scr.name IS '补充概念名称（化学物质名称可能很长）';
COMMENT ON COLUMN cat_mesh_scr.scr_class IS 'SCR 类别（1=化学物质，2=协议，3=疾病，4=生物，5=人口群体，6=其他）';
COMMENT ON COLUMN cat_mesh_scr.note IS '说明（用法说明和注释）';
COMMENT ON COLUMN cat_mesh_scr.frequency IS '频率（文献中出现的频率描述）';
COMMENT ON COLUMN cat_mesh_scr.previous_indexing IS '之前的索引方式（历史参考）';
COMMENT ON COLUMN cat_mesh_scr.date_created IS '创建日期';
COMMENT ON COLUMN cat_mesh_scr.date_revised IS '修订日期（用于增量更新）';
COMMENT ON COLUMN cat_mesh_scr.active_status IS '是否有效（false=已废弃，true=有效）';
COMMENT ON COLUMN cat_mesh_scr.mesh_version IS 'MeSH 版本年份（如 "2025"）';
COMMENT ON COLUMN cat_mesh_scr.metadata IS '其他元数据（JSON 扩展字段）';

-- ------------------------------------------------------------
-- cat_mesh_scr_heading_mapped_to
-- ------------------------------------------------------------
CREATE TABLE cat_mesh_scr_heading_mapped_to
(
    id BIGINT NOT NULL PRIMARY KEY,
    scr_ui VARCHAR(10) NOT NULL,
    descriptor_ui VARCHAR(10) NOT NULL,
    qualifier_ui VARCHAR(10) NULL,
    major_topic BOOLEAN NOT NULL DEFAULT false,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX uk_cat_mesh_scr_heading_mapped_to_descriptor_qualifier ON cat_mesh_scr_heading_mapped_to (scr_ui, descriptor_ui, qualifier_ui);
CREATE INDEX idx_cat_mesh_scr_heading_mapped_to_descriptor ON cat_mesh_scr_heading_mapped_to (descriptor_ui);
CREATE INDEX idx_cat_mesh_scr_heading_mapped_to_scr ON cat_mesh_scr_heading_mapped_to (scr_ui);
CREATE TRIGGER trg_cat_mesh_scr_heading_mapped_to_audit_times
    BEFORE INSERT OR UPDATE ON cat_mesh_scr_heading_mapped_to
    FOR EACH ROW EXECUTE FUNCTION set_audit_times();

COMMENT ON TABLE cat_mesh_scr_heading_mapped_to IS 'SCR 映射关系';
COMMENT ON COLUMN cat_mesh_scr_heading_mapped_to.scr_ui IS 'SCR UI（关联：cat_mesh_scr.ui，格式：C000001）';
COMMENT ON COLUMN cat_mesh_scr_heading_mapped_to.descriptor_ui IS '映射到的主题词 UI（关联：cat_mesh_descriptor.ui，格式：D000001）';
COMMENT ON COLUMN cat_mesh_scr_heading_mapped_to.qualifier_ui IS '限定词 UI（关联：cat_mesh_qualifier.ui，格式：Q000001，可选）';
COMMENT ON COLUMN cat_mesh_scr_heading_mapped_to.major_topic IS '是否为主要主题词（Major Topic）';

-- ------------------------------------------------------------
-- cat_mesh_scr_source
-- ------------------------------------------------------------
CREATE TABLE cat_mesh_scr_source
(
    id BIGINT NOT NULL PRIMARY KEY,
    scr_ui VARCHAR(10) NOT NULL,
    source VARCHAR(500) NOT NULL,
    order_num INTEGER NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_cat_mesh_scr_source_scr ON cat_mesh_scr_source (scr_ui);
CREATE TRIGGER trg_cat_mesh_scr_source_audit_times
    BEFORE INSERT OR UPDATE ON cat_mesh_scr_source
    FOR EACH ROW EXECUTE FUNCTION set_audit_times();

COMMENT ON TABLE cat_mesh_scr_source IS 'SCR 来源';
COMMENT ON COLUMN cat_mesh_scr_source.scr_ui IS 'SCR UI（关联：cat_mesh_scr.ui，格式：C000001）';
COMMENT ON COLUMN cat_mesh_scr_source.source IS '来源（如 NCI2004_11_17/FDA SRS (2023)/OMIM (2013) 等）';
COMMENT ON COLUMN cat_mesh_scr_source.order_num IS '排序号（在同一 SCR 内的顺序）';

-- ------------------------------------------------------------
-- cat_mesh_scr_indexing_info
-- ------------------------------------------------------------
CREATE TABLE cat_mesh_scr_indexing_info
(
    id BIGINT NOT NULL PRIMARY KEY,
    scr_ui VARCHAR(10) NOT NULL,
    descriptor_ui VARCHAR(10) NULL,
    qualifier_ui VARCHAR(10) NULL,
    chemical_ui VARCHAR(10) NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_cat_mesh_scr_indexing_info_scr ON cat_mesh_scr_indexing_info (scr_ui);
CREATE TRIGGER trg_cat_mesh_scr_indexing_info_audit_times
    BEFORE INSERT OR UPDATE ON cat_mesh_scr_indexing_info
    FOR EACH ROW EXECUTE FUNCTION set_audit_times();

COMMENT ON TABLE cat_mesh_scr_indexing_info IS 'SCR 索引信息';
COMMENT ON COLUMN cat_mesh_scr_indexing_info.scr_ui IS 'SCR UI（关联：cat_mesh_scr.ui，格式：C000001）';
COMMENT ON COLUMN cat_mesh_scr_indexing_info.descriptor_ui IS '引用的主题词 UI（可选，关联：cat_mesh_descriptor.ui）';
COMMENT ON COLUMN cat_mesh_scr_indexing_info.qualifier_ui IS '引用的限定词 UI（可选，关联：cat_mesh_qualifier.ui）';
COMMENT ON COLUMN cat_mesh_scr_indexing_info.chemical_ui IS '引用的化学物质 UI（可选，指向其他 SCR 的 UI）';

-- ------------------------------------------------------------
-- cat_mesh_scr_pharmacological_action
-- ------------------------------------------------------------
CREATE TABLE cat_mesh_scr_pharmacological_action
(
    id BIGINT NOT NULL PRIMARY KEY,
    scr_ui VARCHAR(10) NOT NULL,
    descriptor_ui VARCHAR(10) NOT NULL,
    descriptor_name VARCHAR(255) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX uk_cat_mesh_scr_pharmacological_action_scr_descriptor ON cat_mesh_scr_pharmacological_action (scr_ui, descriptor_ui);
CREATE INDEX idx_cat_mesh_scr_pharmacological_action_descriptor ON cat_mesh_scr_pharmacological_action (descriptor_ui);
CREATE INDEX idx_cat_mesh_scr_pharmacological_action_scr ON cat_mesh_scr_pharmacological_action (scr_ui);
CREATE TRIGGER trg_cat_mesh_scr_pharmacological_action_audit_times
    BEFORE INSERT OR UPDATE ON cat_mesh_scr_pharmacological_action
    FOR EACH ROW EXECUTE FUNCTION set_audit_times();

COMMENT ON TABLE cat_mesh_scr_pharmacological_action IS 'SCR 药理作用';
COMMENT ON COLUMN cat_mesh_scr_pharmacological_action.scr_ui IS 'SCR UI（关联：cat_mesh_scr.ui，格式：C000001）';
COMMENT ON COLUMN cat_mesh_scr_pharmacological_action.descriptor_ui IS '药理作用主题词 UI（关联：cat_mesh_descriptor.ui）';
COMMENT ON COLUMN cat_mesh_scr_pharmacological_action.descriptor_name IS '主题词名称（冗余存储，避免查询时 JOIN）';
