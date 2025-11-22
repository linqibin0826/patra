package com.patra.registry.infra.persistence.mapper.dictionary;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.patra.registry.infra.persistence.entity.dictionary.RegSysDictTypeDO;
import java.util.List;
import java.util.Optional;
import org.apache.ibatis.annotations.Param;

/// 只读 Mapper,用于表 `sys_dict_type`.
///
/// Provides convenience queries for dictionary type metadata on the query side.
///
/// SQL implementations reside in `resources/mapper/RegSysDictTypeMapper.xml`. To modify
/// query logic please update the corresponding XML file.
///
/// @author linqibin
/// @since 0.1.0
public interface RegSysDictTypeMapper extends BaseMapper<RegSysDictTypeDO> {

  /// Retrieves a dictionary type by its stable business code.
  ///
  /// @param typeCode dictionary type code (unique)
  /// @return optional dictionary type definition
  Optional<RegSysDictTypeDO> selectByTypeCode(@Param("typeCode") String typeCode);

  /// Lists all non-deleted dictionary types ordered by `type_code`.
  ///
  /// @return dictionary type list
  List<RegSysDictTypeDO> selectAllEnabled();

  /// Counts all non-deleted dictionary types.
  ///
  /// @return active dictionary type count
  int countTotal();

  /// Lists types that allow business-side custom items.
  ///
  /// @return dictionary type list
  List<RegSysDictTypeDO> selectCustomizableTypes();

  /// Lists system-managed dictionary types.
  ///
  /// @return dictionary type list
  List<RegSysDictTypeDO> selectSystemTypes();
}
