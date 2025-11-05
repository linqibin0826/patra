/**
 * MyBatis 数据对象(DO)基类包。
 *
 * <p>本包提供所有持久化实体的抽象基类,封装审计字段、乐观锁和软删除等通用功能,确保项目内数据库实体的一致性和规范性。
 *
 * <h2>职责</h2>
 *
 * <ul>
 *   <li>定义标准化的数据对象基类 {@code BaseDO},包含审计、版本控制和软删除字段
 *   <li>集成 MyBatis-Plus 的字段自动填充、乐观锁和逻辑删除功能
 *   <li>提供统一的主键生成策略(分布式 ID 生成器,如 Snowflake)
 *   <li>支持审计追踪,记录创建人、更新人、IP 地址等信息
 * </ul>
 *
 * <h2>核心组件</h2>
 *
 * <ul>
 *   <li>{@link com.patra.starter.mybatis.entity.BaseDO} - 数据对象抽象基类,所有 DO 应继承此类
 * </ul>
 *
 * <h2>设计决策</h2>
 *
 * <ul>
 *   <li><b>分布式 ID 生成:</b> 使用 {@code @TableId(type = IdType.ASSIGN_ID)} 确保主键在分布式环境下全局唯一
 *   <li><b>审计追踪:</b> 记录创建/更新时间、操作人 ID 和姓名,支持审计需求
 *   <li><b>乐观锁:</b> 使用 {@code @Version} 注解和 {@code version} 字段防止并发更新冲突
 *   <li><b>软删除:</b> 使用 {@code @TableLogic} 注解和 {@code deleted} 字段实现逻辑删除,保留历史数据
 *   <li><b>IP 地址存储:</b> 使用字节数组高效存储 IPv4 和 IPv6 地址
 *   <li><b>审计日志:</b> {@code recordRemarks} 字段以 JSON 格式记录实体变更历史
 * </ul>
 *
 * <h2>字段说明</h2>
 *
 * <table border="1">
 *   <tr><th>字段名</th><th>类型</th><th>说明</th><th>填充时机</th></tr>
 *   <tr><td>id</td><td>Long</td><td>主键,分布式 ID</td><td>插入时自动生成</td></tr>
 *   <tr><td>createdAt</td><td>Instant</td><td>创建时间</td><td>插入时自动填充</td></tr>
 *   <tr><td>createdBy</td><td>Long</td><td>创建人 ID</td><td>插入时自动填充</td></tr>
 *   <tr><td>createdByName</td><td>String</td><td>创建人姓名</td><td>插入时自动填充</td></tr>
 *   <tr><td>updatedAt</td><td>Instant</td><td>更新时间</td><td>插入和更新时填充</td></tr>
 *   <tr><td>updatedBy</td><td>Long</td><td>更新人 ID</td><td>插入和更新时填充</td></tr>
 *   <tr><td>updatedByName</td><td>String</td><td>更新人姓名</td><td>插入和更新时填充</td></tr>
 *   <tr><td>version</td><td>Long</td><td>版本号(乐观锁)</td><td>更新时自动递增</td></tr>
 *   <tr><td>ipAddress</td><td>byte[]</td><td>客户端 IP 地址</td><td>手动设置</td></tr>
 *   <tr><td>deleted</td><td>Boolean</td><td>逻辑删除标志</td><td>删除时自动设置为 true</td></tr>
 *   <tr><td>recordRemarks</td><td>String</td><td>审计日志(JSON 格式)</td><td>手动设置</td></tr>
 * </table>
 *
 * <h2>使用示例</h2>
 *
 * <p><b>定义业务实体:</b>
 * <pre>{@code
 * @Data
 * @SuperBuilder
 * @NoArgsConstructor
 * @AllArgsConstructor
 * @EqualsAndHashCode(callSuper = true)
 * @TableName("provenance")
 * public class ProvenanceDO extends BaseDO {
 *     @TableField("name")
 *     private String name;
 *
 *     @TableField("description")
 *     private String description;
 *
 *     @TableField("config")
 *     private JsonNode config; // 使用 JsonToJsonNodeTypeHandler 自动映射
 * }
 * }</pre>
 *
 * <p><b>插入操作(自动填充审计字段):</b>
 * <pre>{@code
 * ProvenanceDO provenance = ProvenanceDO.builder()
 *     .name("PubMed")
 *     .description("PubMed 数据源配置")
 *     .build();
 * mapper.insert(provenance);
 * // createdAt、updatedAt、version 自动填充
 * }</pre>
 *
 * <p><b>更新操作(乐观锁):</b>
 * <pre>{@code
 * ProvenanceDO provenance = mapper.selectById(id);
 * provenance.setDescription("更新描述");
 * int rows = mapper.updateById(provenance);
 * if (rows == 0) {
 *     // 乐观锁冲突,version 不匹配
 *     throw new OptimisticLockException("数据已被其他用户修改");
 * }
 * // updatedAt 自动更新,version 自动递增
 * }</pre>
 *
 * <p><b>软删除操作:</b>
 * <pre>{@code
 * mapper.deleteById(id); // 实际执行: UPDATE ... SET deleted = 1
 * mapper.selectById(id); // 返回 null,查询自动过滤 deleted = 1 的记录
 * }</pre>
 *
 * <h2>注意事项</h2>
 *
 * <ul>
 *   <li><b>主键生成:</b> 确保应用启动时配置分布式 ID 生成器(如 Snowflake)
 *   <li><b>用户信息填充:</b> 当前 {@code createdBy} 等字段需要集成安全上下文才能自动填充
 *   <li><b>乐观锁使用:</b> 更新时必须使用 {@code updateById(entity)} 方法才会触发版本检查
 *   <li><b>软删除限制:</b> 物理删除需手动执行原生 SQL
 * </ul>
 *
 * <h2>相关模块</h2>
 *
 * <ul>
 *   <li>{@link com.patra.starter.mybatis.handler} - 提供 {@code AuditMetaObjectHandler} 自动填充审计字段
 *   <li>{@link com.patra.starter.mybatis.type} - 提供 JSON 类型转换器
 *   <li>{@link com.patra.starter.mybatis.autoconfig.MybatisPluginAutoConfig} - 配置乐观锁拦截器
 * </ul>
 *
 * @since 0.1.0
 * @author linqibin
 */
package com.patra.starter.mybatis.entity;
