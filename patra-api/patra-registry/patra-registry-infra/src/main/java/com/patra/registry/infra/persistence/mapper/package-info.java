/// MyBatis-Plus Mapper 根包 - 数据库访问接口。
/// 
/// 本包包含所有 MyBatis-Plus Mapper 接口,定义数据库 CRUD 操作和自定义 SQL 查询。Mapper 继承 `BaseMapper<DO>` 获得基础
/// CRUD 能力,并通过注解或 XML 定义复杂查询。
/// 
/// ## 职责
/// 
/// - 继承 MyBatis-Plus `BaseMapper<DO>` 获得基础 CRUD 方法
///   - 使用 `@Select`, `@Insert`, `@Update` 注解定义自定义 SQL
///   - 支持时态查询(基于 `effective_from` 和 `effective_until`)
///   - 实现配置作用域查询(SOURCE/TASK 级)
/// 
/// ## 包结构
/// 
/// - `provenance` - 数据源相关 Mapper
///   - `expr` - 表达式相关 Mapper
///   - `dictionary` - 系统字典 Mapper
/// 
/// ## 命名约定
/// 
/// - Mapper 接口: `*Mapper extends BaseMapper<DO>`
///   - 查询方法: `select*()`, `selectAll*()`, `selectActive*()`
///   - 插入方法: `insert*()`(通常继承自 `BaseMapper`)
///   - 更新方法: `update*()`(通常继承自 `BaseMapper`)
///   - 删除方法: `delete*()`(通常继承自 `BaseMapper`)
/// 
/// ## MyBatis-Plus BaseMapper 方法
/// 
/// 所有 Mapper 自动继承以下方法:
/// 
/// - `int insert(T entity)` - 插入记录
///   - `int deleteById(Serializable id)` - 根据 ID 删除
///   - `int updateById(T entity)` - 根据 ID 更新
///   - `T selectById(Serializable id)` - 根据 ID 查询
///   - `List<T> selectList(Wrapper<T> wrapper)` - 条件查询
///   - `Long selectCount(Wrapper<T> wrapper)` - 统计记录数
/// 
/// ## 自定义查询示例
/// 
/// ### 时态查询
/// 
/// ```java
/// @Select("""
///     SELECT * FROM reg_prov_http_cfg
///     WHERE provenance_id = #{provenanceId
///       AND scope_key = #{scopeKey
///       AND effective_from <= #{at
///       AND (effective_until IS NULL OR effective_until > #{at)
///     ORDER BY effective_from DESC
///     LIMIT 1
/// """)
/// Optional<RegProvHttpCfgDO> selectActiveConfig(
///     @Param("provenanceId") Long provenanceId,
///     @Param("scopeKey") String scopeKey,
///     @Param("at") Instant at
/// );
/// ```
/// 
/// ### 条件查询
/// 
/// ```java
/// @Select("""
///     SELECT * FROM reg_provenance
///     WHERE provenance_code = #{code
///       AND is_active = TRUE
/// """)
/// Optional<RegProvenanceDO> selectByCode(@Param("code") String code);
/// ```
/// 
/// ### 批量查询
/// 
/// ```java
/// @Select("""
///     SELECT * FROM reg_provenance
///     WHERE is_active = TRUE
///     ORDER BY provenance_code
/// """)
/// List<RegProvenanceDO> selectAllActive();
/// ```
/// 
/// ## 技术细节
/// 
/// - **MyBatis-Plus BaseMapper**: 提供开箱即用的 CRUD 方法
///   - **@Mapper 注解**: Spring Boot 自动扫描并注册为 Bean
///   - **@Param 注解**: 绑定方法参数到 SQL 占位符
///   - **Optional 返回值**: 单条记录查询返回 `Optional<DO>`
///   - **List 返回值**: 多条记录查询返回 `List<DO>`
/// 
/// ## 使用示例
/// 
/// ```java
/// @Mapper
/// public interface RegProvenanceMapper extends BaseMapper<RegProvenanceDO> {
/// 
///     @Select("""
///         SELECT * FROM reg_provenance
///         WHERE provenance_code = #{code
///           AND is_active = TRUE
///     """)
///     Optional<RegProvenanceDO> selectByCode(@Param("code") String code);
/// 
///     @Select("""
///         SELECT * FROM reg_provenance
///         WHERE is_active = TRUE
///         ORDER BY provenance_code
///     """)
///     List<RegProvenanceDO> selectAllActive();
/// ```
/// 
/// @since 0.1.0
/// @author linqibin
package com.patra.registry.infra.persistence.mapper;
