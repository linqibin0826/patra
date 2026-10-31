package dev.patra.identity.app.account.usecase.query;

import dev.patra.identity.domain.session.exception.InvalidUserFieldsException;
import dev.patra.identity.domain.session.model.vo.UserFieldViolations;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/// 批量收件信息的用户 ID 列表校验与去重，规则与批量用户摘要相同（`UserSummaryQueryService` 里有同样的一份；
/// 那边这次不动，免得和并行改 identity 的 Issue 撞车，以后可以改用这里）。
final class UserIdBatch {

  /// 一次最多多少个用户 ID。
  static final int MAX_USER_IDS = 100;

  private UserIdBatch() {}

  /// 校验并去重：不能为 `null`、不能含 `null`、去重前不能超过 {@value #MAX_USER_IDS} 个；重复 ID 保留第一次出现。
  ///
  /// @param userIds 请求里的用户 ID
  /// @return 去重后保持请求顺序的集合
  /// @throws InvalidUserFieldsException 任一条不满足
  static Set<Long> requested(List<Long> userIds) {
    if (userIds == null) {
      throw new InvalidUserFieldsException(List.of(UserFieldViolations.userIdsRequired()));
    }
    if (userIds.size() > MAX_USER_IDS) {
      throw new InvalidUserFieldsException(List.of(UserFieldViolations.userIdsTooMany()));
    }
    if (userIds.stream().anyMatch(Objects::isNull)) {
      throw new InvalidUserFieldsException(List.of(UserFieldViolations.userIdsInvalidFormat()));
    }
    return new LinkedHashSet<>(userIds);
  }
}
