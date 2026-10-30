package dev.patra.identity.app.account.usecase.query;

import dev.patra.identity.domain.account.model.read.UserRecipientReadModel;
import dev.patra.identity.domain.account.model.read.UserRecipientView;
import dev.patra.identity.domain.account.port.read.UserReadPort;
import dev.patra.identity.domain.session.exception.InvalidUserFieldsException;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/// 批量收件信息（`POST /_internal/users/recipients`，PAP-142）：notification 的退订落地页取脱敏邮箱与账号状态，
/// 发提醒邮件时取收件地址、是否已验证、用户名与显示名。只读，不经 CommandBus，没有事务。
///
/// 列表规则同批量用户摘要：上限按去重前的原始长度算；重复 ID 保留第一次出现；结果按去重后的请求顺序；identity
/// 不认识的 ID 省略并记 `WARN`。
@Slf4j
@Service
@RequiredArgsConstructor
public class UserRecipientQueryService {

  private final UserReadPort reader;

  /// 按请求顺序出收件信息。
  ///
  /// @param userIds 用户 ID，最多 100 个，可重复
  /// @return 收件信息，按去重后的请求顺序，不认识的省略
  /// @throws InvalidUserFieldsException 列表为 `null`、含 `null` 或超过 100 个
  public List<UserRecipientView> recipients(List<Long> userIds) {
    Set<Long> requested = UserIdBatch.requested(userIds);
    if (requested.isEmpty()) {
      return List.of();
    }
    Map<Long, UserRecipientReadModel> found =
        reader.findRecipients(requested).stream()
            .collect(Collectors.toMap(UserRecipientReadModel::userId, Function.identity()));
    List<Long> missing = requested.stream().filter(id -> !found.containsKey(id)).toList();
    if (!missing.isEmpty()) {
      log.warn("批量收件信息里有不存在的用户: userIds={}", missing);
    }
    return requested.stream()
        .map(found::get)
        .filter(Objects::nonNull)
        .map(UserRecipientReadModel::view)
        .toList();
  }
}
