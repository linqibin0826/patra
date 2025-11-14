/**
 * 表达式实体包 - Expr 相关数据库实体。
 *
 * <p>本包包含表达式元数据的数据库实体对象,映射 {@code reg_expr_*} 和 {@code reg_prov_*} 系列表。表达式元数据支持 {@code
 * patra-expr-kernel} 动态表达式编译,提供 API 参数映射、字段定义、能力声明和渲染规则。
 *
 * <h2>职责</h2>
 *
 * <ul>
 *   <li>映射表达式字段定义表({@code reg_expr_field_dict})
 *   <li>映射数据源能力表({@code reg_prov_expr_capability})
 *   <li>映射 API 参数映射表({@code reg_prov_api_param_map})
 *   <li>映射表达式渲染规则表({@code reg_prov_expr_render_rule})
 * </ul>
 *
 * <h2>核心实体</h2>
 *
 * <ul>
 *   <li>{@link com.patra.registry.infra.persistence.entity.expr.RegExprFieldDictDO} - 表达式字段定义
 *       <ul>
 *         <li>表: {@code reg_expr_field_dict}
 *         <li>字段: {@code field_code}, {@code field_name}, {@code data_type}, {@code semantic_key}
 *         <li>用途: 定义可用于表达式的字段及其类型和语义
 *       </ul>
 *   <li>{@link com.patra.registry.infra.persistence.entity.expr.RegProvExprCapabilityDO} - 数据源能力定义
 *       <ul>
 *         <li>表: {@code reg_prov_expr_capability}
 *         <li>字段: {@code provenance_id}, {@code operation_type}, {@code is_supported}
 *         <li>用途: 声明数据源支持的操作能力(HARVEST, UPDATE 等)
 *       </ul>
 *   <li>{@link com.patra.registry.infra.persistence.entity.expr.RegProvApiParamMapDO} - API 参数映射
 *       <ul>
 *         <li>表: {@code reg_prov_api_param_map}
 *         <li>字段: {@code provenance_id}, {@code logical_param}, {@code api_query_param}, {@code
 *             is_required}
 *         <li>用途: 映射逻辑参数到 API 查询参数(如 {@code publicationDate} → {@code pub_date})
 *       </ul>
 *   <li>{@link com.patra.registry.infra.persistence.entity.expr.RegProvExprRenderRuleDO} - 表达式渲染规则
 *       <ul>
 *         <li>表: {@code reg_prov_expr_render_rule}
 *         <li>字段: {@code provenance_id}, {@code operation_type}, {@code field_code}, {@code
 *             render_template}
 *         <li>用途: 定义表达式如何渲染为 API 查询字符串
 *       </ul>
 * </ul>
 *
 * <h2>表达式元数据关系</h2>
 *
 * <pre>{@code
 * [ExprField] - 定义字段(如 publicationDate, authorName)
 *     ↓ 被引用
 * [ApiParamMapping] - 映射到 API 参数(如 pub_date, author)
 *     ↓ 用于
 * [ExprRenderRule] - 渲染规则(如 "${field}:${value}" → "pub_date:2025-01-01")
 *     ↓ 生成
 * API 查询字符串 → 外部数据源
 * }</pre>
 *
 * <h2>时态支持</h2>
 *
 * <p>部分表达式元数据实体支持时态特性:
 *
 * <pre>{@code
 * @TableField("effective_from")
 * private Instant effectiveFrom;
 *
 * @TableField("effective_until")
 * private Instant effectiveUntil;
 * }</pre>
 *
 * <h2>数据库表设计</h2>
 *
 * <table border="1">
 *   <caption>表达式元数据表</caption>
 *   <tr>
 *     <th>表名</th>
 *     <th>用途</th>
 *     <th>关键字段</th>
 *   </tr>
 *   <tr>
 *     <td>{@code reg_expr_field_dict}</td>
 *     <td>字段定义字典</td>
 *     <td>{@code field_code}, {@code data_type}, {@code semantic_key}</td>
 *   </tr>
 *   <tr>
 *     <td>{@code reg_prov_expr_capability}</td>
 *     <td>数据源能力声明</td>
 *     <td>{@code provenance_id}, {@code operation_type}, {@code is_supported}</td>
 *   </tr>
 *   <tr>
 *     <td>{@code reg_prov_api_param_map}</td>
 *     <td>参数映射</td>
 *     <td>{@code logical_param}, {@code api_query_param}, {@code is_required}</td>
 *   </tr>
 *   <tr>
 *     <td>{@code reg_prov_expr_render_rule}</td>
 *     <td>渲染规则</td>
 *     <td>{@code field_code}, {@code render_template}, {@code render_format}</td>
 *   </tr>
 * </table>
 *
 * <h2>使用示例</h2>
 *
 * <pre>{@code
 * // 字段定义
 * RegExprFieldDictDO field = new RegExprFieldDictDO();
 * field.setFieldCode("publicationDate");
 * field.setDataType("DATE");
 * field.setSemanticKey("temporal");
 *
 * // API 参数映射
 * RegProvApiParamMapDO mapping = new RegProvApiParamMapDO();
 * mapping.setLogicalParam("publicationDate");
 * mapping.setApiQueryParam("pub_date");
 * mapping.setIsRequired(true);
 *
 * // 渲染规则
 * RegProvExprRenderRuleDO rule = new RegProvExprRenderRuleDO();
 * rule.setFieldCode("publicationDate");
 * rule.setRenderTemplate("${field}:${value}");
 * rule.setRenderFormat("yyyy-MM-dd");
 * }</pre>
 *
 * @since 0.1.0
 * @author linqibin
 */
package com.patra.registry.infra.persistence.entity.expr;
