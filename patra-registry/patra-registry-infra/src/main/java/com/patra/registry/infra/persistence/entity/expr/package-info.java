/// 表达式实体包 - Expr 相关数据库实体。
///
/// 本包包含表达式元数据的数据库实体对象,映射 `reg_expr_*` 和 `reg_prov_*` 系列表。表达式元数据支持 `patra-expr-kernel` 动态表达式编译,提供
// API 参数映射、字段定义、能力声明和渲染规则。
///
/// ## 职责
///
/// - 映射表达式字段定义表(`reg_expr_field_dict`)
///   - 映射数据源能力表(`reg_prov_expr_capability`)
///   - 映射 API 参数映射表(`reg_prov_api_param_map`)
///   - 映射表达式渲染规则表(`reg_prov_expr_render_rule`)
///
/// ## 核心实体
///
/// - {@link com.patra.registry.infra.persistence.entity.expr.RegExprFieldDictDO} - 表达式字段定义
///
/// - 表: `reg_expr_field_dict`
///         - 字段: `field_code`, `field_name`, `data_type`, `semantic_key`
///         - 用途: 定义可用于表达式的字段及其类型和语义
///
///   - {@link com.patra.registry.infra.persistence.entity.expr.RegProvExprCapabilityDO} - 数据源能力定义
///
/// - 表: `reg_prov_expr_capability`
///         - 字段: `provenance_id`, `operation_type`, `is_supported`
///         - 用途: 声明数据源支持的操作能力(HARVEST, UPDATE 等)
///
///   - {@link com.patra.registry.infra.persistence.entity.expr.RegProvApiParamMapDO} - API 参数映射
///
/// - 表: `reg_prov_api_param_map`
///         - 字段: `provenance_id`, `logical_param`, `api_query_param`, `is_required`
///         - 用途: 映射逻辑参数到 API 查询参数(如 `publicationDate` → `pub_date`)
///
///   - {@link com.patra.registry.infra.persistence.entity.expr.RegProvExprRenderRuleDO} - 表达式渲染规则
///
/// - 表: `reg_prov_expr_render_rule`
///         - 字段: `provenance_id`, `operation_type`, `field_code`, `render_template`
///         - 用途: 定义表达式如何渲染为 API 查询字符串
///
/// ## 表达式元数据关系
///
/// ```java
/// [ExprField] - 定义字段(如 publicationDate, authorName)
///     ↓ 被引用
/// [ApiParamMapping] - 映射到 API 参数(如 pub_date, author)
///     ↓ 用于
/// [ExprRenderRule] - 渲染规则(如 "${field:${value" → "pub_date:2025-01-01")
///     ↓ 生成
/// API 查询字符串 → 外部数据源
/// ```
///
/// ## 时态支持
///
/// 部分表达式元数据实体支持时态特性:
///
/// ```java
/// @TableField("effective_from")
/// private Instant effectiveFrom;
///
/// @TableField("effective_until")
/// private Instant effectiveUntil;
/// ```
///
/// ## 数据库表设计
///
/// <table border="1">
///   <caption>表达式元数据表</caption>
///   <tr>
///     <th>表名</th>
///     <th>用途</th>
///     <th>关键字段</th>
///   </tr>
///   <tr>
///     <td>`reg_expr_field_dict`</td>
///     <td>字段定义字典</td>
///     <td>`field_code`, `data_type`, `semantic_key`</td>
///   </tr>
///   <tr>
///     <td>`reg_prov_expr_capability`</td>
///     <td>数据源能力声明</td>
///     <td>`provenance_id`, `operation_type`, `is_supported`</td>
///   </tr>
///   <tr>
///     <td>`reg_prov_api_param_map`</td>
///     <td>参数映射</td>
///     <td>`logical_param`, `api_query_param`, `is_required`</td>
///   </tr>
///   <tr>
///     <td>`reg_prov_expr_render_rule`</td>
///     <td>渲染规则</td>
///     <td>`field_code`, `render_template`, `render_format`</td>
///   </tr>
/// </table>
///
/// ## 使用示例
///
/// ```java
/// // 字段定义
/// RegExprFieldDictDO field = new RegExprFieldDictDO();
/// field.setFieldCode("publicationDate");
/// field.setDataType("DATE");
/// field.setSemanticKey("temporal");
///
/// // API 参数映射
/// RegProvApiParamMapDO mapping = new RegProvApiParamMapDO();
/// mapping.setLogicalParam("publicationDate");
/// mapping.setApiQueryParam("pub_date");
/// mapping.setIsRequired(true);
///
/// // 渲染规则
/// RegProvExprRenderRuleDO rule = new RegProvExprRenderRuleDO();
/// rule.setFieldCode("publicationDate");
/// rule.setRenderTemplate("${field:${value");
/// rule.setRenderFormat("yyyy-MM-dd");
/// ```
///
/// @since 0.1.0
/// @author linqibin
package com.patra.registry.infra.persistence.entity.expr;
