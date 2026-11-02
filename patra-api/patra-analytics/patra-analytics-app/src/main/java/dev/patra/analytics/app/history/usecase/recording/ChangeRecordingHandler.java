package dev.patra.analytics.app.history.usecase.recording;

import dev.patra.analytics.domain.history.port.repository.RecordingSettingRepository;
import dev.patra.tool.core.security.CurrentUserPort;
import dev.patra.tool.cqrs.CommandHandler;
import java.time.Clock;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/// 改阅读记录开关（spec §4.3）：一条 upsert，不开事务；关掉只停后续，已有的保留。
@Component
@RequiredArgsConstructor
public class ChangeRecordingHandler
    implements CommandHandler<ChangeRecordingCommand, RecordingResult> {

  private final RecordingSettingRepository settings;
  private final CurrentUserPort currentUser;
  private final Clock clock;

  /// 保存开关。
  ///
  /// @param command 命令
  /// @return 保存后的开关
  @Override
  public RecordingResult handle(ChangeRecordingCommand command) {
    settings.saveEnabled(currentUser.require().userId(), command.enabled(), clock.instant());
    return RecordingResult.of(command.enabled());
  }
}
