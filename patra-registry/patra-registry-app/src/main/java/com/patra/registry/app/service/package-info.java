/// 用例编排器 - 应用层业务用例实现。
/// 
/// 本包包含应用层编排器(Orchestrator),负责协调领域服务和仓储操作,实现完整的业务用例流程。编排器是应用层的核心组件,遵循薄应用层原则,不包含业务逻辑。
/// 
/// ## 职责
/// 
/// - 实现业务用例的流程协调和步骤编排
///   - 调用领域仓储接口查询或持久化聚合根
///   - 协调多个领域对象完成复杂查询或数据组装
///   - 委托转换器将领域对象转换为只读查询 DTO
///   - 处理应用级事务边界(通过 `@Transactional` 注解)
/// 
/// ## 核心编排器
/// 
/// - {@link com.patra.registry.app.service.ProvenanceConfigOrchestrator} - 数据源配置查询编排器
///       
/// - 列出所有数据源
///         - 查询单个数据源元数据
///         - 加载完整配置聚合(支持时态切片)
/// 
///   - {@link com.patra.registry.app.service.ExprQueryOrchestrator} - 表达式查询编排器
///       
/// - 加载表达式快照(字段、能力、映射、规则)
///         - 支持时态切片查询
/// 
/// ## 命名约定
/// 
/// - 编排器命名: `*Orchestrator`
///   - 查询用例: `find*()`, `list*()`, `load*()`
///   - 命令用例: `create*()`, `update*()`, `delete*()`
/// 
/// ## 设计模式
/// 
/// - **应用服务模式**: 编排领域对象完成用例,不包含业务逻辑
///   - **依赖注入**: 通过构造器注入仓储和转换器
///   - **门面模式**: 为外部调用者提供简化的用例入口
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
///         Instant effectiveTime = at != null ? at : Instant.now();
/// 
///         return repository.findProvenanceByCode(code)
///             .flatMap(provenance ->
///                 repository.loadConfiguration(provenance.id(), operationType, effectiveTime)
///                     .map(assembler::toQuery));
/// ```
/// 
/// @since 0.1.0
/// @author linqibin
package com.patra.registry.app.service;
