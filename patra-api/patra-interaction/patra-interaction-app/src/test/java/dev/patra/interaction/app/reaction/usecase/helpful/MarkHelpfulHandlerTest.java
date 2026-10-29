package dev.patra.interaction.app.reaction.usecase.helpful;

import static dev.patra.interaction.app.reaction.ReactionFixtures.NOW;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.patra.interaction.app.reaction.service.ReactionTargets;
import dev.patra.interaction.domain.reaction.exception.HelpfulTargetNotFoundException;
import dev.patra.interaction.domain.reaction.exception.SelfHelpfulException;
import dev.patra.interaction.domain.reaction.model.enums.HelpfulTargetType;
import dev.patra.interaction.domain.reaction.model.vo.HelpfulTarget;
import dev.patra.interaction.domain.reaction.model.vo.TargetProfile;
import dev.patra.interaction.domain.reaction.port.repository.HelpfulRepository;
import dev.patra.interaction.domain.subject.model.enums.PublishKind;
import dev.patra.interaction.domain.subject.port.gateway.PublishGuardGateway;
import dev.patra.tool.core.security.AccountType;
import dev.patra.tool.core.security.ClientType;
import dev.patra.tool.core.security.CurrentUser;
import dev.patra.tool.core.security.CurrentUserPort;
import dev.patra.tool.test.transaction.RecordingTransactions;
import java.time.Clock;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/// 点有帮助（spec §4.1）：事务外解析目标、预检已点过、查资格；事务内配额锁 → 目标锁 → 锁内复核 → 明细 → 计数。
class MarkHelpfulHandlerTest {

  private static final long ME = 8L;
  private static final long AUTHOR = 7L;
  private static final HelpfulTarget TARGET = HelpfulTarget.of(HelpfulTargetType.COMMENT, 42L);

  private final ReactionTargets targets = mock(ReactionTargets.class);
  private final PublishGuardGateway guard = mock(PublishGuardGateway.class);
  private final HelpfulRepository helpful = mock(HelpfulRepository.class);
  private final CurrentUserPort currentUser = mock(CurrentUserPort.class);
  private final List<String> events = new ArrayList<>();
  private MarkHelpfulHandler handler;

  @BeforeEach
  void setUp() {
    when(currentUser.require())
        .thenReturn(CurrentUser.of(ME, 1L, AccountType.USER, ClientType.WEB));
    givenProfile(TargetProfile.of(TARGET, AUTHOR, true, false));
    doAnswer(
            invocation -> {
              events.add("eligible");
              return null;
            })
        .when(guard)
        .requireEligible(ME);
    doAnswer(invocation -> events.add("admit")).when(guard).admit(ME, PublishKind.HELPFUL);
    doAnswer(invocation -> events.add("lock")).when(helpful).lockTarget(TARGET);
    doAnswer(
            invocation -> {
              events.add("exists");
              return false;
            })
        .when(helpful)
        .exists(ME, TARGET);
    doAnswer(invocation -> events.add("insert"))
        .when(helpful)
        .insert(anyLong(), any(), any(), any());
    doAnswer(invocation -> events.add("target" + invocation.getArgument(1, Integer.class)))
        .when(helpful)
        .adjustTargetCount(any(), anyInt());
    doAnswer(invocation -> events.add("received" + invocation.getArgument(1, Long.class)))
        .when(helpful)
        .adjustReceived(anyLong(), anyLong());
    doAnswer(
            invocation -> {
              events.add("count");
              return 3;
            })
        .when(helpful)
        .countOf(TARGET);
    handler =
        new MarkHelpfulHandler(
            targets,
            guard,
            helpful,
            currentUser,
            new RecordingTransactions(events),
            Clock.fixed(NOW, ZoneOffset.UTC));
  }

