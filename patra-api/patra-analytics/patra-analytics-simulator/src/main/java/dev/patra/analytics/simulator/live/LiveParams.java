package dev.patra.analytics.simulator.live;

import java.net.URI;
import java.time.Duration;
import java.util.List;

/// 实时压测的参数（spec §9）。
///
/// @param run 运行号
/// @param seed 种子
/// @param ramp 各级速率（单一速率即一级）
/// @param collector 采集端点
/// @param maxInFlight 同时在途的请求上限
/// @param warmUp 开跑前补上「已经开始」的会话，一开始就是稳态（压测要开；只测到达或收尾的单测关掉）
/// @param drainTimeout 停止产生新会话后，最多再等多久让在线会话走完
public record LiveParams(
    int run,
    long seed,
    List<RateStep> ramp,
    URI collector,
    int maxInFlight,
    boolean warmUp,
    Duration drainTimeout) {

  /// 防御性拷贝。
  public LiveParams {
    ramp = List.copyOf(ramp);
  }
}
