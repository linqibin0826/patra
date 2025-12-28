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

  /// 通过标准代码查询来源标准。
  ///
  /// @param standardCode 标准代码
  /// @return 可选的来源标准
  Optional<ReferenceStandard> findByCode(String standardCode);
}
