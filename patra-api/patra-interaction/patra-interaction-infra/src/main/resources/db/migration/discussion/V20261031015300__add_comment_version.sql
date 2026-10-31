-- ============================================================
-- discussion 子域：评论正文版本（PAP-141）
-- 发表为 1，每次正文确有变化加 1（发表 5 分钟内的原地修改也算），和正文写在同一条 UPDATE 里。
-- 集成事件带上它，消费方按它丢弃晚到的旧编辑；编辑时间在拿行锁前取，并发编辑时会和提交顺序相反，不能代替它。
-- 已有的评论都算版本 1。
-- ============================================================

ALTER TABLE discussion.comment
    ADD COLUMN version INTEGER NOT NULL DEFAULT 1;

ALTER TABLE discussion.comment
    ADD CONSTRAINT ck_comment_version CHECK (version > 0);

COMMENT ON COLUMN discussion.comment.version IS 'Body version: 1 on publish, +1 on every effective edit; carried by integration events so consumers drop stale edits';
