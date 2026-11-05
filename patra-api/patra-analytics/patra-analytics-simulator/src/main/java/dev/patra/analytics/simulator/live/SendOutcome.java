package dev.patra.analytics.simulator.live;

/// 一次发送（含重试）的结果。
public sealed interface SendOutcome {

  /// 采集端点收下了（202）。
  ///
  /// @param accepted 合格条数
  /// @param rejected 进 dlq 的条数（合成事件不该有）
  record Delivered(int accepted, int rejected) implements SendOutcome {}

  /// 最终没送达。
  ///
  /// @param status 最后一次的状态码；网络错误为 -1
  /// @param reason 说明
  record Failed(int status, String reason) implements SendOutcome {

    /// 结果不明：最后一次是超时、连接错误或 5xx，采集端点可能已经收下（写进了 Kafka），不能当成「没送达」。
    ///
    /// @return 是否结果不明
    public boolean uncertain() {
      return status < 0 || status >= 500;
    }
  }
}
