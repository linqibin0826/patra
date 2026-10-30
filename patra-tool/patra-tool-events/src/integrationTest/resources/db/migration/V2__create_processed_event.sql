-- 消费端已处理事件（patra-tool-events 的 JdbcProcessedEventAdapter），消费事件的服务照抄。
-- 主键（消费方, 事件 ID）：同一个事件被同一服务的两个队列消费时各记各的。
CREATE TABLE processed_event
(
    consumer     TEXT                     NOT NULL,
    event_id     UUID                     NOT NULL,
    processed_at TIMESTAMP WITH TIME ZONE NOT NULL,
    PRIMARY KEY (consumer, event_id)
);
