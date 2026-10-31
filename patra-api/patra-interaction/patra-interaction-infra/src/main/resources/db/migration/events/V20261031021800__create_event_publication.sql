-- ============================================================
-- 事件发布登记表（PAP-141）：interaction 外送集成事件的 Outbox，由 patra-tool-events 使用。
-- 照抄 patra-tool/patra-tool-events/src/integrationTest/resources/db/migration/V1__create_event_publication.sql
-- （Spring Modulith 2.1.2 JDBC 第二版结构）。表名与列名由 Modulith 固定，不能改；放 public，不属于任何子域。
-- 完成模式用默认的 DELETE：发完即删，表里只剩没发出去的事件。
-- ============================================================

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
