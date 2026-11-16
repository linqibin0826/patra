-- venue 子域：出版载体（期刊等）及其标识符、统计、MeSH、关系、收录历史、评级
-- 引用关系由应用保证，不建外键

-- ------------------------------------------------------------
-- cat_venue
-- ------------------------------------------------------------
CREATE TABLE cat_venue
(
    id BIGINT NOT NULL PRIMARY KEY,
    venue_type VARCHAR(32) NOT NULL,
    title VARCHAR(500) NOT NULL,
    provenance_code VARCHAR(32) NOT NULL,
    last_synced_at TIMESTAMPTZ NULL,
    nlm_id VARCHAR(20) NULL,
    issn_l VARCHAR(10) NULL,
    openalex_id VARCHAR(50) NULL,
    abbreviated_title VARCHAR(200) NULL,
    primary_language VARCHAR(10) NULL,
    country_code VARCHAR(2) NULL,
    image_object_key VARCHAR(512) NULL,
    cited_by_count INTEGER NULL,
    publication_profile JSONB NULL,
    citation_metrics JSONB NULL,
    open_access JSONB NULL,
    affiliated_societies JSONB NULL,
    deleted_at TIMESTAMPTZ NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by BIGINT      NULL,
    updated_by BIGINT      NULL,
    ip_address INET        NULL,
    record_remarks JSONB       NULL,
    version BIGINT      NOT NULL DEFAULT 0,
    CONSTRAINT ck_cat_venue_country_code CHECK (((country_code IS NULL) OR ((country_code)::text ~ '^[A-Z]{2}$'::text)))
);
CREATE UNIQUE INDEX uk_cat_venue_nlm ON cat_venue (nlm_id);
CREATE INDEX idx_cat_venue_cited_by_count ON cat_venue (cited_by_count);
CREATE INDEX idx_cat_venue_provenance ON cat_venue (provenance_code);
CREATE INDEX idx_cat_venue_title ON cat_venue (title);
CREATE INDEX idx_cat_venue_type ON cat_venue (venue_type);
CREATE TRIGGER trg_cat_venue_audit_times
    BEFORE INSERT OR UPDATE ON cat_venue
    FOR EACH ROW EXECUTE FUNCTION set_audit_times();

COMMENT ON TABLE cat_venue IS '出版载体';
COMMENT ON COLUMN cat_venue.venue_type IS '载体类型：JOURNAL/REPOSITORY/CONFERENCE/EBOOK_PLATFORM/BOOK_SERIES/METADATA/OTHER';
COMMENT ON COLUMN cat_venue.title IS '载体标题（主名称）';
COMMENT ON COLUMN cat_venue.provenance_code IS '数据来源代码：OPENALEX/PUBMED/CROSSREF/DOAJ/MANUAL';
COMMENT ON COLUMN cat_venue.last_synced_at IS '最后同步时间（UTC）';
COMMENT ON COLUMN cat_venue.nlm_id IS 'NLM 唯一标识符';
COMMENT ON COLUMN cat_venue.issn_l IS 'Linking ISSN';
COMMENT ON COLUMN cat_venue.openalex_id IS 'OpenAlex Source ID';
COMMENT ON COLUMN cat_venue.abbreviated_title IS '缩写标题';
COMMENT ON COLUMN cat_venue.primary_language IS '主要语言代码（BCP 47）';
COMMENT ON COLUMN cat_venue.country_code IS '国家代码（ISO 3166-1 alpha-2）';
COMMENT ON COLUMN cat_venue.image_object_key IS '封面图片对象存储键（可空，指向 venue-cover 桶中的对象键）';
COMMENT ON COLUMN cat_venue.cited_by_count IS '被引用总次数（来自 OpenAlex CitationMetrics，快速访问字段）';
COMMENT ON COLUMN cat_venue.publication_profile IS '出版概况（出版历史、语言、宿主机构、索引信息等）';
COMMENT ON COLUMN cat_venue.citation_metrics IS '引用指标（works_count、cited_by_count、h_index 等）';
COMMENT ON COLUMN cat_venue.open_access IS '开放获取信息（OA 状态 + APC 定价）';
COMMENT ON COLUMN cat_venue.affiliated_societies IS '关联学会列表';
COMMENT ON COLUMN cat_venue.deleted_at IS '删除时间，空表示未删除';

