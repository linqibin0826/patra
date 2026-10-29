package dev.patra.registry.domain.port;

import dev.patra.common.ProvenanceCode;
import dev.patra.registry.domain.exception.provenance.ProvenanceNotFoundException;

/// 数据源读端口，供其他子域按数据源代码读取数据源信息。
///
/// @author linqibin
/// @since 0.1.0
public interface ProvenanceReadPort {

  /// 按数据源代码解析数据源 ID。
  ///
  /// @param code 数据源代码，不可为 null
  /// @return 数据源 ID
  /// @throws ProvenanceNotFoundException 数据源代码不存在时
  long requireIdByCode(ProvenanceCode code);
}
