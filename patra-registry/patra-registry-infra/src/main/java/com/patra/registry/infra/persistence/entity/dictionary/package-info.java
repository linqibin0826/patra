/// 系统字典实体包 - Dictionary 相关数据库实体。
///
/// 本包包含系统字典的数据库实体对象,映射 `reg_sys_dict_*` 系列表。系统字典提供统一的枚举代码和业务字典项管理,支持多语言、别名和层级结构。
///
/// ## 职责
///
/// - 映射字典类型表(`reg_sys_dict_type`)
///   - 映射字典项表(`reg_sys_dict_item`)
///   - 映射字典项别名表(`reg_sys_dict_item_alias`)
///   - 支持字典的层级结构和多语言
///
/// ## 核心实体
///
/// - {@link com.patra.registry.infra.persistence.entity.dictionary.RegSysDictTypeDO} - 字典类型
///
/// - 表: `reg_sys_dict_type`
///         - 字段: `type_code`, `type_name`, `description`
///         - 用途: 定义字典分类(如 COUNTRY_CODE, LANGUAGE, STATUS)
///
///   - {@link com.patra.registry.infra.persistence.entity.dictionary.RegSysDictItemDO} - 字典项
///
/// - 表: `reg_sys_dict_item`
///         - 字段: `dict_type_code`, `item_code`, `item_value`, `parent_item_code`
///         - 用途: 定义具体的字典值(如 COUNTRY_CODE → CN, US, GB)
///
///   - {@link com.patra.registry.infra.persistence.entity.dictionary.RegSysDictItemAliasDO} -
///       字典项别名
///
/// - 表: `reg_sys_dict_item_alias`
///         - 字段: `dict_item_id`, `alias_value`, `locale`
///         - 用途: 支持同一字典项的多种表示和国际化(如 China/中国/Chine)
///
/// ## 字典层级结构
///
/// 字典项支持父子层级关系,通过 `parent_item_code` 字段实现:
///
/// ```java
/// [字典类型: REGION]
///   ├── ASIA (parent: null)
///   │   ├── CHINA (parent: ASIA)
///   │   └── JAPAN (parent: ASIA)
///   └── EUROPE (parent: null)
///       ├── UK (parent: EUROPE)
///       └── FRANCE (parent: EUROPE)
/// ```
///
/// ## 多语言支持
///
/// 字典项别名支持多语言和同义词:
///
/// ```java
/// [字典项: CHINA]
///   ├── CN (locale: en, alias_value: China)
///   ├── CN (locale: zh, alias_value: 中国)
///   ├── CN (locale: fr, alias_value: Chine)
///   └── CN (locale: en, alias_value: PRC) // 同义词
/// ```
///
/// ## 数据库表设计
///
/// <table border="1">
///   <caption>系统字典表</caption>
///   <tr>
///     <th>表名</th>
///     <th>用途</th>
///     <th>关键字段</th>
///   </tr>
///   <tr>
///     <td>`reg_sys_dict_type`</td>
///     <td>字典类型定义</td>
///     <td>`type_code`, `type_name`, `is_system`</td>
///   </tr>
///   <tr>
///     <td>`reg_sys_dict_item`</td>
///     <td>字典项</td>
///     <td>`dict_type_code`, `item_code`, `item_value`, `parent_item_code`</td>
///   </tr>
///   <tr>
///     <td>`reg_sys_dict_item_alias`</td>
///     <td>字典项别名</td>
///     <td>`dict_item_id`, `alias_value`, `locale`</td>
///   </tr>
/// </table>
///
/// ## 使用场景
///
/// - **国家代码**: `COUNTRY_CODE` → CN, US, GB, FR...
///   - **语言代码**: `LANGUAGE` → en, zh, fr, ja...
///   - **状态枚举**: `STATUS` → ACTIVE, INACTIVE, PENDING...
///   - **业务分类**: `CATEGORY` → TECH, HEALTH, FINANCE...
///
/// ## 使用示例
///
/// ```java
/// // 字典类型
/// RegSysDictTypeDO type = new RegSysDictTypeDO();
/// type.setTypeCode("COUNTRY_CODE");
/// type.setTypeName("Country Codes");
/// type.setIsSystem(true);
///
/// // 字典项
/// RegSysDictItemDO item = new RegSysDictItemDO();
/// item.setDictTypeCode("COUNTRY_CODE");
/// item.setItemCode("CN");
/// item.setItemValue("China");
/// item.setParentItemCode(null);
///
/// // 字典别名
/// RegSysDictItemAliasDO alias = new RegSysDictItemAliasDO();
/// alias.setDictItemId(item.getId());
/// alias.setAliasValue("中国");
/// alias.setLocale("zh");
/// ```
///
/// @since 0.1.0
/// @author linqibin
package com.patra.registry.infra.persistence.entity.dictionary;
