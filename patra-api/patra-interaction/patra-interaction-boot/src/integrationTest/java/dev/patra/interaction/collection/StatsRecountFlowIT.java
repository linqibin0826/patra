package dev.patra.interaction.collection;

import static org.assertj.core.api.Assertions.assertThat;

import dev.patra.interaction.app.subject.usecase.recount.CounterResult;
import dev.patra.interaction.app.subject.usecase.recount.RecountResult;
import dev.patra.interaction.app.subject.usecase.recount.RecountStatsCommand;
import dev.patra.tool.cqrs.CommandBus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.test.web.servlet.client.EntityExchangeResult;

/// 按明细重算（PAP-133）的完整流程：经真实的命令总线与各子域登记的计数，数据一致时差异为 0；改坏的行被修正并报告。
class StatsRecountFlowIT extends CollectionFlowITSupport {

  private static final long A = 6001L;
  private static final long B = 6002L;

  @Autowired private CommandBus commandBus;

  /// 一篇文献；A、B 都是正常、已验证、主页公开的用户。A 收藏并评论，B 收藏、回复 A 的评论并给它点有帮助。
  @BeforeEach
  void seed() {
    CATALOG_DATA.put(100L, "1", "Lancet", "2019");
    IDENTITY_DATA.put(A, "ACTIVE", true, "PUBLIC");
    IDENTITY_DATA.put(B, "ACTIVE", true, "PUBLIC");
    collect(A, 100L);
    collect(B, 100L);
    EntityExchangeResult<String> comment =
        call(
            HttpMethod.POST,
            A,
            "/discussions/publication/100/comments",
            "{\"body\":\"根\",\"parentId\":null}");
    assertThat(status(comment)).as(comment.getResponseBody()).isEqualTo(201);
    String commentId = json(comment).get("commentId").asString();
    EntityExchangeResult<String> reply =
        call(
            HttpMethod.POST,
            B,
            "/discussions/publication/100/comments",
            "{\"body\":\"回复\",\"parentId\":\"" + commentId + "\"}");
    assertThat(status(reply)).as(reply.getResponseBody()).isEqualTo(201);
    assertThat(status(call(HttpMethod.PUT, B, "/comments/" + commentId + "/helpful", null)))
        .isEqualTo(200);
  }

  /// AC：数据一致时差异为 0。
  @Test
  void should_report_zero_when_consistent() {
    RecountResult result = commandBus.handle(RecountStatsCommand.of());

    assertThat(result.corrected()).isZero();
    assertThat(result.counters())
        .extracting(CounterResult::counter)
        .containsExactly(
            "collection.collector_count",
            "discussion.comment_count",
            "discussion.thread_stats",
            "reaction.helpful_count",
            "reaction.helpful_received");
  }

  /// AC：人为改坏收藏人数、删掉有帮助数的统计行、把获赞改成 0、改坏楼回复数与对象评论总数 → 各修正 1 行、值回到明细数；
  /// 再跑差异为 0。
  @Test
  void should_fix_corrupted_rows_and_report() {
    jdbcTemplate.update("UPDATE collection.subject_stats SET collector_count = 9");
    jdbcTemplate.update("DELETE FROM reaction.target_stats");
    jdbcTemplate.update("UPDATE reaction.user_stats SET helpful_received = 0 WHERE user_id = ?", A);
    jdbcTemplate.update("UPDATE discussion.thread_stats SET reply_count = 3");
    jdbcTemplate.update("UPDATE discussion.subject_stats SET comment_count = 9");

    RecountResult result = commandBus.handle(RecountStatsCommand.of());

    assertThat(result.counters())
        .containsExactly(
            CounterResult.of("collection.collector_count", 1),
            CounterResult.of("discussion.comment_count", 1),
            CounterResult.of("discussion.thread_stats", 1),
            CounterResult.of("reaction.helpful_count", 1),
            CounterResult.of("reaction.helpful_received", 1));
    assertThat(count("SELECT sum(collector_count) FROM collection.subject_stats")).isEqualTo(2L);
    assertThat(count("SELECT sum(helpful_count) FROM reaction.target_stats")).isEqualTo(1L);
    assertThat(count("SELECT helpful_received FROM reaction.user_stats WHERE user_id = ?", A))
        .isEqualTo(1L);
    assertThat(count("SELECT sum(reply_count) FROM discussion.thread_stats")).isEqualTo(1L);
    assertThat(count("SELECT sum(comment_count) FROM discussion.subject_stats")).isEqualTo(2L);
    assertThat(commandBus.handle(RecountStatsCommand.of()).corrected()).isZero();
  }
}
