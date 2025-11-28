package com.patra.registry.infra.persistence.mapper.dictionary;

import com.patra.registry.infra.persistence.entity.dictionary.RegSysDictItemDO;
import com.patra.starter.mybatis.mapper.PatraBaseMapper;
import java.util.List;
import java.util.Optional;
import org.apache.ibatis.annotations.Param;

/// 只读 Mapper,用于表 `sys_dict_item`。
///
/// 提供字典项及其默认值的查询辅助方法。
///
/// SQL 语句位于 `resources/mapper/RegSysDictItemMapper.xml`。
///
/// @author linqibin
/// @since 0.1.0
public interface RegSysDictItemMapper extends PatraBaseMapper<RegSysDictItemDO> {

  /// 通过类型代码和项代码对查找激活的字典项。
  ///
  /// @param typeCode 字典类型代码
  /// @param itemCode 字典项代码
  /// @return 字典项(可选)
  Optional<RegSysDictItemDO> selectByTypeAndItemCode(
      @Param("typeCode") String typeCode, @Param("itemCode") String itemCode);

  /// 列出指定类型的激活项,按显示顺序和项代码排序。
  ///
  /// @param typeCode 字典类型代码
  /// @return 字典项列表
  List<RegSysDictItemDO> selectEnabledByTypeCode(@Param("typeCode") String typeCode);

  /// 返回给定类型代码的默认项(如果有)。
  ///
  /// @param typeCode 字典类型代码
  /// @return 默认字典项(可选)
  Optional<RegSysDictItemDO> selectDefaultByTypeCode(@Param("typeCode") String typeCode);

  /// 按类型标识符列出激活项(内部使用,类型 ID 可用时)。
  ///
  /// @param typeId 字典类型 ID
  /// @return 字典项列表
  List<RegSysDictItemDO> selectEnabledByTypeId(@Param("typeId") Long typeId);

  /// 统计给定类型代码的激活项数量。
  ///
  /// @param typeCode 字典类型代码
  /// @return 激活项数量
  int countEnabledByTypeCode(@Param("typeCode") String typeCode);

  /// 检测当前定义了多个默认值的类型代码。
  ///
  /// @return 类型代码列表
  List<String> selectTypesWithMultipleDefaults();

  /// 返回未配置任何默认项的类型代码。
  ///
  /// @return 类型代码列表
  List<String> selectTypesWithoutDefaults();

  /// 统计所有类型的激活字典项总数。
  ///
  /// @return 激活项总数
  int countTotalEnabled();

  /// 统计所有字典项,不包括软删除行。
  ///
  /// @return 字典项总数
  int countTotal();
}
