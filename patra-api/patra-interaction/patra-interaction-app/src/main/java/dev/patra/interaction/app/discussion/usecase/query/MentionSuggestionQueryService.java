package dev.patra.interaction.app.discussion.usecase.query;

import dev.patra.interaction.app.discussion.service.DiscussionSubjects;
import dev.patra.interaction.domain.discussion.policy.DiscussionLimits;
import dev.patra.interaction.domain.discussion.port.mention.MentionDirectoryPort;
import dev.patra.interaction.domain.discussion.port.read.DiscussionReadPort;
import dev.patra.interaction.domain.subject.model.enums.AccountState;
import dev.patra.interaction.domain.subject.model.enums.SubjectType;
import dev.patra.interaction.domain.subject.model.read.SubjectView;
import dev.patra.interaction.domain.subject.model.read.UserCard;
import dev.patra.interaction.domain.subject.model.vo.SubjectOwner;
import dev.patra.interaction.domain.subject.port.user.UserDirectoryPort;
import dev.patra.tool.core.security.CurrentUserPort;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/// @ 联想（spec 6.3，要登录）：本讨论的参与者在前（排除访客本人与匿名所有者；非匿名的投稿经验所有者排第一；只留正常、
/// 主页对访客可见的），最多 6 位；有查询词时再并上 identity 的前缀联想，按用户 ID 去重，合计最多 8 位。
@Component
@RequiredArgsConstructor
public class MentionSuggestionQueryService {

  private final DiscussionSubjects subjects;
  private final DiscussionReadPort reads;
  private final UserDirectoryPort users;
  private final MentionDirectoryPort directory;
  private final CurrentUserPort currentUser;

  /// 联想。
  ///
  /// @param type 对外类型名
  /// @param key 对外 key
  /// @param q `@` 后面已输入的原文，可空
  /// @return 联想项，参与者在前
  public List<SuggestionView> suggest(String type, String key, String q) {
    long viewer = currentUser.require().userId();
    SubjectType subjectType = subjects.requireDiscussable(type, key);
    Optional<SubjectView> subject = subjects.findForRead(subjectType, key);
    List<UserCard> participants =
        subject.map(found -> participants(found, viewer)).orElse(List.of());
    String prefix = q == null ? "" : q.strip();
    if (prefix.isEmpty()) {
      return participants.stream()
          .limit(DiscussionLimits.SUGGEST_PARTICIPANTS)
          .map(card -> SuggestionView.of(card, true))
          .toList();
    }
    String lower = prefix.toLowerCase(Locale.ROOT);
    List<UserCard> matched =
        participants.stream()
            .filter(card -> matches(card, lower))
            .limit(DiscussionLimits.SUGGEST_PARTICIPANTS)
            .toList();
    List<SuggestionView> result = new ArrayList<>();
    Set<Long> seen = new HashSet<>();
    for (UserCard card : matched) {
      result.add(SuggestionView.of(card, true));
      seen.add(card.userId());
    }
    int limit =
        Math.min(
            DiscussionLimits.IDENTITY_SUGGEST_MAX, DiscussionLimits.SUGGEST_TOTAL + matched.size());
    for (UserCard card : directory.suggest(prefix, viewer, limit)) {
      if (result.size() >= DiscussionLimits.SUGGEST_TOTAL) {
        break;
      }
      if (seen.add(card.userId())) {
        result.add(SuggestionView.of(card, false));
      }
    }
    return result;
  }

  /// 本讨论的参与者：非匿名所有者在前，再按最近发言；只留正常、主页可见、有用户名的。
  ///
  /// @param subject 对象
  /// @param viewer 访客
  /// @return 名片，按顺序
  private List<UserCard> participants(SubjectView subject, long viewer) {
    SubjectOwner owner = subject.owner();
    List<Long> ids = new ArrayList<>();
    List<Long> excluded = new ArrayList<>(List.of(viewer));
    if (owner != null && owner.userId() != viewer) {
      excluded.add(owner.userId());
      if (!owner.anonymous()) {
        ids.add(owner.userId());
      }
    }
    ids.addAll(
        reads.findRecentParticipants(
            subject.subjectId(), excluded, DiscussionLimits.PARTICIPANT_SCAN));
    if (ids.isEmpty()) {
      return List.of();
    }
    Map<Long, UserCard> cards = users.findCards(ids, viewer);
    return ids.stream()
        .map(cards::get)
        .filter(
            card ->
                card != null
                    && card.state() == AccountState.ACTIVE
                    && card.profileVisible()
                    && card.username() != null)
        .toList();
  }

  /// 用户名或昵称（不分大小写）以查询词开头。
  ///
  /// @param card 名片
  /// @param lowerPrefix 小写的查询词
  /// @return 匹配时为 `true`
  private static boolean matches(UserCard card, String lowerPrefix) {
    return card.username().toLowerCase(Locale.ROOT).startsWith(lowerPrefix)
        || (card.displayName() != null
            && card.displayName().toLowerCase(Locale.ROOT).startsWith(lowerPrefix));
  }
}
