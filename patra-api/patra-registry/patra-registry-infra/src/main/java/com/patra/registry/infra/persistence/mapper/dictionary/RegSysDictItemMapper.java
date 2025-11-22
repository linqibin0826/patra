package com.patra.registry.infra.persistence.mapper.dictionary;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.patra.registry.infra.persistence.entity.dictionary.RegSysDictItemDO;
import java.util.List;
import java.util.Optional;
import org.apache.ibatis.annotations.Param;

/// 只读 Mapper,用于表 `sys_dict_item`.
/// 
/// Offers query helpers for dictionary items and their defaults.
/// 
/// SQL statements reside in `resources/mapper/RegSysDictItemMapper.xml`.
/// 
/// @author linqibin
/// @since 0.1.0
public interface RegSysDictItemMapper extends BaseMapper<RegSysDictItemDO> {

  /// Finds an enabled dictionary item by type code and item code pair.
  Optional<RegSysDictItemDO> selectByTypeAndItemCode(
      @Param("typeCode") String typeCode, @Param("itemCode") String itemCode);

  /// Lists enabled items for the specified type ordered by display order and item code.
  List<RegSysDictItemDO> selectEnabledByTypeCode(@Param("typeCode") String typeCode);

  /// Returns the default item (if any) for the given type code.
  Optional<RegSysDictItemDO> selectDefaultByTypeCode(@Param("typeCode") String typeCode);

  /// Lists enabled items by type identifier (used internally where type id is available).
  List<RegSysDictItemDO> selectEnabledByTypeId(@Param("typeId") Long typeId);

  /// Counts enabled items for a given type code.
  int countEnabledByTypeCode(@Param("typeCode") String typeCode);

  /// Detects type codes that currently have multiple defaults defined.
  List<String> selectTypesWithMultipleDefaults();

  /// Returns type codes that do not have any default item configured.
  List<String> selectTypesWithoutDefaults();

  /// Counts all enabled dictionary items across all types.
  int countTotalEnabled();

  /// Counts all dictionary items excluding soft-deleted rows.
  int countTotal();
}
