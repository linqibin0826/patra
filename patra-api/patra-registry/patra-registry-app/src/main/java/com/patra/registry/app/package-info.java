/// Registry 应用层根包 - 用例编排与协调。
/// 
/// 本包是六边形架构应用层的根包,包含用例编排器、转换器和应用服务。应用层不包含业务逻辑,仅负责编排领域对象和仓储操作,实现用例协调。
/// 
/// ## 架构定位
/// 
/// 在六边形架构中,本层位于领域核心和适配器之间:
/// 
/// - **上游依赖**: 被 `patra-registry-adapter` 适配器层调用
///   - **下游依赖**: 调用 `patra-registry-domain` 领域模型和端口
///   - **协作关系**: 通过仓储接口协调 `patra-registry-infra` 基础设施层
/// 
/// ## 包结构
/// 
/// - `service` - 用例编排器,实现业务用例的流程协调
///   - `converter` - 领域对象到查询 DTO 的转换器(组装器)
/// 
/// ## 核心职责
/// 
/// - 编排领域服务和仓储操作完成业务用例
///   - 协调跨聚合根的查询和数据组装
///   - 将领域对象转换为只读查询 DTO 供外部消费
///   - 处理应用级事务边界(如需要)
/// 
/// ## 设计原则
/// 
/// - **薄应用层**: 不包含业务逻辑,仅编排领域对象
///   - **用例导向**: 每个编排器对应一个业务用例或用例组
///   - **依赖反转**: 通过端口接口依赖领域层,不依赖基础设施实现
///   - **无框架污染**: 核心编排逻辑不依赖 Spring 等框架(仅使用注解)
/// 
/// ## 典型用例
/// 
/// - 加载数据源配置聚合(整合多维度配置为统一视图)
///   - 查询表达式快照(组装字段、能力、映射、规则)
///   - 列出系统字典(转换领域字典为 API DTO)
/// 
/// ## 使用示例
/// 
/// ```java
/// @Service
/// @RequiredArgsConstructor
/// public class ProvenanceConfigOrchestrator {
///     private final ProvenanceConfigRepository repository;
///     private final ProvenanceQueryAssembler assembler;
/// 
///     public Optional<ProvenanceConfigQuery> loadConfiguration(
///         ProvenanceCode code, String operationType, Instant at) {
///         // 1. 查询领域对象
///         var provenance = repository.findProvenanceByCode(code);
///         // 2. 加载配置聚合
///         var config = repository.loadConfiguration(...);
///         // 3. 转换为查询 DTO
///         return config.map(assembler::toQuery);
/// ```
/// 
/// @since 0.1.0
/// @author linqibin
package com.patra.registry.app;
