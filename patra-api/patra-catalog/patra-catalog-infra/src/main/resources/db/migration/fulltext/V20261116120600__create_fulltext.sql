-- fulltext 子域：PMC 开放获取全文的抓取解析状态与正文
-- 引用关系由应用保证，不建外键

-- ------------------------------------------------------------
-- cat_publication_fulltext
-- ------------------------------------------------------------
CREATE TABLE cat_publication_fulltext
(
    id BIGINT NOT NULL PRIMARY KEY,
    publication_id BIGINT NOT NULL,
    pmcid VARCHAR(20) NOT NULL,
    pmc_version VARCHAR(30) NULL,
    status VARCHAR(20) NOT NULL,
    unavailable_reason VARCHAR(30) NULL,
    license_code VARCHAR(30) NULL,
    is_manuscript BOOLEAN NULL,
    object_prefix VARCHAR(200) NULL,
    xml_md5 CHAR(32) NULL,
    pdf_object_key VARCHAR(300) NULL,
    pdf_size BIGINT NULL,
    pdf_md5 CHAR(32) NULL,
    media JSONB NOT NULL DEFAULT '[]'::jsonb,
    fetched_at TIMESTAMPTZ NULL,
    parser_version INTEGER NULL,
    parsed_at TIMESTAMPTZ NULL,
    word_count INTEGER NULL,
    attempt_count INTEGER NOT NULL DEFAULT 0,
    last_error VARCHAR(2000) NULL,
    next_attempt_at TIMESTAMPTZ NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by BIGINT      NULL,
    updated_by BIGINT      NULL,
    ip_address INET        NULL,
    record_remarks JSONB       NULL,
    version BIGINT      NOT NULL DEFAULT 0
);
CREATE UNIQUE INDEX uk_cat_publication_fulltext_publication ON cat_publication_fulltext (publication_id);
CREATE INDEX idx_cat_publication_fulltext_pmcid ON cat_publication_fulltext (pmcid);
CREATE INDEX idx_cat_publication_fulltext_status_next ON cat_publication_fulltext (status, next_attempt_at);
CREATE INDEX idx_cat_publication_fulltext_status_parser ON cat_publication_fulltext (status, parser_version);
CREATE TRIGGER trg_cat_publication_fulltext_audit_times
    BEFORE INSERT OR UPDATE ON cat_publication_fulltext
    FOR EACH ROW EXECUTE FUNCTION set_audit_times();

COMMENT ON TABLE cat_publication_fulltext IS '全文记录';
COMMENT ON COLUMN cat_publication_fulltext.publication_id IS '所属文献（cat_publication.id），全表唯一';
COMMENT ON COLUMN cat_publication_fulltext.pmcid IS 'PMC 编号，如 PMC13583816';
COMMENT ON COLUMN cat_publication_fulltext.pmc_version IS 'PMC 版本号，如 PMC13583816.1';
COMMENT ON COLUMN cat_publication_fulltext.status IS '全文状态：待抓取、已抓取、已解析、抓取失败、解析失败、没有正文、不可用等';
COMMENT ON COLUMN cat_publication_fulltext.unavailable_reason IS '不可用的原因，只在不可用时有值';
COMMENT ON COLUMN cat_publication_fulltext.license_code IS '许可协议，如 CC BY';
COMMENT ON COLUMN cat_publication_fulltext.is_manuscript IS '是否作者手稿';
COMMENT ON COLUMN cat_publication_fulltext.object_prefix IS '全文文件在对象存储里的前缀';
COMMENT ON COLUMN cat_publication_fulltext.xml_md5 IS '全文 XML 的 MD5';
COMMENT ON COLUMN cat_publication_fulltext.pdf_object_key IS 'PDF 的对象键';
COMMENT ON COLUMN cat_publication_fulltext.pdf_size IS 'PDF 大小（字节）';
COMMENT ON COLUMN cat_publication_fulltext.pdf_md5 IS 'PDF 的 MD5';
COMMENT ON COLUMN cat_publication_fulltext.media IS '图片清单（文件名、宽、高），JSON 数组';
COMMENT ON COLUMN cat_publication_fulltext.fetched_at IS '抓取完成时间';
COMMENT ON COLUMN cat_publication_fulltext.parser_version IS '解析器版本，落后于当前版本的会重新解析';
COMMENT ON COLUMN cat_publication_fulltext.parsed_at IS '解析完成时间';
COMMENT ON COLUMN cat_publication_fulltext.word_count IS '正文字数';
COMMENT ON COLUMN cat_publication_fulltext.attempt_count IS '抓取或解析的尝试次数';
COMMENT ON COLUMN cat_publication_fulltext.last_error IS '最近一次失败的错误信息';
COMMENT ON COLUMN cat_publication_fulltext.next_attempt_at IS '下次重试时间';

-- ------------------------------------------------------------
-- cat_publication_fulltext_body
-- ------------------------------------------------------------
CREATE TABLE cat_publication_fulltext_body
(
    id BIGINT NOT NULL PRIMARY KEY,
    publication_id BIGINT NOT NULL,
    body JSONB NOT NULL,
    parser_version INTEGER NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX uk_cat_publication_fulltext_body_publication ON cat_publication_fulltext_body (publication_id);
CREATE TRIGGER trg_cat_publication_fulltext_body_audit_times
    BEFORE INSERT OR UPDATE ON cat_publication_fulltext_body
    FOR EACH ROW EXECUTE FUNCTION set_audit_times();

COMMENT ON TABLE cat_publication_fulltext_body IS '解析后的正文，一篇文献一行；只按文献整篇读，不在 JSON 里查询';
COMMENT ON COLUMN cat_publication_fulltext_body.publication_id IS '所属文献（cat_publication.id），全表唯一';
COMMENT ON COLUMN cat_publication_fulltext_body.body IS '正文结构，JSON';
COMMENT ON COLUMN cat_publication_fulltext_body.parser_version IS '生成这份正文的解析器版本';
