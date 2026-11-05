/// ingest 子域的基础设施层。
///
/// 入库（PAP-168）：批量消费 events.raw / events.dlq，整批写进 ClickHouse 的明细表与 dlq 表，物化视图随之生成汇总。
package dev.patra.analytics.infra.ingest;
