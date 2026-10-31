package dev.patra.notification.adapter.mail.scheduler;

import com.xxl.job.core.context.XxlJobHelper;
import com.xxl.job.core.handler.annotation.XxlJob;
import dev.patra.notification.app.mail.usecase.digest.DigestScheduleResult;
import dev.patra.notification.app.mail.usecase.digest.ScheduleDailyDigestCommand;
import dev.patra.tool.cqrs.CommandBus;
import java.util.Arrays;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/// 每日汇总建名单任务（handler `dailyDigestJob`，spec 5.2、7.4）：只建投递记录，下一分钟的 `reminderMailJob` 发出。
///
/// mini 的调度中心在执行器 `patra-notification` 下 8 点那一小时里每 10 分钟一次（Cron `0 0/10 8 * * ?`；路由第一个、
/// 阻塞处理「丢弃后续调度」、超时 300 秒、不重试）：8:00 那次没跑成时 8:10 补上，唯一约束挡住重复。
/// 手动验收时任务参数写 `users=用户ID,用户ID`：只给列出的人建，窗口右端是此刻；参数为空就是定时。
@Slf4j
@Component
@RequiredArgsConstructor
public class DailyDigestScheduleJob {

  /// 手动参数的写法：`users=` 后面是逗号分隔的正整数。
  private static final Pattern USERS =
      Pattern.compile("users=([1-9][0-9]{0,18}(?:,[1-9][0-9]{0,18})*)");

  /// 手动一次最多列几个人。
  private static final int MAX_USERS = 100;

  private final CommandBus commandBus;

  /// 执行一次。
  @XxlJob("dailyDigestJob")
  public void execute() {
    String param = XxlJobHelper.getJobParam();
    Optional<ScheduleDailyDigestCommand> command = parse(param);
    if (command.isEmpty()) {
      XxlJobHelper.handleFail("参数应为空或 users=用户ID,…（最多 " + MAX_USERS + " 个）: " + param);
      return;
    }
    try {
      DigestScheduleResult result = commandBus.handle(command.get());
      XxlJobHelper.handleSuccess(
          "窗口 %s：名单 %d 人，新建 %d 封，清理 %d 条"
              .formatted(
                  result.windowEnd(), result.subscribers(), result.created(), result.purged()));
    } catch (RuntimeException ex) {
      log.error("每日汇总建名单失败：{}", ex.getMessage(), ex);
      XxlJobHelper.handleFail("执行失败：" + ex.getMessage());
    }
  }

  /// 解析任务参数。
  ///
  /// @param param 参数，可空
  /// @return 命令；参数不认识时为空
  static Optional<ScheduleDailyDigestCommand> parse(String param) {
    String compact = param == null ? "" : param.replaceAll("\\s", "");
    if (compact.isEmpty()) {
      return Optional.of(ScheduleDailyDigestCommand.scheduled());
    }
    if (!USERS.matcher(compact).matches()) {
      return Optional.empty();
    }
    Set<Long> users =
        Arrays.stream(compact.substring("users=".length()).split(","))
            .map(Long::parseLong)
            .collect(Collectors.toSet());
    return users.size() > MAX_USERS
        ? Optional.empty()
        : Optional.of(ScheduleDailyDigestCommand.manual(users));
  }
}
