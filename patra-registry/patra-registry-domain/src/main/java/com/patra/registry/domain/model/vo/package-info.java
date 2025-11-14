/**
 * Registry 值对象根包 - DDD 值对象模式实现。
 *
 * <p>本包是 Registry 服务所有值对象的根包,包含多个子包,按业务领域组织值对象定义。 值对象是 DDD 战术设计的核心模式,表示通过属性值而非标识符定义的领域概念。
 *
 * <h2>职责</h2>
 *
 * <ul>
 *   <li>定义不可变的值对象,封装业务属性和验证逻辑
 *   <li>按业务领域组织值对象(provenance、expr)
 *   <li>提供值语义比较(基于属性值而非对象引用)
 *   <li>支持领域聚合根的组合
 * </ul>
 *
 * <h2>子包结构</h2>
 *
 * <ul>
 *   <li>{@link com.patra.registry.domain.model.vo.provenance} - 数据源配置值对象, 包含
 *       HTTP、重试、分页、批处理、限流、时间窗口等配置
 *   <li>{@link com.patra.registry.domain.model.vo.expr} - 表达式值对象, 包含字段定义、渲染规则、参数映射、能力声明等
 * </ul>
 *
 * <h2>设计原则</h2>
 *
 * <ul>
 *   <li><strong>不可变性</strong>: 所有值对象使用 {@code record} 实现,创建后不可修改
 *   <li><strong>自验证</strong>: 通过规范构造器强制执行业务约束,快速失败
 *   <li><strong>值语义</strong>: 基于属性值比较相等性,支持集合操作和去重
 *   <li><strong>组合优先</strong>: 通过组合简单值对象构建复杂领域概念
 *   <li><strong>框架无关</strong>: 纯 Java 对象,不依赖 Spring、JPA 等框架
 * </ul>
 *
 * <h2>值对象 vs 实体</h2>
 *
 * <table border="1">
 *   <tr>
 *     <th>特性</th>
 *     <th>值对象</th>
 *     <th>实体</th>
 *   </tr>
 *   <tr>
 *     <td>标识符</td>
 *     <td>无唯一标识符</td>
 *     <td>有唯一标识符(ID)</td>
 *   </tr>
 *   <tr>
 *     <td>相等性</td>
 *     <td>基于属性值</td>
 *     <td>基于标识符</td>
 *   </tr>
 *   <tr>
 *     <td>可变性</td>
 *     <td>不可变</td>
 *     <td>可变(通过方法修改状态)</td>
 *   </tr>
 *   <tr>
 *     <td>生命周期</td>
 *     <td>无独立生命周期</td>
 *     <td>有独立生命周期</td>
 *   </tr>
 * </table>
 *
 * <h2>使用场景</h2>
 *
 * <ul>
 *   <li><strong>配置管理</strong>: HTTP 配置、重试配置等运营配置
 *   <li><strong>元数据描述</strong>: 数据源元数据、表达式字段定义
 *   <li><strong>业务规则封装</strong>: 将验证逻辑封装在值对象内部
 *   <li><strong>聚合组合</strong>: 作为聚合根的组成部分
 * </ul>
 *
 * <h2>使用示例</h2>
 *
 * <pre>{@code
 * // 创建不可变值对象
 * HttpConfig httpConfig = new HttpConfig(
 *     1L,
 *     1L,
 *     "HARVEST",
 *     Instant.now(),
 *     null,
 *     null,
 *     5000,
 *     10000,
 *     30000,
 *     true,
 *     null,
 *     "RESPECT",
 *     60000,
 *     null,
 *     null
 * );
 *
 * // 值语义比较
 * HttpConfig another = new HttpConfig(...); // 相同属性值
 * boolean equals = httpConfig.equals(another); // true
 *
 * // 组合值对象构建聚合根
 * ProvenanceConfiguration config = new ProvenanceConfiguration(
 *     provenance,
 *     windowOffset,
 *     pagination,
 *     httpConfig,  // 组合值对象
 *     batching,
 *     retry,
 *     rateLimit
 * );
 * }</pre>
 *
 * @since 0.1.0
 * @author linqibin
 */
package com.patra.registry.domain.model.vo;