-- ------------------------------------------------------------
-- cat_venue_identifier
-- ------------------------------------------------------------
CREATE TABLE cat_venue_identifier
(
    id BIGINT NOT NULL PRIMARY KEY,
    venue_id BIGINT NOT NULL,
    identifier_type VARCHAR(32) NOT NULL,
    identifier_value VARCHAR(255) NOT NULL,
    is_primary BOOLEAN NOT NULL DEFAULT false,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX uk_cat_venue_identifier_type_value ON cat_venue_identifier (venue_id, identifier_type, identifier_value);
CREATE INDEX idx_cat_venue_identifier_type_value ON cat_venue_identifier (identifier_type, identifier_value);
CREATE UNIQUE INDEX uk_cat_venue_identifier_nlm ON cat_venue_identifier (identifier_value) WHERE ((identifier_type)::text = 'NLM'::text);
CREATE TRIGGER trg_cat_venue_identifier_audit_times
    BEFORE INSERT OR UPDATE ON cat_venue_identifier
    FOR EACH ROW EXECUTE FUNCTION set_audit_times();

COMMENT ON TABLE cat_venue_identifier IS '载体标识符';
COMMENT ON COLUMN cat_venue_identifier.venue_id IS '载体 ID（外键：cat_venue.id）';
COMMENT ON COLUMN cat_venue_identifier.identifier_type IS '标识符类型：OPENALEX/ISSN/ISSN_L/ISBN/NLM/MAG/FATCAT/WIKIDATA/CODEN';
COMMENT ON COLUMN cat_venue_identifier.identifier_value IS '标识符值';
COMMENT ON COLUMN cat_venue_identifier.is_primary IS '是否首选标识符（同类型中，0=否，1=是）';

-- ------------------------------------------------------------
-- cat_venue_publication_stats
-- ------------------------------------------------------------
CREATE TABLE cat_venue_publication_stats
(
    id BIGINT NOT NULL PRIMARY KEY,
    venue_id BIGINT NOT NULL,
    year SMALLINT NOT NULL,
    works_count INTEGER NOT NULL DEFAULT 0,
    cited_by_count INTEGER NOT NULL DEFAULT 0,
    oa_works_count INTEGER NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX uk_cat_venue_publication_stats_venue_year ON cat_venue_publication_stats (venue_id, year);
CREATE INDEX idx_cat_venue_publication_stats_cited_by_count ON cat_venue_publication_stats (cited_by_count);
CREATE INDEX idx_cat_venue_publication_stats_works_count ON cat_venue_publication_stats (works_count);
CREATE INDEX idx_cat_venue_publication_stats_year ON cat_venue_publication_stats (year);
CREATE TRIGGER trg_cat_venue_publication_stats_audit_times
    BEFORE INSERT OR UPDATE ON cat_venue_publication_stats
    FOR EACH ROW EXECUTE FUNCTION set_audit_times();

COMMENT ON TABLE cat_venue_publication_stats IS '载体年度发文统计';
COMMENT ON COLUMN cat_venue_publication_stats.venue_id IS '载体 ID（外键：cat_venue.id）';
COMMENT ON COLUMN cat_venue_publication_stats.year IS '统计年份（1900-2100）';
COMMENT ON COLUMN cat_venue_publication_stats.works_count IS '该年发表作品数量';
COMMENT ON COLUMN cat_venue_publication_stats.cited_by_count IS '该年被引用次数';
COMMENT ON COLUMN cat_venue_publication_stats.oa_works_count IS '该年 OA 作品数量（可选）';

-- ------------------------------------------------------------
-- cat_venue_mesh
-- ------------------------------------------------------------
CREATE TABLE cat_venue_mesh
(
    id BIGINT NOT NULL PRIMARY KEY,
    venue_id BIGINT NOT NULL,
    descriptor_name VARCHAR(255) NOT NULL,
    descriptor_ui VARCHAR(20) NULL,
    is_major_topic BOOLEAN NOT NULL DEFAULT false,
    qualifier_name VARCHAR(100) NULL,
    qualifier_ui VARCHAR(20) NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX uk_cat_venue_mesh_venue_descriptor ON cat_venue_mesh (venue_id, descriptor_name);
CREATE INDEX idx_cat_venue_mesh_descriptor ON cat_venue_mesh (descriptor_ui);
CREATE INDEX idx_cat_venue_mesh_is_major ON cat_venue_mesh (is_major_topic);
CREATE INDEX idx_cat_venue_mesh_venue ON cat_venue_mesh (venue_id);
CREATE TRIGGER trg_cat_venue_mesh_audit_times
    BEFORE INSERT OR UPDATE ON cat_venue_mesh
    FOR EACH ROW EXECUTE FUNCTION set_audit_times();

COMMENT ON TABLE cat_venue_mesh IS '载体 MeSH 主题词';
COMMENT ON COLUMN cat_venue_mesh.venue_id IS '载体 ID（外键：cat_venue.id）';
COMMENT ON COLUMN cat_venue_mesh.descriptor_name IS 'MeSH 描述符名称';
COMMENT ON COLUMN cat_venue_mesh.descriptor_ui IS 'MeSH 描述符唯一标识符（格式：D000001）';
COMMENT ON COLUMN cat_venue_mesh.is_major_topic IS '是否主要主题（0=否，1=是）';
COMMENT ON COLUMN cat_venue_mesh.qualifier_name IS 'MeSH 限定符名称';
COMMENT ON COLUMN cat_venue_mesh.qualifier_ui IS 'MeSH 限定符唯一标识符（格式：Q000001）';

-- ------------------------------------------------------------
-- cat_venue_relation
-- ------------------------------------------------------------
CREATE TABLE cat_venue_relation
(
    id BIGINT NOT NULL PRIMARY KEY,
    venue_id BIGINT NOT NULL,
    related_venue_id BIGINT NULL,
    related_nlm_id VARCHAR(20) NULL,
    related_title VARCHAR(500) NOT NULL,
    relation_type VARCHAR(32) NOT NULL,
    effective_date DATE NULL,
    notes VARCHAR(500) NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX uk_cat_venue_relation_related_type ON cat_venue_relation (venue_id, related_nlm_id, relation_type);
CREATE INDEX idx_cat_venue_relation_related_venue ON cat_venue_relation (related_venue_id);
CREATE INDEX idx_cat_venue_relation_type ON cat_venue_relation (relation_type);
CREATE INDEX idx_cat_venue_relation_venue ON cat_venue_relation (venue_id);
CREATE TRIGGER trg_cat_venue_relation_audit_times
    BEFORE INSERT OR UPDATE ON cat_venue_relation
    FOR EACH ROW EXECUTE FUNCTION set_audit_times();

COMMENT ON TABLE cat_venue_relation IS '载体关联关系';
COMMENT ON COLUMN cat_venue_relation.venue_id IS '载体 ID（外键：cat_venue.id）';
COMMENT ON COLUMN cat_venue_relation.related_venue_id IS '关联期刊 ID（如果已在系统中）';
COMMENT ON COLUMN cat_venue_relation.related_nlm_id IS '关联期刊 NLM ID';
COMMENT ON COLUMN cat_venue_relation.related_title IS '关联期刊标题';
COMMENT ON COLUMN cat_venue_relation.relation_type IS '关系类型：PRECEDING/SUCCEEDING/ABSORBED/ABSORBED_BY/MERGED/SPLIT_FROM 等';
COMMENT ON COLUMN cat_venue_relation.effective_date IS '生效日期';
COMMENT ON COLUMN cat_venue_relation.notes IS '备注说明';

-- ------------------------------------------------------------
-- cat_venue_indexing_history
-- ------------------------------------------------------------
CREATE TABLE cat_venue_indexing_history
(
    id BIGINT NOT NULL PRIMARY KEY,
    venue_id BIGINT NOT NULL,
    indexing_source VARCHAR(32) NOT NULL,
    currently_indexed BOOLEAN NOT NULL DEFAULT false,
    indexing_treatment VARCHAR(20) NULL,
    citation_subset VARCHAR(20) NULL,
    start_year SMALLINT NULL,
    start_volume VARCHAR(20) NULL,
    start_issue VARCHAR(20) NULL,
    end_year SMALLINT NULL,
    end_volume VARCHAR(20) NULL,
    end_issue VARCHAR(20) NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX uk_cat_venue_indexing_history_source_start ON cat_venue_indexing_history (venue_id, indexing_source, start_year);
CREATE INDEX idx_cat_venue_indexing_history_citation_subset ON cat_venue_indexing_history (citation_subset);
CREATE INDEX idx_cat_venue_indexing_history_venue ON cat_venue_indexing_history (venue_id);
CREATE INDEX idx_cat_venue_indexing_history_source_indexed ON cat_venue_indexing_history (indexing_source, currently_indexed);
CREATE TRIGGER trg_cat_venue_indexing_history_audit_times
    BEFORE INSERT OR UPDATE ON cat_venue_indexing_history
    FOR EACH ROW EXECUTE FUNCTION set_audit_times();

COMMENT ON TABLE cat_venue_indexing_history IS '载体索引历史';
COMMENT ON COLUMN cat_venue_indexing_history.venue_id IS '载体 ID（外键：cat_venue.id）';
COMMENT ON COLUMN cat_venue_indexing_history.indexing_source IS '索引来源：MEDLINE/PMC 等';
COMMENT ON COLUMN cat_venue_indexing_history.currently_indexed IS '当前是否被索引（0=否，1=是）';
COMMENT ON COLUMN cat_venue_indexing_history.indexing_treatment IS '索引处理方式：FULL/SELECTIVE';
COMMENT ON COLUMN cat_venue_indexing_history.citation_subset IS '引用子集：IM/AIM/N/D/H/K/T/E/S/X/B/C/F/Q';
COMMENT ON COLUMN cat_venue_indexing_history.start_year IS '索引开始年份';
COMMENT ON COLUMN cat_venue_indexing_history.start_volume IS '索引开始卷号';
COMMENT ON COLUMN cat_venue_indexing_history.start_issue IS '索引开始期号';
COMMENT ON COLUMN cat_venue_indexing_history.end_year IS '索引结束年份（仍在索引则为 null）';
COMMENT ON COLUMN cat_venue_indexing_history.end_volume IS '索引结束卷号';
COMMENT ON COLUMN cat_venue_indexing_history.end_issue IS '索引结束期号';

-- ------------------------------------------------------------
-- cat_venue_jcr_rating
-- ------------------------------------------------------------
CREATE TABLE cat_venue_jcr_rating
(
    id BIGINT NOT NULL PRIMARY KEY,
    venue_id BIGINT NOT NULL,
    year SMALLINT NOT NULL,
    impact_factor NUMERIC(10,4) NULL,
    wos_overall_quartile VARCHAR(10) NULL,
    subject VARCHAR(100) NULL,
    collection VARCHAR(10) NULL,
    jif_quartile VARCHAR(10) NULL,
    jif_rank VARCHAR(20) NULL,
    jif_percentile NUMERIC(5,2) NULL,
    jci_subject VARCHAR(100) NULL,
    jci_collection VARCHAR(10) NULL,
    jci_quartile VARCHAR(10) NULL,
    jci_rank VARCHAR(20) NULL,
    jci_percentile NUMERIC(5,2) NULL,
    jci_value NUMERIC(10,4) NULL,
    self_citation_rate NUMERIC(5,2) NULL,
    research_direction VARCHAR(200) NULL,
    source_url VARCHAR(500) NULL,
    fetched_at TIMESTAMPTZ NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX uk_cat_venue_jcr_rating_venue_year ON cat_venue_jcr_rating (venue_id, year);
CREATE INDEX idx_cat_venue_jcr_rating_impact_factor ON cat_venue_jcr_rating (impact_factor);
CREATE INDEX idx_cat_venue_jcr_rating_venue ON cat_venue_jcr_rating (venue_id);
CREATE INDEX idx_cat_venue_jcr_rating_jif_quartile ON cat_venue_jcr_rating (jif_quartile);
CREATE TRIGGER trg_cat_venue_jcr_rating_audit_times
    BEFORE INSERT OR UPDATE ON cat_venue_jcr_rating
    FOR EACH ROW EXECUTE FUNCTION set_audit_times();

COMMENT ON TABLE cat_venue_jcr_rating IS 'JCR 期刊评级';
COMMENT ON COLUMN cat_venue_jcr_rating.venue_id IS '关联期刊 ID（逻辑外键 → cat_venue.id）';
COMMENT ON COLUMN cat_venue_jcr_rating.year IS '评级年份（2000-2100）';
COMMENT ON COLUMN cat_venue_jcr_rating.impact_factor IS 'JIF 影响因子';
COMMENT ON COLUMN cat_venue_jcr_rating.wos_overall_quartile IS 'WOS 综合分区等级（1区-4区，LetPub 基于 JIF + JCI 综合评定）';
COMMENT ON COLUMN cat_venue_jcr_rating.subject IS 'JIF 学科分类（如 "MULTIDISCIPLINARY SCIENCES"）';
COMMENT ON COLUMN cat_venue_jcr_rating.collection IS 'JIF 收录集（SCIE/SSCI/AHCI）';
COMMENT ON COLUMN cat_venue_jcr_rating.jif_quartile IS 'JIF 分区（Q1-Q4）';
COMMENT ON COLUMN cat_venue_jcr_rating.jif_rank IS 'JIF 排名（如 "2/136"）';
COMMENT ON COLUMN cat_venue_jcr_rating.jif_percentile IS 'JIF 学科百分位（0.00-100.00，如 99.00）';
COMMENT ON COLUMN cat_venue_jcr_rating.jci_subject IS 'JCI 学科分类（多数情况下同 subject）';
COMMENT ON COLUMN cat_venue_jcr_rating.jci_collection IS 'JCI 收录集（多数情况下同 collection）';
COMMENT ON COLUMN cat_venue_jcr_rating.jci_quartile IS 'JCI 分区';
COMMENT ON COLUMN cat_venue_jcr_rating.jci_rank IS 'JCI 排名';
COMMENT ON COLUMN cat_venue_jcr_rating.jci_percentile IS 'JCI 学科百分位（0.00-100.00，如 98.90）';
COMMENT ON COLUMN cat_venue_jcr_rating.jci_value IS 'JCI 数值（Journal Citation Indicator 本身的数值，如 11.14）';
COMMENT ON COLUMN cat_venue_jcr_rating.self_citation_rate IS '自引率（0.00-100.00，如 1.60；Clarivate 年度指标，LetPub 仅提供最新年值）';
COMMENT ON COLUMN cat_venue_jcr_rating.research_direction IS '研究方向/学科领域';
COMMENT ON COLUMN cat_venue_jcr_rating.source_url IS '数据来源 URL';
COMMENT ON COLUMN cat_venue_jcr_rating.fetched_at IS '数据抓取时间（UTC，微秒精度）';

-- ------------------------------------------------------------
-- cat_venue_cas_rating
-- ------------------------------------------------------------
CREATE TABLE cat_venue_cas_rating
(
    id BIGINT NOT NULL PRIMARY KEY,
    venue_id BIGINT NOT NULL,
    year SMALLINT NOT NULL,
    edition VARCHAR(20) NOT NULL,
    major_category VARCHAR(50) NULL,
    major_quartile VARCHAR(10) NULL,
    minor_subject VARCHAR(100) NULL,
    minor_quartile VARCHAR(10) NULL,
    is_top_journal BOOLEAN NULL,
    is_review_journal BOOLEAN NULL,
    source_url VARCHAR(500) NULL,
    fetched_at TIMESTAMPTZ NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX uk_cat_venue_cas_rating_venue_year_edition ON cat_venue_cas_rating (venue_id, year, edition);
CREATE INDEX idx_cat_venue_cas_rating_venue ON cat_venue_cas_rating (venue_id);
CREATE INDEX idx_cat_venue_cas_rating_is_top_journal ON cat_venue_cas_rating (is_top_journal);
CREATE INDEX idx_cat_venue_cas_rating_major_quartile ON cat_venue_cas_rating (major_quartile);
CREATE TRIGGER trg_cat_venue_cas_rating_audit_times
    BEFORE INSERT OR UPDATE ON cat_venue_cas_rating
    FOR EACH ROW EXECUTE FUNCTION set_audit_times();

COMMENT ON TABLE cat_venue_cas_rating IS 'CAS 中科院分区';
COMMENT ON COLUMN cat_venue_cas_rating.venue_id IS '关联期刊 ID（逻辑外键 → cat_venue.id）';
COMMENT ON COLUMN cat_venue_cas_rating.year IS '分区年份（2000-2100）';
COMMENT ON COLUMN cat_venue_cas_rating.edition IS 'CAS 版本名称（升级版/新锐版/基础版）';
COMMENT ON COLUMN cat_venue_cas_rating.major_category IS '大类学科（如"医学"）';
COMMENT ON COLUMN cat_venue_cas_rating.major_quartile IS '大类分区（1区-4区）';
COMMENT ON COLUMN cat_venue_cas_rating.minor_subject IS '小类学科（如"肿瘤学"）';
COMMENT ON COLUMN cat_venue_cas_rating.minor_quartile IS '小类分区（1区-4区）';
COMMENT ON COLUMN cat_venue_cas_rating.is_top_journal IS '是否为 Top 期刊';
COMMENT ON COLUMN cat_venue_cas_rating.is_review_journal IS '是否为综述期刊';
COMMENT ON COLUMN cat_venue_cas_rating.source_url IS '数据来源 URL';
COMMENT ON COLUMN cat_venue_cas_rating.fetched_at IS '数据抓取时间（UTC，微秒精度）';

-- ------------------------------------------------------------
-- cat_venue_scopus_rating
-- ------------------------------------------------------------
CREATE TABLE cat_venue_scopus_rating
(
    id BIGINT NOT NULL PRIMARY KEY,
    venue_id BIGINT NOT NULL,
    year SMALLINT NOT NULL,
    cite_score NUMERIC(10,4) NULL,
    cite_score_tracker NUMERIC(10,4) NULL,
    sjr NUMERIC(10,4) NULL,
    snip NUMERIC(10,4) NULL,
    document_count INTEGER NULL,
    citation_count INTEGER NULL,
    percent_cited NUMERIC(5,2) NULL,
    subject_area VARCHAR(200) NULL,
    quartile VARCHAR(5) NULL,
    percentile NUMERIC(5,2) NULL,
    scopus_source_id VARCHAR(20) NULL,
    source_url VARCHAR(500) NULL,
    fetched_at TIMESTAMPTZ NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX uk_cat_venue_scopus_rating_venue_year ON cat_venue_scopus_rating (venue_id, year);
CREATE INDEX idx_cat_venue_scopus_rating_cite_score ON cat_venue_scopus_rating (cite_score);
CREATE INDEX idx_cat_venue_scopus_rating_quartile ON cat_venue_scopus_rating (quartile);
CREATE INDEX idx_cat_venue_scopus_rating_venue ON cat_venue_scopus_rating (venue_id);
CREATE TRIGGER trg_cat_venue_scopus_rating_audit_times
    BEFORE INSERT OR UPDATE ON cat_venue_scopus_rating
    FOR EACH ROW EXECUTE FUNCTION set_audit_times();

COMMENT ON TABLE cat_venue_scopus_rating IS 'Scopus 期刊指标';
COMMENT ON COLUMN cat_venue_scopus_rating.venue_id IS '关联期刊 ID（逻辑外键 → cat_venue.id）';
COMMENT ON COLUMN cat_venue_scopus_rating.year IS '指标年份（2000-2100）';
COMMENT ON COLUMN cat_venue_scopus_rating.cite_score IS 'CiteScore';
COMMENT ON COLUMN cat_venue_scopus_rating.cite_score_tracker IS 'CiteScore Tracker（当年预估值）';
COMMENT ON COLUMN cat_venue_scopus_rating.sjr IS 'SCImago Journal Rank';
COMMENT ON COLUMN cat_venue_scopus_rating.snip IS 'Source Normalized Impact per Paper';
COMMENT ON COLUMN cat_venue_scopus_rating.document_count IS '该年发文量';
COMMENT ON COLUMN cat_venue_scopus_rating.citation_count IS '该年被引次数';
COMMENT ON COLUMN cat_venue_scopus_rating.percent_cited IS '被引文献百分比';
COMMENT ON COLUMN cat_venue_scopus_rating.subject_area IS '主 ASJC 学科领域';
COMMENT ON COLUMN cat_venue_scopus_rating.quartile IS 'CiteScore 分区（Q1-Q4）';
COMMENT ON COLUMN cat_venue_scopus_rating.percentile IS '学科内百分位排名';
COMMENT ON COLUMN cat_venue_scopus_rating.scopus_source_id IS 'Scopus Source ID';
COMMENT ON COLUMN cat_venue_scopus_rating.source_url IS '数据来源 URL';
COMMENT ON COLUMN cat_venue_scopus_rating.fetched_at IS '数据抓取时间（UTC，微秒精度）';

-- ------------------------------------------------------------
-- cat_venue_cas_warning
-- ------------------------------------------------------------
CREATE TABLE cat_venue_cas_warning
(
    id BIGINT NOT NULL PRIMARY KEY,
    venue_id BIGINT NOT NULL,
    published_year SMALLINT NOT NULL,
    published_month SMALLINT NULL,
    edition_label VARCHAR(30) NOT NULL,
    in_warning_list BOOLEAN NOT NULL,
    warning_level VARCHAR(10) NULL,
    raw_text VARCHAR(500) NULL,
    source_url VARCHAR(500) NULL,
    fetched_at TIMESTAMPTZ NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX uk_cat_venue_cas_warning_venue_year_edition ON cat_venue_cas_warning (venue_id, published_year, edition_label);
CREATE INDEX idx_cat_venue_cas_warning_venue ON cat_venue_cas_warning (venue_id);
CREATE INDEX idx_cat_venue_cas_warning_in_warning ON cat_venue_cas_warning (in_warning_list);
CREATE INDEX idx_cat_venue_cas_warning_published_year ON cat_venue_cas_warning (published_year);
CREATE INDEX idx_cat_venue_cas_warning_level ON cat_venue_cas_warning (warning_level);
CREATE TRIGGER trg_cat_venue_cas_warning_audit_times
    BEFORE INSERT OR UPDATE ON cat_venue_cas_warning
    FOR EACH ROW EXECUTE FUNCTION set_audit_times();

COMMENT ON TABLE cat_venue_cas_warning IS 'CAS 中科院期刊预警名单';
COMMENT ON COLUMN cat_venue_cas_warning.venue_id IS '关联期刊 ID（逻辑外键 → cat_venue.id）';
COMMENT ON COLUMN cat_venue_cas_warning.published_year IS '预警名单发布年份（2020-2100）';
COMMENT ON COLUMN cat_venue_cas_warning.published_month IS '预警名单发布月份 1-12（可空）';
COMMENT ON COLUMN cat_venue_cas_warning.edition_label IS '原始版本标签：新锐学术版 / 2025版 / 2024版 / 2023版 等';
COMMENT ON COLUMN cat_venue_cas_warning.in_warning_list IS '是否在预警名单中：true=预警，false=正常';
COMMENT ON COLUMN cat_venue_cas_warning.warning_level IS '预警级别（HIGH/MEDIUM/LOW，仅当 inWarningList=true 时可能有值）';
COMMENT ON COLUMN cat_venue_cas_warning.raw_text IS '原始描述文本（保留 LetPub 页面原句以便追溯）';
COMMENT ON COLUMN cat_venue_cas_warning.source_url IS '数据来源 URL';
COMMENT ON COLUMN cat_venue_cas_warning.fetched_at IS '数据抓取时间（UTC，微秒精度）';
