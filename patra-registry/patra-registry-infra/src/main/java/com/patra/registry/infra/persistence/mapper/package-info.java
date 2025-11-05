/**
 * MyBatis-Plus Mapper 根包 - 数据库访问接口。
 *
 * <p>本包包含所有 MyBatis-Plus Mapper 接口,定义数据库 CRUD 操作和自定义 SQL 查询。Mapper 继承 {@code BaseMapper<DO>} 获得基础 CRUD 能力,并通过注解或 XML 定义复杂查询。
 *
 * <h2>职责</h2>
 *
 * <ul>
 *   <li>继承 MyBatis-Plus {@code BaseMapper<DO>} 获得基础 CRUD 方法
 *   <li>使用 {@code @Select}, {@code @Insert}, {@code @Update} 注解定义自定义 SQL
 *   <li>支持时态查询(基于 {@code effective_from} 和 {@code effective_until})
 *   <li>实现配置作用域查询(SOURCE/TASK 级)
 * </ul>
 *
 * <h2>包结构</h2>
 *
 * <ul>
 *   <li>{@code provenance} - 数据源相关 Mapper
 *   <li>{@code expr} - 表达式相关 Mapper
 *   <li>{@code dictionary} - 系统字典 Mapper
 * </ul>
 *
 * <h2>命名约定</h2>
 *
 * <ul>
 *   <li>Mapper 接口: {@code *Mapper extends BaseMapper<DO>}
 *   <li>查询方法: {@code select*()}, {@code selectAll*()}, {@code selectActive*()}
 *   <li>插入方法: {@code insert*()}(通常继承自 {@code BaseMapper})
 *   <li>更新方法: {@code update*()}(通常继承自 {@code BaseMapper})
 *   <li>删除方法: {@code delete*()}(通常继承自 {@code BaseMapper})
 * </ul>
 *
 * <h2>MyBatis-Plus BaseMapper 方法</h2>
 *
 * <p>所有 Mapper 自动继承以下方法:
 *
 * <ul>
 *   <li>{@code int insert(T entity)} - 插入记录
 *   <li>{@code int deleteById(Serializable id)} - 根据 ID 删除
 *   <li>{@code int updateById(T entity)} - 根据 ID 更新
 *   <li>{@code T selectById(Serializable id)} - 根据 ID 查询
 *   <li>{@code List<T> selectList(Wrapper<T> wrapper)} - 条件查询
 *   <li>{@code Long selectCount(Wrapper<T> wrapper)} - 统计记录数
 * </ul>
 *
 * <h2>自定义查询示例</h2>
 *
 * <h3>时态查询</h3>
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
 * <h3>条件查询</h3>
 *
 * <pre>{@code
 * @Select("""
 *     SELECT * FROM reg_provenance
 *     WHERE provenance_code = #{code}
 *       AND is_active = TRUE
 * """)
 * Optional<RegProvenanceDO> selectByCode(@Param("code") String code);
 * }</pre>
 *
 * <h3>批量查询</h3>
 *
 * <pre>{@code
 * @Select("""
 *     SELECT * FROM reg_provenance
 *     WHERE is_active = TRUE
 *     ORDER BY provenance_code
 * """)
 * List<RegProvenanceDO> selectAllActive();
 * }</pre>
 *
 * <h2>技术细节</h2>
 *
 * <ul>
 *   <li><b>MyBatis-Plus BaseMapper</b>: 提供开箱即用的 CRUD 方法
 *   <li><b>@Mapper 注解</b>: Spring Boot 自动扫描并注册为 Bean
 *   <li><b>@Param 注解</b>: 绑定方法参数到 SQL 占位符
 *   <li><b>Optional 返回值</b>: 单条记录查询返回 {@code Optional<DO>}
 *   <li><b>List 返回值</b>: 多条记录查询返回 {@code List<DO>}
 * </ul>
 *
 * <h2>使用示例</h2>
 *
 * <pre>{@code
 * @Mapper
 * public interface RegProvenanceMapper extends BaseMapper<RegProvenanceDO> {
 *
 *     @Select("""
 *         SELECT * FROM reg_provenance
 *         WHERE provenance_code = #{code}
 *           AND is_active = TRUE
 *     """)
 *     Optional<RegProvenanceDO> selectByCode(@Param("code") String code);
 *
 *     @Select("""
 *         SELECT * FROM reg_provenance
 *         WHERE is_active = TRUE
 *         ORDER BY provenance_code
 *     """)
 *     List<RegProvenanceDO> selectAllActive();
 * }
 * }</pre>
 *
 * @since 0.1.0
 * @author linqibin
 */
package com.patra.registry.infra.persistence.mapper;
