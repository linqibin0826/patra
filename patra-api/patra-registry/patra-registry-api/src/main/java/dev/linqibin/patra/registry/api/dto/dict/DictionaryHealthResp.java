package dev.linqibin.patra.registry.api.dto.dict;

import java.util.List;

/// 字典健康度指标,用于子系统监控。
///
/// 字段说明:
///
/// @author linqibin
/// @since 0.1.0
public record DictionaryHealthResp(
    int totalTypes,
    int totalItems,
    int enabledItems,
    List<String> typesWithoutDefault,
    List<String> typesWithMultipleDefaults) {
  /// 规范构造器,强制不变性约束并创建防御性副本。
  ///
  /// @param totalTypes 注册的字典类型总数
  /// @param totalItems 所有类型的字典项总数
  /// @param enabledItems 所有类型的启用字典项总数
  /// @param typesWithoutDefault 缺少默认项的类型代码列表
  /// @param typesWithMultipleDefaults 有多个默认项的类型代码列表
  public DictionaryHealthResp {
    if (totalTypes < 0) {
      throw new IllegalArgumentException("字典类型总数不能为负数");
    }
    if (totalItems < 0) {
      throw new IllegalArgumentException("字典项总数不能为负数");
    }
    if (enabledItems < 0) {
      throw new IllegalArgumentException("启用项总数不能为负数");
    }
    typesWithoutDefault =
        typesWithoutDefault != null ? List.copyOf(typesWithoutDefault) : List.of();
    typesWithMultipleDefaults =
        typesWithMultipleDefaults != null ? List.copyOf(typesWithMultipleDefaults) : List.of();
  }

  /// 检查字典配置是否健康。
  ///
  /// @return 当未检测到缺失或重复的默认项时返回 `true`
  public boolean isHealthy() {
    return typesWithoutDefault.isEmpty() && typesWithMultipleDefaults.isEmpty();
  }
}
