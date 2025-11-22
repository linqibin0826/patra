/// 日志增强包。
/// 
/// 本包提供 Logback 自定义转换器(Converter),用于在日志格式中嵌入分布式追踪信息(Trace ID、Span ID、Segment ID)。 集成 SkyWalking
/// APM Toolkit,确保日志与分布式追踪链路关联。
/// 
/// ## 职责
/// 
/// - 提供 Logback 转换器,从 SkyWalking 提取追踪 ID
///   - 支持在日志格式中嵌入追踪上下文
///   - 确保日志与 APM 追踪链路的关联
/// 
/// ## 核心组件
/// 
/// - {@link com.patra.starter.core.logging.TraceIdConverter} - 追踪 ID 转换器(%traceId)
///   - {@link com.patra.starter.core.logging.SpanIdConverter} - Span ID 转换器(%spanId)
///   - {@link com.patra.starter.core.logging.SegmentIdConverter} - Segment ID 转换器(%segmentId)
/// 
/// ## 使用示例
/// 
/// ### Logback 配置
/// 
/// ```java
/// <configuration>
///   <!-- 注册自定义转换器 -->
///   <conversionRule conversionWord="traceId"
///                   converterClass="com.patra.starter.core.logging.TraceIdConverter"/>
///   <conversionRule conversionWord="spanId"
///                   converterClass="com.patra.starter.core.logging.SpanIdConverter"/>
///   <conversionRule conversionWord="segmentId"
///                   converterClass="com.patra.starter.core.logging.SegmentIdConverter"/>
/// 
///   <!-- 使用追踪 ID 转换器 -->
///   <appender name="CONSOLE" class="ch.qos.logback.core.ConsoleAppender">
///     <encoder>
///       <pattern>%d{HH:mm:ss.SSS [%traceId] [%spanId] %-5level %logger{36 - %msg%n</pattern>
///     </encoder>
///   </appender>
/// 
///   <root level="INFO">
///     <appender-ref ref="CONSOLE"/>
///   </root>
/// </configuration>
/// ```
/// 
/// ### 日志输出示例
/// 
/// ```
/// 
/// 10:30:45.123 [abc123def456] [span-789] INFO  c.p.i.a.PlanIngestionOrchestrator - 开始导入计划
/// 10:30:45.234 [abc123def456] [span-790] DEBUG c.p.i.d.r.PlanRepository - 保存计划到数据库
/// 10:30:45.456 [abc123def456] [span-791] ERROR c.p.i.a.PlanIngestionOrchestrator - 导入失败: 计划重复
/// 
/// ```
/// 
/// ## 集成 SkyWalking
/// 
/// 转换器依赖 SkyWalking APM Toolkit 提取追踪上下文:
/// 
/// ```java
/// // SkyWalking Agent 自动注入
/// TraceContext.traceId()     → abc123def456
/// TraceContext.spanId()      → span-789
/// TraceContext.segmentId()   → segment-001
/// ```
/// 
/// ## 追踪 ID 的来源
/// 
/// ## 应用场景
/// 
/// ### 场景 1: 关联日志与追踪链路
/// 
/// ```
/// 
/// SkyWalking UI 追踪链路
///   ├─ Span 1: HTTP GET /api/plans/123 [traceId=abc123]
///   ├─ Span 2: PlanIngestionOrchestrator.ingest() [traceId=abc123]
///   └─ Span 3: PlanRepository.save() [traceId=abc123]
/// 
/// 日志文件
///   10:30:45.123 [abc123] 开始导入计划        ← 关联到 Span 2
///   10:30:45.234 [abc123] 保存计划到数据库    ← 关联到 Span 3
/// 
/// ```
/// 
/// ### 场景 2: 快速定位问题
/// 
/// ```
/// 
/// 1. 用户报告错误,提供追踪 ID: abc123def456
/// 2. 在日志系统中搜索: grep "abc123def456" app.log
/// 3. 查看完整的调用链日志
/// 4. 在 SkyWalking UI 中查看分布式追踪详情
/// 
/// ```
/// 
/// ### 场景 3: 聚合日志分析
/// 
/// ```java
/// // ELK Stack 日志查询
/// traceId: "abc123def456"
/// 
/// // 返回该请求的所有日志(跨多个微服务)
/// patra-ingest:  [abc123] 开始导入计划
/// patra-registry: [abc123] 查询 Provenance 配置
/// patra-ingest:  [abc123] 导入完成
/// ```
/// 
/// ## 配置建议
/// 
/// ### 开发环境
/// 
/// ```java
/// <pattern>%d{HH:mm:ss.SSS [%traceId] %-5level %logger{36 - %msg%n</pattern>
/// ```
/// 
/// ### 生产环境(JSON 格式)
/// 
/// ```java
/// <encoder class="net.logstash.logback.encoder.LogstashEncoder">
///   <customFields>{"service":"patra-ingest"</customFields>
///   <fieldNames>
///     <traceId>traceId</traceId>
///     <spanId>spanId</spanId>
///   </fieldNames>
/// </encoder>
/// ```
/// 
/// ## 依赖要求
/// 
/// - **必需**: `apm-toolkit-logback-1.x`(SkyWalking Logback 插件)
///   - **运行时**: SkyWalking Agent(JVM 参数: -javaagent:/path/to/skywalking-agent.jar)
///   - **可选**: `logstash-logback-encoder`(JSON 日志格式)
/// 
/// ## 注意事项
/// 
/// - 不要在性能敏感的代码中频繁调用 `TraceContext`,转换器已优化性能
///   - 确保 SkyWalking Agent 在 JVM 启动时正确加载
///   - 在非 Web 请求场景(如定时任务),追踪 ID 可能为空
/// 
/// @since 0.1.0
/// @author linqibin
package com.patra.starter.core.logging;
