package dev.patra.interaction.domain.post.model.enums;

import dev.patra.interaction.domain.post.exception.InvalidPostRequestException;
import dev.patra.interaction.domain.post.policy.ExperienceRules;
import java.time.LocalDate;
import java.util.Arrays;

/// 期刊汇总的统计窗口（按投稿日期）。
public enum SummaryWindow {
  /// 近 3 年（默认）：投稿日期不早于今天减 3 年。
  THREE_YEARS("3y"),
  /// 全部。
  ALL("all");

  private final String param;

  /// 定义窗口。
  ///
  /// @param param 请求参数里的写法
  SummaryWindow(String param) {
    this.param = param;
  }

  /// 请求参数里的写法。
  ///
  /// @return `3y` / `all`
  public String param() {
    return param;
  }

  /// 窗口起点（含）。
  ///
  /// @param today 北京时间的今天
  /// @return 起点；全部时是最早能填的日期
  public LocalDate since(LocalDate today) {
    return this == THREE_YEARS ? today.minusYears(3) : ExperienceRules.EARLIEST;
  }

  /// 按请求参数查找。
  ///
  /// @param param 参数，可空
  /// @return 窗口；没给时是近 3 年
  /// @throws InvalidPostRequestException 未知写法
  public static SummaryWindow fromParam(String param) {
    if (param == null || param.isBlank()) {
      return THREE_YEARS;
    }
    return Arrays.stream(values())
        .filter(w -> w.param.equals(param))
        .findFirst()
        .orElseThrow(() -> new InvalidPostRequestException("统计窗口只能是 3y 或 all：" + param));
  }
}
