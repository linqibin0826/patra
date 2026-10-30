-- 消费端已处理事件：各子域的消费者在业务事务里先记一行（ProcessedEventPort），同一事件重投时据此跳过。
-- 结构照抄 patra-tool-events 的模板。主键（消费方, 事件 ID）：同一事件被本服务的两个队列消费时各记各的。
CREATE TABLE processed_event
(
    consumer     TEXT                     NOT NULL,
    event_id     UUID                     NOT NULL,
    processed_at TIMESTAMP WITH TIME ZONE NOT NULL,
    PRIMARY KEY (consumer, event_id)
);
