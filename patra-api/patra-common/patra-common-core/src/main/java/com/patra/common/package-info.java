/**
 * Patra 平台核心共享库根包。
 *
 * <p>本包是 patra-common-core 模块的根包,提供所有 Patra 微服务必需的核心基础设施,
 * 包括 DDD 领域层基类、异常处理框架、共享枚举、JSON 工具和通用工具类。
 *
 * <h2>模块职责</h2>
 *
 * <ul>
 *   <li>提供 DDD 领域层基础抽象(聚合根、领域事件)
 *   <li>提供统一的异常体系(领域异常、应用异常)
 *   <li>提供跨服务共享的枚举定义(数据源、优先级、配置作用域等)
 *   <li>提供 JSON 处理工具(规范化、ObjectMapper 持有者)
 *   <li>提供消息通道键标识符,支持事件总线路由
 *   <li>提供通用工具类(哈希计算、字符串处理等)
 * </ul>
 *
 * <h2>包结构</h2>
 *
 * <ul>
 *   <li>{@link com.patra.common.domain} - 领域层基础包,
 *       提供聚合根基类({@link com.patra.common.domain.AggregateRoot})和领域事件接口
 *   <li>{@link com.patra.common.error} - 异常处理框架包,
 *       提供领域异常基类({@link com.patra.common.error.DomainException})和错误特征枚举
 *   <li>{@link com.patra.common.enums} - 共享枚举包,
 *       提供跨服务共享的枚举定义({@link com.patra.common.enums.ProvenanceCode}、
 *       {@link com.patra.common.enums.Priority} 等)
 *   <li>{@link com.patra.common.json} - JSON 工具包,
 *       提供 JSON 规范化器({@link com.patra.common.json.JsonNormalizer})和 ObjectMapper 持有者
 *   <li>{@link com.patra.common.messaging} - 消息通道包,
 *       提供通道键接口({@link com.patra.common.messaging.ChannelKey})
 *   <li>{@link com.patra.common.util} - 通用工具包,
 *       提供哈希计算({@link com.patra.common.util.HashUtils})等工具类
 * </ul>
 *
 * <h2>设计原则</h2>
 *
 * <ul>
 *   <li><strong>框架无关</strong>: 领域层基类仅依赖 JDK,不依赖 Spring、JPA 等框架
 *   <li><strong>六边形架构兼容</strong>: 清晰的层次边界,支持领域驱动设计
 *   <li><strong>事件驱动架构</strong>: 聚合根支持领域事件收集,应用层负责发布
 *   <li><strong>一致性优先</strong>: 统一的异常体系、共享的枚举定义、标准化的 JSON 处理
 * </ul>
 *
 * <h2>核心特性</h2>
 *
 * <ul>
 *   <li><strong>聚合根基类</strong>: 支持领域事件收集、乐观锁版本管理、标识符生命周期管理
 *   <li><strong>异常体系</strong>: 领域异常和应用异常分离,支持 HTTP 状态码映射
 *   <li><strong>ProvenanceCode</strong>: 数据源枚举,支持字符串解析和别名识别
 *   <li><strong>JSON 规范化</strong>: 确定性 JSON 输出,支持内容签名和去重键生成
 *   <li><strong>消息通道键</strong>: 三部分命名约定,支持事件总线路由
 * </ul>
 *
 * <h2>依赖关系</h2>
 *
 * <ul>
 *   <li><strong>上游依赖</strong>:
 *       <ul>
 *         <li>Hutool - 日期/字符串工具、加密工具
 *         <li>Jackson - JSON 序列化/反序列化
 *         <li>SLF4J (provided) - 日志 API
 *       </ul>
 *   <li><strong>下游消费者</strong>:
 *       <ul>
 *         <li>所有微服务: patra-ingest、patra-registry、patra-gateway 等
 *         <li>所有层级: *-domain、*-app、*-infra、*-adapter
 *         <li>Starter 模块: patra-spring-boot-starter-*
 *       </ul>
 * </ul>
 *
 * <h2>使用示例</h2>
 *
 * <pre>{@code
 * // 1. 创建聚合根
 * public class LiteratureBatch extends AggregateRoot<LiteratureBatchId> {
 *     private String batchId;
 *     private BatchStatus status;
 *
 *     public void complete(int processedCount) {
 *         this.status = BatchStatus.COMPLETED;
 *         addDomainEvent(new BatchCompletedEvent(getId(), processedCount, Instant.now()));
 *     }
 * }
 *
 * // 2. 定义领域异常
 * public class InvalidProvenanceException extends DomainException {
 *     public InvalidProvenanceException(String provenance) {
 *         super("无效的数据源: " + provenance);
 *     }
 * }
 *
 * // 3. 使用 ProvenanceCode 枚举
 * ProvenanceCode source = ProvenanceCode.parse(rawSource);
 * if (source == ProvenanceCode.PUBMED) {
 *     // PubMed 特定处理逻辑
 * }
 *
 * // 4. JSON 规范化用于去重
 * JsonNormalizerResult result = JsonNormalizer.normalizeDefault(literatureData);
 * String contentHash = HashUtils.sha256(result.getHashMaterial());
 * // 使用 contentHash 作为去重键
 *
 * // 5. 定义消息通道键
 * public enum IngestChannels implements ChannelKey {
 *     TASK_READY("ingest", "task", "ready");
 *     // ...
 * }
 * String channel = IngestChannels.TASK_READY.channel();  // "INGEST_TASK_READY"
 * }</pre>
 *
 * @since 0.1.0
 * @author linqibin
 */
package com.patra.common;
