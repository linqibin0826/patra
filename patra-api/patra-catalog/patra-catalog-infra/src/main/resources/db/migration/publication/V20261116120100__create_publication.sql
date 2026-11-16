-- publication 子域：文献及其子表、作者与研究者关联、MeSH 标引、关键词、资助、PubMed 文件进度、原文缓存、载体实例
-- 引用关系由应用保证，不建外键

-- ------------------------------------------------------------
-- cat_publication
-- ------------------------------------------------------------
CREATE TABLE cat_publication
(
    id BIGINT NOT NULL PRIMARY KEY,
    provenance_code VARCHAR(32) NOT NULL,
    pmid VARCHAR(15) NULL,
    doi VARCHAR(200) NULL,
    venue_id BIGINT NULL,
    venue_instance_id BIGINT NULL,
    title VARCHAR(2000) NOT NULL,
    original_title VARCHAR(1000) NULL,
    language_raw VARCHAR(50) NULL,
    language_code VARCHAR(10) NULL,
    language_base VARCHAR(5) GENERATED ALWAYS AS (split_part((language_code)::text, '-'::text, 1)) STORED,
    publication_status VARCHAR(32) NULL,
    media_type VARCHAR(32) NULL,
    publication_year SMALLINT NULL,
    is_oa BOOLEAN NOT NULL DEFAULT false,
    oa_status VARCHAR(20) NULL,
    authors_complete BOOLEAN NOT NULL DEFAULT true,
    citation_count INTEGER NULL DEFAULT 0,
    number_of_references INTEGER NULL DEFAULT 0,
    conflict_of_interest VARCHAR(500) NULL,
    ext_data JSONB NULL,
    last_synced_at TIMESTAMPTZ NULL,
    deleted_at TIMESTAMPTZ NULL,
    publication_month SMALLINT NULL,
    publication_day SMALLINT NULL,
    medline_pgn VARCHAR(200) NULL,
    start_page VARCHAR(50) NULL,
    end_page VARCHAR(50) NULL,
    journal_iso_abbreviation VARCHAR(200) NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by BIGINT      NULL,
    updated_by BIGINT      NULL,
    ip_address INET        NULL,
    record_remarks JSONB       NULL,
    version BIGINT      NOT NULL DEFAULT 0,
    CONSTRAINT ck_cat_publication_day CHECK (((publication_day >= 1) AND (publication_day <= 31))),
    CONSTRAINT ck_cat_publication_day_needs_month CHECK (((publication_day IS NULL) OR (publication_month IS NOT NULL))),
    CONSTRAINT ck_cat_publication_month CHECK (((publication_month >= 1) AND (publication_month <= 12)))
);
CREATE UNIQUE INDEX uk_cat_publication_doi ON cat_publication (doi);
CREATE UNIQUE INDEX uk_cat_publication_pmid ON cat_publication (pmid);
CREATE INDEX idx_cat_publication_is_oa ON cat_publication (is_oa);
CREATE INDEX idx_cat_publication_language_base ON cat_publication (language_base);
CREATE INDEX idx_cat_publication_provenance ON cat_publication (provenance_code);
CREATE INDEX idx_cat_publication_year ON cat_publication (publication_year);
CREATE INDEX idx_cat_publication_venue ON cat_publication (venue_id);
CREATE INDEX idx_cat_publication_venue_live ON cat_publication (venue_id, id DESC) WHERE (deleted_at IS NULL);
CREATE INDEX idx_cat_publication_venue_instance ON cat_publication (venue_instance_id);
CREATE TRIGGER trg_cat_publication_audit_times
    BEFORE INSERT OR UPDATE ON cat_publication
    FOR EACH ROW EXECUTE FUNCTION set_audit_times();

COMMENT ON TABLE cat_publication IS '文献出版物';
COMMENT ON COLUMN cat_publication.provenance_code IS '数据来源代码（PUBMED, EPMC, CROSSREF 等）';
COMMENT ON COLUMN cat_publication.pmid IS 'PubMed ID（1-15位数字，可为 null）';
COMMENT ON COLUMN cat_publication.doi IS 'Digital Object Identifier（最长200字符，可为 null）';
COMMENT ON COLUMN cat_publication.venue_id IS '载体 ID（冗余优化：避免二级 JOIN）';
COMMENT ON COLUMN cat_publication.venue_instance_id IS '载体实例 ID（外键：cat_venue_instance.id）';
COMMENT ON COLUMN cat_publication.title IS '文献标题（英文或原语言）';
COMMENT ON COLUMN cat_publication.original_title IS '原始语言标题（非英文时填充）';
COMMENT ON COLUMN cat_publication.language_raw IS '原始语言值（外部采集）';
COMMENT ON COLUMN cat_publication.language_code IS '标准语言代码（BCP 47，如 zh-CN, en-US）';
COMMENT ON COLUMN cat_publication.language_base IS '基础语种代码（数据库生成列，只读）';
COMMENT ON COLUMN cat_publication.publication_status IS '出版状态（PUBLISHED, IN_PRESS, PREPRINT 等）';
COMMENT ON COLUMN cat_publication.media_type IS '媒介类型（PRINT, ELECTRONIC, BOTH 等）';
COMMENT ON COLUMN cat_publication.publication_year IS '出版年份（1800-2100）';
COMMENT ON COLUMN cat_publication.is_oa IS '是否有 OA 版本（冗余：快速筛选）';
COMMENT ON COLUMN cat_publication.oa_status IS '最佳 OA 状态（gold/green/hybrid/bronze/closed）';
COMMENT ON COLUMN cat_publication.authors_complete IS '作者列表是否完整';
COMMENT ON COLUMN cat_publication.citation_count IS '被引次数（定期更新）';
COMMENT ON COLUMN cat_publication.number_of_references IS '参考文献数量';
COMMENT ON COLUMN cat_publication.conflict_of_interest IS '利益冲突声明';
COMMENT ON COLUMN cat_publication.ext_data IS '扩展数据（JSON 格式，灵活扩展）';
COMMENT ON COLUMN cat_publication.last_synced_at IS '最后同步时间（用于增量更新判断）';
COMMENT ON COLUMN cat_publication.deleted_at IS '删除时间，空表示未删除；PubMed 删除某篇时同步软删';
COMMENT ON COLUMN cat_publication.publication_month IS '出版月份（主出版日期冗余；只到年时为 null）';
COMMENT ON COLUMN cat_publication.publication_day IS '出版日（主出版日期冗余；只到年月时为 null）';
COMMENT ON COLUMN cat_publication.medline_pgn IS 'PubMed MedlinePgn 原文（NLM 缩写形式，如 `1451-61`）';
COMMENT ON COLUMN cat_publication.start_page IS 'PubMed StartPage';
COMMENT ON COLUMN cat_publication.end_page IS 'PubMed EndPage';
COMMENT ON COLUMN cat_publication.journal_iso_abbreviation IS '期刊 ISO 缩写（Journal/ISOAbbreviation）';

