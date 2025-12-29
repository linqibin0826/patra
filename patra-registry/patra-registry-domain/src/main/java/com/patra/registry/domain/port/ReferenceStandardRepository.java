package com.patra.registry.domain.port;

import com.patra.registry.domain.model.vo.reference.ReferenceStandard;
import java.util.Optional;

/// 来源标准查询仓储接口。
///
/// 提供来源标准的只读访问能力。
///
/// @author linqibin
/// @since 0.1.0
public interface ReferenceStandardRepository {

  /// 通过字典类型代码和标准代码查询来源标准。
  ///
  /// @param dictTypeCode 字典类型代码
  /// @param standardCode 标准代码
  /// @return 可选的来源标准
  Optional<ReferenceStandard> findByDictTypeCodeAndStandardCode(
      String dictTypeCode, String standardCode);

  /// 查询指定字典类型的规范标准。
  ///
  /// 规范标准是该字典类型中 `canonical = true` 且 `enabled = true` 的标准，
  /// 定义了 `sys_dict_item.item_code` 应遵循的格式。
  ///
  /// @param dictTypeCode 字典类型代码
  /// @return 可选的规范标准
  Optional<ReferenceStandard> findCanonicalByDictTypeCode(String dictTypeCode);
}
