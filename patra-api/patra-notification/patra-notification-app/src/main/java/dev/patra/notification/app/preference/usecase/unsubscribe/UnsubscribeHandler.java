package dev.patra.notification.app.preference.usecase.unsubscribe;

import dev.patra.notification.domain.kernel.model.enums.NotificationCategory;
import dev.patra.notification.domain.preference.exception.UnsubscribeLinkInvalidException;
import dev.patra.notification.domain.preference.model.aggregate.EmailPreference;
import dev.patra.notification.domain.preference.model.enums.UnsubscribeCategory;
import dev.patra.notification.domain.preference.model.vo.UnsubscribeToken;
import dev.patra.notification.domain.preference.port.repository.EmailPreferenceRepository;
import dev.patra.tool.cqrs.CommandHandler;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionOperations;

/// 凭令牌退订（spec 5.2、第 12 节）。事务外解析链接，不合法不查库；事务里按令牌锁行、设成关，有变化才写回。
/// 设成关而不是切换，所以重复调用结果相同。不调 identity：identity 挂了也能退订（spec 5.4）。
@Slf4j
@Component
@RequiredArgsConstructor
public class UnsubscribeHandler implements CommandHandler<UnsubscribeCommand, UnsubscribeResult> {

  private final EmailPreferenceRepository preferences;
  private final TransactionOperations transactions;
  private final Clock clock;

  /// 退订。
  ///
  /// @param command 命令
  /// @return 结果
  @Override
  public UnsubscribeResult handle(UnsubscribeCommand command) {
    UnsubscribeToken token =
        UnsubscribeToken.parse(command.token()).orElseThrow(UnsubscribeLinkInvalidException::new);
    UnsubscribeCategory category =
        UnsubscribeCategory.parse(command.category())
            .filter(c -> !command.oneClick() || c.inLinks())
            .orElseThrow(UnsubscribeLinkInvalidException::new);
    Instant now = clock.instant();
    return Objects.requireNonNull(
        transactions.execute(
            status -> {
              EmailPreference preference =
                  preferences
                      .findByTokenForUpdate(token)
                      .orElseThrow(UnsubscribeLinkInvalidException::new);
              List<NotificationCategory> turnedOff = preference.unsubscribe(category);
              if (!turnedOff.isEmpty()) {
                preferences.updateModes(preference, now);
              }
              log.info(
                  "提醒邮件退订: userId={}, category={}, turnedOff={}, oneClick={}",
                  preference.getUserId(),
                  category,
                  turnedOff,
                  command.oneClick());
              return UnsubscribeResult.builder()
                  .category(category)
                  .turnedOff(turnedOff)
                  .reply(preference.getReply())
                  .mention(preference.getMention())
                  .othersEnabled(preference.anyEmailOnAfter(category))
                  .build();
            }));
  }
}
