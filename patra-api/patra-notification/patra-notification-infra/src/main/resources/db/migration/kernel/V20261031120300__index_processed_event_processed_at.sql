-- 已处理事件的保留期清理（patra-tool-events 的 processedEventPurgeJob，默认删 30 天前的行）按处理时间删，
-- 补一个按 processed_at 的索引，免得每天全表扫描。
CREATE INDEX processed_event_processed_at_idx ON processed_event (processed_at);
