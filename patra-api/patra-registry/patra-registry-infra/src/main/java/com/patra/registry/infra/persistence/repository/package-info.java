/**
 * 仓储接口实现 - 领域端口的持久化实现。
 *
 * <p>本包包含领域仓储接口的 MyBatis-Plus 实现,负责协调 Mapper 和 Converter,将领域对象持久化到数据库并查询聚合根。仓储实现是领域层和持久化技术之间的桥梁。
 *
 * <h2>职责</h2>
 *
 * <ul>
 *   <li>实现 {@code patra-registry-domain} 定义的仓储端口接口
 *   <li>协调多个 Mapper 完成复杂查询和聚合操作
 *   <li>调用 Converter 实现 DO ↔ 领域对象的双向转换
 *   <li>实现配置优先级逻辑(TASK 级覆盖 SOURCE 级)
 *   <li>处理时态查询(基于 {@code effectiveFrom} 和 {@code effectiveUntil})
 * </ul>
 *
 * <h2>核心仓储实现</h2>
 *
 * <ul>
 *   <li>{@link com.patra.registry.infra.persistence.repository.ProvenanceConfigRepositoryMpImpl} -
 *       数据源配置仓储
 *       <ul>
 *         <li>查询数据源元数据
 *         <li>加载完整配置聚合(HTTP、分页、重试、速率限制等)
 *         <li>支持时态切片和配置优先级
 *       </ul>
 *   <li>{@link com.patra.registry.infra.persistence.repository.ExprRepositoryMpImpl} - 表达式仓储
 *       <ul>
 *         <li>加载表达式快照(字段、能力、映射、规则)
 *         <li>支持时态查询
 *       </ul>
 * </ul>
 *
 * <h2>命名约定</h2>
 *
 * <ul>
 *   <li>仓储实现: {@code *RepositoryMpImpl}
 *   <li>查询方法: {@code find*()}, {@code findAll*()}, {@code load*()}
 *   <li>命令方法: {@code save*()}, {@code update*()}, {@code delete*()}
 * </ul>
 *
 * <h2>设计模式</h2>
 *
 * <ul>
 *   <li><b>仓储模式</b>: 封装数据访问逻辑,提供集合式接口
 *   <li><b>组合模式</b>: 组合多个 Mapper 完成复杂聚合查询
 *   <li><b>策略模式</b>: 实现配置优先级策略(TASK → SOURCE → 默认值)
 * </ul>
 *
 * <h2>配置优先级实现</h2>
 *
 * <pre>{@code
 * // 1. 尝试查询 TASK 级配置
 * Optional<ConfigDO> taskConfig = mapper.selectByScope(TASK, operationType, at);
 *
 * // 2. 如果 TASK 级不存在,回退到 SOURCE 级
 * if (taskConfig.isEmpty()) {
 *     taskConfig = mapper.selectByScope(SOURCE, null, at);
 * }
 *
 * // 3. 转换为领域对象
 * return taskConfig.map(converter::toDomain);
 * }</pre>
 *
 * <h2>时态查询实现</h2>
 *
 * <pre>{@code
 * // 查询指定时间点有效的配置
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
 * <h2>使用示例</h2>
 *
 * <pre>{@code
 * @Repository
 * @RequiredArgsConstructor
 * public class ProvenanceConfigRepositoryMpImpl implements ProvenanceConfigRepository {
 *     private final RegProvenanceMapper provenanceMapper;
 *     private final RegProvHttpCfgMapper httpCfgMapper;
 *     private final ProvenanceEntityConverter converter;
 *
 *     @Override
 *     public Optional<HttpConfig> findActiveHttpConfig(
 *         Long provenanceId, String operationType, Instant at) {
 *         // 1. 尝试 TASK 级
 *         String taskKey = RegistryKeyStandardizer.toScopeKey("TASK", operationType);
 *         var taskConfig = httpCfgMapper.selectActiveConfig(provenanceId, taskKey, at);
 *
 *         // 2. 回退到 SOURCE 级
 *         if (taskConfig.isEmpty()) {
 *             String sourceKey = RegistryKeyStandardizer.toScopeKey("SOURCE", null);
 *             taskConfig = httpCfgMapper.selectActiveConfig(provenanceId, sourceKey, at);
 *         }
 *
 *         // 3. 转换
 *         return taskConfig.map(converter::toDomain);
 *     }
 * }
 * }</pre>
 *
 * @since 0.1.0
 * @author linqibin
 */
package com.patra.registry.infra.persistence.repository;
