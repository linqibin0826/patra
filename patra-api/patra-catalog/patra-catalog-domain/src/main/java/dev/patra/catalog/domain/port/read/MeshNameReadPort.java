package dev.patra.catalog.domain.port.read;

import java.util.Collection;
import java.util.Map;

/// MeSH 名称读端口：按名称批量查主题词、限定词的 UI（名称区分大小写）。
///
/// @author linqibin
/// @since 0.1.0
public interface MeshNameReadPort {

  /// 按名称批量查主题词 UI。
  ///
  /// @param names 主题词名称；null 或空时返回空 Map
  /// @return 名称 → UI；未命中的名称不出现，同名多条取先查到的
  Map<String, String> findDescriptorUisByNameIn(Collection<String> names);

  /// 按名称批量查限定词 UI。
  ///
  /// @param names 限定词名称；null 或空时返回空 Map
  /// @return 名称 → UI；未命中的名称不出现，同名多条取先查到的
  Map<String, String> findQualifierUisByNameIn(Collection<String> names);
}
