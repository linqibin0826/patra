/// 领域对象到查询 DTO 转换器 - 应用层数据组装器。
/// 
/// 本包包含 MapStruct 转换器(Assembler),负责将领域层的值对象和聚合根转换为只读查询 DTO,供外部客户端(如 Feign
/// 客户端)消费。转换器遵循单向映射原则,仅支持领域对象到查询 DTO 的转换。
/// 
/// ## 职责
/// 
/// - 将领域值对象转换为只读查询 DTO
///   - 将聚合根转换为包含完整数据的查询 DTO
///   - 隔离领域模型和外部表示层,防止领域对象泄漏
///   - 支持 REST API 和 Feign 客户端的数据契约
/// 
/// ## 核心转换器
/// 
/// - {@link com.patra.registry.app.converter.ProvenanceQueryAssembler} - 数据源转换器
///       
/// - 转换 `Provenance` → `ProvenanceQuery`
///         - 转换 `ProvenanceConfiguration` → `ProvenanceConfigQuery`
///         - 转换各类配置值对象(HTTP、分页、重试、速率限制等)
/// 
///   - {@link com.patra.registry.app.converter.ExprQueryAssembler} - 表达式转换器
///       
/// - 转换 `ExprSnapshot` → `ExprSnapshotQuery`
///         - 转换表达式元数据(字段、能力、映射、规则)
/// 
/// ## 命名约定
/// 
/// - 转换器命名: `*QueryAssembler`
///   - 转换方法: `toQuery(DomainObject)`
///   - 批量转换: `toQuery(List<DomainObject>)`
/// 
/// ## 设计模式
/// 
/// - **组装器模式**: 将复杂领域对象组装为简化的查询 DTO
///   - **MapStruct 自动映射**: 减少手写样板代码,提高维护性
///   - **单向映射**: 仅支持领域对象到查询 DTO 的转换(读侧)
/// 
/// ## 使用示例
/// 
/// ```java
/// @Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
/// public interface ProvenanceQueryAssembler {
///     ProvenanceQuery toQuery(Provenance provenance);
///     ProvenanceConfigQuery toQuery(ProvenanceConfiguration configuration);
///     WindowOffsetQuery toQuery(WindowOffsetConfig config);
///     // ...其他配置转换方法
/// ```
/// 
/// ## 技术细节
/// 
/// - **MapStruct 配置**: `componentModel = "spring"` 启用 Spring 依赖注入
///   - **严格映射**: `unmappedTargetPolicy = ERROR` 确保所有字段显式映射
///   - **编译时生成**: MapStruct 在编译期生成转换代码,性能优于反射
/// 
/// @since 0.1.0
/// @author linqibin
package com.patra.registry.app.converter;
