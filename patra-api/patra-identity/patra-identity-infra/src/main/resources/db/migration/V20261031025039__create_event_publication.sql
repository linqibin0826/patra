-- 事件发布登记表（PAP-104）：安全邮件在业务事务里登记成一行，提交后由事件模块外送到 RabbitMQ（identity.events），
-- broker 确认收下即删行（completion-mode: delete，安全邮件带一次性令牌链接，不留历史）；没发出去的行由补投任务每分钟重发。
-- 表名与列名是 Spring Modulith 2.1.2 JDBC 第二版结构（schemas/v2/schema-postgresql.sql），由框架固定，不能改、不加 idn_ 前缀。
CREATE TABLE event_publication
(
    id                     UUID                     NOT NULL PRIMARY KEY,
    listener_id            TEXT                     NOT NULL,
    event_type             TEXT                     NOT NULL,
    serialized_event       TEXT                     NOT NULL,
    publication_date       TIMESTAMP WITH TIME ZONE NOT NULL,
    completion_date        TIMESTAMP WITH TIME ZONE,
    status                 TEXT,
    completion_attempts    INT,
    last_resubmission_date TIMESTAMP WITH TIME ZONE
);
CREATE INDEX event_publication_serialized_event_hash_idx ON event_publication USING hash (serialized_event);
CREATE INDEX event_publication_by_completion_date_idx ON event_publication (completion_date);
