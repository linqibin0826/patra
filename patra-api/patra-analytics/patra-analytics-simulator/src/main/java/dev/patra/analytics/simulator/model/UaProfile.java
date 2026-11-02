package dev.patra.analytics.simulator.model;

/// 一种浏览器（或爬虫）的 UA 及其预先解析好的富化字段。
///
/// 灌历史直接写这些字段，实时压测把 `userAgent` 交给采集端点解析；测试钉住两边一致（spec §5.2）。
///
/// @param userAgent UA 原文
/// @param browserFamily 浏览器族（富化事件的 `browser_family`）
/// @param osFamily 系统族
/// @param deviceClass 设备类别
/// @param crawler 是否声明身份的爬虫（命中 COUNTER-Robots）
/// @param weight 抽样权重
public record UaProfile(
    String userAgent,
    String browserFamily,
    String osFamily,
    String deviceClass,
    boolean crawler,
    double weight) {}
