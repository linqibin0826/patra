/**
 * 系统字典 Mapper 包 - Dictionary 相关数据库访问接口。
 *
 * <p>本包包含系统字典的 MyBatis-Plus Mapper 接口,提供字典类型、字典项和别名的查询能力,支持层级结构和多语言查询。
 *
 * <h2>职责</h2>
 *
 * <ul>
 *   <li>提供字典类型的 CRUD 操作
 *   <li>查询字典项(支持父子层级)
 *   <li>查询字典项别名(支持多语言)
 *   <li>实现字典的层级查询和国际化
 * </ul>
 *
 * <h2>核心 Mapper</h2>
 *
 * <ul>
 *   <li>{@link com.patra.registry.infra.persistence.mapper.dictionary.RegSysDictTypeMapper} - 字典类型 Mapper
 *       <ul>
 *         <li>{@code selectByCode(String typeCode)} - 根据类型代码查询</li>
 *         <li>{@code selectAllSystem()} - 查询所有系统字典类型</li>
 *       </ul>
 *   </li>
 *   <li>{@link com.patra.registry.infra.persistence.mapper.dictionary.RegSysDictItemMapper} - 字典项 Mapper
 *       <ul>
 *         <li>{@code selectByType(String typeCode)} - 查询指定类型的所有字典项</li>
 *         <li>{@code selectByParent(String parentCode)} - 查询子级字典项</li>
 *         <li>{@code selectRootItems(String typeCode)} - 查询顶级字典项(无父级)</li>
 *       </ul>
 *   </li>
 *   <li>{@link com.patra.registry.infra.persistence.mapper.dictionary.RegSysDictItemAliasMapper} - 字典别名 Mapper
 *       <ul>
 *         <li>{@code selectByItem(Long dictItemId)} - 查询字典项的所有别名</li>
 *         <li>{@code selectByLocale(String locale)} - 查询指定语言的别名</li>
 *       </ul>
 *   </li>
 * </ul>
 *
 * <h2>层级查询示例</h2>
 *
 * <pre>{@code
 * // 1. 查询顶级字典项(无父级)
 * List<RegSysDictItemDO> rootItems = dictItemMapper.selectRootItems("REGION");
 * // 结果: [ASIA, EUROPE, AMERICA]
 *
 * // 2. 查询子级字典项
 * List<RegSysDictItemDO> asiaChildren = dictItemMapper.selectByParent("ASIA");
 * // 结果: [CHINA, JAPAN, KOREA]
 *
 * // 3. 递归构建树形结构
 * Map<String, List<RegSysDictItemDO>> tree = buildTree(rootItems);
 * }</pre>
 *
 * <h2>多语言查询示例</h2>
 *
 * <pre>{@code
 * // 1. 查询字典项
 * RegSysDictItemDO item = dictItemMapper.selectByCode("CHINA");
 *
 * // 2. 查询所有别名
 * List<RegSysDictItemAliasDO> aliases = aliasMapper.selectByItem(item.getId());
 * // 结果:
 * //   - (en, China)
 * //   - (zh, 中国)
 * //   - (fr, Chine)
 *
 * // 3. 查询指定语言的别名
 * List<RegSysDictItemAliasDO> zhAliases =
 *     aliasMapper.selectByLocale(item.getId(), "zh");
 * // 结果: [(zh, 中国)]
 * }</pre>
 *
 * <h2>常用查询方法</h2>
 *
 * <ul>
 *   <li>{@code selectByCode(String code)} - 根据代码查询(类型/字典项)
 *   <li>{@code selectByType(String typeCode)} - 查询指定类型的所有字典项
 *   <li>{@code selectByParent(String parentCode)} - 查询子级字典项(层级查询)
 *   <li>{@code selectRootItems(String typeCode)} - 查询顶级字典项
 *   <li>{@code selectByItem(Long dictItemId)} - 查询字典项的别名
 *   <li>{@code selectByLocale(String locale)} - 按语言查询别名
 * </ul>
 *
 * <h2>使用示例</h2>
 *
 * <pre>{@code
 * @Mapper
 * public interface RegSysDictItemMapper extends BaseMapper<RegSysDictItemDO> {
 *
 *     @Select("""
 *         SELECT * FROM reg_sys_dict_item
 *         WHERE dict_type_code = #{typeCode}
 *         ORDER BY sort_order, item_code
 *     """)
 *     List<RegSysDictItemDO> selectByType(@Param("typeCode") String typeCode);
 *
 *     @Select("""
 *         SELECT * FROM reg_sys_dict_item
 *         WHERE dict_type_code = #{typeCode}
 *           AND parent_item_code = #{parentCode}
 *         ORDER BY sort_order, item_code
 *     """)
 *     List<RegSysDictItemDO> selectByParent(
 *         @Param("typeCode") String typeCode,
 *         @Param("parentCode") String parentCode
 *     );
 *
 *     @Select("""
 *         SELECT * FROM reg_sys_dict_item
 *         WHERE dict_type_code = #{typeCode}
 *           AND parent_item_code IS NULL
 *         ORDER BY sort_order, item_code
 *     """)
 *     List<RegSysDictItemDO> selectRootItems(@Param("typeCode") String typeCode);
 * }
 * }</pre>
 *
 * @since 0.1.0
 * @author linqibin
 */
package com.patra.registry.infra.persistence.mapper.dictionary;
