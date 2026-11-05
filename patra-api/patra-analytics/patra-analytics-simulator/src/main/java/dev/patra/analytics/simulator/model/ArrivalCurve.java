package dev.patra.analytics.simulator.model;

import dev.patra.analytics.simulator.event.BeijingDays;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.SplittableRandom;

/// 会话到达率曲线（spec §5.3）：每小时到达率 = 日均 × 昼夜系数（北京时间）× 周内系数，即非齐次泊松过程的强度。
public final class ArrivalCurve {

  private static final int SECONDS_PER_HOUR = 3_600;

  private final List<Double> hourly;
  private final List<Double> weekday;
  private final WeightedSampler hourSampler;

  /// 按模型参数建曲线。
  ///
  /// @param model 流量模型（`hourly` 已归一化成均值 1）
  public ArrivalCurve(TrafficModel model) {
    this.hourly = model.hourly();
    this.weekday = model.weekday();
    this.hourSampler =
        new WeightedSampler(hourly.stream().mapToDouble(Double::doubleValue).toArray());
  }

  /// 北京时间某小时的系数（24 小时的均值为 1）。
  ///
  /// @param hour 0～23
  /// @return 系数
  public double hourWeight(int hour) {
    return hourly.get(hour);
  }

  /// 星期几的系数。
  ///
  /// @param day 星期
  /// @return 系数
  public double dayWeight(DayOfWeek day) {
    return weekday.get(day.getValue() - 1);
  }

  /// 在某天里抽一个会话开始时刻：按小时系数抽小时，小时内均匀（泊松过程在小时内的到达时刻是均匀的）。
  ///
  /// @param day 北京时间的日期
  /// @param random 随机源
  /// @return 开始时刻
  public Instant sampleStart(LocalDate day, SplittableRandom random) {
    int hour = hourSampler.sample(random);
    long offsetMillis =
        (long) hour * SECONDS_PER_HOUR * 1_000 + random.nextLong(SECONDS_PER_HOUR * 1_000L);
    return day.atStartOfDay(BeijingDays.ZONE).toInstant().plusMillis(offsetMillis);
  }

  /// 把总事件数按周内系数分到各天，合计严格等于总数（余数按最大余额法分）。
  ///
  /// @param totalEvents 总事件数
  /// @param days 日期
  /// @return 每天的事件数，按 `days` 的顺序
  public Map<LocalDate, Long> allocate(long totalEvents, List<LocalDate> days) {
    double sum = days.stream().mapToDouble(d -> dayWeight(d.getDayOfWeek())).sum();
    Map<LocalDate, Long> allocation = new LinkedHashMap<>();
    double[] remainders = new double[days.size()];
    long assigned = 0;
    for (int i = 0; i < days.size(); i++) {
      double exact = totalEvents * dayWeight(days.get(i).getDayOfWeek()) / sum;
      long floor = (long) Math.floor(exact);
      allocation.put(days.get(i), floor);
      remainders[i] = exact - floor;
      assigned += floor;
    }
    for (long left = totalEvents - assigned; left > 0; left--) {
      int best = 0;
      for (int i = 1; i < remainders.length; i++) {
        if (remainders[i] > remainders[best]) {
          best = i;
        }
      }
      allocation.merge(days.get(best), 1L, Long::sum);
      remainders[best] = -1;
    }
    return allocation;
  }
}
