package dev.patra.identity.app.account.usecase.profile;

import dev.patra.identity.domain.account.exception.CatalogUnavailableException;
import dev.patra.identity.domain.account.model.aggregate.User;
import dev.patra.identity.domain.account.model.aggregate.UserProfile;
import dev.patra.identity.domain.account.model.vo.DisplayName;
import dev.patra.identity.domain.account.model.vo.profile.Affiliation;
import dev.patra.identity.domain.account.model.vo.profile.MeshUi;
import dev.patra.identity.domain.account.model.vo.profile.ParsedProfile;
import dev.patra.identity.domain.account.model.vo.profile.ProfileContent;
import dev.patra.identity.domain.account.model.vo.profile.ResearchInterest;
import dev.patra.identity.domain.account.model.vo.profile.RorId;
import dev.patra.identity.domain.account.policy.ProfilePolicy;
import dev.patra.identity.domain.account.port.catalog.CatalogReferencePort;
import dev.patra.identity.domain.account.port.repository.UserProfileRepository;
import dev.patra.identity.domain.account.port.repository.UserRepository;
import dev.patra.identity.domain.session.exception.InvalidUserFieldsException;
import dev.patra.identity.domain.session.exception.UserModifiedConcurrentlyException;
import dev.patra.identity.domain.session.model.vo.UserFieldViolations;
import dev.patra.tool.core.error.field.FieldViolation;
import dev.patra.tool.core.security.AuthenticationRequiredException;
import dev.patra.tool.core.security.CurrentUserPort;
import dev.patra.tool.cqrs.CommandHandler;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionOperations;

/// 当前用户整份替换资料。
///
/// 顺序：读用户（不加锁）判状态 → 读当前资料 → 字段规则（错误一次报全）→ 事务外向 catalog 取新选的机构与
/// 主题词的名称快照（已存的不再调，沿用库里的快照；不存在的一起报 422，catalog 不可用第一次失败即 503）
/// → 事务内加用户行锁复核状态、重读资料、组装、保存。不加锁的第一段让 catalog 调用不占数据库连接；
/// 用户行锁和其他 `/me/**` 写操作是同一把锁。
@Slf4j
@Component
@RequiredArgsConstructor
public class UpdateProfileHandler implements CommandHandler<UpdateProfileCommand, UserProfile> {

  private final CurrentUserPort currentUserPort;
  private final UserRepository users;
  private final UserProfileRepository profiles;
  private final ProfilePolicy profilePolicy;
  private final CatalogReferencePort catalog;
  private final TransactionOperations transactions;

  /// 保存资料。
  ///
  /// @param command 命令
  /// @return 保存后的资料
  @Override
  public UserProfile handle(UpdateProfileCommand command) {
    long userId = currentUserPort.require().userId();
    User user = users.findById(userId).orElseThrow(AuthenticationRequiredException::new);
    user.requireActive();
    UserProfile before = requireProfile(userId);
    ParsedProfile parsed = profilePolicy.parse(command.draft(), before);
    Snapshots snapshots = resolveNewNames(parsed, before);
    return Objects.requireNonNull(transactions.execute(status -> save(userId, parsed, snapshots)));
  }

  /// 事务内：加锁复核状态、重读资料、组装、保存。
  ///
  /// @param userId 用户 ID
  /// @param parsed 校验过的输入
  /// @param snapshots 新取到的名称快照
  /// @return 保存后的资料
  private UserProfile save(long userId, ParsedProfile parsed, Snapshots snapshots) {
    User locked = users.findByIdForUpdate(userId).orElseThrow(AuthenticationRequiredException::new);
    locked.requireActive();
    UserProfile profile = requireProfile(userId);
    Affiliation previousAffiliation = profile.getAffiliation().orElse(null);
    String previousDisplayName = profile.getDisplayName().map(DisplayName::value).orElse(null);
    boolean displayNameChanged = profile.edit(assemble(parsed, snapshots, profile));
    UserProfile saved = profiles.save(profile);
    if (displayNameChanged) {
      log.info(
          "昵称已修改: userId={}, from={}, to={}",
          userId,
          previousDisplayName,
          saved.getDisplayName().map(DisplayName::value).orElse(null));
    }
    log.info(
        "资料已更新: userId={}, displayNameChanged={}, organizationChanged={}, interests={}",
        userId,
        displayNameChanged,
        !Objects.equals(previousAffiliation, saved.getAffiliation().orElse(null)),
        saved.getResearchInterests().size());
    return saved;
  }

