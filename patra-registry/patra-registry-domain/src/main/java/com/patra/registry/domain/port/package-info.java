/**
 * Registry 出站端口包 - 六边形架构仓储接口定义。
 *
 * <p>本包定义了领域层的出站端口(仓储接口),遵循六边形架构的依赖倒置原则。 仓储接口在领域层定义,由基础设施层({@code patra-registry-infra})实现,
 * 确保领域层不依赖具体的持久化技术。
 *
 * <h2>职责</h2>
 *
 * <ul>
 *   <li>定义领域对象的持久化和查询接口(仓储模式)
 *   <li>使用领域语言表达业务查询意图,隐藏底层技术细节
 *   <li>支持聚合根的加载、保存和重建
 *   <li>提供时态查询接口,支持配置的历史版本查询
 *   <li>实现依赖倒置,领域层不依赖基础设施层
 * </ul>
 *
 * <h2>核心仓储接口</h2>
 *
 * <ul>
 *   <li>{@link com.patra.registry.domain.port.ProvenanceConfigRepository} - 数据源配置仓储, 负责 {@link
 *       com.patra.registry.domain.model.aggregate.ProvenanceConfiguration} 聚合根的持久化和查询
 *   <li>{@link com.patra.registry.domain.port.ExprRepository} - 表达式仓储, 负责表达式元数据的查询和快照加载
 * </ul>
 *
 * <h2>设计模式</h2>
 *
 * <ul>
 *   <li><strong>仓储模式</strong>: 封装领域对象的持久化逻辑,提供类似集合的访问接口
 *   <li><strong>依赖倒置</strong>: 接口在领域层定义,实现在基础设施层,领域层不依赖具体实现
 *   <li><strong>时态查询</strong>: 所有 {@code findActive*} 方法接受 {@code at} 参数,查询指定时刻有效的配置
 *   <li><strong>作用域优先级</strong>: 查询方法支持配置作用域优先级(TASK > OPERATION > SOURCE)
 * </ul>
 *
 * <h2>时态查询支持</h2>
 *
 * <p>所有配置查询方法都支持时态查询,通过 {@code at} 参数指定查询时间点:
 *
 * <ul>
 *   <li>查询在指定时刻生效的配置({@code effectiveFrom <= at < effectiveTo})
 *   <li>支持配置的安全更新,不影响正在运行的任务
 *   <li>提供配置审计和历史回溯能力
 *   <li>支持基于时间的 A/B 测试和渐进式发布
 * </ul>
 *
 * <h2>查询方法约定</h2>
 *
 * <ul>
 *   <li><strong>findProvenanceBy*</strong>: 查询数据源元数据
 *   <li><strong>findActive*</strong>: 查询指定时刻有效的配置(时态查询)
 *   <li><strong>loadConfiguration</strong>: 加载完整的配置聚合根
 * </ul>
 *
 * <h2>使用示例</h2>
 *
 * <pre>{@code
 * // 查询数据源元数据
 * Optional<Provenance> provenance = repository.findProvenanceByCode(ProvenanceCode.PUBMED);
 *
 * // 时态查询: 获取当前时刻有效的 HTTP 配置
 * Optional<HttpConfig> httpConfig = repository.findActiveHttpConfig(
 *     provenanceId,
 *     "HARVEST",
 *     Instant.now()
 * );
 *
 * // 加载完整配置聚合根
 * Optional<ProvenanceConfiguration> config = repository.loadConfiguration(
 *     provenanceId,
 *     "HARVEST",
 *     Instant.now()
 * );
 * }</pre>
 *
 * <h2>实现要求</h2>
 *
 * <p>基础设施层实现仓储接口时必须遵循以下约定:
 *
 * <ul>
 *   <li>使用事务确保数据一致性
 *   <li>正确处理时态查询条件({@code effectiveFrom <= at < effectiveTo})
 *   <li>应用配置作用域优先级规则(TASK > OPERATION > SOURCE)
 *   <li>返回不可变领域对象(值对象、聚合根)
 *   <li>抛出领域异常({@link com.patra.registry.domain.exception.RegistryNotFoundException})而非技术异常
 * </ul>
 *
 * @since 0.1.0
 * @author linqibin
 */
package com.patra.registry.domain.port;
