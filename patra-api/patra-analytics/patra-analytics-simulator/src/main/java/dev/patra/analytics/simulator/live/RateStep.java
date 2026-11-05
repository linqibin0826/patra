package dev.patra.analytics.simulator.live;

import java.time.Duration;

/// 压测的一级速率。
///
/// @param eventsPerSecond 目标事件速率（条/秒）
/// @param length 持续多久
public record RateStep(double eventsPerSecond, Duration length) {}
