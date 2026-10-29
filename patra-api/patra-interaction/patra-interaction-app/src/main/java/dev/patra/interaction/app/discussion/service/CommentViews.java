package dev.patra.interaction.app.discussion.service;

import dev.patra.interaction.app.discussion.usecase.query.CommentView;
import dev.patra.interaction.app.discussion.usecase.query.MentionView;
import dev.patra.interaction.app.discussion.usecase.query.ReplyToView;
import dev.patra.interaction.domain.discussion.model.enums.CommentState;
import dev.patra.interaction.domain.discussion.model.read.CommentRecord;
import dev.patra.interaction.domain.discussion.policy.MentionSyntax;
import dev.patra.interaction.domain.subject.model.enums.AccountState;
import dev.patra.interaction.domain.subject.model.read.SubjectView;
import dev.patra.interaction.domain.subject.model.read.UserCard;
import dev.patra.interaction.domain.subject.model.vo.SubjectOwner;
import dev.patra.interaction.domain.subject.port.user.UserDirectoryPort;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/// 把评论拼成对外的视图（spec 5.7、5.8），读写共用。
///
/// 作者、`replyTo` 的作者、被 @ 的人一次批量向 identity 取齐（每 100 个一批）；匿名所有者只在访客是他本人时取。
/// 主页对访客不可见的名片去掉用户名与头像地址（PAP-108 第 5.5 节）；identity 不可用原样抛出。
@Component
@RequiredArgsConstructor
public class CommentViews {

  private final UserDirectoryPort users;

  /// 拼视图。
  ///
  /// @param comments 评论，按要展示的顺序
  /// @param subject 对象（所有者与匿名标记）
  /// @param viewerId 访客，匿名为 `null`
  /// @param knownCards 已经拿到的名片（如发表者自己的），不再向 identity 要
  /// @return 视图，与 `comments` 一一对应
  public List<CommentView> assemble(
      List<CommentRecord> comments,
      SubjectView subject,
      Long viewerId,
      Map<Long, UserCard> knownCards) {
    SubjectOwner owner = subject.owner();
    Set<Long> needed = new LinkedHashSet<>();
    for (CommentRecord comment : comments) {
      if (comment.isDeleted()) {
        continue;
      }
      if (!isAnonymousOwner(owner, comment.authorId()) || isViewer(viewerId, comment.authorId())) {
        needed.add(comment.authorId());
      }
      if (needsReplyTarget(comment) && !isAnonymousOwner(owner, comment.parentAuthorId())) {
        needed.add(comment.parentAuthorId());
      }
      needed.addAll(MentionSyntax.mentionedIds(comment.body()));
    }
    Map<Long, UserCard> cards = new HashMap<>(knownCards);
    needed.removeAll(knownCards.keySet());
    cards.putAll(cards(needed, viewerId));
    return comments.stream().map(comment -> toView(comment, owner, viewerId, cards)).toList();
  }

  /// 修改历史等只要提及实体的地方。
  ///
  /// @param userIds 被 @ 的人
  /// @param viewerId 访客，匿名为 `null`
  /// @return 提及，与 `userIds` 的顺序一致
  public List<MentionView> mentions(Collection<Long> userIds, Long viewerId) {
    Map<Long, UserCard> cards = cards(userIds, viewerId);
    return userIds.stream().map(id -> toMention(id, cards.get(id))).toList();
  }

  /// 原样的名片（不按可见性遮蔽），每 100 个一批。
  ///
  /// @param userIds 用户
  /// @param viewerId 访客，匿名为 `null`
  /// @return 用户 ID → 名片；identity 省略的不在结果里
  public Map<Long, UserCard> cards(Collection<Long> userIds, Long viewerId) {
    List<Long> ids = List.copyOf(new LinkedHashSet<>(userIds));
    Map<Long, UserCard> cards = new HashMap<>();
    for (int from = 0; from < ids.size(); from += UserDirectoryPort.MAX_USERS) {
      List<Long> batch =
          ids.subList(from, Math.min(ids.size(), from + UserDirectoryPort.MAX_USERS));
      cards.putAll(users.findCards(batch, viewerId));
    }
    return cards;
  }

