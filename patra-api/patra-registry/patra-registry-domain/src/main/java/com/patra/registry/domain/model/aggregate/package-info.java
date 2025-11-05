/**
 * Registry 领域聚合根包 - DDD 聚合根模式实现。
 *
 * <p>本包包含 Registry 服务的聚合根对象,定义了一致性边界和业务不变性约束。
 * 聚合根是 DDD 战术设计的核心模式,确保领域对象的完整性和一致性。
 *
 * <h2>职责</h2>
 *
 * <ul>
 *   <li>定义聚合的一致性边界,确保业务规则的完整执行
 *   <li>组装多个值对象和实体,提供统一的业务视图
 *   <li>强制执行聚合内的业务不变性约束
 *   <li>作为外部访问聚合内对象的唯一入口
 *   <li>支持 CQRS 读端,提供只读聚合视图
 * </ul>
 *
 * <h2>核心聚合根</h2>
 *
 * <ul>
 *   <li>{@link com.patra.registry.domain.model.aggregate.ProvenanceConfiguration} - 数据源配置聚合根,
 *       聚合数据源元数据和多维运营配置(HTTP、重试、分页、批处理、限流、时间窗口)的完整视图
 * </ul>
 *
 * <h2>设计模式</h2>
 *
 * <ul>
 *   <li><strong>只读聚合</strong>: {@link com.patra.registry.domain.model.aggregate.ProvenanceConfiguration}
 *       用于 CQRS 读端,通过仓储接口在特定时刻加载完整配置快照
 *   <li><strong>一致性边界</strong>: 聚合根确保数据源元数据是必需核心,各维度配置为可选项
 *   <li><strong>不变性保护</strong>: 使用 {@code record} 实现不可变性,通过规范构造器强制验证
 * </ul>
 *
 * <h2>ProvenanceConfiguration 聚合</h2>
 *
 * <p>数据源配置聚合根聚合了 7 个配置维度:
 *
 * <ul>
 *   <li><strong>provenance</strong> - 数据源元数据(必需核心)
 *   <li>windowOffset - 时间窗口偏移配置(可选)
 *   <li>pagination - 分页策略配置(可选)
 *   <li>http - HTTP 客户端配置(可选)
 *   <li>batching - 批处理配置(可选)
 *   <li>retry - 重试策略配置(可选)
 *   <li>rateLimit - 速率限制配置(可选)
 * </ul>
 *
 * <h2>业务规则</h2>
 *
 * <ul>
 *   <li>数据源元数据是聚合的必需核心,不可为 null
 *   <li>各维度配置均为可选,根据数据源特性按需配置
 *   <li>配置作用域遵循优先级规则: TASK 级 > OPERATION 级 > SOURCE 级
 *   <li>仅当数据源处于激活状态时,配置才被视为完整可用
 * </ul>
 *
 * <h2>生命周期</h2>
 *
 * <p>聚合根通过仓储接口({@link com.patra.registry.domain.port.ProvenanceConfigRepository})
 * 加载和持久化:
 *
 * <ul>
 *   <li><strong>加载</strong>: 通过 {@code loadConfiguration()} 方法在特定时刻加载完整配置快照
 *   <li><strong>时态查询</strong>: 支持基于时间点(at 参数)查询当时有效的配置
 *   <li><strong>作用域过滤</strong>: 根据操作类型(operationType)应用配置优先级规则
 * </ul>
 *
 * <h2>使用示例</h2>
 *
 * <pre>{@code
 * // 创建完整配置聚合根
 * ProvenanceConfiguration config = new ProvenanceConfiguration(
 *     provenance,      // 必需的数据源元数据
 *     windowOffset,    // 可选的时间窗口配置
 *     pagination,      // 可选的分页配置
 *     http,            // 可选的 HTTP 配置
 *     batching,        // 可选的批处理配置
 *     retry,           // 可选的重试配置
 *     rateLimit        // 可选的限流配置
 * );
 *
 * // 检查配置完整性
 * if (config.isComplete()) {
 *     // 数据源激活且可用
 * }
 *
 * // 检查特定配置维度
 * if (config.hasHttpConfig()) {
 *     HttpConfig httpConfig = config.http();
 *     // 使用 HTTP 配置
 * }
 * }</pre>
 *
 * @since 0.1.0
 * @author linqibin
 */
package com.patra.registry.domain.model.aggregate;
