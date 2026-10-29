package dev.patra.interaction.app.discussion;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import dev.patra.interaction.domain.discussion.model.enums.CommentState;
import dev.patra.interaction.domain.discussion.model.read.CommentRecord;
import dev.patra.interaction.domain.subject.model.enums.AccountState;
import dev.patra.interaction.domain.subject.model.enums.SubjectState;
import dev.patra.interaction.domain.subject.model.enums.SubjectType;
import dev.patra.interaction.domain.subject.model.read.SubjectView;
import dev.patra.interaction.domain.subject.model.read.UserCard;
import dev.patra.interaction.domain.subject.model.vo.SubjectId;
import dev.patra.interaction.domain.subject.model.vo.SubjectOwner;
import dev.patra.interaction.domain.subject.model.vo.SubjectSnapshot;
import dev.patra.tool.core.security.AccountType;
import dev.patra.tool.core.security.AuthenticationRequiredException;
import dev.patra.tool.core.security.ClientType;
import dev.patra.tool.core.security.CurrentUser;
import dev.patra.tool.core.security.CurrentUserPort;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import org.jspecify.annotations.Nullable;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionOperations;

/// 讨论 app 层单元测试的共用数据：当前用户、记录边界的事务、评论与名片、对象视图。
public final class DiscussionFixture {

  /// 事务开始的事件。
  public static final String BEGIN = "tx-begin";

  /// 事务结束的事件。
  public static final String END = "tx-end";

  /// 时刻。
  public static final Instant T0 = Instant.parse("2026-10-27T08:00:00Z");

  /// 文献对象。
  public static final SubjectId PUB_ID = SubjectId.of(11L);

  /// 投稿经验对象。
  public static final SubjectId POST_ID = SubjectId.of(21L);

  /// 只有静态方法。
  private DiscussionFixture() {}

  /// 已登录的当前用户。
  ///
  /// @param userId 用户 ID
  /// @return 端口
  public static CurrentUserPort signedIn(long userId) {
    CurrentUserPort port = mock(CurrentUserPort.class);
    CurrentUser user = CurrentUser.of(userId, 1L, AccountType.USER, ClientType.WEB);
    when(port.current()).thenReturn(Optional.of(user));
    when(port.require()).thenReturn(user);
    return port;
  }

  /// 匿名访客。
  ///
  /// @return 端口
  public static CurrentUserPort anonymous() {
    CurrentUserPort port = mock(CurrentUserPort.class);
    when(port.current()).thenReturn(Optional.empty());
    when(port.require()).thenThrow(new AuthenticationRequiredException());
    return port;
  }

  /// 直接执行回调、并把边界记进事件列表的「事务」。
  ///
  /// @param events 事件列表
  /// @return 事务
  public static TransactionOperations recording(List<String> events) {
    return new TransactionOperations() {
      /// 在「事务」里执行回调并记下边界。
      ///
      /// @param action 回调
      /// @param <T> 返回类型
      /// @return 回调结果
      @Override
      public <T> @Nullable T execute(TransactionCallback<T> action) {
        events.add(BEGIN);
        TransactionStatus status = new SimpleTransactionStatus();
        T result = action.doInTransaction(status);
        events.add(END);
        return result;
      }

      /// 不返回结果的版本。
      ///
      /// @param action 回调
      @Override
      public void executeWithoutResult(Consumer<TransactionStatus> action) {
        execute(
            status -> {
              action.accept(status);
              return null;
            });
      }
    };
  }

  /// 文献对象的视图（没有所有者）。
  ///
  /// @return 视图
  public static SubjectView publication() {
    return SubjectView.builder()
        .subjectId(PUB_ID)
        .type(SubjectType.PUBLICATION)
        .key("100")
        .state(SubjectState.ACTIVE)
        .snapshot(SubjectSnapshot.of("Title", "J", "2019", null))
        .build();
  }

  /// 投稿经验对象的视图。
  ///
  /// @param ownerId 所有者
  /// @param anonymous 所有者是否匿名
  /// @return 视图
  public static SubjectView post(long ownerId, boolean anonymous) {
    return SubjectView.builder()
        .subjectId(POST_ID)
        .type(SubjectType.POST)
        .key("500")
        .state(SubjectState.ACTIVE)
        .snapshot(SubjectSnapshot.of(null, "J", "2026-03", null))
        .owner(SubjectOwner.of(ownerId, anonymous))
        .build();
  }

  /// 一条正常的根评论（对象是文献）。
  ///
  /// @param commentId 评论 ID
  /// @param authorId 作者
  /// @return 构造器
  public static CommentRecord.CommentRecordBuilder root(long commentId, long authorId) {
    return CommentRecord.builder()
        .commentId(commentId)
        .subjectId(PUB_ID)
        .rootId(commentId)
        .authorId(authorId)
        .state(CommentState.ACTIVE)
        .body("正文")
        .createdAt(T0);
  }

  /// 一条正常的回复。
  ///
  /// @param commentId 评论 ID
  /// @param rootId 根评论
  /// @param parentId 父评论
  /// @param parentAuthorId 父评论的作者
  /// @param authorId 作者
  /// @return 构造器
  public static CommentRecord.CommentRecordBuilder reply(
      long commentId, long rootId, long parentId, long parentAuthorId, long authorId) {
    return root(commentId, authorId)
        .rootId(rootId)
        .parentId(parentId)
        .parentAuthorId(parentAuthorId)
        .parentState(CommentState.ACTIVE);
  }

  /// 正常、主页可见的名片：用户名 `u{id}`、昵称 `D{id}`。
  ///
  /// @param userId 用户 ID
  /// @return 名片
  public static UserCard card(long userId) {
    return card(userId, AccountState.ACTIVE, true);
  }

  /// 指定状态与可见性的名片；已注销的是占位。
  ///
  /// @param userId 用户 ID
  /// @param state 账号状态
  /// @param profileVisible 主页是否对访客可见
  /// @return 名片
  public static UserCard card(long userId, AccountState state, boolean profileVisible) {
    boolean deleted = state == AccountState.DELETED;
    return UserCard.builder()
        .userId(userId)
        .username(deleted ? null : "u" + userId)
        .displayName(deleted ? null : "D" + userId)
        .avatarUrl(deleted ? null : "/users/" + userId + "/avatar?v=a")
        .avatarPalette(3)
        .state(state)
        .emailVerified(!deleted)
        .profileVisible(profileVisible)
        .build();
  }
}
