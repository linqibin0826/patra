package com.patra.registry.infra.persistence.mapper.dictionary;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.patra.registry.infra.persistence.entity.dictionary.RegSysDictTypeDO;
import java.util.List;
import java.util.Optional;
import org.apache.ibatis.annotations.Param;

/// 只读 Mapper,用于表 `sys_dict_type`。
///
/// 在查询侧提供字典类型元数据的便捷查询。
///
/// SQL 实现位于 `resources/mapper/RegSysDictTypeMapper.xml`。要修改查询逻辑,请更新相应的 XML 文件。
///
/// @author linqibin
/// @since 0.1.0
public interface RegSysDictTypeMapper extends BaseMapper<RegSysDictTypeDO> {

  /// 通过稳定的业务代码查询字典类型。
  ///
  /// @param typeCode 字典类型代码(唯一)
  /// @return 字典类型定义(可选)
  Optional<RegSysDictTypeDO> selectByTypeCode(@Param("typeCode") String typeCode);

  /// 列出按 `type_code` 排序的所有未删除字典类型。
  ///
  /// @return 字典类型列表
  List<RegSysDictTypeDO> selectAllEnabled();

  /// 统计所有未删除字典类型的数量。
  ///
  /// @return 激活字典类型计数
  int countTotal();

  /// 列出允许业务侧自定义项的类型。
  ///
  /// @return 字典类型列表
  List<RegSysDictTypeDO> selectCustomizableTypes();

  /// 列出系统管理的字典类型。
  ///
  /// @return 字典类型列表
  List<RegSysDictTypeDO> selectSystemTypes();
}
