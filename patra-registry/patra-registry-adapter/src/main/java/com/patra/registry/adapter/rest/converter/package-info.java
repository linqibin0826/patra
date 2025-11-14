/**
 * 查询 DTO 到 API 响应 DTO 转换器 - REST 适配器数据映射。
 *
 * <p>本包包含 MapStruct 转换器,负责将应用层查询 DTO 转换为 API 契约定义的响应 DTO,供外部 Feign 客户端消费。转换器遵循单向映射原则,仅支持查询 DTO 到响应
 * DTO 的转换。
 *
 * <h2>职责</h2>
 *
 * <ul>
 *   <li>将应用层查询 DTO 转换为 API 响应 DTO
 *   <li>实现 {@code patra-registry-api} 模块定义的数据契约
 *   <li>支持 Feign 客户端序列化和反序列化
 *   <li>隔离内部查询模型和外部 API 模型
 * </ul>
 *
 * <h2>核心转换器</h2>
 *
 * <ul>
 *   <li>{@link com.patra.registry.adapter.rest.converter.ProvenanceApiConverter} - 数据源 API 转换器
 *       <ul>
 *         <li>转换 {@code ProvenanceQuery} → {@code ProvenanceResp}
 *         <li>转换 {@code ProvenanceConfigQuery} → {@code ProvenanceConfigResp}
 *         <li>转换各类配置查询 DTO 到响应 DTO
 *       </ul>
 *   <li>{@link com.patra.registry.adapter.rest.converter.ExprApiConverter} - 表达式 API 转换器
 *       <ul>
 *         <li>转换 {@code ExprSnapshotQuery} → {@code ExprSnapshotResp}
 *         <li>转换表达式元数据查询 DTO 到响应 DTO
 *       </ul>
 * </ul>
 *
 * <h2>命名约定</h2>
 *
 * <ul>
 *   <li>转换器命名: {@code *ApiConverter}
 *   <li>转换方法: {@code toResp(QueryDTO)}
 *   <li>批量转换: {@code toResp(List<QueryDTO>)}
 * </ul>
 *
 * <h2>设计模式</h2>
 *
 * <ul>
 *   <li><b>DTO 转换模式</b>: 将内部查询模型转换为外部 API 模型
 *   <li><b>MapStruct 自动映射</b>: 减少手写样板代码,提高维护性
 *   <li><b>单向映射</b>: 仅支持查询 DTO 到响应 DTO 的转换(读侧)
 * </ul>
 *
 * <h2>转换层次</h2>
 *
 * <pre>{@code
 * 领域层值对象 (Provenance)
 *     ↓ ProvenanceQueryAssembler (app.converter)
 * 查询 DTO (ProvenanceQuery)
 *     ↓ ProvenanceApiConverter (adapter.rest.converter)
 * API 响应 DTO (ProvenanceResp)
 *     ↓ Jackson 序列化
 * JSON 响应 → Feign 客户端
 * }</pre>
 *
 * <h2>使用示例</h2>
 *
 * <pre>{@code
 * @Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
 * public interface ProvenanceApiConverter {
 *     ProvenanceResp toResp(ProvenanceQuery query);
 *     ProvenanceConfigResp toResp(ProvenanceConfigQuery query);
 *     WindowOffsetResp toResp(WindowOffsetQuery query);
 *     // ...其他配置转换方法
 * }
 * }</pre>
 *
 * <h2>技术细节</h2>
 *
 * <ul>
 *   <li><b>MapStruct 配置</b>: {@code componentModel = "spring"} 启用 Spring 依赖注入
 *   <li><b>严格映射</b>: {@code unmappedTargetPolicy = ERROR} 确保所有字段显式映射
 *   <li><b>Feign 兼容</b>: 生成的 DTO 必须符合 Feign 序列化要求
 * </ul>
 *
 * @since 0.1.0
 * @author linqibin
 */
package com.patra.registry.adapter.rest.converter;
