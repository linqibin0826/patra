package com.patra.registry.infra.persistence.mapper.dictionary;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.patra.registry.infra.persistence.entity.dictionary.RegSysDictItemAliasDO;
import com.patra.registry.infra.persistence.entity.dictionary.RegSysDictItemDO;
import java.util.List;
import java.util.Optional;
import org.apache.ibatis.annotations.Param;

/// 只读 Mapper,用于表 `sys_dict_item_alias`.
///
/// Provides query utilities for linking external alias codes to internal dictionary items.
///
/// SQL definitions live in `resources/mapper/RegSysDictItemAliasMapper.xml`.
///
/// @author linqibin
/// @since 0.1.0
public interface RegSysDictItemAliasMapper extends BaseMapper<RegSysDictItemAliasDO> {

  /// Resolves an enabled dictionary item by its external alias.
  Optional<RegSysDictItemDO> selectItemByAlias(
      @Param("sourceSystem") String sourceSystem, @Param("externalCode") String externalCode);

  /// Lists all aliases associated with the specified dictionary item.
  List<RegSysDictItemAliasDO> selectByItemId(@Param("itemId") Long itemId);

  /// Lists aliases that originate from a specific external system.
  List<RegSysDictItemAliasDO> selectBySourceSystem(@Param("sourceSystem") String sourceSystem);

  /// Fetches an alias by external system and code pair.
  Optional<RegSysDictItemAliasDO> selectBySourceAndCode(
      @Param("sourceSystem") String sourceSystem, @Param("externalCode") String externalCode);

  /// Lists aliases for all items belonging to the given dictionary type code.
  List<RegSysDictItemAliasDO> selectByTypeCode(@Param("typeCode") String typeCode);

  /// Counts the total number of active aliases.
  int countTotal();

  /// Lists distinct external system identifiers that appear in alias data.
  List<String> selectDistinctSourceSystems();

  /// Counts the number of aliases supplied by a particular external system.
  int countBySourceSystem(@Param("sourceSystem") String sourceSystem);
}
