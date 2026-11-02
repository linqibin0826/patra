package dev.patra.analytics.domain.collect.port.useragent;

import dev.patra.analytics.domain.collect.model.vo.UserAgentInfo;

/// UA 解析。
public interface UserAgentPort {

  /// 解析一个 UA。
  ///
  /// @param userAgent 原始 UA，可为空字符串
  /// @return 解析结果，设备类别总有值
  UserAgentInfo parse(String userAgent);
}
