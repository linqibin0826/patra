package dev.patra.interaction.subject;

import static com.github.tomakehurst.wiremock.client.WireMock.anyUrl;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.github.tomakehurst.wiremock.verification.LoggedRequest;
import dev.patra.interaction.app.subject.usecase.reconcile.ReconcileSubjectsCommand;
import dev.patra.interaction.app.subject.usecase.reconcile.ReconciliationResult;
import dev.patra.interaction.domain.subject.exception.SubjectErrorTrait;
import dev.patra.interaction.domain.subject.exception.SubjectNotFoundException;
import dev.patra.interaction.domain.subject.model.enums.SubjectCapability;
import dev.patra.interaction.domain.subject.model.enums.SubjectState;
import dev.patra.interaction.domain.subject.model.enums.SubjectType;
import dev.patra.interaction.domain.subject.model.vo.SubjectId;
import dev.patra.interaction.domain.subject.model.vo.SubjectOwner;
import dev.patra.interaction.domain.subject.model.vo.SubjectSnapshot;
import dev.patra.interaction.domain.subject.port.gateway.SubjectGateway;
import dev.patra.interaction.domain.subject.port.read.SubjectReadPort;
import dev.patra.interaction.support.InteractionFlowITSupport;
import dev.patra.tool.cqrs.CommandBus;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.support.TransactionTemplate;

/// 本地对象（投稿经验）的完整流程（PAP-120 的 AC）：由所属子域在自己的事务里登记、改状态；不进每日对账；所有者与
/// 匿名标记经读端口读到。
class LocalSubjectFlowIT extends InteractionFlowITSupport {

  private static final SubjectType POST = SubjectType.POST;
  private static final SubjectOwner OWNER = SubjectOwner.of(9L, true);
  private static final SubjectSnapshot SNAPSHOT =
      SubjectSnapshot.of(null, "The Lancet", "2026-03", "Lancet");

  @Autowired private SubjectGateway gateway;
  @Autowired private SubjectReadPort readPort;
  @Autowired private CommandBus commandBus;
  @Autowired private TransactionTemplate transactions;

  /// AC：本地对象不进对账。对账照常处理文献，catalog 收不到任何帖子的请求，帖子那一行一个字都不变。
  @Test
  void should_keep_local_subject_out_of_reconciliation() {
    CATALOG_DATA.put(100L, "1", "Lancet", "2019");
    gateway.resolveForInteraction(SubjectType.PUBLICATION, "100", SubjectCapability.COLLECT);
    SubjectId post = register(7L);
    String before = rowOf(post);
    catalog.resetRequests();

    ReconciliationResult result = commandBus.handle(ReconcileSubjectsCommand.of());

    assertThat(result.scanned()).isEqualTo(1);
    assertThat(catalog.findAll(postRequestedFor(anyUrl())))
        .extracting(LoggedRequest::getBodyAsString)
        .isNotEmpty()
        .noneMatch(body -> body.contains("post") || body.contains("\"7\""));
    assertThat(rowOf(post)).isEqualTo(before);
  }

  /// AC：状态只由所属子域更新。删除后不能再在它下面发表，所有者与匿名标记照样读得到。
  @Test
  void should_change_state_only_through_owning_subdomain() {
    SubjectId post = register(7L);

    transactions.executeWithoutResult(
        status -> gateway.updateLocal(POST, 7L, SubjectState.UNAVAILABLE, SNAPSHOT));

    assertThatThrownBy(() -> gateway.resolveForInteraction(POST, "7", SubjectCapability.DISCUSS))
        .isInstanceOfSatisfying(
            SubjectNotFoundException.class,
            ex -> assertThat(ex.getErrorTraits()).contains(SubjectErrorTrait.SUBJECT_UNAVAILABLE));
    assertThat(readPort.findByIds(List.of(post)).get(post).owner()).isEqualTo(OWNER);
    assertThat(readPort.findByIds(List.of(post)).get(post).state())
        .isEqualTo(SubjectState.UNAVAILABLE);
  }

  /// 登记与改状态必须在调用方的事务里，和对象本身的写入一起提交或回滚。
  @Test
  void should_require_callers_transaction() {
    assertThatThrownBy(() -> gateway.registerLocal(POST, 7L, OWNER, SNAPSHOT))
        .isInstanceOf(IllegalTransactionStateException.class);
    assertThatThrownBy(() -> gateway.updateLocal(POST, 7L, SubjectState.UNAVAILABLE, SNAPSHOT))
        .isInstanceOf(IllegalTransactionStateException.class);
    assertThat(count("SELECT count(*) FROM subject.registry")).isZero();
  }

  /// 事务外改一个已登记的帖子：直接拒绝，这一行一个字都不变。
  @Test
  void should_leave_existing_row_untouched_when_updated_outside_transaction() {
    SubjectId post = register(7L);
    String before = rowOf(post);

    assertThatThrownBy(() -> gateway.updateLocal(POST, 7L, SubjectState.UNAVAILABLE, SNAPSHOT))
        .isInstanceOf(IllegalTransactionStateException.class);
    assertThat(rowOf(post)).isEqualTo(before);
  }

  /// 没登记过的帖子就是不存在，不带「已失效」原因码。
  @Test
  void should_reject_unregistered_post() {
    assertThatThrownBy(() -> gateway.resolveForInteraction(POST, "8", SubjectCapability.DISCUSS))
        .isInstanceOfSatisfying(
            SubjectNotFoundException.class,
            ex ->
                assertThat(ex.getErrorTraits())
                    .doesNotContain(SubjectErrorTrait.SUBJECT_UNAVAILABLE));
  }

  /// 在事务里登记一个帖子。
  ///
  /// @param postId 帖子 ID
  /// @return 本地 ID
  private SubjectId register(long postId) {
    return transactions.execute(status -> gateway.registerLocal(POST, postId, OWNER, SNAPSHOT));
  }

  /// 一行的全部内容（比较「什么都没改」）。
  ///
  /// @param subjectId 本地 ID
  /// @return 文本
  private String rowOf(SubjectId subjectId) {
    return jdbcTemplate.queryForObject(
        "SELECT concat_ws('|', subject_id, natural_key, source_id, state, snapshot::text,"
            + " snapshot_at, owner_user_id, owner_anonymous, updated_at)"
            + " FROM subject.registry WHERE subject_id = ?",
        String.class,
        subjectId.value());
  }
}