  /// 向 catalog 取新选的机构与主题词名称；库里已有的不调。
  ///
  /// @param parsed 校验过的输入
  /// @param before 当前资料
  /// @return 新取到的快照
  /// @throws InvalidUserFieldsException 有标识在 catalog 里不存在，全部一起报
  /// @throws CatalogUnavailableException catalog 不可用
  private Snapshots resolveNewNames(ParsedProfile parsed, UserProfile before) {
    List<FieldViolation> violations = new ArrayList<>();
    String organizationName = null;
    RorId rorId = parsed.organizationRorId();
    if (rorId != null && !storedRorId(before).filter(rorId::equals).isPresent()) {
      Optional<String> name = catalog.organizationName(rorId);
      if (name.isEmpty()) {
        violations.add(UserFieldViolations.organizationRorIdNotFound());
      } else {
        organizationName = name.get();
      }
    }
    Set<MeshUi> stored =
        before.getResearchInterests().stream()
            .map(ResearchInterest::ui)
            .collect(Collectors.toSet());
    Map<MeshUi, String> meshNames = new HashMap<>();
    List<MeshUi> uis = parsed.researchInterests();
    for (int i = 0; i < uis.size(); i++) {
      MeshUi ui = uis.get(i);
      if (stored.contains(ui)) {
        continue;
      }
      Optional<String> name = catalog.meshName(ui);
      if (name.isEmpty()) {
        violations.add(UserFieldViolations.researchInterestNotFound(i));
      } else {
        meshNames.put(ui, name.get());
      }
    }
    if (!violations.isEmpty()) {
      throw new InvalidUserFieldsException(violations);
    }
    return new Snapshots(organizationName, meshNames);
  }

  /// 组装资料：新取的快照用新的，沿用的从这次（事务内）读到的资料里取。
  ///
  /// @param parsed 校验过的输入
  /// @param snapshots 新取到的快照
  /// @param current 事务内读到的资料
  /// @return 资料内容
  /// @throws UserModifiedConcurrentlyException 要沿用的快照已被并发的保存删掉
  private static ProfileContent assemble(
      ParsedProfile parsed, Snapshots snapshots, UserProfile current) {
    return ProfileContent.builder()
        .displayName(parsed.displayName())
        .bio(parsed.bio())
        .links(parsed.links())
        .occupation(parsed.occupation())
        .jobTitle(parsed.jobTitle())
        .affiliation(affiliation(parsed, snapshots, current))
        .department(parsed.department())
        .researchInterests(interests(parsed, snapshots, current))
        .build();
  }

  /// 机构：ROR ID → 快照（新取的或沿用的）；否则自由文本；都没有为 `null`。
  ///
  /// @param parsed 校验过的输入
  /// @param snapshots 新取到的快照
  /// @param current 事务内读到的资料
  /// @return 机构
  private static Affiliation affiliation(
      ParsedProfile parsed, Snapshots snapshots, UserProfile current) {
    RorId rorId = parsed.organizationRorId();
    if (rorId == null) {
      return parsed.organizationName() == null
          ? null
          : Affiliation.FreeText.of(parsed.organizationName());
    }
    if (snapshots.organizationName() != null) {
      return Affiliation.Registered.of(rorId, snapshots.organizationName());
    }
    return current
        .getAffiliation()
        .filter(Affiliation.Registered.class::isInstance)
        .map(Affiliation.Registered.class::cast)
        .filter(registered -> registered.rorId().equals(rorId))
        .orElseThrow(UserModifiedConcurrentlyException::new);
  }

  /// 关注领域：按输入顺序，每个 UI 配新取的或沿用的英文名。
  ///
  /// @param parsed 校验过的输入
  /// @param snapshots 新取到的快照
  /// @param current 事务内读到的资料
  /// @return 关注领域
  private static List<ResearchInterest> interests(
      ParsedProfile parsed, Snapshots snapshots, UserProfile current) {
    Map<MeshUi, String> stored =
        current.getResearchInterests().stream()
            .collect(Collectors.toMap(ResearchInterest::ui, ResearchInterest::name));
    List<ResearchInterest> interests = new ArrayList<>();
    for (MeshUi ui : parsed.researchInterests()) {
      String name = snapshots.meshNames().getOrDefault(ui, stored.get(ui));
      if (name == null) {
        throw new UserModifiedConcurrentlyException();
      }
      interests.add(ResearchInterest.of(ui, name));
    }
    return interests;
  }

  /// 资料里已存的 ROR ID。
  ///
  /// @param profile 资料
  /// @return ROR ID；自由文本或没有机构时为空
  private static Optional<RorId> storedRorId(UserProfile profile) {
    return profile
        .getAffiliation()
        .filter(Affiliation.Registered.class::isInstance)
        .map(affiliation -> ((Affiliation.Registered) affiliation).rorId());
  }

  /// 读资料行；缺行是数据不一致（注册时同事务建的）。
  ///
  /// @param userId 用户 ID
  /// @return 资料
  private UserProfile requireProfile(long userId) {
    return profiles
        .findByUserId(userId)
        .orElseThrow(() -> new IllegalStateException("用户 " + userId + " 没有资料行"));
  }

  /// 事务外从 catalog 新取到的名称快照。
  ///
  /// @param organizationName 新选的机构的名称，没有新选时为 `null`
  /// @param meshNames 新加的主题词的英文名
  private record Snapshots(String organizationName, Map<MeshUi, String> meshNames) {

    /// 防御性拷贝。
    Snapshots {
      meshNames = Map.copyOf(meshNames);
    }
  }
}
