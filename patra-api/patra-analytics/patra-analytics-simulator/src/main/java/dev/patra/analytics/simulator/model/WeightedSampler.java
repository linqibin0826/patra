package dev.patra.analytics.simulator.model;

import java.util.Arrays;
import java.util.SplittableRandom;

/// 按权重抽下标：预先算好累积分布，抽样用二分查找。
public final class WeightedSampler {

  private final double[] cumulative;

  /// 按权重建分布。
  ///
  /// @param weights 各下标的权重（非负，至少一个）
  public WeightedSampler(double[] weights) {
    if (weights.length == 0) {
      throw new IllegalArgumentException("权重个数要大于 0");
    }
    cumulative = new double[weights.length];
    double sum = 0;
    for (int i = 0; i < weights.length; i++) {
      sum += weights[i];
      cumulative[i] = sum;
    }
    for (int i = 0; i < cumulative.length; i++) {
      cumulative[i] /= sum;
    }
    cumulative[cumulative.length - 1] = 1.0;
  }

  /// 抽一个下标。
  ///
  /// @param random 随机源
  /// @return 下标
  public int sample(SplittableRandom random) {
    int found = Arrays.binarySearch(cumulative, random.nextDouble());
    int index = found >= 0 ? found + 1 : -found - 1;
    return Math.min(index, cumulative.length - 1);
  }

  /// 某个下标的概率。
  ///
  /// @param index 下标
  /// @return 概率
  public double probability(int index) {
    return index == 0 ? cumulative[0] : cumulative[index] - cumulative[index - 1];
  }

  /// 下标个数。
  ///
  /// @return 个数
  public int size() {
    return cumulative.length;
  }
}
