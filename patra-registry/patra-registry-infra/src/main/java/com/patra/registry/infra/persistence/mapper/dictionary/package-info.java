/// 系统字典 Mapper 包 - Dictionary 相关数据库访问接口。
/// 
/// 本包包含系统字典的 MyBatis-Plus Mapper 接口,提供字典类型、字典项和别名的查询能力,支持层级结构和多语言查询。
/// 
/// ## 职责
/// 
/// - 提供字典类型的 CRUD 操作
///   - 查询字典项(支持父子层级)
///   - 查询字典项别名(支持多语言)
///   - 实现字典的层级查询和国际化
/// 
/// ## 核心 Mapper
/// 
/// - {@link com.patra.registry.infra.persistence.mapper.dictionary.RegSysDictTypeMapper} - 字典类型
///       Mapper
///       
/// - `selectByCode(String typeCode)` - 根据类型代码查询
///         - `selectAllSystem()` - 查询所有系统字典类型
/// 
///   - {@link com.patra.registry.infra.persistence.mapper.dictionary.RegSysDictItemMapper} - 字典项
///       Mapper
///       
/// - `selectByType(String typeCode)` - 查询指定类型的所有字典项
///         - `selectByParent(String parentCode)` - 查询子级字典项
///         - `selectRootItems(String typeCode)` - 查询顶级字典项(无父级)
/// 
///   - {@link com.patra.registry.infra.persistence.mapper.dictionary.RegSysDictItemAliasMapper} -
///       字典别名 Mapper
///       
/// - `selectByItem(Long dictItemId)` - 查询字典项的所有别名
///         - `selectByLocale(String locale)` - 查询指定语言的别名
/// 
/// ## 层级查询示例
/// 
/// ```java
/// // 1. 查询顶级字典项(无父级)
/// List<RegSysDictItemDO> rootItems = dictItemMapper.selectRootItems("REGION");
/// // 结果: [ASIA, EUROPE, AMERICA]
/// 
/// // 2. 查询子级字典项
/// List<RegSysDictItemDO> asiaChildren = dictItemMapper.selectByParent("ASIA");
/// // 结果: [CHINA, JAPAN, KOREA]
/// 
/// // 3. 递归构建树形结构
/// Map<String, List<RegSysDictItemDO>> tree = buildTree(rootItems);
/// ```
/// 
/// ## 多语言查询示例
/// 
/// ```java
/// // 1. 查询字典项
/// RegSysDictItemDO item = dictItemMapper.selectByCode("CHINA");
/// 
/// // 2. 查询所有别名
/// List<RegSysDictItemAliasDO> aliases = aliasMapper.selectByItem(item.getId());
/// // 结果:
/// //   - (en, China)
/// //   - (zh, 中国)
/// //   - (fr, Chine)
/// 
/// // 3. 查询指定语言的别名
/// List<RegSysDictItemAliasDO> zhAliases =
///     aliasMapper.selectByLocale(item.getId(), "zh");
/// // 结果: [(zh, 中国)]
/// ```
/// 
/// ## 常用查询方法
/// 
/// - `selectByCode(String code)` - 根据代码查询(类型/字典项)
///   - `selectByType(String typeCode)` - 查询指定类型的所有字典项
///   - `selectByParent(String parentCode)` - 查询子级字典项(层级查询)
///   - `selectRootItems(String typeCode)` - 查询顶级字典项
///   - `selectByItem(Long dictItemId)` - 查询字典项的别名
///   - `selectByLocale(String locale)` - 按语言查询别名
/// 
/// ## 使用示例
/// 
/// ```java
/// @Mapper
/// public interface RegSysDictItemMapper extends BaseMapper<RegSysDictItemDO> {
/// 
///     @Select("""
///         SELECT * FROM reg_sys_dict_item
///         WHERE dict_type_code = #{typeCode
///         ORDER BY sort_order, item_code
///     """)
///     List<RegSysDictItemDO> selectByType(@Param("typeCode") String typeCode);
/// 
///     @Select("""
///         SELECT * FROM reg_sys_dict_item
///         WHERE dict_type_code = #{typeCode
///           AND parent_item_code = #{parentCode
///         ORDER BY sort_order, item_code
///     """)
///     List<RegSysDictItemDO> selectByParent(
///         @Param("typeCode") String typeCode,
///         @Param("parentCode") String parentCode
///     );
/// 
///     @Select("""
///         SELECT * FROM reg_sys_dict_item
///         WHERE dict_type_code = #{typeCode
///           AND parent_item_code IS NULL
///         ORDER BY sort_order, item_code
///     """)
///     List<RegSysDictItemDO> selectRootItems(@Param("typeCode") String typeCode);
/// ```
/// 
/// @since 0.1.0
/// @author linqibin
package com.patra.registry.infra.persistence.mapper.dictionary;
