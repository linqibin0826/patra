/**
 * 实体转换器 - 数据库实体与领域对象双向映射。
 *
 * <p>本包包含 MapStruct 转换器,负责将数据库实体(DO)与领域值对象(VO)进行双向转换。转换器隔离持久化模型和领域模型,确保领域层不依赖数据库表结构。
 *
 * <h2>职责</h2>
 *
 * <ul>
 *   <li>将数据库实体(DO)转换为领域值对象(VO)
 *   <li>将领域聚合根转换为数据库实体(DO)用于持久化
 *   <li>处理类型转换(如 {@code TINYINT(1)} → {@code Boolean})
 *   <li>处理 JSON 字段的序列化和反序列化
 * </ul>
 *
 * <h2>核心转换器</h2>
 *
 * <ul>
 *   <li>{@link com.patra.registry.infra.persistence.converter.ProvenanceEntityConverter} - 数据源实体转换器
 *       <ul>
 *         <li>转换 {@code RegProvenanceDO} ↔ {@code Provenance}
 *         <li>转换各类配置 DO ↔ 配置 VO(HTTP、分页、重试等)
 *       </ul>
 *   <li>{@link com.patra.registry.infra.persistence.converter.ExprEntityConverter} - 表达式实体转换器
 *       <ul>
 *         <li>转换 {@code RegExprFieldDictDO} ↔ {@code ExprField}
 *         <li>转换表达式元数据 DO ↔ VO
 *       </ul>
 * </ul>
 *
 * <h2>命名约定</h2>
 *
 * <ul>
 *   <li>转换器命名: {@code *EntityConverter}
 *   <li>DO → VO: {@code toDomain(DO)}
 *   <li>VO → DO: {@code toEntity(VO)}
 * </ul>
 *
 * <h2>设计模式</h2>
 *
 * <ul>
 *   <li><b>适配器模式</b>: 将持久化模型适配为领域模型
 *   <li><b>MapStruct 自动映射</b>: 减少手写样板代码,提高维护性
 *   <li><b>双向转换</b>: 支持查询侧(DO → VO)和命令侧(VO → DO)
 * </ul>
 *
 * <h2>特殊处理</h2>
 *
 * <ul>
 *   <li><b>布尔字段映射</b>: MySQL {@code TINYINT(1)} → Java {@code Boolean}
 *       <pre>{@code
 * @Mapping(target = "active",
 *          expression = "java(Boolean.TRUE.equals(entity.getIsActive()))")
 *
 * }</pre>
 *   <li><b>JSON 字段映射</b>: JSON 字符串 → Jackson {@code JsonNode}
 *       <pre>{@code
 * @Mapping(target = "customHeaders",
 *          expression = "java(jsonHelper.parseJson(entity.getCustomHeaders()))")
 *
 * }</pre>
 *   <li><b>枚举代码映射</b>: 字符串代码 → 枚举值
 *       <pre>{@code
 * @Mapping(target = "code", source = "provenanceCode")
 *
 * }</pre>
 * </ul>
 *
 * <h2>使用示例</h2>
 *
 * <pre>{@code
 * @Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
 * public interface ProvenanceEntityConverter {
 *
 *     @Mapping(target = "code", source = "provenanceCode")
 *     @Mapping(target = "name", source = "provenanceName")
 *     @Mapping(target = "active",
 *              expression = "java(Boolean.TRUE.equals(entity.getIsActive()))")
 *     Provenance toDomain(RegProvenanceDO entity);
 *
 *     WindowOffsetConfig toDomain(RegProvWindowOffsetCfgDO entity);
 *     PaginationConfig toDomain(RegProvPaginationCfgDO entity);
 *     HttpConfig toDomain(RegProvHttpCfgDO entity);
 *     // ...其他配置转换方法
 * }
 * }</pre>
 *
 * <h2>技术细节</h2>
 *
 * <ul>
 *   <li><b>MapStruct 配置</b>: {@code componentModel = "spring"} 启用 Spring 依赖注入
 *   <li><b>宽松映射</b>: {@code unmappedTargetPolicy = IGNORE} 允许 DO 有额外字段(如审计字段)
 *   <li><b>编译时生成</b>: MapStruct 在编译期生成转换代码,性能优于反射
 * </ul>
 *
 * @since 0.1.0
 * @author linqibin
 */
package com.patra.registry.infra.persistence.converter;
