package com.patra.catalog.domain.port.registry;

import java.util.Map;
import java.util.Set;

/// 国家编码解析端口。
///
/// 提供将原始国家编码（如 ISO-3、英文名称等）解析为标准 ISO 3166-1 alpha-2 代码的能力。
/// 通过 patra-registry 服务的字典解析 API 实现。
///
/// **错误处理策略**：
///
/// - 远程服务不可用时返回空 Map，不影响主流程
/// - 单个值解析失败时，该值不在结果中
/// - 不抛出异常，因为国家编码是可选字段
///
/// @author linqibin
/// @since 0.1.0
public interface CountryCodeResolverPort {

  /// 批量解析国家编码。
  ///
  /// 将原始国家编码集合解析为标准 ISO 3166-1 alpha-2 代码。
  /// 解析失败的值不会出现在返回结果中。
  ///
  /// @param rawCodes 原始国家编码集合（支持 ISO-2、ISO-3、英文名称等格式）
  /// @return 原始值 → 标准 ISO-2 代码的映射，解析失败的值不在结果中
  Map<String, String> resolveCountryCodes(Set<String> rawCodes);
}
