/// 表达式 Mapper 包 - Expr 相关数据库访问接口。
///
/// 本包包含表达式元数据的 MyBatis-Plus Mapper 接口,提供字段定义、能力声明、参数映射和渲染规则的查询能力。
///
/// ## 职责
///
/// - 提供表达式字段定义的 CRUD 操作
///   - 查询数据源的能力声明(HARVEST, UPDATE 等)
///   - 查询 API 参数映射规则
///   - 查询表达式渲染规则
///
/// ## 核心 Mapper
///
/// - {@link com.patra.registry.infra.persistence.mapper.expr.RegExprFieldDictMapper} - 表达式字段
///       Mapper
///
/// - `selectByCode(String code)` - 根据字段代码查询
///         - `selectAll()` - 查询所有字段定义
///
///   - {@link com.patra.registry.infra.persistence.mapper.expr.RegProvExprCapabilityMapper} -
///       数据源能力 Mapper
///
/// - `selectByProvenance(Long provenanceId)` - 查询数据源支持的能力
///         - `selectByOperation(String operationType)` - 查询支持指定操作的数据源
///
///   - {@link com.patra.registry.infra.persistence.mapper.expr.RegProvApiParamMapMapper} - API
///       参数映射 Mapper
///
/// - `selectByProvenance(Long provenanceId)` - 查询数据源的参数映射
///         - `selectByLogicalParam(String logicalParam)` - 查询逻辑参数的映射
///
///   - {@link com.patra.registry.infra.persistence.mapper.expr.RegProvExprRenderRuleMapper} - 渲染规则
///       Mapper
///
/// - `selectByProvenance(Long provenanceId)` - 查询数据源的渲染规则
///         - `selectByField(String fieldCode)` - 查询字段的渲染规则
///
/// ## 表达式快照查询
///
/// 表达式 Mapper 支持快照查询,一次性加载完整的表达式元数据:
///
/// ```java
/// // 1. 查询所有字段定义
/// List<RegExprFieldDictDO> fields = fieldDictMapper.selectAll();
///
/// // 2. 查询数据源能力
/// List<RegProvExprCapabilityDO> capabilities =
///     capabilityMapper.selectByProvenance(provenanceId);
///
/// // 3. 查询参数映射
/// List<RegProvApiParamMapDO> mappings =
///     paramMapMapper.selectByProvenance(provenanceId);
///
/// // 4. 查询渲染规则
/// List<RegProvExprRenderRuleDO> rules =
///     renderRuleMapper.selectByProvenance(provenanceId);
///
/// // 5. 组装为表达式快照
/// ExprSnapshot snapshot = assembler.toSnapshot(fields, capabilities, mappings, rules);
/// ```
///
/// ## 时态支持
///
/// 部分表达式元数据支持时态查询:
///
/// ```java
/// @Select("""
///     SELECT * FROM reg_prov_api_param_map
///     WHERE provenance_id = #{provenanceId
///       AND effective_from <= #{at
///       AND (effective_until IS NULL OR effective_until > #{at)
/// """)
/// List<RegProvApiParamMapDO> selectActiveByProvenance(
///     @Param("provenanceId") Long provenanceId,
///     @Param("at") Instant at
/// );
/// ```
///
/// ## 常用查询方法
///
/// - `selectAll()` - 查询所有记录(字段定义)
///   - `selectByProvenance(Long provenanceId)` - 查询指定数据源的元数据
///   - `selectByOperation(String operationType)` - 按操作类型查询
///   - `selectByField(String fieldCode)` - 按字段代码查询
///
/// ## 使用示例
///
/// ```java
/// @Mapper
/// public interface RegProvApiParamMapMapper extends BaseMapper<RegProvApiParamMapDO> {
///
///     @Select("""
///         SELECT * FROM reg_prov_api_param_map
///         WHERE provenance_id = #{provenanceId
///         ORDER BY logical_param
///     """)
///     List<RegProvApiParamMapDO> selectByProvenance(
///         @Param("provenanceId") Long provenanceId
///     );
///
///     @Select("""
///         SELECT * FROM reg_prov_api_param_map
///         WHERE logical_param = #{logicalParam
///     """)
///     List<RegProvApiParamMapDO> selectByLogicalParam(
///         @Param("logicalParam") String logicalParam
///     );
/// ```
///
/// @since 0.1.0
/// @author linqibin
package com.patra.registry.infra.persistence.mapper.expr;