  @Test
  void should_mark_inside_transaction_in_lock_order() {
    HelpfulResult result = handler.handle(MarkHelpfulCommand.of(HelpfulTargetType.COMMENT, 42L));

    assertThat(result).isEqualTo(HelpfulResult.of(true, 3));
    assertThat(events)
        .containsExactly(
            "require",
            "exists",
            "eligible",
            RecordingTransactions.BEGIN,
            "admit",
            "lock",
            "require",
            "insert",
            "target1",
            "received1",
            "count",
            RecordingTransactions.END);
    verify(helpful).insert(ME, TARGET, AUTHOR, NOW);
    verify(helpful).adjustReceived(AUTHOR, 1L);
  }

  /// 匿名目标：明细的受益人为空，不加获赞。
  @Test
  void should_not_credit_anonymous_author() {
    givenProfile(TargetProfile.of(TARGET, AUTHOR, true, true));

    handler.handle(MarkHelpfulCommand.of(HelpfulTargetType.COMMENT, 42L));

    verify(helpful).insert(ME, TARGET, null, NOW);
    verify(helpful).adjustTargetCount(TARGET, 1);
    verify(helpful, never()).adjustReceived(anyLong(), anyLong());
  }

  /// 并发的另一个请求先插了：什么都不加，照样返回已点。
  @Test
  void should_skip_counters_when_insert_lost_race() {
    when(helpful.insert(anyLong(), any(), any(), any())).thenReturn(false);

    HelpfulResult result = handler.handle(MarkHelpfulCommand.of(HelpfulTargetType.COMMENT, 42L));

    assertThat(result).isEqualTo(HelpfulResult.of(true, 3));
    verify(helpful, never()).adjustTargetCount(any(), anyInt());
    verify(helpful, never()).adjustReceived(anyLong(), anyLong());
  }

  /// 已经点过：不查资格、不开事务、不耗额度（点过之后进了冷静期的人重复点也是幂等的 200）。
  @Test
  void should_return_without_quota_when_already_marked() {
    when(helpful.exists(ME, TARGET)).thenReturn(true);

    HelpfulResult result = handler.handle(MarkHelpfulCommand.of(HelpfulTargetType.COMMENT, 42L));

    assertThat(result).isEqualTo(HelpfulResult.of(true, 3));
    assertThat(events).doesNotContain(RecordingTransactions.BEGIN);
    verify(guard, never()).requireEligible(anyLong());
    verify(guard, never()).admit(anyLong(), any());
  }

  /// 自己的、不能点的：在打 identity 之前就拒绝。
  @Test
  void should_reject_before_remote_call() {
    givenProfile(TargetProfile.of(TARGET, ME, true, true));
    assertThatThrownBy(() -> handler.handle(MarkHelpfulCommand.of(HelpfulTargetType.COMMENT, 42L)))
        .isInstanceOf(SelfHelpfulException.class);

    givenProfile(TargetProfile.of(TARGET, AUTHOR, false, false));
    assertThatThrownBy(() -> handler.handle(MarkHelpfulCommand.of(HelpfulTargetType.COMMENT, 42L)))
        .isInstanceOf(HelpfulTargetNotFoundException.class);

    verify(guard, never()).requireEligible(anyLong());
  }

  /// 锁内重读发现目标已删：拒绝，不插入（事务回滚，记的额度一起回滚）。
  @Test
  void should_reject_when_target_gone_inside_lock() {
    when(targets.require(TARGET))
        .thenAnswer(
            invocation -> {
              events.add("require");
              return TargetProfile.of(TARGET, AUTHOR, !events.contains("lock"), false);
            });

    assertThatThrownBy(() -> handler.handle(MarkHelpfulCommand.of(HelpfulTargetType.COMMENT, 42L)))
        .isInstanceOf(HelpfulTargetNotFoundException.class);
    verify(helpful, never()).insert(anyLong(), any(), any(), any());
  }

  /// 桩目标画像，每次解析记一笔。
  ///
  /// @param profile 画像
  private void givenProfile(TargetProfile profile) {
    when(targets.require(TARGET))
        .thenAnswer(
            invocation -> {
              events.add("require");
              return profile;
            });
  }
}
