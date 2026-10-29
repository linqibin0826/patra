package dev.patra.identity.app.account.usecase.query;

import dev.patra.identity.domain.account.model.read.UserSummaryReadModel;
import dev.patra.identity.domain.account.model.read.UserSummaryView;
import dev.patra.identity.domain.account.port.read.UserReadPort;
import dev.patra.identity.domain.session.exception.InvalidUserFieldsException;
import dev.patra.identity.domain.session.model.vo.UserFieldViolations;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/// 批量用户摘要（`POST /_internal/users/summaries`，PAP-108）：给 interaction 拼收藏夹所有者，并替访客算好主页
/// 是否可见。只读，不经 CommandBus，没有事务。
///
/// 上限按去重前的原始长度算；重复 ID 保留第一次出现；结果按去重后的请求顺序；identity 不认识的 ID 省略并记
/// `WARN`（用户行从不物理删除，不认识只可能是调用方的数据错误）。
@Slf4j
@Service
@RequiredArgsConstructor
public class UserSummaryQueryService {

  /// 一次最多多少个用户 ID。
  public static final int MAX_USER_IDS = 100;

  private final UserReadPort reader;

  /// 按请求顺序出摘要。
  ///
  /// @param userIds 用户 ID，最多 100 个，可重复
  /// @param viewerId 访客的用户 ID，匿名时为空
  /// @return 摘要，按去重后的请求顺序，不认识的省略
  /// @throws InvalidUserFieldsException 列表为 `null`、含 `null` 或超过 100 个
  public List<UserSummaryView> summaries(List<Long> userIds, Optional<Long> viewerId) {
    validate(userIds);
    if (userIds.isEmpty()) {
      return List.of();
    }
    Set<Long> requested = new LinkedHashSet<>(userIds);
    Map<Long, UserSummaryReadModel> found =
        reader.findSummaries(requested).stream()
            .collect(Collectors.toMap(UserSummaryReadModel::userId, Function.identity()));
    List<Long> missing = requested.stream().filter(id -> !found.containsKey(id)).toList();
    if (!missing.isEmpty()) {
      log.warn("批量用户摘要里有不存在的用户: userIds={}", missing);
    }
    return requested.stream()
        .map(found::get)
        .filter(Objects::nonNull)
        .map(user -> user.viewFor(viewerId))
        .toList();
  }

  /// 校验请求：不能为 `null`、不能含 `null`、不能超过上限。
  ///
  /// @param userIds 用户 ID
  /// @throws InvalidUserFieldsException 任一条不满足
  private static void validate(List<Long> userIds) {
    if (userIds == null) {
      throw new InvalidUserFieldsException(List.of(UserFieldViolations.userIdsRequired()));
    }
    if (userIds.size() > MAX_USER_IDS) {
      throw new InvalidUserFieldsException(List.of(UserFieldViolations.userIdsTooMany()));
    }
    if (userIds.stream().anyMatch(Objects::isNull)) {
      throw new InvalidUserFieldsException(List.of(UserFieldViolations.userIdsInvalidFormat()));
    }
  }
}