  /// 一条评论的视图。
  ///
  /// @param comment 评论
  /// @param owner 对象所有者，可空
  /// @param viewerId 访客
  /// @param cards 名片
  /// @return 视图
  private static CommentView toView(
      CommentRecord comment, SubjectOwner owner, Long viewerId, Map<Long, UserCard> cards) {
    CommentView.CommentViewBuilder view =
        CommentView.builder()
            .commentId(comment.commentId())
            .rootId(comment.rootId())
            .parentId(comment.parentId())
            .hasReplies(comment.hasReplies())
            .createdAt(comment.createdAt());
    if (comment.isDeleted()) {
      return view.deleted(true).build();
    }
    long authorId = comment.authorId();
    boolean anonymous = isAnonymousOwner(owner, authorId);
    boolean mine = isViewer(viewerId, authorId);
    List<MentionView> mentions = new ArrayList<>();
    for (Long id : MentionSyntax.mentionedIds(comment.body())) {
      mentions.add(toMention(id, cards.get(id)));
    }
    return view.author(anonymous && !mine ? null : masked(cards.get(authorId)))
        .anonymous(anonymous)
        .owner(owner != null && owner.userId() == authorId)
        .mine(mine)
        .replyTo(replyTo(comment, owner, cards))
        .body(comment.body())
        .mentions(mentions)
        .edited(comment.edited())
        .build();
  }

  /// 「回复 @X」的对象。
  ///
  /// @param comment 评论
  /// @param owner 对象所有者，可空
  /// @param cards 名片
  /// @return 对象；回复根评论或根评论本身为 `null`
  private static ReplyToView replyTo(
      CommentRecord comment, SubjectOwner owner, Map<Long, UserCard> cards) {
    if (!needsReplyTarget(comment)) {
      return null;
    }
    if (comment.parentState() == CommentState.DELETED) {
      return ReplyToView.of(true, false, null);
    }
    if (isAnonymousOwner(owner, comment.parentAuthorId())) {
      return ReplyToView.of(false, true, null);
    }
    return ReplyToView.of(false, false, masked(cards.get(comment.parentAuthorId())));
  }

  /// 回复的不是根评论。
  ///
  /// @param comment 评论
  /// @return 要给「回复 @X」时为 `true`
  private static boolean needsReplyTarget(CommentRecord comment) {
    return comment.parentId() != null && comment.parentId() != comment.rootId();
  }

  /// 是不是对象的匿名所有者。
  ///
  /// @param owner 对象所有者，可空
  /// @param userId 用户，可空
  /// @return 是时为 `true`
  private static boolean isAnonymousOwner(SubjectOwner owner, Long userId) {
    return owner != null && owner.anonymous() && userId != null && owner.userId() == userId;
  }

  /// 是不是访客本人。
  ///
  /// @param viewerId 访客，可空
  /// @param userId 用户
  /// @return 是时为 `true`
  private static boolean isViewer(Long viewerId, long userId) {
    return viewerId != null && viewerId == userId;
  }

  /// 按可见性遮蔽：主页对访客不可见时去掉用户名与头像地址。
  ///
  /// @param card 名片，可空
  /// @return 遮蔽后的名片；`null` 原样返回
  private static UserCard masked(UserCard card) {
    if (card == null || card.profileVisible()) {
      return card;
    }
    return UserCard.builder()
        .userId(card.userId())
        .displayName(card.displayName())
        .avatarPalette(card.avatarPalette())
        .state(card.state())
        .emailVerified(card.emailVerified())
        .profileVisible(false)
        .build();
  }

  /// 提及实体。
  ///
  /// @param userId 被 @ 的人
  /// @param card 他的名片，identity 省略时为 `null`
  /// @return 提及
  private static MentionView toMention(long userId, UserCard card) {
    if (card == null || card.state() == AccountState.DELETED) {
      return MentionView.of(userId, null, null, true);
    }
    return MentionView.of(
        userId, card.profileVisible() ? card.username() : null, card.displayName(), false);
  }
}
