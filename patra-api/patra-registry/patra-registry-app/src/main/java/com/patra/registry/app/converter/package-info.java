/**
 * 领域对象到查询 DTO 转换器 - 应用层数据组装器。
 *
 * <p>本包包含 MapStruct 转换器(Assembler),负责将领域层的值对象和聚合根转换为只读查询 DTO,供外部客户端(如 Feign 客户端)消费。转换器遵循单向映射原则,仅支持领域对象到查询 DTO 的转换。
 *
 * <h2>职责</h2>
 *
 * <ul>
 *   <li>将领域值对象转换为只读查询 DTO
 *   <li>将聚合根转换为包含完整数据的查询 DTO
 *   <li>隔离领域模型和外部表示层,防止领域对象泄漏
 *   <li>支持 REST API 和 Feign 客户端的数据契约
 * </ul>
 *
 * <h2>核心转换器</h2>
 *
 * <ul>
 *   <li>{@link com.patra.registry.app.converter.ProvenanceQueryAssembler} - 数据源转换器
 *       <ul>
 *         <li>转换 {@code Provenance} → {@code ProvenanceQuery}</li>
 *         <li>转换 {@code ProvenanceConfiguration} → {@code ProvenanceConfigQuery}</li>
 *         <li>转换各类配置值对象(HTTP、分页、重试、速率限制等)</li>
 *       </ul>
 *   </li>
 *   <li>{@link com.patra.registry.app.converter.ExprQueryAssembler} - 表达式转换器
 *       <ul>
 *         <li>转换 {@code ExprSnapshot} → {@code ExprSnapshotQuery}</li>
 *         <li>转换表达式元数据(字段、能力、映射、规则)</li>
 *       </ul>
 *   </li>
 * </ul>
 *
 * <h2>命名约定</h2>
 *
 * <ul>
 *   <li>转换器命名: {@code *QueryAssembler}
 *   <li>转换方法: {@code toQuery(DomainObject)}
 *   <li>批量转换: {@code toQuery(List<DomainObject>)}
 * </ul>
 *
 * <h2>设计模式</h2>
 *
 * <ul>
 *   <li><b>组装器模式</b>: 将复杂领域对象组装为简化的查询 DTO
 *   <li><b>MapStruct 自动映射</b>: 减少手写样板代码,提高维护性
 *   <li><b>单向映射</b>: 仅支持领域对象到查询 DTO 的转换(读侧)
 * </ul>
 *
 * <h2>使用示例</h2>
 *
 * <pre>{@code
 * @Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
 * public interface ProvenanceQueryAssembler {
 *     ProvenanceQuery toQuery(Provenance provenance);
 *     ProvenanceConfigQuery toQuery(ProvenanceConfiguration configuration);
 *     WindowOffsetQuery toQuery(WindowOffsetConfig config);
 *     // ...其他配置转换方法
 * }
 * }</pre>
 *
 * <h2>技术细节</h2>
 *
 * <ul>
 *   <li><b>MapStruct 配置</b>: {@code componentModel = "spring"} 启用 Spring 依赖注入
 *   <li><b>严格映射</b>: {@code unmappedTargetPolicy = ERROR} 确保所有字段显式映射
 *   <li><b>编译时生成</b>: MapStruct 在编译期生成转换代码,性能优于反射
 * </ul>
 *
 * @since 0.1.0
 * @author linqibin
 */
package com.patra.registry.app.converter;
