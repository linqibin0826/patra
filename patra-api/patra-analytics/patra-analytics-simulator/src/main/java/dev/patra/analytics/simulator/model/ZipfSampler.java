package dev.patra.analytics.simulator.model;

import java.util.SplittableRandom;

/// Zipf 分布抽样：排第 k 名（0 起）的概率 ∝ 1 / (k + 1)^s。
public final class ZipfSampler {

  private final WeightedSampler sampler;

  /// 建一个 n 个排名的 Zipf 分布。
  ///
  /// @param n 排名个数
  /// @param s 指数
  public ZipfSampler(int n, double s) {
    this.sampler = new WeightedSampler(weights(n, s));
  }

  /// 各排名的 Zipf 权重（未归一化）。
  ///
  /// @param n 排名个数
  /// @param s 指数
  /// @return 权重
  static double[] weights(int n, double s) {
    double[] weights = new double[n];
    for (int k = 0; k < n; k++) {
      weights[k] = 1.0 / Math.pow(k + 1, s);
    }
    return weights;
  }

  /// 抽一个排名。
  ///
  /// @param random 随机源
  /// @return 排名，0 起
  public int sample(SplittableRandom random) {
    return sampler.sample(random);
  }

  /// 某个排名的概率。
  ///
  /// @param rank 排名，0 起
  /// @return 概率
  public double probability(int rank) {
    return sampler.probability(rank);
  }
}
