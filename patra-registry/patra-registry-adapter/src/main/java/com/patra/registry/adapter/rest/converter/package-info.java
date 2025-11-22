/// 查询 DTO 到 API 响应 DTO 转换器 - REST 适配器数据映射。
/// 
/// 本包包含 MapStruct 转换器,负责将应用层查询 DTO 转换为 API 契约定义的响应 DTO,供外部 Feign 客户端消费。转换器遵循单向映射原则,仅支持查询 DTO 到响应
/// DTO 的转换。
/// 
/// ## 职责
/// 
/// - 将应用层查询 DTO 转换为 API 响应 DTO
///   - 实现 `patra-registry-api` 模块定义的数据契约
///   - 支持 Feign 客户端序列化和反序列化
///   - 隔离内部查询模型和外部 API 模型
/// 
/// ## 核心转换器
/// 
/// - {@link com.patra.registry.adapter.rest.converter.ProvenanceApiConverter} - 数据源 API 转换器
///       
/// - 转换 `ProvenanceQuery` → `ProvenanceResp`
///         - 转换 `ProvenanceConfigQuery` → `ProvenanceConfigResp`
///         - 转换各类配置查询 DTO 到响应 DTO
/// 
///   - {@link com.patra.registry.adapter.rest.converter.ExprApiConverter} - 表达式 API 转换器
///       
/// - 转换 `ExprSnapshotQuery` → `ExprSnapshotResp`
///         - 转换表达式元数据查询 DTO 到响应 DTO
/// 
/// ## 命名约定
/// 
/// - 转换器命名: `*ApiConverter`
///   - 转换方法: `toResp(QueryDTO)`
///   - 批量转换: `toResp(List<QueryDTO>)`
/// 
/// ## 设计模式
/// 
/// - **DTO 转换模式**: 将内部查询模型转换为外部 API 模型
///   - **MapStruct 自动映射**: 减少手写样板代码,提高维护性
///   - **单向映射**: 仅支持查询 DTO 到响应 DTO 的转换(读侧)
/// 
/// ## 转换层次
/// 
/// ```java
/// 领域层值对象 (Provenance)
///     ↓ ProvenanceQueryAssembler (app.converter)
/// 查询 DTO (ProvenanceQuery)
///     ↓ ProvenanceApiConverter (adapter.rest.converter)
/// API 响应 DTO (ProvenanceResp)
///     ↓ Jackson 序列化
/// JSON 响应 → Feign 客户端
/// ```
/// 
/// ## 使用示例
/// 
/// ```java
/// @Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
/// public interface ProvenanceApiConverter {
///     ProvenanceResp toResp(ProvenanceQuery query);
///     ProvenanceConfigResp toResp(ProvenanceConfigQuery query);
///     WindowOffsetResp toResp(WindowOffsetQuery query);
///     // ...其他配置转换方法
/// ```
/// 
/// ## 技术细节
/// 
/// - **MapStruct 配置**: `componentModel = "spring"` 启用 Spring 依赖注入
///   - **严格映射**: `unmappedTargetPolicy = ERROR` 确保所有字段显式映射
///   - **Feign 兼容**: 生成的 DTO 必须符合 Feign 序列化要求
/// 
/// @since 0.1.0
/// @author linqibin
package com.patra.registry.adapter.rest.converter;
