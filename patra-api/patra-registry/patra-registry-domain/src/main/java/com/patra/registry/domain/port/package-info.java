/// Registry 出站端口包 - 六边形架构仓储接口定义。
/// 
/// 本包定义了领域层的出站端口(仓储接口),遵循六边形架构的依赖倒置原则。 仓储接口在领域层定义,由基础设施层(`patra-registry-infra`)实现,
/// 确保领域层不依赖具体的持久化技术。
/// 
/// ## 职责
/// 
/// - 定义领域对象的持久化和查询接口(仓储模式)
///   - 使用领域语言表达业务查询意图,隐藏底层技术细节
///   - 支持聚合根的加载、保存和重建
///   - 提供时态查询接口,支持配置的历史版本查询
///   - 实现依赖倒置,领域层不依赖基础设施层
/// 
/// ## 核心仓储接口
/// 
/// - {@link com.patra.registry.domain.port.ProvenanceConfigRepository} - 数据源配置仓储, 负责 {@link
///       com.patra.registry.domain.model.aggregate.ProvenanceConfiguration} 聚合根的持久化和查询
///   - {@link com.patra.registry.domain.port.ExprRepository} - 表达式仓储, 负责表达式元数据的查询和快照加载
/// 
/// ## 设计模式
/// 
/// - **仓储模式**: 封装领域对象的持久化逻辑,提供类似集合的访问接口
///   - **依赖倒置**: 接口在领域层定义,实现在基础设施层,领域层不依赖具体实现
///   - **时态查询**: 所有 `findActive*` 方法接受 `at` 参数,查询指定时刻有效的配置
///   - **作用域优先级**: 查询方法支持配置作用域优先级(TASK > OPERATION > SOURCE)
/// 
/// ## 时态查询支持
/// 
/// 所有配置查询方法都支持时态查询,通过 `at` 参数指定查询时间点:
/// 
/// - 查询在指定时刻生效的配置(`effectiveFrom <= at < effectiveTo`)
///   - 支持配置的安全更新,不影响正在运行的任务
///   - 提供配置审计和历史回溯能力
///   - 支持基于时间的 A/B 测试和渐进式发布
/// 
/// ## 查询方法约定
/// 
/// - **findProvenanceBy***: 查询数据源元数据
///   - **findActive***: 查询指定时刻有效的配置(时态查询)
///   - **loadConfiguration**: 加载完整的配置聚合根
/// 
/// ## 使用示例
/// 
/// ```java
/// // 查询数据源元数据
/// Optional<Provenance> provenance = repository.findProvenanceByCode(ProvenanceCode.PUBMED);
/// 
/// // 时态查询: 获取当前时刻有效的 HTTP 配置
/// Optional<HttpConfig> httpConfig = repository.findActiveHttpConfig(
///     provenanceId,
///     "HARVEST",
///     Instant.now()
/// );
/// 
/// // 加载完整配置聚合根
/// Optional<ProvenanceConfiguration> config = repository.loadConfiguration(
///     provenanceId,
///     "HARVEST",
///     Instant.now()
/// );
/// ```
/// 
/// ## 实现要求
/// 
/// 基础设施层实现仓储接口时必须遵循以下约定:
/// 
/// - 使用事务确保数据一致性
///   - 正确处理时态查询条件(`effectiveFrom <= at < effectiveTo`)
///   - 应用配置作用域优先级规则(TASK > OPERATION > SOURCE)
///   - 返回不可变领域对象(值对象、聚合根)
///   - 抛出领域异常({@link com.patra.registry.domain.exception.RegistryNotFoundException})而非技术异常
/// 
/// @since 0.1.0
/// @author linqibin
package com.patra.registry.domain.port;
