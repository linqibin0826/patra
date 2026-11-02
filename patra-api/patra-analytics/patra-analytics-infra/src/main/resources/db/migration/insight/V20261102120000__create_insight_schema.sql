-- insight 子域：热读与共现的快照（PAP-170）。只存 catalog 的文献 ID，不建跨库外键；两张快照表都是整表替换
CREATE SCHEMA insight;

-- 热读快照：上榜的（ranked，position 1..100）在前，最新收录补位的接在后面
CREATE TABLE insight.hot_reading (
    position     SMALLINT          PRIMARY KEY,          -- 1 起，页面按它排
    article_id   BIGINT            NOT NULL UNIQUE,
    ranked       BOOLEAN           NOT NULL,             -- false = 最新收录补位
    score        DOUBLE PRECISION,                       -- 补位为 NULL
    readers_7d   INTEGER                                 -- 补位为 NULL
);

-- 共现快照：每篇最多 20 行
CREATE TABLE insight.co_reading (
    article_id          BIGINT            NOT NULL,
    position            SMALLINT          NOT NULL,      -- 1..20
    related_article_id  BIGINT            NOT NULL,
    similarity          DOUBLE PRECISION  NOT NULL,
    co_readers          INTEGER           NOT NULL,      -- 只供排查，不对外
    PRIMARY KEY (article_id, position)
);

-- 每种快照最近一次成功计算的时间
CREATE TABLE insight.snapshot_run (
    kind         VARCHAR(16)  PRIMARY KEY,               -- HOT_READING / CO_READING
    computed_at  TIMESTAMPTZ  NOT NULL,
    row_count    INTEGER      NOT NULL
);
