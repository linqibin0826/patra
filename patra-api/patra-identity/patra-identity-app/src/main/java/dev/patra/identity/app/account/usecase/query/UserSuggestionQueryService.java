package dev.patra.identity.app.account.usecase.query;

import dev.patra.identity.domain.account.model.read.ProfileAccess;
import dev.patra.identity.domain.account.model.read.UserSummaryReadModel;
import dev.patra.identity.domain.account.model.read.UserSummaryView;
import dev.patra.identity.domain.account.model.vo.DisplayNamePrefix;
import dev.patra.identity.domain.account.model.vo.UsernamePrefix;
import dev.patra.identity.domain.account.port.read.UserReadPort;
import dev.patra.identity.domain.session.exception.InvalidUserFieldsException;
import dev.patra.identity.domain.session.model.vo.UserFieldViolations;
import dev.patra.tool.core.error.field.FieldViolation;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/// @ 联想的前缀查询（`POST /_internal/users/suggest`，PAP-118）：给 interaction 列出主页对访客可见的正常账号。
/// 只读，不经 CommandBus，没有事务。
///
/// 两路候选，每路最多 200 个：用户名以该前缀开头的 `ACTIVE` 账号（按用户名字节序），昵称以该前缀开头（不分
/// 大小写）的 `ACTIVE` 账号（按小写昵称码点序）。用户名一路在前、昵称一路在后，按用户 ID 去重；去掉访客本人和
/// 主页对访客不可见的（规则只用 `ProfileAccess`），挑够条数上限为止，用户名一路已够时不查昵称一路。前缀既不
/// 可能是用户名的开头、也不可能是昵称的开头时直接返回空，不查库。
@Service
@RequiredArgsConstructor
public class UserSuggestionQueryService {

  /// 条数上限最大多少。
  public static final int MAX_LIMIT = 20;

  /// 最多扫多少个候选：前缀很短时只看按字节序的前 200 个正常账号，过滤后不足就返回不足的。
  public static final int CANDIDATE_CAP = 200;

  private final UserReadPort reader;

  /// 按用户名前缀联想。
  ///
  /// @param prefix 前缀，先规范化
  /// @param viewerId 访客的用户 ID，匿名时为空
  /// @param limit 最多几个，1 ~ 20
  /// @return 摘要，按用户名字节序
  /// @throws InvalidUserFieldsException 前缀或条数上限缺失、条数上限越界（违例一起报）
  public List<UserSummaryView> suggest(String prefix, Optional<Long> viewerId, Integer limit) {
    validate(prefix, limit);
    Map<Long, UserSummaryReadModel> picked = new LinkedHashMap<>();
    UsernamePrefix.parse(prefix)
        .ifPresent(
            byUsername ->
                pick(
                    reader.findActiveSummariesByUsernamePrefix(byUsername, CANDIDATE_CAP),
                    viewerId,
                    limit,
                    picked));
    if (picked.size() < limit) {
      DisplayNamePrefix.parse(prefix)
          .ifPresent(
              byDisplayName ->
                  pick(
                      reader.findActiveSummariesByDisplayNamePrefix(byDisplayName, CANDIDATE_CAP),
                      viewerId,
                      limit,
                      picked));
    }
    return picked.values().stream().map(candidate -> candidate.viewFor(viewerId)).toList();
  }

  /// 从一路候选里按顺序挑：跳过已经挑过的人和不能出现在联想里的人，挑够条数上限为止。
  ///
  /// @param candidates 这一路的候选，按库里的顺序
  /// @param viewerId 访客的用户 ID，匿名时为空
  /// @param limit 条数上限
  /// @param picked 已挑出的人，按挑出的顺序；就地追加
  private static void pick(
      List<UserSummaryReadModel> candidates,
      Optional<Long> viewerId,
      int limit,
      Map<Long, UserSummaryReadModel> picked) {
    for (UserSummaryReadModel candidate : candidates) {
      if (picked.size() >= limit) {
        return;
      }
      if (!picked.containsKey(candidate.userId()) && isSuggestable(candidate.access(), viewerId)) {
        picked.put(candidate.userId(), candidate);
      }
    }
  }

  /// 能不能出现在这位访客的联想里：不是访客本人，且主页对访客可见（规则只在 `ProfileAccess` 里）。
  ///
  /// @param access 候选人的主页可见性规则
  /// @param viewerId 访客的用户 ID，匿名时为空
  /// @return 能时为 `true`
  private static boolean isSuggestable(ProfileAccess access, Optional<Long> viewerId) {
    return !access.isOwner(viewerId) && access.isVisibleTo(viewerId);
  }

  /// 校验请求：前缀、条数上限都要有，条数上限在 1 ~ 20；违例收齐后一起抛。
  ///
  /// @param prefix 前缀
  /// @param limit 条数上限
  /// @throws InvalidUserFieldsException 有任一违例
  private static void validate(String prefix, Integer limit) {
    List<FieldViolation> violations = new ArrayList<>();
    if (prefix == null) {
      violations.add(UserFieldViolations.prefixRequired());
    }
    if (limit == null) {
      violations.add(UserFieldViolations.limitRequired());
    } else if (limit < 1 || limit > MAX_LIMIT) {
      violations.add(UserFieldViolations.limitOutOfRange());
    }
    if (!violations.isEmpty()) {
      throw new InvalidUserFieldsException(violations);
    }
  }
}
