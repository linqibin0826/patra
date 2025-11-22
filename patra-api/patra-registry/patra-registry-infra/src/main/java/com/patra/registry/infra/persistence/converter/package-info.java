/// 实体转换器 - 数据库实体与领域对象双向映射。
///
/// 本包包含 MapStruct 转换器,负责将数据库实体(DO)与领域值对象(VO)进行双向转换。转换器隔离持久化模型和领域模型,确保领域层不依赖数据库表结构。
///
/// ## 职责
///
/// - 将数据库实体(DO)转换为领域值对象(VO)
///   - 将领域聚合根转换为数据库实体(DO)用于持久化
///   - 处理类型转换(如 `TINYINT(1)` → `Boolean`)
///   - 处理 JSON 字段的序列化和反序列化
///
/// ## 核心转换器
///
/// - {@link com.patra.registry.infra.persistence.converter.ProvenanceEntityConverter} - 数据源实体转换器
///
/// - 转换 `RegProvenanceDO` ↔ `Provenance`
///         - 转换各类配置 DO ↔ 配置 VO(HTTP、分页、重试等)
///
///   - {@link com.patra.registry.infra.persistence.converter.ExprEntityConverter} - 表达式实体转换器
///
/// - 转换 `RegExprFieldDictDO` ↔ `ExprField`
///         - 转换表达式元数据 DO ↔ VO
///
/// ## 命名约定
///
/// - 转换器命名: `*EntityConverter`
///   - DO → VO: `toDomain(DO)`
///   - VO → DO: `toEntity(VO)`
///
/// ## 设计模式
///
/// - **适配器模式**: 将持久化模型适配为领域模型
///   - **MapStruct 自动映射**: 减少手写样板代码,提高维护性
///   - **双向转换**: 支持查询侧(DO → VO)和命令侧(VO → DO)
///
/// ## 特殊处理
///
/// - **布尔字段映射**: MySQL `TINYINT(1)` → Java `Boolean`
///       ```java
/// @Mapping(target = "active",
///          expression = "java(Boolean.TRUE.equals(entity.getIsActive()))")
/// ```
///   - **JSON 字段映射**: JSON 字符串 → Jackson `JsonNode`
///       ```java
/// @Mapping(target = "customHeaders",
///          expression = "java(jsonHelper.parseJson(entity.getCustomHeaders()))")
/// ```
///   - **枚举代码映射**: 字符串代码 → 枚举值
///       ```java
/// @Mapping(target = "code", source = "provenanceCode")
/// ```
///
/// ## 使用示例
///
/// ```java
/// @Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
/// public interface ProvenanceEntityConverter {
///
///     @Mapping(target = "code", source = "provenanceCode")
///     @Mapping(target = "name", source = "provenanceName")
///     @Mapping(target = "active",
///              expression = "java(Boolean.TRUE.equals(entity.getIsActive()))")
///     Provenance toDomain(RegProvenanceDO entity);
///
///     WindowOffsetConfig toDomain(RegProvWindowOffsetCfgDO entity);
///     PaginationConfig toDomain(RegProvPaginationCfgDO entity);
///     HttpConfig toDomain(RegProvHttpCfgDO entity);
///     // ...其他配置转换方法
/// ```
///
/// ## 技术细节
///
/// - **MapStruct 配置**: `componentModel = "spring"` 启用 Spring 依赖注入
///   - **宽松映射**: `unmappedTargetPolicy = IGNORE` 允许 DO 有额外字段(如审计字段)
///   - **编译时生成**: MapStruct 在编译期生成转换代码,性能优于反射
///
/// @since 0.1.0
/// @author linqibin
package com.patra.registry.infra.persistence.converter;