-- ------------------------------------------------------------
-- cat_publication_identifier
-- ------------------------------------------------------------
CREATE TABLE cat_publication_identifier
(
    id BIGINT NOT NULL PRIMARY KEY,
    publication_id BIGINT NOT NULL,
    type VARCHAR(20) NOT NULL,
    identifier_value VARCHAR(255) NOT NULL,
    source VARCHAR(50) NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_cat_publication_identifier_publication_type ON cat_publication_identifier (publication_id, type);
CREATE INDEX idx_cat_publication_identifier_type_value ON cat_publication_identifier (type, identifier_value);
CREATE TRIGGER trg_cat_publication_identifier_audit_times
    BEFORE INSERT OR UPDATE ON cat_publication_identifier
    FOR EACH ROW EXECUTE FUNCTION set_audit_times();

COMMENT ON TABLE cat_publication_identifier IS '文献标识符';
COMMENT ON COLUMN cat_publication_identifier.publication_id IS '文献 ID（外键：cat_publication.id）';
COMMENT ON COLUMN cat_publication_identifier.type IS '标识符类型：PMID/DOI/PMC/PII/ARXIV 等';
COMMENT ON COLUMN cat_publication_identifier.identifier_value IS '标识符值';
COMMENT ON COLUMN cat_publication_identifier.source IS '标识符来源（PubMed/Crossref/Manual）';

-- ------------------------------------------------------------
-- cat_publication_abstract
-- ------------------------------------------------------------
CREATE TABLE cat_publication_abstract
(
    id BIGINT NOT NULL PRIMARY KEY,
    publication_id BIGINT NOT NULL,
    plain_text TEXT NULL,
    structured_sections JSONB NULL,
    copyright VARCHAR(1000) NULL,
    abstract_type VARCHAR(32) NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX uk_cat_publication_abstract_publication ON cat_publication_abstract (publication_id);
CREATE TRIGGER trg_cat_publication_abstract_audit_times
    BEFORE INSERT OR UPDATE ON cat_publication_abstract
    FOR EACH ROW EXECUTE FUNCTION set_audit_times();

COMMENT ON TABLE cat_publication_abstract IS '文献摘要';
COMMENT ON COLUMN cat_publication_abstract.publication_id IS '文献 ID（外键：cat_publication.id，一对一关系）';
COMMENT ON COLUMN cat_publication_abstract.plain_text IS '纯文本摘要';
COMMENT ON COLUMN cat_publication_abstract.structured_sections IS '结构化摘要段落（有序数组 `[{"label","text"}]`；纯文本摘要为 null）';
COMMENT ON COLUMN cat_publication_abstract.copyright IS '版权信息';
COMMENT ON COLUMN cat_publication_abstract.abstract_type IS '摘要类型：structured/unstructured/graphical/none';

-- ------------------------------------------------------------
-- cat_publication_date
-- ------------------------------------------------------------
CREATE TABLE cat_publication_date
(
    id BIGINT NOT NULL PRIMARY KEY,
    publication_id BIGINT NOT NULL,
    date_type VARCHAR(50) NOT NULL,
    date_value DATE NULL,
    year SMALLINT NOT NULL,
    month SMALLINT NULL,
    day SMALLINT NULL,
    date_precision VARCHAR(10) NOT NULL DEFAULT 'year',
    season VARCHAR(100) NULL,
    date_string VARCHAR(200) NULL,
    is_primary BOOLEAN NOT NULL DEFAULT false,
    order_num INTEGER NULL,
    metadata JSONB NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_cat_publication_date_publication ON cat_publication_date (publication_id);
CREATE INDEX idx_cat_publication_date_type ON cat_publication_date (date_type);
CREATE INDEX idx_cat_publication_date_value ON cat_publication_date (date_value);
CREATE INDEX idx_cat_publication_date_year ON cat_publication_date (year);
CREATE TRIGGER trg_cat_publication_date_audit_times
    BEFORE INSERT OR UPDATE ON cat_publication_date
    FOR EACH ROW EXECUTE FUNCTION set_audit_times();

COMMENT ON TABLE cat_publication_date IS '文献日期';
COMMENT ON COLUMN cat_publication_date.publication_id IS '文献 ID（外键：cat_publication.id）';
COMMENT ON COLUMN cat_publication_date.date_type IS '日期类型：Received/Accepted/Published/Revised/EPub/PPub 等';
COMMENT ON COLUMN cat_publication_date.date_value IS '日期值（仅完整日期时填充）';
COMMENT ON COLUMN cat_publication_date.year IS '年份（必填）';
COMMENT ON COLUMN cat_publication_date.month IS '月份（1-12，可为空）';
COMMENT ON COLUMN cat_publication_date.day IS '日期（1-31，可为空）';
COMMENT ON COLUMN cat_publication_date.date_precision IS '精度：year/month/day';
COMMENT ON COLUMN cat_publication_date.season IS '季节（如 "Spring 2024"）';
COMMENT ON COLUMN cat_publication_date.date_string IS '原始日期字符串';
COMMENT ON COLUMN cat_publication_date.is_primary IS '是否主要日期';
COMMENT ON COLUMN cat_publication_date.order_num IS '顺序号';
COMMENT ON COLUMN cat_publication_date.metadata IS '扩展信息，JSON';

-- ------------------------------------------------------------
-- cat_publication_metadata
-- ------------------------------------------------------------
CREATE TABLE cat_publication_metadata
(
    id BIGINT NOT NULL PRIMARY KEY,
    publication_id BIGINT NOT NULL,
    indexing_status VARCHAR(50) NULL,
    indexing_method VARCHAR(50) NULL,
    indexed_date DATE NULL,
    data_source VARCHAR(50) NULL,
    import_batch VARCHAR(50) NULL,
    import_date TIMESTAMPTZ NULL,
    owner VARCHAR(50) NULL,
    citation_subset VARCHAR(20) NULL,
    quality_score VARCHAR(2) NULL,
    completeness_score VARCHAR(2) NULL,
    has_full_text BOOLEAN NOT NULL DEFAULT false,
    full_text_url VARCHAR(200) NULL,
    review_status VARCHAR(50) NULL,
    review_date DATE NULL,
    reviewer VARCHAR(100) NULL,
    validation_errors JSONB NULL,
    processing_notes JSONB NULL,
    ext_metadata JSONB NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX uk_cat_publication_metadata_publication ON cat_publication_metadata (publication_id);
CREATE INDEX idx_cat_publication_metadata_data_source ON cat_publication_metadata (data_source);
CREATE INDEX idx_cat_publication_metadata_import_batch ON cat_publication_metadata (import_batch);
CREATE INDEX idx_cat_publication_metadata_indexing_status ON cat_publication_metadata (indexing_status);
CREATE INDEX idx_cat_publication_metadata_review_status ON cat_publication_metadata (review_status);
CREATE TRIGGER trg_cat_publication_metadata_audit_times
    BEFORE INSERT OR UPDATE ON cat_publication_metadata
    FOR EACH ROW EXECUTE FUNCTION set_audit_times();

COMMENT ON TABLE cat_publication_metadata IS '文献元数据';
COMMENT ON COLUMN cat_publication_metadata.publication_id IS '文献 ID（外键：cat_publication.id，一对一关系）';
COMMENT ON COLUMN cat_publication_metadata.indexing_status IS '索引状态：Pending/Indexed/MEDLINE/PubMed-not-MEDLINE 等';
COMMENT ON COLUMN cat_publication_metadata.indexing_method IS '索引方法：Automated/Curated/In-Data-Review';
COMMENT ON COLUMN cat_publication_metadata.indexed_date IS '索引日期';
COMMENT ON COLUMN cat_publication_metadata.data_source IS '数据来源：PubMed/EPMC/Crossref/Manual';
COMMENT ON COLUMN cat_publication_metadata.import_batch IS '导入批次标识';
COMMENT ON COLUMN cat_publication_metadata.import_date IS '导入时间';
COMMENT ON COLUMN cat_publication_metadata.owner IS '数据记录所有者（NLM, NASA, PIP 等）';
COMMENT ON COLUMN cat_publication_metadata.citation_subset IS '引用子集标识（IM, AIM 等）';
COMMENT ON COLUMN cat_publication_metadata.quality_score IS '质量评分：A/B/C/D/F';
COMMENT ON COLUMN cat_publication_metadata.completeness_score IS '完整性评分：A/B/C/D/F';
COMMENT ON COLUMN cat_publication_metadata.has_full_text IS '是否有全文';
COMMENT ON COLUMN cat_publication_metadata.full_text_url IS '全文链接';
COMMENT ON COLUMN cat_publication_metadata.review_status IS '审核状态：Pending/Reviewed/Rejected/Approved';
COMMENT ON COLUMN cat_publication_metadata.review_date IS '审核日期';
COMMENT ON COLUMN cat_publication_metadata.reviewer IS '审核人姓名';
COMMENT ON COLUMN cat_publication_metadata.validation_errors IS '验证错误（JSON 数组）';
COMMENT ON COLUMN cat_publication_metadata.processing_notes IS '处理注释（JSON 数组）';
COMMENT ON COLUMN cat_publication_metadata.ext_metadata IS '其余元数据，JSON';

-- ------------------------------------------------------------
-- cat_publication_alternative_abstract
-- ------------------------------------------------------------
CREATE TABLE cat_publication_alternative_abstract
(
    id BIGINT NOT NULL PRIMARY KEY,
    publication_id BIGINT NOT NULL,
    abstract_id BIGINT NULL,
    language_code VARCHAR(10) NOT NULL,
    source_type VARCHAR(64) NOT NULL DEFAULT 'unknown',
    language_name VARCHAR(50) NULL,
    plain_text TEXT NULL,
    structured_sections JSONB NULL,
    copyright VARCHAR(1000) NULL,
    translation_type VARCHAR(50) NULL,
    translator VARCHAR(100) NULL,
    translation_date DATE NULL,
    quality_level VARCHAR(50) NULL,
    is_official BOOLEAN NOT NULL DEFAULT false,
    order_num INTEGER NULL,
    metadata JSONB NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX uk_cat_publication_alternative_abstract_language_source ON cat_publication_alternative_abstract (publication_id, language_code, source_type);
CREATE INDEX idx_cat_publication_alternative_abstract_abstract ON cat_publication_alternative_abstract (abstract_id);
CREATE INDEX idx_cat_publication_alternative_abstract_language ON cat_publication_alternative_abstract (language_code);
CREATE INDEX idx_cat_publication_alternative_abstract_publication ON cat_publication_alternative_abstract (publication_id);
CREATE TRIGGER trg_cat_publication_alternative_abstract_audit_times
    BEFORE INSERT OR UPDATE ON cat_publication_alternative_abstract
    FOR EACH ROW EXECUTE FUNCTION set_audit_times();

COMMENT ON TABLE cat_publication_alternative_abstract IS '文献翻译摘要';
COMMENT ON COLUMN cat_publication_alternative_abstract.publication_id IS '文献 ID（外键：cat_publication.id）';
COMMENT ON COLUMN cat_publication_alternative_abstract.abstract_id IS '主摘要 ID（外键：cat_publication_abstract.id）';
COMMENT ON COLUMN cat_publication_alternative_abstract.language_code IS '语言代码（ISO 639-1，如 "zh-CN"、"ja"）';
COMMENT ON COLUMN cat_publication_alternative_abstract.source_type IS '摘要来源类型（如 `publisher`、`plain-language-summary`）';
COMMENT ON COLUMN cat_publication_alternative_abstract.language_name IS '语言名称（如 "Chinese"、"Japanese"）';
COMMENT ON COLUMN cat_publication_alternative_abstract.plain_text IS '纯文本摘要';
COMMENT ON COLUMN cat_publication_alternative_abstract.structured_sections IS '结构化摘要段落（有序数组 `[{"label","text"}]`；纯文本摘要为 null）';
COMMENT ON COLUMN cat_publication_alternative_abstract.copyright IS '版权信息';
COMMENT ON COLUMN cat_publication_alternative_abstract.translation_type IS '翻译类型：Official/Professional/Machine/Community';
COMMENT ON COLUMN cat_publication_alternative_abstract.translator IS '译者姓名或机构';
COMMENT ON COLUMN cat_publication_alternative_abstract.translation_date IS '翻译日期';
COMMENT ON COLUMN cat_publication_alternative_abstract.quality_level IS '质量级别：Excellent/Good/Fair/Poor';
COMMENT ON COLUMN cat_publication_alternative_abstract.is_official IS '是否官方翻译';
COMMENT ON COLUMN cat_publication_alternative_abstract.order_num IS '顺序号';
COMMENT ON COLUMN cat_publication_alternative_abstract.metadata IS '扩展信息，JSON';

-- ------------------------------------------------------------
-- cat_publication_oa_location
-- ------------------------------------------------------------
CREATE TABLE cat_publication_oa_location
(
    id BIGINT NOT NULL PRIMARY KEY,
    publication_id BIGINT NOT NULL,
    oa_status VARCHAR(20) NOT NULL,
    location_type VARCHAR(50) NULL,
    url VARCHAR(500) NULL,
    host_domain VARCHAR(200) NULL,
    repository_name VARCHAR(100) NULL,
    repository_id VARCHAR(100) NULL,
    version_type VARCHAR(50) NULL,
    license VARCHAR(100) NULL,
    available_date DATE NULL,
    embargo_end_date DATE NULL,
    is_best BOOLEAN NOT NULL DEFAULT false,
    priority INTEGER NULL,
    evidence_source VARCHAR(50) NULL,
    checked_date TIMESTAMPTZ NULL,
    is_active BOOLEAN NOT NULL DEFAULT true,
    pmcid VARCHAR(200) NULL,
    access_metrics JSONB NULL,
    metadata JSONB NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX uk_cat_publication_oa_location_url ON cat_publication_oa_location (publication_id, url);
CREATE INDEX idx_cat_publication_oa_location_type ON cat_publication_oa_location (location_type);
CREATE INDEX idx_cat_publication_oa_location_publication ON cat_publication_oa_location (publication_id);
CREATE INDEX idx_cat_publication_oa_location_status ON cat_publication_oa_location (oa_status);
CREATE TRIGGER trg_cat_publication_oa_location_audit_times
    BEFORE INSERT OR UPDATE ON cat_publication_oa_location
    FOR EACH ROW EXECUTE FUNCTION set_audit_times();

COMMENT ON TABLE cat_publication_oa_location IS '文献开放获取位置';
COMMENT ON COLUMN cat_publication_oa_location.publication_id IS '文献 ID（外键：cat_publication.id）';
COMMENT ON COLUMN cat_publication_oa_location.oa_status IS 'OA 状态：gold/green/hybrid/bronze/closed';
COMMENT ON COLUMN cat_publication_oa_location.location_type IS '位置类型：publisher/repository/pubmed_central/preprint';
COMMENT ON COLUMN cat_publication_oa_location.url IS '访问 URL';
COMMENT ON COLUMN cat_publication_oa_location.host_domain IS '托管域名';
COMMENT ON COLUMN cat_publication_oa_location.repository_name IS '仓库名称';
COMMENT ON COLUMN cat_publication_oa_location.repository_id IS '仓库标识符';
COMMENT ON COLUMN cat_publication_oa_location.version_type IS '版本类型：publishedVersion/acceptedVersion/submittedVersion';
COMMENT ON COLUMN cat_publication_oa_location.license IS '许可证';
COMMENT ON COLUMN cat_publication_oa_location.available_date IS '可用日期';
COMMENT ON COLUMN cat_publication_oa_location.embargo_end_date IS '禁发期结束日期';
COMMENT ON COLUMN cat_publication_oa_location.is_best IS '是否最佳位置';
COMMENT ON COLUMN cat_publication_oa_location.priority IS '优先级（数值越小优先级越高）';
COMMENT ON COLUMN cat_publication_oa_location.evidence_source IS '证据来源（Unpaywall/OpenAlex）';
COMMENT ON COLUMN cat_publication_oa_location.checked_date IS '链接检查时间';
COMMENT ON COLUMN cat_publication_oa_location.is_active IS '是否有效';
COMMENT ON COLUMN cat_publication_oa_location.pmcid IS 'PMC ID';
COMMENT ON COLUMN cat_publication_oa_location.access_metrics IS '访问指标，JSON';
COMMENT ON COLUMN cat_publication_oa_location.metadata IS '扩展信息，JSON';

-- ------------------------------------------------------------
-- cat_publication_reference
-- ------------------------------------------------------------
CREATE TABLE cat_publication_reference
(
    id BIGINT NOT NULL PRIMARY KEY,
    publication_id BIGINT NOT NULL,
    cited_publication_id BIGINT NULL,
    cited_pmid VARCHAR(20) NULL,
    cited_doi VARCHAR(200) NULL,
    citation_text VARCHAR(2000) NULL,
    article_title VARCHAR(500) NULL,
    source VARCHAR(500) NULL,
    volume VARCHAR(100) NULL,
    issue VARCHAR(100) NULL,
    pages VARCHAR(50) NULL,
    year SMALLINT NULL,
    authors VARCHAR(500) NULL,
    reference_type VARCHAR(50) NULL,
    reference_number INTEGER NOT NULL,
    is_retracted BOOLEAN NOT NULL DEFAULT false,
    metadata JSONB NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX uk_cat_publication_reference_number ON cat_publication_reference (publication_id, reference_number);
CREATE INDEX idx_cat_publication_reference_cited_doi ON cat_publication_reference (cited_doi);
CREATE INDEX idx_cat_publication_reference_cited_pmid ON cat_publication_reference (cited_pmid);
CREATE INDEX idx_cat_publication_reference_cited_publication ON cat_publication_reference (cited_publication_id);
CREATE INDEX idx_cat_publication_reference_publication ON cat_publication_reference (publication_id);
CREATE INDEX idx_cat_publication_reference_retracted ON cat_publication_reference (is_retracted);
CREATE INDEX idx_cat_publication_reference_year ON cat_publication_reference (year);
CREATE TRIGGER trg_cat_publication_reference_audit_times
    BEFORE INSERT OR UPDATE ON cat_publication_reference
    FOR EACH ROW EXECUTE FUNCTION set_audit_times();

COMMENT ON TABLE cat_publication_reference IS '文献的参考文献列表；目前没有代码读写';
COMMENT ON COLUMN cat_publication_reference.publication_id IS '引用方文献（cat_publication.id）';
COMMENT ON COLUMN cat_publication_reference.cited_publication_id IS '被引文献在本库里的 ID，未匹配为空';
COMMENT ON COLUMN cat_publication_reference.cited_pmid IS '被引文献的 PMID';
COMMENT ON COLUMN cat_publication_reference.cited_doi IS '被引文献的 DOI';
COMMENT ON COLUMN cat_publication_reference.citation_text IS '原始引文文本';
COMMENT ON COLUMN cat_publication_reference.article_title IS '被引文献标题';
COMMENT ON COLUMN cat_publication_reference.source IS '被引文献的出处（期刊名等）';
COMMENT ON COLUMN cat_publication_reference.volume IS '卷';
COMMENT ON COLUMN cat_publication_reference.issue IS '期';
COMMENT ON COLUMN cat_publication_reference.pages IS '页码';
COMMENT ON COLUMN cat_publication_reference.year IS '出版年';
COMMENT ON COLUMN cat_publication_reference.authors IS '作者串';
COMMENT ON COLUMN cat_publication_reference.reference_type IS '参考文献类型';
COMMENT ON COLUMN cat_publication_reference.reference_number IS '在参考文献列表里的序号，同一文献内唯一';
COMMENT ON COLUMN cat_publication_reference.is_retracted IS '被引文献是否已撤稿';
COMMENT ON COLUMN cat_publication_reference.metadata IS '扩展信息，JSON';

-- ------------------------------------------------------------
-- cat_publication_type
-- ------------------------------------------------------------
CREATE TABLE cat_publication_type
(
    id BIGINT NOT NULL PRIMARY KEY,
    publication_id BIGINT NOT NULL,
    type_id VARCHAR(50) NULL,
    type_value VARCHAR(200) NOT NULL,
    vocabulary_source VARCHAR(50) NULL,
    type_order INTEGER NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX uk_cat_publication_type_value_vocabulary ON cat_publication_type (publication_id, type_value, vocabulary_source);
CREATE INDEX idx_cat_publication_type_publication ON cat_publication_type (publication_id);
CREATE INDEX idx_cat_publication_type_value ON cat_publication_type (type_value);
CREATE TRIGGER trg_cat_publication_type_audit_times
    BEFORE INSERT OR UPDATE ON cat_publication_type
    FOR EACH ROW EXECUTE FUNCTION set_audit_times();

COMMENT ON TABLE cat_publication_type IS '文献出版类型关联';
COMMENT ON COLUMN cat_publication_type.publication_id IS '出版物 ID（外键：cat_publication.id）';
COMMENT ON COLUMN cat_publication_type.type_id IS '发表类型的唯一标识符（来自受控词表，可为空）';
COMMENT ON COLUMN cat_publication_type.type_value IS '发表类型的文本描述（如 "Journal Article", "Review"）';
COMMENT ON COLUMN cat_publication_type.vocabulary_source IS '词表来源（如 "MeSH", "Crossref"）';
COMMENT ON COLUMN cat_publication_type.type_order IS '类型顺序';

-- ------------------------------------------------------------
-- cat_publication_external_reference
-- ------------------------------------------------------------
CREATE TABLE cat_publication_external_reference
(
    id BIGINT NOT NULL PRIMARY KEY,
    publication_id BIGINT NOT NULL,
    database_name VARCHAR(50) NOT NULL,
    database_category VARCHAR(100) NULL,
    accession_number VARCHAR(200) NOT NULL,
    url VARCHAR(500) NULL,
    reference_type VARCHAR(50) NULL,
    description VARCHAR(500) NULL,
    access_date DATE NULL,
    database_version VARCHAR(50) NULL,
    order_num INTEGER NOT NULL DEFAULT 1,
    metadata JSONB NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX uk_cat_publication_external_reference_database_accession ON cat_publication_external_reference (publication_id, database_name, accession_number);
CREATE INDEX idx_cat_publication_external_reference_accession ON cat_publication_external_reference (accession_number);
CREATE INDEX idx_cat_publication_external_reference_category ON cat_publication_external_reference (database_category);
CREATE INDEX idx_cat_publication_external_reference_database ON cat_publication_external_reference (database_name);
CREATE INDEX idx_cat_publication_external_reference_publication ON cat_publication_external_reference (publication_id);
CREATE TRIGGER trg_cat_publication_external_reference_audit_times
    BEFORE INSERT OR UPDATE ON cat_publication_external_reference
    FOR EACH ROW EXECUTE FUNCTION set_audit_times();

COMMENT ON TABLE cat_publication_external_reference IS '文献在外部数据库里的条目（基因库、试验注册等）；目前没有代码读写';
COMMENT ON COLUMN cat_publication_external_reference.publication_id IS '所属文献（cat_publication.id）';
COMMENT ON COLUMN cat_publication_external_reference.database_name IS '外部数据库名，如 GenBank、ClinicalTrials.gov';
COMMENT ON COLUMN cat_publication_external_reference.database_category IS '外部数据库类别';
COMMENT ON COLUMN cat_publication_external_reference.accession_number IS '外部数据库里的编号';
COMMENT ON COLUMN cat_publication_external_reference.url IS '条目链接';
COMMENT ON COLUMN cat_publication_external_reference.reference_type IS '关联类型';
COMMENT ON COLUMN cat_publication_external_reference.description IS '说明';
COMMENT ON COLUMN cat_publication_external_reference.access_date IS '访问日期';
COMMENT ON COLUMN cat_publication_external_reference.database_version IS '外部数据库版本';
COMMENT ON COLUMN cat_publication_external_reference.order_num IS '显示顺序';
COMMENT ON COLUMN cat_publication_external_reference.metadata IS '扩展信息，JSON';

-- ------------------------------------------------------------
-- cat_publication_related_item
-- ------------------------------------------------------------
CREATE TABLE cat_publication_related_item
(
    id BIGINT NOT NULL PRIMARY KEY,
    publication_id BIGINT NOT NULL,
    related_publication_id BIGINT NULL,
    related_pmid VARCHAR(20) NULL,
    related_doi VARCHAR(200) NULL,
    relationship_type VARCHAR(50) NOT NULL,
    title VARCHAR(500) NULL,
    description VARCHAR(500) NULL,
    relationship_date TIMESTAMPTZ NULL,
    initiated_by VARCHAR(100) NULL,
    status VARCHAR(50) NULL,
    order_num INTEGER NOT NULL DEFAULT 1,
    metadata JSONB NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_cat_publication_related_item_date ON cat_publication_related_item (relationship_date);
CREATE INDEX idx_cat_publication_related_item_related_publication ON cat_publication_related_item (related_publication_id);
CREATE INDEX idx_cat_publication_related_item_publication ON cat_publication_related_item (publication_id);
CREATE INDEX idx_cat_publication_related_item_status ON cat_publication_related_item (status);
CREATE INDEX idx_cat_publication_related_item_relationship_type ON cat_publication_related_item (relationship_type);
CREATE TRIGGER trg_cat_publication_related_item_audit_times
    BEFORE INSERT OR UPDATE ON cat_publication_related_item
    FOR EACH ROW EXECUTE FUNCTION set_audit_times();

COMMENT ON TABLE cat_publication_related_item IS '与本文献有关联的其他文献（勘误、撤稿声明、评论等）；目前没有代码读写';
COMMENT ON COLUMN cat_publication_related_item.publication_id IS '所属文献（cat_publication.id）';
COMMENT ON COLUMN cat_publication_related_item.related_publication_id IS '关联文献在本库里的 ID，未匹配为空';
COMMENT ON COLUMN cat_publication_related_item.related_pmid IS '关联文献的 PMID';
COMMENT ON COLUMN cat_publication_related_item.related_doi IS '关联文献的 DOI';
COMMENT ON COLUMN cat_publication_related_item.relationship_type IS '关联类型，如勘误、撤稿声明、评论';
COMMENT ON COLUMN cat_publication_related_item.title IS '关联文献标题';
COMMENT ON COLUMN cat_publication_related_item.description IS '说明';
COMMENT ON COLUMN cat_publication_related_item.relationship_date IS '建立关联的时间';
COMMENT ON COLUMN cat_publication_related_item.initiated_by IS '发起方';
COMMENT ON COLUMN cat_publication_related_item.status IS '关联状态';
COMMENT ON COLUMN cat_publication_related_item.order_num IS '显示顺序';
COMMENT ON COLUMN cat_publication_related_item.metadata IS '扩展信息，JSON';

-- ------------------------------------------------------------
-- cat_publication_supplemental_object
-- ------------------------------------------------------------
CREATE TABLE cat_publication_supplemental_object
(
    id BIGINT NOT NULL PRIMARY KEY,
    publication_id BIGINT NOT NULL,
    object_type VARCHAR(50) NOT NULL,
    content_type VARCHAR(50) NULL,
    title VARCHAR(500) NULL,
    description VARCHAR(1000) NULL,
    url VARCHAR(500) NULL,
    file_name VARCHAR(255) NULL,
    file_size BIGINT NULL,
    doi VARCHAR(100) NULL,
    license VARCHAR(50) NULL,
    authors VARCHAR(500) NULL,
    order_num INTEGER NOT NULL DEFAULT 1,
    is_public BOOLEAN NOT NULL DEFAULT true,
    available_date DATE NULL,
    metadata JSONB NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_cat_publication_supplemental_object_available_date ON cat_publication_supplemental_object (available_date);
CREATE INDEX idx_cat_publication_supplemental_object_doi ON cat_publication_supplemental_object (doi);
CREATE INDEX idx_cat_publication_supplemental_object_object_type ON cat_publication_supplemental_object (object_type);
CREATE INDEX idx_cat_publication_supplemental_object_public ON cat_publication_supplemental_object (is_public);
CREATE INDEX idx_cat_publication_supplemental_object_publication ON cat_publication_supplemental_object (publication_id);
CREATE TRIGGER trg_cat_publication_supplemental_object_audit_times
    BEFORE INSERT OR UPDATE ON cat_publication_supplemental_object
    FOR EACH ROW EXECUTE FUNCTION set_audit_times();

COMMENT ON TABLE cat_publication_supplemental_object IS '文献的补充材料（数据集、附件等）；目前没有代码读写';
COMMENT ON COLUMN cat_publication_supplemental_object.publication_id IS '所属文献（cat_publication.id）';
COMMENT ON COLUMN cat_publication_supplemental_object.object_type IS '材料类型';
COMMENT ON COLUMN cat_publication_supplemental_object.content_type IS '内容的 MIME 类型';
COMMENT ON COLUMN cat_publication_supplemental_object.title IS '标题';
COMMENT ON COLUMN cat_publication_supplemental_object.description IS '说明';
COMMENT ON COLUMN cat_publication_supplemental_object.url IS '下载链接';
COMMENT ON COLUMN cat_publication_supplemental_object.file_name IS '文件名';
COMMENT ON COLUMN cat_publication_supplemental_object.file_size IS '文件大小（字节）';
COMMENT ON COLUMN cat_publication_supplemental_object.doi IS '材料自己的 DOI';
COMMENT ON COLUMN cat_publication_supplemental_object.license IS '许可协议';
COMMENT ON COLUMN cat_publication_supplemental_object.authors IS '作者串';
COMMENT ON COLUMN cat_publication_supplemental_object.order_num IS '显示顺序';
COMMENT ON COLUMN cat_publication_supplemental_object.is_public IS '是否公开';
COMMENT ON COLUMN cat_publication_supplemental_object.available_date IS '开放日期';
COMMENT ON COLUMN cat_publication_supplemental_object.metadata IS '扩展信息，JSON';

-- ------------------------------------------------------------
-- cat_publication_history
-- ------------------------------------------------------------
CREATE TABLE cat_publication_history
(
    id BIGINT NOT NULL PRIMARY KEY,
    publication_id BIGINT NOT NULL,
    event_type VARCHAR(50) NOT NULL,
    event_date TIMESTAMPTZ NOT NULL,
    date_precision VARCHAR(10) NULL,
    description VARCHAR(500) NULL,
    actor VARCHAR(100) NULL,
    previous_status VARCHAR(100) NULL,
    new_status VARCHAR(100) NULL,
    order_num INTEGER NOT NULL,
    is_public BOOLEAN NOT NULL DEFAULT true,
    metadata JSONB NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX uk_cat_publication_history_order ON cat_publication_history (publication_id, order_num);
CREATE INDEX idx_cat_publication_history_event_date ON cat_publication_history (event_date);
CREATE INDEX idx_cat_publication_history_event_type ON cat_publication_history (event_type);
CREATE INDEX idx_cat_publication_history_publication ON cat_publication_history (publication_id);
CREATE INDEX idx_cat_publication_history_timeline ON cat_publication_history (publication_id, event_date, order_num);
CREATE TRIGGER trg_cat_publication_history_audit_times
    BEFORE INSERT OR UPDATE ON cat_publication_history
    FOR EACH ROW EXECUTE FUNCTION set_audit_times();

COMMENT ON TABLE cat_publication_history IS '文献的出版历史事件（投稿、接收、上线等）；目前没有代码读写';
COMMENT ON COLUMN cat_publication_history.publication_id IS '所属文献（cat_publication.id）';
COMMENT ON COLUMN cat_publication_history.event_type IS '事件类型，如投稿、接收、上线';
COMMENT ON COLUMN cat_publication_history.event_date IS '事件时间';
COMMENT ON COLUMN cat_publication_history.date_precision IS '时间精度，如年、月、日';
COMMENT ON COLUMN cat_publication_history.description IS '说明';
COMMENT ON COLUMN cat_publication_history.actor IS '事件发起方';
COMMENT ON COLUMN cat_publication_history.previous_status IS '事件前的状态';
COMMENT ON COLUMN cat_publication_history.new_status IS '事件后的状态';
COMMENT ON COLUMN cat_publication_history.order_num IS '事件顺序，同一文献内唯一';
COMMENT ON COLUMN cat_publication_history.is_public IS '是否公开';
COMMENT ON COLUMN cat_publication_history.metadata IS '扩展信息，JSON';

-- ------------------------------------------------------------
-- cat_publication_author
-- ------------------------------------------------------------
CREATE TABLE cat_publication_author
(
    id BIGINT NOT NULL PRIMARY KEY,
    publication_id BIGINT NOT NULL,
    author_id BIGINT NULL,
    author_order INTEGER NOT NULL DEFAULT 1,
    display_name VARCHAR(200) NOT NULL,
    last_name VARCHAR(200) NULL,
    fore_name VARCHAR(200) NULL,
    initials VARCHAR(20) NULL,
    suffix VARCHAR(50) NULL,
    collective_name VARCHAR(500) NULL,
    orcid VARCHAR(19) NULL,
    is_first_author BOOLEAN NOT NULL DEFAULT false,
    is_corresponding_author BOOLEAN NOT NULL DEFAULT false,
    is_equal_contribution BOOLEAN NOT NULL DEFAULT false,
    email VARCHAR(255) NULL,
    author_metadata JSONB NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX uk_cat_publication_author_order ON cat_publication_author (publication_id, author_order);
CREATE INDEX idx_cat_publication_author_corresponding ON cat_publication_author (is_corresponding_author);
CREATE INDEX idx_cat_publication_author_first_author ON cat_publication_author (is_first_author);
CREATE INDEX idx_cat_publication_author_author ON cat_publication_author (author_id);
CREATE UNIQUE INDEX uk_cat_publication_author_author ON cat_publication_author (publication_id, author_id) WHERE (author_id IS NOT NULL);
CREATE TRIGGER trg_cat_publication_author_audit_times
    BEFORE INSERT OR UPDATE ON cat_publication_author
    FOR EACH ROW EXECUTE FUNCTION set_audit_times();

COMMENT ON TABLE cat_publication_author IS '文献-作者关联';
COMMENT ON COLUMN cat_publication_author.publication_id IS '出版物 ID（外键：cat_publication.id）';
COMMENT ON COLUMN cat_publication_author.author_id IS '作者 ID（外键：cat_author.id；软关联位——仅 ORCID 命中已消歧作者时填，其余为 null）';
COMMENT ON COLUMN cat_publication_author.author_order IS '作者顺序（1=第一作者，2=第二作者...）';
COMMENT ON COLUMN cat_publication_author.display_name IS '展示名（"LastName ForeName" 或集体作者名，恒非空）';
COMMENT ON COLUMN cat_publication_author.last_name IS '姓氏（LastName）';
COMMENT ON COLUMN cat_publication_author.fore_name IS '名字（ForeName）';
COMMENT ON COLUMN cat_publication_author.initials IS '首字母缩写（Initials）';
COMMENT ON COLUMN cat_publication_author.suffix IS '后缀（Suffix，如 Jr、III）';
COMMENT ON COLUMN cat_publication_author.collective_name IS '集体作者名（CollectiveName；个人作者为 null）';
COMMENT ON COLUMN cat_publication_author.orcid IS '归一化 ORCID（存档位，便于未来重关联/审计；未提供或校验失败为 null）';
COMMENT ON COLUMN cat_publication_author.is_first_author IS '是否第一作者';
COMMENT ON COLUMN cat_publication_author.is_corresponding_author IS '是否通讯作者';
COMMENT ON COLUMN cat_publication_author.is_equal_contribution IS '是否同等贡献作者';
COMMENT ON COLUMN cat_publication_author.email IS '作者邮箱（通讯作者时常填写）';
COMMENT ON COLUMN cat_publication_author.author_metadata IS '作者元数据（JSON 格式，灵活扩展）';

-- ------------------------------------------------------------
-- cat_publication_author_affiliation
-- ------------------------------------------------------------
CREATE TABLE cat_publication_author_affiliation
(
    id BIGINT NOT NULL PRIMARY KEY,
    publication_author_id BIGINT NOT NULL,
    publication_id BIGINT NOT NULL,
    affiliation_order INTEGER NOT NULL DEFAULT 1,
    affiliation_string VARCHAR(2000) NOT NULL,
    ror_id VARCHAR(50) NULL,
    ringgold_id VARCHAR(20) NULL,
    grid_id VARCHAR(50) NULL,
    organization_id BIGINT NULL,
    disambiguation_status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    disambiguation_method VARCHAR(50) NULL,
    disambiguation_score NUMERIC(5,4) NULL,
    disambiguated_at TIMESTAMPTZ NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX uk_cat_publication_author_affiliation_order ON cat_publication_author_affiliation (publication_author_id, affiliation_order);
CREATE INDEX idx_cat_publication_author_affiliation_disambiguation ON cat_publication_author_affiliation (disambiguation_status);
CREATE INDEX idx_cat_publication_author_affiliation_organization ON cat_publication_author_affiliation (organization_id);
CREATE INDEX idx_cat_publication_author_affiliation_publication ON cat_publication_author_affiliation (publication_id);
CREATE INDEX idx_cat_publication_author_affiliation_ringgold ON cat_publication_author_affiliation (ringgold_id);
CREATE INDEX idx_cat_publication_author_affiliation_ror ON cat_publication_author_affiliation (ror_id);
CREATE TRIGGER trg_cat_publication_author_affiliation_audit_times
    BEFORE INSERT OR UPDATE ON cat_publication_author_affiliation
    FOR EACH ROW EXECUTE FUNCTION set_audit_times();

COMMENT ON TABLE cat_publication_author_affiliation IS '作者-机构归属';
COMMENT ON COLUMN cat_publication_author_affiliation.publication_author_id IS '文献-作者关联 ID（外键：cat_publication_author.id）';
COMMENT ON COLUMN cat_publication_author_affiliation.publication_id IS '出版物 ID（冗余，避免 JOIN）';
COMMENT ON COLUMN cat_publication_author_affiliation.affiliation_order IS '机构顺序（1=第一机构，2=第二机构...）';
COMMENT ON COLUMN cat_publication_author_affiliation.affiliation_string IS '原始机构字符串（外部采集，未标准化）';
COMMENT ON COLUMN cat_publication_author_affiliation.ror_id IS 'ROR ID（如 "03vek6s52"）';
COMMENT ON COLUMN cat_publication_author_affiliation.ringgold_id IS 'Ringgold ID';
COMMENT ON COLUMN cat_publication_author_affiliation.grid_id IS 'GRID ID（历史数据，已废弃）';
COMMENT ON COLUMN cat_publication_author_affiliation.organization_id IS '关联机构 ID（外键：cat_organization.id，延迟填充）';
COMMENT ON COLUMN cat_publication_author_affiliation.disambiguation_status IS '消歧状态（PENDING/MATCHED/UNMATCHED/AMBIGUOUS）';
COMMENT ON COLUMN cat_publication_author_affiliation.disambiguation_method IS '消歧方法（ROR_ID/RINGGOLD/GRID/NAME_MATCH/MANUAL）';
COMMENT ON COLUMN cat_publication_author_affiliation.disambiguation_score IS '消歧置信度（0.0000-1.0000）';
COMMENT ON COLUMN cat_publication_author_affiliation.disambiguated_at IS '消歧时间（UTC）';

-- ------------------------------------------------------------
-- cat_investigator
-- ------------------------------------------------------------
CREATE TABLE cat_investigator
(
    id BIGINT NOT NULL PRIMARY KEY,
    last_name VARCHAR(200) NULL,
    fore_name VARCHAR(200) NULL,
    initials VARCHAR(50) NULL,
    suffix VARCHAR(50) NULL,
    orcid VARCHAR(50) NULL,
    researcher_id VARCHAR(100) NULL,
    investigator_type VARCHAR(100) NULL,
    affiliation_name VARCHAR(500) NULL,
    email VARCHAR(255) NULL,
    dedup_key VARCHAR(255) NULL,
    metadata JSONB NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by BIGINT      NULL,
    updated_by BIGINT      NULL,
    ip_address INET        NULL,
    record_remarks JSONB       NULL,
    version BIGINT      NOT NULL DEFAULT 0
);
CREATE INDEX idx_cat_investigator_dedup_key ON cat_investigator (dedup_key);
CREATE INDEX idx_cat_investigator_email ON cat_investigator (email);
CREATE INDEX idx_cat_investigator_orcid ON cat_investigator (orcid);
CREATE TRIGGER trg_cat_investigator_audit_times
    BEFORE INSERT OR UPDATE ON cat_investigator
    FOR EACH ROW EXECUTE FUNCTION set_audit_times();

COMMENT ON TABLE cat_investigator IS '研究者';
COMMENT ON COLUMN cat_investigator.last_name IS '姓（Last Name/Family Name）';
COMMENT ON COLUMN cat_investigator.fore_name IS '名（First Name/Given Name）';
COMMENT ON COLUMN cat_investigator.initials IS '姓名缩写（如 "J.K."）';
COMMENT ON COLUMN cat_investigator.suffix IS '后缀（如 "Jr.", "III", "MD", "PhD"）';
COMMENT ON COLUMN cat_investigator.orcid IS 'ORCID 标识符（格式: 0000-0001-2345-6789）';
COMMENT ON COLUMN cat_investigator.researcher_id IS '研究者 ID（ResearcherID/Publons）';
COMMENT ON COLUMN cat_investigator.investigator_type IS '研究者类型（如 "PI", "CoI", "Collaborator"）';
COMMENT ON COLUMN cat_investigator.affiliation_name IS '机构名称（文本，不关联 organization 表）';
COMMENT ON COLUMN cat_investigator.email IS '邮箱地址';
COMMENT ON COLUMN cat_investigator.dedup_key IS '复合去重键（应用层计算，MD5 哈希）';
COMMENT ON COLUMN cat_investigator.metadata IS '研究者元数据（JSON 格式，灵活扩展）';

-- ------------------------------------------------------------
-- cat_publication_investigator
-- ------------------------------------------------------------
CREATE TABLE cat_publication_investigator
(
    id BIGINT NOT NULL PRIMARY KEY,
    publication_id BIGINT NOT NULL,
    investigator_id BIGINT NOT NULL,
    role VARCHAR(100) NULL,
    is_contact BOOLEAN NOT NULL DEFAULT false,
    order_num INTEGER NULL,
    responsibility VARCHAR(1000) NULL,
    metadata JSONB NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX uk_cat_publication_investigator_investigator ON cat_publication_investigator (publication_id, investigator_id);
CREATE INDEX idx_cat_publication_investigator_investigator ON cat_publication_investigator (investigator_id);
CREATE INDEX idx_cat_publication_investigator_publication ON cat_publication_investigator (publication_id);
CREATE INDEX idx_cat_publication_investigator_role ON cat_publication_investigator (role);
CREATE TRIGGER trg_cat_publication_investigator_audit_times
    BEFORE INSERT OR UPDATE ON cat_publication_investigator
    FOR EACH ROW EXECUTE FUNCTION set_audit_times();

COMMENT ON TABLE cat_publication_investigator IS '文献-研究者关联';
COMMENT ON COLUMN cat_publication_investigator.publication_id IS '出版物 ID（外键：cat_publication.id）';
COMMENT ON COLUMN cat_publication_investigator.investigator_id IS '研究者 ID（外键：cat_investigator.id）';
COMMENT ON COLUMN cat_publication_investigator.role IS '角色（如 "principal", "co-investigator", "coordinator"）';
COMMENT ON COLUMN cat_publication_investigator.is_contact IS '是否联系人';
COMMENT ON COLUMN cat_publication_investigator.order_num IS '顺序号（多个研究者时排序）';
COMMENT ON COLUMN cat_publication_investigator.responsibility IS '职责描述（文本）';
COMMENT ON COLUMN cat_publication_investigator.metadata IS '关联元数据（JSON 格式，灵活扩展）';

-- ------------------------------------------------------------
-- cat_publication_personal_name_subject
-- ------------------------------------------------------------
CREATE TABLE cat_publication_personal_name_subject
(
    id BIGINT NOT NULL PRIMARY KEY,
    publication_id BIGINT NOT NULL,
    last_name VARCHAR(200) NULL,
    fore_name VARCHAR(200) NULL,
    initials VARCHAR(50) NULL,
    suffix VARCHAR(100) NULL,
    dates VARCHAR(100) NULL,
    description VARCHAR(500) NULL,
    subject_type VARCHAR(50) NULL,
    identifier VARCHAR(100) NULL,
    order_num INTEGER NULL,
    metadata JSONB NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_cat_publication_personal_name_subject_last_name ON cat_publication_personal_name_subject (last_name);
CREATE INDEX idx_cat_publication_personal_name_subject_publication ON cat_publication_personal_name_subject (publication_id);
CREATE INDEX idx_cat_publication_personal_name_subject_subject_type ON cat_publication_personal_name_subject (subject_type);
CREATE TRIGGER trg_cat_publication_personal_name_subject_audit_times
    BEFORE INSERT OR UPDATE ON cat_publication_personal_name_subject
    FOR EACH ROW EXECUTE FUNCTION set_audit_times();

COMMENT ON TABLE cat_publication_personal_name_subject IS '人物主题';
COMMENT ON COLUMN cat_publication_personal_name_subject.publication_id IS '出版物 ID（外键：cat_publication.id）';
COMMENT ON COLUMN cat_publication_personal_name_subject.last_name IS '姓（Last Name/Family Name）';
COMMENT ON COLUMN cat_publication_personal_name_subject.fore_name IS '名（First Name/Given Name）';
COMMENT ON COLUMN cat_publication_personal_name_subject.initials IS '姓名缩写（如 "J.K."）';
COMMENT ON COLUMN cat_publication_personal_name_subject.suffix IS '后缀/头衔（如 "Jr.", "King", "Emperor"）';
COMMENT ON COLUMN cat_publication_personal_name_subject.dates IS '生卒年代（如 "1820-1910", "c. 460 BC - c. 370 BC"）';
COMMENT ON COLUMN cat_publication_personal_name_subject.description IS '人物描述（简短介绍）';
COMMENT ON COLUMN cat_publication_personal_name_subject.subject_type IS '主题类型（如 "biography", "history", "memorial"）';
COMMENT ON COLUMN cat_publication_personal_name_subject.identifier IS '人物标识符（如 VIAF ID, Wikidata ID）';
COMMENT ON COLUMN cat_publication_personal_name_subject.order_num IS '顺序号（多个主题人物时排序）';
COMMENT ON COLUMN cat_publication_personal_name_subject.metadata IS '人物元数据（JSON 格式，灵活扩展）';

-- ------------------------------------------------------------
-- cat_publication_mesh_heading
-- ------------------------------------------------------------
CREATE TABLE cat_publication_mesh_heading
(
    id BIGINT NOT NULL PRIMARY KEY,
    publication_id BIGINT NOT NULL,
    descriptor_ui VARCHAR(10) NOT NULL,
    is_major_topic BOOLEAN NOT NULL DEFAULT false,
    heading_order INTEGER NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX uk_cat_publication_mesh_heading_descriptor ON cat_publication_mesh_heading (publication_id, descriptor_ui);
CREATE INDEX idx_cat_publication_mesh_heading_descriptor ON cat_publication_mesh_heading (descriptor_ui);
CREATE INDEX idx_cat_publication_mesh_heading_descriptor_publication ON cat_publication_mesh_heading (descriptor_ui, publication_id DESC);
CREATE INDEX idx_cat_publication_mesh_heading_major_topic ON cat_publication_mesh_heading (is_major_topic);
CREATE INDEX idx_cat_publication_mesh_heading_publication ON cat_publication_mesh_heading (publication_id);
CREATE TRIGGER trg_cat_publication_mesh_heading_audit_times
    BEFORE INSERT OR UPDATE ON cat_publication_mesh_heading
    FOR EACH ROW EXECUTE FUNCTION set_audit_times();

COMMENT ON TABLE cat_publication_mesh_heading IS '文献-MeSH 主题标引关联';
COMMENT ON COLUMN cat_publication_mesh_heading.publication_id IS '出版物 ID（外键：cat_publication.id）';
COMMENT ON COLUMN cat_publication_mesh_heading.descriptor_ui IS 'MeSH 主题词 UI（关联键：cat_mesh_descriptor.ui）';
COMMENT ON COLUMN cat_publication_mesh_heading.is_major_topic IS '是否为文章的主要主题';
COMMENT ON COLUMN cat_publication_mesh_heading.heading_order IS '标引顺序（可选）';

-- ------------------------------------------------------------
-- cat_publication_mesh_qualifier
-- ------------------------------------------------------------
CREATE TABLE cat_publication_mesh_qualifier
(
    id BIGINT NOT NULL PRIMARY KEY,
    publication_mesh_heading_id BIGINT NOT NULL,
    qualifier_ui VARCHAR(10) NOT NULL,
    is_major_topic BOOLEAN NOT NULL DEFAULT false,
    qualifier_order INTEGER NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX uk_cat_publication_mesh_qualifier_heading_qualifier ON cat_publication_mesh_qualifier (publication_mesh_heading_id, qualifier_ui);
CREATE INDEX idx_cat_publication_mesh_qualifier_heading ON cat_publication_mesh_qualifier (publication_mesh_heading_id);
CREATE INDEX idx_cat_publication_mesh_qualifier_major_topic ON cat_publication_mesh_qualifier (is_major_topic);
CREATE INDEX idx_cat_publication_mesh_qualifier_qualifier ON cat_publication_mesh_qualifier (qualifier_ui);
CREATE TRIGGER trg_cat_publication_mesh_qualifier_audit_times
    BEFORE INSERT OR UPDATE ON cat_publication_mesh_qualifier
    FOR EACH ROW EXECUTE FUNCTION set_audit_times();

COMMENT ON TABLE cat_publication_mesh_qualifier IS '文献-MeSH 限定词关联';
COMMENT ON COLUMN cat_publication_mesh_qualifier.publication_mesh_heading_id IS '文献 MeSH 标引 ID（外键：cat_publication_mesh_heading.id）';
COMMENT ON COLUMN cat_publication_mesh_qualifier.qualifier_ui IS 'MeSH 限定词 UI（关联键：cat_mesh_qualifier.ui）';
COMMENT ON COLUMN cat_publication_mesh_qualifier.is_major_topic IS '是否为文章的主要主题';
COMMENT ON COLUMN cat_publication_mesh_qualifier.qualifier_order IS '限定词顺序（可选）';

-- ------------------------------------------------------------
-- cat_publication_suppl_mesh
-- ------------------------------------------------------------
CREATE TABLE cat_publication_suppl_mesh
(
    id BIGINT NOT NULL PRIMARY KEY,
    publication_id BIGINT NOT NULL,
    scr_ui VARCHAR(10) NOT NULL,
    suppl_order INTEGER NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX uk_cat_publication_suppl_mesh_scr ON cat_publication_suppl_mesh (publication_id, scr_ui);
CREATE INDEX idx_cat_publication_suppl_mesh_scr_ui ON cat_publication_suppl_mesh (scr_ui);
CREATE TRIGGER trg_cat_publication_suppl_mesh_audit_times
    BEFORE INSERT OR UPDATE ON cat_publication_suppl_mesh
    FOR EACH ROW EXECUTE FUNCTION set_audit_times();

COMMENT ON TABLE cat_publication_suppl_mesh IS '文献-补充 MeSH 概念关联';
COMMENT ON COLUMN cat_publication_suppl_mesh.publication_id IS '文献 ID（外键：cat_publication.id）';
COMMENT ON COLUMN cat_publication_suppl_mesh.scr_ui IS 'SCR UI（关联键：cat_mesh_scr.ui）';
COMMENT ON COLUMN cat_publication_suppl_mesh.suppl_order IS '补充概念顺序（可选）';

-- ------------------------------------------------------------
-- cat_keyword
-- ------------------------------------------------------------
CREATE TABLE cat_keyword
(
    id BIGINT NOT NULL PRIMARY KEY,
    term VARCHAR(500) NOT NULL,
    source VARCHAR(50) NULL,
    language VARCHAR(10) NULL,
    normalized_term VARCHAR(500) NULL,
    frequency INTEGER NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by BIGINT      NULL,
    updated_by BIGINT      NULL,
    ip_address INET        NULL,
    record_remarks JSONB       NULL,
    version BIGINT      NOT NULL DEFAULT 0
);
CREATE UNIQUE INDEX uk_cat_keyword_normalized_term ON cat_keyword (normalized_term);
CREATE INDEX idx_cat_keyword_frequency ON cat_keyword (frequency DESC);
CREATE INDEX idx_cat_keyword_source_lang ON cat_keyword (source, language);
CREATE TRIGGER trg_cat_keyword_audit_times
    BEFORE INSERT OR UPDATE ON cat_keyword
    FOR EACH ROW EXECUTE FUNCTION set_audit_times();

COMMENT ON TABLE cat_keyword IS '关键词';
COMMENT ON COLUMN cat_keyword.term IS '关键词原始形式';
COMMENT ON COLUMN cat_keyword.source IS '关键词来源';
COMMENT ON COLUMN cat_keyword.language IS '语言代码（ISO 639-1）';
COMMENT ON COLUMN cat_keyword.normalized_term IS '规范化词形';
COMMENT ON COLUMN cat_keyword.frequency IS '出现频次';

-- ------------------------------------------------------------
-- cat_publication_keyword
-- ------------------------------------------------------------
CREATE TABLE cat_publication_keyword
(
    id BIGINT NOT NULL PRIMARY KEY,
    publication_id BIGINT NOT NULL,
    keyword_id BIGINT NOT NULL,
    is_major BOOLEAN NOT NULL DEFAULT false,
    order_num INTEGER NULL,
    keyword_set VARCHAR(50) NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_cat_publication_keyword_keyword_publication ON cat_publication_keyword (keyword_id, publication_id);
CREATE INDEX idx_cat_publication_keyword_publication_keyword ON cat_publication_keyword (publication_id, keyword_id);
CREATE TRIGGER trg_cat_publication_keyword_audit_times
    BEFORE INSERT OR UPDATE ON cat_publication_keyword
    FOR EACH ROW EXECUTE FUNCTION set_audit_times();

COMMENT ON TABLE cat_publication_keyword IS '文献关键词关联';
COMMENT ON COLUMN cat_publication_keyword.publication_id IS '出版物 ID（外键：cat_publication.id）';
COMMENT ON COLUMN cat_publication_keyword.keyword_id IS '关键词 ID（外键：cat_keyword.id）';
COMMENT ON COLUMN cat_publication_keyword.is_major IS '是否为主要关键词';
COMMENT ON COLUMN cat_publication_keyword.order_num IS '顺序号（在同一文献内的排序）';
COMMENT ON COLUMN cat_publication_keyword.keyword_set IS '关键词集（如 Author/Editor，区分不同来源）';

-- ------------------------------------------------------------
-- cat_publication_funding
-- ------------------------------------------------------------
CREATE TABLE cat_publication_funding
(
    id BIGINT NOT NULL PRIMARY KEY,
    publication_id BIGINT NOT NULL,
    organization_id BIGINT NULL,
    grant_id VARCHAR(200) NULL,
    funder_name_raw VARCHAR(500) NULL,
    funder_acronym_raw VARCHAR(100) NULL,
    funder_identifier_raw VARCHAR(200) NULL,
    country_raw VARCHAR(100) NULL,
    funding_order INTEGER NOT NULL DEFAULT 1,
    provenance_code VARCHAR(32) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX uk_cat_publication_funding_org_grant ON cat_publication_funding (publication_id, organization_id, grant_id);
CREATE INDEX idx_cat_publication_funding_organization ON cat_publication_funding (organization_id);
CREATE INDEX idx_cat_publication_funding_provenance ON cat_publication_funding (provenance_code);
CREATE INDEX idx_cat_publication_funding_publication ON cat_publication_funding (publication_id);
CREATE TRIGGER trg_cat_publication_funding_audit_times
    BEFORE INSERT OR UPDATE ON cat_publication_funding
    FOR EACH ROW EXECUTE FUNCTION set_audit_times();

COMMENT ON TABLE cat_publication_funding IS '文献资助信息关联';
COMMENT ON COLUMN cat_publication_funding.publication_id IS '出版物 ID（外键：cat_publication.id）';
COMMENT ON COLUMN cat_publication_funding.organization_id IS '资助机构 ID（外键：cat_organization.id，匹配后填充）';
COMMENT ON COLUMN cat_publication_funding.grant_id IS '资助项目编号/授权号';
COMMENT ON COLUMN cat_publication_funding.funder_name_raw IS '资助机构原始名称（来自数据源）';
COMMENT ON COLUMN cat_publication_funding.funder_acronym_raw IS '资助机构缩写原始值（如 NIH, NSF, NSFC）';
COMMENT ON COLUMN cat_publication_funding.funder_identifier_raw IS '资助机构标识符原始值（FundRef ID/ROR ID 等）';
COMMENT ON COLUMN cat_publication_funding.country_raw IS '国家原始值';
COMMENT ON COLUMN cat_publication_funding.funding_order IS '资助信息顺序（用于排序显示）';
COMMENT ON COLUMN cat_publication_funding.provenance_code IS '数据来源（PUBMED/OPENALEX/CROSSREF 等）';

-- ------------------------------------------------------------
-- cat_pubmed_file
-- ------------------------------------------------------------
CREATE TABLE cat_pubmed_file
(
    id BIGINT NOT NULL PRIMARY KEY,
    file_name VARCHAR(40) NOT NULL,
    file_seq INTEGER NOT NULL,
    status VARCHAR(20) NOT NULL,
    scope VARCHAR(20) NOT NULL,
    created_count INTEGER NOT NULL DEFAULT 0,
    updated_count INTEGER NOT NULL DEFAULT 0,
    deleted_count INTEGER NOT NULL DEFAULT 0,
    skipped_absent_count INTEGER NOT NULL DEFAULT 0,
    skipped_invalid_count INTEGER NOT NULL DEFAULT 0,
    doi_conflict_count INTEGER NOT NULL DEFAULT 0,
    skip_error_count INTEGER NOT NULL DEFAULT 0,
    started_at TIMESTAMPTZ NULL,
    finished_at TIMESTAMPTZ NULL,
    last_error VARCHAR(2000) NULL,
    job_generation VARCHAR(64) NULL,
    kind VARCHAR(10) NOT NULL,
    skipped_stale_count INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX uk_cat_pubmed_file_name ON cat_pubmed_file (file_name);
CREATE INDEX idx_cat_pubmed_file_seq ON cat_pubmed_file (file_seq);
CREATE TRIGGER trg_cat_pubmed_file_audit_times
    BEFORE INSERT OR UPDATE ON cat_pubmed_file
    FOR EACH ROW EXECUTE FUNCTION set_audit_times();

COMMENT ON TABLE cat_pubmed_file IS 'PubMed 文件（baseline 与 updatefiles）处理进度';
COMMENT ON COLUMN cat_pubmed_file.file_name IS 'PubMed 文件名，如 pubmed26n1336.xml.gz，全表唯一';
COMMENT ON COLUMN cat_pubmed_file.file_seq IS '文件序号，续跑按它找下一个文件';
COMMENT ON COLUMN cat_pubmed_file.status IS '处理状态：RUNNING、COMPLETED、FAILED';
COMMENT ON COLUMN cat_pubmed_file.scope IS '导入范围，如只更新已有文献 EXISTING_ONLY';
COMMENT ON COLUMN cat_pubmed_file.created_count IS '本文件新建的文献数';
COMMENT ON COLUMN cat_pubmed_file.updated_count IS '本文件更新的文献数';
COMMENT ON COLUMN cat_pubmed_file.deleted_count IS '本文件删除的文献数';
COMMENT ON COLUMN cat_pubmed_file.skipped_absent_count IS '因库里没有这篇而跳过的数量';
COMMENT ON COLUMN cat_pubmed_file.skipped_invalid_count IS '因记录不合法而跳过的数量';
COMMENT ON COLUMN cat_pubmed_file.doi_conflict_count IS 'DOI 与库里别的文献冲突的数量';
COMMENT ON COLUMN cat_pubmed_file.skip_error_count IS '处理出错跳过的数量';
COMMENT ON COLUMN cat_pubmed_file.started_at IS '开始处理时间';
COMMENT ON COLUMN cat_pubmed_file.finished_at IS '结束处理时间';
COMMENT ON COLUMN cat_pubmed_file.last_error IS '最近一次失败的错误信息';
COMMENT ON COLUMN cat_pubmed_file.job_generation IS '处理这个文件的作业代次，可空';
COMMENT ON COLUMN cat_pubmed_file.kind IS '文件种类（baseline / updatefiles）';
COMMENT ON COLUMN cat_pubmed_file.skipped_stale_count IS '比库里版本旧而跳过的记录数';

-- ------------------------------------------------------------
-- cat_publication_source
-- ------------------------------------------------------------
CREATE TABLE cat_publication_source
(
    id BIGINT NOT NULL PRIMARY KEY,
    pmid VARCHAR(15) NOT NULL,
    source_file VARCHAR(40) NOT NULL,
    source_rank INTEGER NOT NULL,
    raw_xml TEXT COMPRESSION lz4 NULL,
    parsed_at TIMESTAMPTZ NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX uk_cat_publication_source_pmid ON cat_publication_source (pmid);
CREATE TRIGGER trg_cat_publication_source_audit_times
    BEFORE INSERT OR UPDATE ON cat_publication_source
    FOR EACH ROW EXECUTE FUNCTION set_audit_times();

COMMENT ON TABLE cat_publication_source IS '每篇文献最后一次写入（或软删）它的 PubMed 文件位置，以及该文件里这篇的原文；后出现的文件取代先出现的（NLM 规则）';
COMMENT ON COLUMN cat_publication_source.pmid IS 'PMID，全表唯一；墓碑行（只被软删、从没写过原文）也有 PMID';
COMMENT ON COLUMN cat_publication_source.source_file IS '最后写入这篇的 PubMed 文件名';
COMMENT ON COLUMN cat_publication_source.source_rank IS '文件先后：年 × 10000 + 文件序号，据此挡住旧版本';
COMMENT ON COLUMN cat_publication_source.raw_xml IS '该文件里这篇的 <PubmedArticle> 原文，供本地重算；墓碑行为空';
COMMENT ON COLUMN cat_publication_source.parsed_at IS '最近一次按原文解析入库的时间';

-- ------------------------------------------------------------
-- cat_venue_instance
-- ------------------------------------------------------------
CREATE TABLE cat_venue_instance
(
    id BIGINT NOT NULL PRIMARY KEY,
    venue_id BIGINT NOT NULL,
    volume VARCHAR(100) NULL,
    issue VARCHAR(100) NULL,
    edition VARCHAR(100) NULL,
    publication_year SMALLINT NOT NULL,
    publication_month SMALLINT NULL,
    publication_day SMALLINT NULL,
    conference_name VARCHAR(255) NULL,
    conference_start_date DATE NULL,
    conference_end_date DATE NULL,
    conference_location VARCHAR(200) NULL,
    instance_metadata JSONB NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by BIGINT      NULL,
    updated_by BIGINT      NULL,
    ip_address INET        NULL,
    record_remarks JSONB       NULL,
    version BIGINT      NOT NULL DEFAULT 0
);
CREATE INDEX idx_cat_venue_instance_year ON cat_venue_instance (publication_year);
CREATE INDEX idx_cat_venue_instance_venue ON cat_venue_instance (venue_id);
CREATE INDEX idx_cat_venue_instance_volume_issue ON cat_venue_instance (venue_id, volume, issue);
CREATE UNIQUE INDEX uk_cat_venue_instance_identity ON cat_venue_instance (venue_id, volume, issue, edition, conference_name, publication_year) NULLS NOT DISTINCT;
CREATE TRIGGER trg_cat_venue_instance_audit_times
    BEFORE INSERT OR UPDATE ON cat_venue_instance
    FOR EACH ROW EXECUTE FUNCTION set_audit_times();

COMMENT ON TABLE cat_venue_instance IS '载体实例';
COMMENT ON COLUMN cat_venue_instance.venue_id IS '载体 ID（逻辑外键：cat_venue.id）';
COMMENT ON COLUMN cat_venue_instance.volume IS '卷号（如 "45", "2023"，期刊专用）';
COMMENT ON COLUMN cat_venue_instance.issue IS '期号（如 "3", "Suppl 1"，期刊专用）';
COMMENT ON COLUMN cat_venue_instance.edition IS '版次（如 "2nd Edition"，书籍专用）';
COMMENT ON COLUMN cat_venue_instance.publication_year IS '出版年份（必填，1800-2100）';
COMMENT ON COLUMN cat_venue_instance.publication_month IS '出版月份（1-12，可选）';
COMMENT ON COLUMN cat_venue_instance.publication_day IS '出版日期（1-31，可选）';
COMMENT ON COLUMN cat_venue_instance.conference_name IS '会议名称（会议专用）';
COMMENT ON COLUMN cat_venue_instance.conference_start_date IS '会议开始日期（会议专用）';
COMMENT ON COLUMN cat_venue_instance.conference_end_date IS '会议结束日期（会议专用）';
COMMENT ON COLUMN cat_venue_instance.conference_location IS '会议地点（会议专用）';
COMMENT ON COLUMN cat_venue_instance.instance_metadata IS '实例元数据（JSON 格式，灵活扩展）';
