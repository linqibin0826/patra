-- 事件发布登记表（Spring Modulith 2.1.2 JDBC 第二版结构，schemas/v2/schema-postgresql.sql，与 2.0.8 相同），发布事件的服务照抄。
-- 表名与列名由 Modulith 固定，不能改；完成模式选 ARCHIVE 的服务另建同结构的 event_publication_archive。
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
