package dev.patra.tool.events.resubmit;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.modulith.events.EventPublication;
import org.springframework.modulith.events.EventPublication.Status;
import org.springframework.modulith.events.IncompleteEventPublications;
import org.springframework.modulith.events.core.EventPublicationRegistry;
import org.springframework.modulith.events.core.Staleness;

/// 把事件发布登记表里没完成的行重新外送一遍。
///
/// 每次执行两步：
///
/// 1. 卡在 `RESUBMITTED` 10 分钟没动静的行改回 `FAILED`。Modulith 补投前先把行标成 `RESUBMITTED`，
///    并拒绝再补投已是这个状态的行；补投途中进程崩了，这一行会永远卡住。Modulith 自带的过期检查是
///    `SchedulingConfigurer`，要 `@EnableScheduling` 才会自己跑，所以在这里主动调
///    `markStalePublicationsFailed`，只监控 `RESUBMITTED`（按最后一次补投时间判断）。
/// 2. 补投发布时间早于 `minAge` 的未完成行（`PUBLISHED`、`PROCESSING`、`FAILED`）。刚提交的事件正在异步外送，不去抢。
///    `PROCESSING` 表示外送任务正在执行（发送或等确认）：10 分钟内有动静的不再派一次，否则 broker 阻塞时
///    每轮都会把同一行再排一份；超过 10 分钟没动静的当作进程崩溃留下的，照常补投。`RESUBMITTED` 表示已被领走
///    （可能还在执行器里排队，也可能是别的实例领的），Modulith 不会再派发它，所以也不计入补投数、不打日志；
///    超过 10 分钟的已在第 1 步改回 `FAILED`。
///    尝试 10 次以上的行（首次发送记 1，每补投一次加 1）打 WARN：多半是路由键没有队列绑定，日志里据此查卡住的事件。
///    不设上限，继续每分钟重试，broker 停几个小时恢复后照样能补上。
///
/// 补投出去的事件在异步线程里发送，本方法返回时可能还没发完：成功的行被删除或标完成，失败的回到 `FAILED`，
/// 留给下一轮。
@Slf4j
@RequiredArgsConstructor
public class EventPublicationResubmitter {

  /// 尝试到这个次数还没发出去，就打 WARN。
  private static final int STUCK_ATTEMPTS = 10;

  /// `RESUBMITTED`、`PROCESSING` 的行超过这个时长没动静就当作卡住：前者改回 `FAILED`，后者照常补投。
  private static final Duration STALE_AFTER = Duration.ofMinutes(10);

  private final IncompleteEventPublications publications;

  private final EventPublicationRegistry registry;

  private final Clock clock;

  private final Duration minAge;

  /// 执行一次补投。
  ///
  /// @return 本次交出去补投的行数
  public int resubmit() {
    Instant now = clock.instant();
    Instant cutoff = now.minus(minAge);
    Instant inFlightCutoff = now.minus(STALE_AFTER);
    registry.markStalePublicationsFailed(
        status -> status == Status.RESUBMITTED ? STALE_AFTER : Staleness.UNCONFIGURED_DURATION);
    AtomicInteger count = new AtomicInteger();
    publications.resubmitIncompletePublications(
        publication -> {
          boolean due =
              publication.getPublicationDate().isBefore(cutoff)
                  && !inFlight(publication, inFlightCutoff);
          if (due) {
            count.incrementAndGet();
            warnIfStuck(publication);
          }
          return due;
        });
    if (count.get() > 0) {
      log.info("补投 {} 条未完成的事件发布", count.get());
    }
    return count.get();
  }

  /// 是否还在发送：`RESUBMITTED`（已被领走），或 `PROCESSING` 且上次补投（没补投过就是发布时间）晚于
  /// `inFlightCutoff`。
  ///
  /// @param publication 登记行
  /// @param inFlightCutoff 早于它没动静就不算在发送
  /// @return 还在发送返回 `true`
  private static boolean inFlight(EventPublication publication, Instant inFlightCutoff) {
    if (publication.getStatus() == Status.RESUBMITTED) {
      return true;
    }
    if (publication.getStatus() != Status.PROCESSING) {
      return false;
    }
    Instant lastActivity =
        publication.getLastResubmissionDate() != null
            ? publication.getLastResubmissionDate()
            : publication.getPublicationDate();
    return !lastActivity.isBefore(inFlightCutoff);
  }

  /// 尝试次数到了 {@link #STUCK_ATTEMPTS} 就打 WARN，列出登记行 ID、发布时间、尝试次数（事件类型按 ID 查表）。
  ///
  /// 不读事件内容：反序列化失败时异常会在 Modulith 的处理之外抛出，整轮补投中断。
  ///
  /// @param publication 登记行
  private static void warnIfStuck(EventPublication publication) {
    if (publication.getCompletionAttempts() >= STUCK_ATTEMPTS) {
      log.warn(
          "事件发布已尝试 {} 次仍未发出（多半是路由键没有队列绑定）：登记行 ID {}，发布于 {}",
          publication.getCompletionAttempts(),
          publication.getIdentifier(),
          publication.getPublicationDate());
    }
  }
}
