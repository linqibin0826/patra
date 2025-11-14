/**
 * 数据源 Mapper 包 - Provenance 相关数据库访问接口。
 *
 * <p>本包包含数据源(Provenance)及其运营配置的 MyBatis-Plus Mapper 接口,提供时态查询、配置作用域查询和聚合查询能力。
 *
 * <h2>职责</h2>
 *
 * <ul>
 *   <li>提供数据源元数据的 CRUD 操作
 *   <li>提供运营配置的时态查询(基于 {@code effective_from/until})
 *   <li>支持配置作用域查询(SOURCE/TASK 级)
 *   <li>实现配置优先级查询逻辑
 * </ul>
 *
 * <h2>核心 Mapper</h2>
 *
 * <ul>
 *   <li>{@link com.patra.registry.infra.persistence.mapper.provenance.RegProvenanceMapper} - 数据源
 *       Mapper
 *       <ul>
 *         <li>{@code selectByCode(String code)} - 根据代码查询数据源
 *         <li>{@code selectAllActive()} - 查询所有激活的数据源
 *       </ul>
 *   <li>{@link com.patra.registry.infra.persistence.mapper.provenance.RegProvWindowOffsetCfgMapper}
 *       - 时间窗口偏移 Mapper
 *   <li>{@link com.patra.registry.infra.persistence.mapper.provenance.RegProvPaginationCfgMapper} -
 *       分页配置 Mapper
 *   <li>{@link com.patra.registry.infra.persistence.mapper.provenance.RegProvHttpCfgMapper} - HTTP
 *       配置 Mapper
 *   <li>{@link com.patra.registry.infra.persistence.mapper.provenance.RegProvBatchingCfgMapper} -
 *       批处理配置 Mapper
 *   <li>{@link com.patra.registry.infra.persistence.mapper.provenance.RegProvRetryCfgMapper} - 重试配置
 *       Mapper
 *   <li>{@link com.patra.registry.infra.persistence.mapper.provenance.RegProvRateLimitCfgMapper} -
 *       速率限制 Mapper
 * </ul>
 *
 * <h2>时态查询模式</h2>
 *
 * <p>所有配置 Mapper 提供时态查询方法,查询指定时间点的有效配置:
 *
 * <pre>{@code
 * @Select("""
 *     SELECT * FROM reg_prov_http_cfg
 *     WHERE provenance_id = #{provenanceId}
 *       AND scope_key = #{scopeKey}
 *       AND effective_from <= #{at}
 *       AND (effective_until IS NULL OR effective_until > #{at})
 *     ORDER BY effective_from DESC
 *     LIMIT 1
 * """)
 * Optional<RegProvHttpCfgDO> selectActiveConfig(
 *     @Param("provenanceId") Long provenanceId,
 *     @Param("scopeKey") String scopeKey,
 *     @Param("at") Instant at
 * );
 * }</pre>
 *
 * <h2>配置作用域查询</h2>
 *
 * <p>配置 Mapper 支持按作用域查询,实现 TASK 级覆盖 SOURCE 级的优先级:
 *
 * <pre>{@code
 * // 1. 尝试查询 TASK 级配置
 * String taskKey = "TASK:HARVEST";
 * var taskConfig = mapper.selectActiveConfig(provenanceId, taskKey, Instant.now());
 *
 * // 2. 如果 TASK 级不存在,回退到 SOURCE 级
 * if (taskConfig.isEmpty()) {
 *     String sourceKey = "SOURCE";
 *     taskConfig = mapper.selectActiveConfig(provenanceId, sourceKey, Instant.now());
 * }
 * }</pre>
 *
 * <h2>常用查询方法</h2>
 *
 * <ul>
 *   <li>{@code selectByCode(String code)} - 根据数据源代码查询
 *   <li>{@code selectAllActive()} - 查询所有激活的记录
 *   <li>{@code selectActiveConfig(...)} - 查询指定时间点的有效配置
 *   <li>{@code selectByProvenance(Long provenanceId)} - 查询指定数据源的所有配置
 * </ul>
 *
 * <h2>SQL 编写规范</h2>
 *
 * <ul>
 *   <li>使用 Java 15+ 文本块({@code """..."""})编写多行 SQL
 *   <li>使用 {@code @Param} 注解显式绑定参数
 *   <li>单条记录查询返回 {@code Optional<DO>}
 *   <li>多条记录查询返回 {@code List<DO>}
 *   <li>时态查询必须包含 {@code effective_from <= #{at} AND (effective_until IS NULL OR effective_until >
 *       #{at})}
 * </ul>
 *
 * @since 0.1.0
 * @author linqibin
 */
package com.patra.registry.infra.persistence.mapper.provenance;
