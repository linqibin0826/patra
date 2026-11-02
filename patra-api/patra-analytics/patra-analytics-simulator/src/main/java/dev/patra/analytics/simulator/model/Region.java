package dev.patra.analytics.simulator.model;

import java.util.List;

/// 访客地区：国家码与省份，以及一组经 ip2region 查得出这个地区的真实 IP（实时压测用）。
///
/// @param countryCode ISO 国家码；查不到地区时为 `null`
/// @param province 省份原文，只在国内时有
/// @param ips 该地区的 IP
/// @param weight 抽样权重
public record Region(String countryCode, String province, List<String> ips, double weight) {

  /// 防御性拷贝。
  public Region {
    ips = ips != null ? List.copyOf(ips) : List.of();
  }
}
