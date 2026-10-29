package dev.patra.identity.app.account.usecase.query;

import dev.patra.identity.domain.account.model.aggregate.FormerUsername;
import dev.patra.identity.domain.account.model.read.UserSummaryReadModel;
import dev.patra.identity.domain.account.model.vo.Username;
import dev.patra.identity.domain.account.port.read.UserReadPort;
import dev.patra.identity.domain.account.port.repository.FormerUsernameRepository;
import dev.patra.identity.domain.session.exception.InvalidUserFieldsException;
import dev.patra.identity.domain.session.model.vo.UserFieldViolations;
import java.time.Clock;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/// 用户名批量解析（`POST /_internal/users/resolve/by-usernames`，PAP-118）：interaction 写评论时把 `@用户名`
/// 换成用户 ID。只读，不经 CommandBus，没有事务。
///
/// 1. `null` 元素与不合用户名语法的元素跳过；原文完全相同的只留第一次出现。都被跳过时不查库。
/// 2. 先按当前用户名查；查不到的，再查此刻仍在保留期内的旧名（取改名时间最近的一条），给原主。
/// 3. 状态原样带上，四种都可能；是不是正常账号由调用方判。主页可见性不看。
/// 4. 结果按请求顺序、回显请求原文；解析不到的省略。解析不到属正常情况（手误、`@` 不是提及），不打日志。
@Service
@RequiredArgsConstructor
public class UserResolutionQueryService {

  /// 一次最多多少个用户名。
  public static final int MAX_USERNAMES = 100;

  private final UserReadPort reader;
  private final FormerUsernameRepository formerUsernames;
  private final Clock clock;

  /// 批量解析用户名。
  ///
  /// @param usernames 用户名原文（不带 `@`），最多 100 个，按去重前的长度算
  /// @return 解析到的那些，按请求顺序，`username` 是请求原文
  /// @throws InvalidUserFieldsException 列表为 `null` 或超过 100 个
  public List<ResolvedUser> resolveByUsernames(List<String> usernames) {
    validate(usernames);
    Set<String> requested =
        usernames.stream()
            .filter(raw -> Username.violation(raw).isEmpty())
            .collect(Collectors.toCollection(LinkedHashSet::new));
    if (requested.isEmpty()) {
      return List.of();
    }
    List<Username> names = requested.stream().map(Username::of).distinct().toList();
    Map<String, UserSummaryReadModel> current =
        reader.findSummariesByUsernames(names).stream()
            .collect(Collectors.toMap(UserSummaryReadModel::username, Function.identity()));
    List<Username> misses =
        names.stream().filter(name -> !current.containsKey(name.value())).toList();
    Map<String, UserSummaryReadModel> reserved =
        misses.isEmpty() ? Map.of() : reservedOwners(misses);
    List<ResolvedUser> resolved = new ArrayList<>();
    for (String raw : requested) {
      String name = Username.normalize(raw);
      UserSummaryReadModel owner = current.getOrDefault(name, reserved.get(name));
      if (owner != null) {
        resolved.add(ResolvedUser.of(raw, owner.userId(), owner.status()));
      }
    }
    return resolved;
  }

  /// 此刻仍在保留期内的旧名 → 原主此刻的摘要原始数据：先查保留记录，再按原主 ID 一次取齐。
  ///
  /// @param names 当前用户名查不到的名字
  /// @return 旧名 → 原主；不在保留期的名字不在其中
  private Map<String, UserSummaryReadModel> reservedOwners(List<Username> names) {
    Map<String, Long> ownerIds =
        formerUsernames.findAllReserved(names, clock.instant()).stream()
            .collect(
                Collectors.toMap(
                    former -> former.getUsername().value(), FormerUsername::getUserId));
    if (ownerIds.isEmpty()) {
      return Map.of();
    }
    Map<Long, UserSummaryReadModel> owners =
        reader.findSummaries(ownerIds.values().stream().distinct().toList()).stream()
            .collect(Collectors.toMap(UserSummaryReadModel::userId, Function.identity()));
    Map<String, UserSummaryReadModel> reserved = new HashMap<>();
    ownerIds.forEach(
        (name, ownerId) -> {
          UserSummaryReadModel owner = owners.get(ownerId);
          if (owner != null) {
            reserved.put(name, owner);
          }
        });
    return reserved;
  }

  /// 校验请求：不能为 `null`，按去重前的长度不能超过上限。
  ///
  /// @param usernames 用户名原文
  /// @throws InvalidUserFieldsException 任一条不满足
  private static void validate(List<String> usernames) {
    if (usernames == null) {
      throw new InvalidUserFieldsException(List.of(UserFieldViolations.usernamesRequired()));
    }
    if (usernames.size() > MAX_USERNAMES) {
      throw new InvalidUserFieldsException(List.of(UserFieldViolations.usernamesTooMany()));
    }
  }
}
