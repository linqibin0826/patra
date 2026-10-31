package dev.patra.notification.app.preference.usecase.change;

import dev.patra.notification.app.preference.usecase.query.EmailPreferenceView;
import dev.patra.notification.domain.kernel.model.enums.NotificationCategory;
import dev.patra.notification.domain.preference.exception.InvalidPreferenceRequestException;
import dev.patra.notification.domain.preference.model.aggregate.EmailPreference;
import dev.patra.notification.domain.preference.model.read.EmailMode;
import dev.patra.notification.domain.preference.model.vo.UnsubscribeToken;
import dev.patra.notification.domain.preference.port.repository.EmailPreferenceRepository;
import dev.patra.tool.core.security.CurrentUserPort;
import dev.patra.tool.cqrs.CommandHandler;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionOperations;

/// 改一类的邮件档位（spec 第 12 节）。事务外校验参数；事务里没有行就先插默认值（带新令牌），再锁行、改、写回。
/// 行锁让同一个人同时改两类、或改档位时恰好来了一键退订的几个请求排队执行，后一个读到前一个提交后的值。
@Component
@RequiredArgsConstructor
public class ChangeEmailModeHandler
    implements CommandHandler<ChangeEmailModeCommand, EmailPreferenceView> {

  private final EmailPreferenceRepository preferences;
  private final CurrentUserPort currentUser;
  private final TransactionOperations transactions;
  private final Clock clock;
  private final SecureRandom random = new SecureRandom();

  /// 改档位。
  ///
  /// @param command 命令
  /// @return 写回后的两类档位
  @Override
  public EmailPreferenceView handle(ChangeEmailModeCommand command) {
    long userId = currentUser.require().userId();
    NotificationCategory category =
        NotificationCategory.parse(command.category())
            .filter(NotificationCategory::hasEmail)
            .orElseThrow(() -> new InvalidPreferenceRequestException("类别只能是 REPLY 或 MENTION"));
    EmailMode mode =
        EmailMode.parse(command.mode())
            .orElseThrow(() -> new InvalidPreferenceRequestException("档位只能是 OFF、INSTANT 或 DIGEST"));
    EmailPreference defaults =
        EmailPreference.createDefault(userId, UnsubscribeToken.generate(random));
    Instant now = clock.instant();
    return Objects.requireNonNull(
        transactions.execute(
            status -> {
              preferences.insertIfAbsent(defaults, now);
              EmailPreference preference = preferences.findByUserIdForUpdate(userId).orElseThrow();
              preference.change(category, mode);
              preferences.updateModes(preference, now);
              return EmailPreferenceView.from(preference);
            }));
  }
}
