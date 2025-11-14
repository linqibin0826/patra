/**
 * 系统字典实体包 - Dictionary 相关数据库实体。
 *
 * <p>本包包含系统字典的数据库实体对象,映射 {@code reg_sys_dict_*} 系列表。系统字典提供统一的枚举代码和业务字典项管理,支持多语言、别名和层级结构。
 *
 * <h2>职责</h2>
 *
 * <ul>
 *   <li>映射字典类型表({@code reg_sys_dict_type})
 *   <li>映射字典项表({@code reg_sys_dict_item})
 *   <li>映射字典项别名表({@code reg_sys_dict_item_alias})
 *   <li>支持字典的层级结构和多语言
 * </ul>
 *
 * <h2>核心实体</h2>
 *
 * <ul>
 *   <li>{@link com.patra.registry.infra.persistence.entity.dictionary.RegSysDictTypeDO} - 字典类型
 *       <ul>
 *         <li>表: {@code reg_sys_dict_type}
 *         <li>字段: {@code type_code}, {@code type_name}, {@code description}
 *         <li>用途: 定义字典分类(如 COUNTRY_CODE, LANGUAGE, STATUS)
 *       </ul>
 *   <li>{@link com.patra.registry.infra.persistence.entity.dictionary.RegSysDictItemDO} - 字典项
 *       <ul>
 *         <li>表: {@code reg_sys_dict_item}
 *         <li>字段: {@code dict_type_code}, {@code item_code}, {@code item_value}, {@code
 *             parent_item_code}
 *         <li>用途: 定义具体的字典值(如 COUNTRY_CODE → CN, US, GB)
 *       </ul>
 *   <li>{@link com.patra.registry.infra.persistence.entity.dictionary.RegSysDictItemAliasDO} -
 *       字典项别名
 *       <ul>
 *         <li>表: {@code reg_sys_dict_item_alias}
 *         <li>字段: {@code dict_item_id}, {@code alias_value}, {@code locale}
 *         <li>用途: 支持同一字典项的多种表示和国际化(如 China/中国/Chine)
 *       </ul>
 * </ul>
 *
 * <h2>字典层级结构</h2>
 *
 * <p>字典项支持父子层级关系,通过 {@code parent_item_code} 字段实现:
 *
 * <pre>{@code
 * [字典类型: REGION]
 *   ├── ASIA (parent: null)
 *   │   ├── CHINA (parent: ASIA)
 *   │   └── JAPAN (parent: ASIA)
 *   └── EUROPE (parent: null)
 *       ├── UK (parent: EUROPE)
 *       └── FRANCE (parent: EUROPE)
 * }</pre>
 *
 * <h2>多语言支持</h2>
 *
 * <p>字典项别名支持多语言和同义词:
 *
 * <pre>{@code
 * [字典项: CHINA]
 *   ├── CN (locale: en, alias_value: China)
 *   ├── CN (locale: zh, alias_value: 中国)
 *   ├── CN (locale: fr, alias_value: Chine)
 *   └── CN (locale: en, alias_value: PRC) // 同义词
 * }</pre>
 *
 * <h2>数据库表设计</h2>
 *
 * <table border="1">
 *   <caption>系统字典表</caption>
 *   <tr>
 *     <th>表名</th>
 *     <th>用途</th>
 *     <th>关键字段</th>
 *   </tr>
 *   <tr>
 *     <td>{@code reg_sys_dict_type}</td>
 *     <td>字典类型定义</td>
 *     <td>{@code type_code}, {@code type_name}, {@code is_system}</td>
 *   </tr>
 *   <tr>
 *     <td>{@code reg_sys_dict_item}</td>
 *     <td>字典项</td>
 *     <td>{@code dict_type_code}, {@code item_code}, {@code item_value}, {@code parent_item_code}</td>
 *   </tr>
 *   <tr>
 *     <td>{@code reg_sys_dict_item_alias}</td>
 *     <td>字典项别名</td>
 *     <td>{@code dict_item_id}, {@code alias_value}, {@code locale}</td>
 *   </tr>
 * </table>
 *
 * <h2>使用场景</h2>
 *
 * <ul>
 *   <li><b>国家代码</b>: {@code COUNTRY_CODE} → CN, US, GB, FR...
 *   <li><b>语言代码</b>: {@code LANGUAGE} → en, zh, fr, ja...
 *   <li><b>状态枚举</b>: {@code STATUS} → ACTIVE, INACTIVE, PENDING...
 *   <li><b>业务分类</b>: {@code CATEGORY} → TECH, HEALTH, FINANCE...
 * </ul>
 *
 * <h2>使用示例</h2>
 *
 * <pre>{@code
 * // 字典类型
 * RegSysDictTypeDO type = new RegSysDictTypeDO();
 * type.setTypeCode("COUNTRY_CODE");
 * type.setTypeName("Country Codes");
 * type.setIsSystem(true);
 *
 * // 字典项
 * RegSysDictItemDO item = new RegSysDictItemDO();
 * item.setDictTypeCode("COUNTRY_CODE");
 * item.setItemCode("CN");
 * item.setItemValue("China");
 * item.setParentItemCode(null);
 *
 * // 字典别名
 * RegSysDictItemAliasDO alias = new RegSysDictItemAliasDO();
 * alias.setDictItemId(item.getId());
 * alias.setAliasValue("中国");
 * alias.setLocale("zh");
 * }</pre>
 *
 * @since 0.1.0
 * @author linqibin
 */
package com.patra.registry.infra.persistence.entity.dictionary;
