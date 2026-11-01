/// collect 子域的应用层。
///
/// 采集（PAP-167）：一批采集信封进来，逐条预检、校验、富化、判爬虫、查地区，合格的写 events.raw、不合格的写 events.dlq。
package dev.patra.analytics.app.collect;
