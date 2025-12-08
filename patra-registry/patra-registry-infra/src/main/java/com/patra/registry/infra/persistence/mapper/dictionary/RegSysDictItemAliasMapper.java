package com.patra.registry.infra.persistence.mapper.dictionary;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.patra.registry.infra.persistence.entity.dictionary.RegSysDictItemAliasDO;
import com.patra.registry.infra.persistence.entity.dictionary.RegSysDictItemDO;
import java.util.List;
import java.util.Optional;
import org.apache.ibatis.annotations.Param;

/// 只读 Mapper,用于表 `sys_dict_item_alias`。
///
/// 提供查询工具以将外部别名代码链接到内部字典项。
///
/// SQL 定义位于 `resources/mapper/RegSysDictItemAliasMapper.xml`。
///
/// @author linqibin
/// @since 0.1.0
public interface RegSysDictItemAliasMapper extends BaseMapper<RegSysDictItemAliasDO> {

  /// 通过外部别名解析激活的字典项。
  ///
  /// @param sourceSystem 来源系统
  /// @param externalCode 外部代码
  /// @return 字典项(可选)
  Optional<RegSysDictItemDO> selectItemByAlias(
      @Param("sourceSystem") String sourceSystem, @Param("externalCode") String externalCode);

  /// 列出与指定字典项关联的所有别名。
  ///
  /// @param itemId 字典项 ID
  /// @return 别名列表
  List<RegSysDictItemAliasDO> selectByItemId(@Param("itemId") Long itemId);

  /// 列出源自特定外部系统的别名。
  ///
  /// @param sourceSystem 来源系统
  /// @return 别名列表
  List<RegSysDictItemAliasDO> selectBySourceSystem(@Param("sourceSystem") String sourceSystem);

  /// 通过外部系统和代码对获取别名。
  ///
  /// @param sourceSystem 来源系统
  /// @param externalCode 外部代码
  /// @return 别名(可选)
  Optional<RegSysDictItemAliasDO> selectBySourceAndCode(
      @Param("sourceSystem") String sourceSystem, @Param("externalCode") String externalCode);

  /// 列出属于给定字典类型代码的所有项的别名。
  ///
  /// @param typeCode 字典类型代码
  /// @return 别名列表
  List<RegSysDictItemAliasDO> selectByTypeCode(@Param("typeCode") String typeCode);

  /// 统计激活别名的总数。
  ///
  /// @return 别名总数
  int countTotal();

  /// 列出别名数据中出现的不同外部系统标识符。
  ///
  /// @return 来源系统列表
  List<String> selectDistinctSourceSystems();

  /// 统计特定外部系统提供的别名数量。
  ///
  /// @param sourceSystem 来源系统
  /// @return 别名数量
  int countBySourceSystem(@Param("sourceSystem") String sourceSystem);
}
