package dev.patra.interaction.discussion;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.test.web.servlet.client.EntityExchangeResult;
import tools.jackson.databind.JsonNode;

/// 修改历史（PAP-121 的 AC）：发布 5 分钟内的修改不产生版本，之后每次编辑产生一版，历史对所有人可见；删除后历史不再
/// 公开；账号受限的人不能编辑、但能删除。
class DiscussionRevisionFlowIT extends DiscussionFlowITSupport {

  private static final long A = 1001L;
  private static final long B = 1002L;
  private static final long PAPER = 100L;

  @BeforeEach
  void world() {
    publication(PAPER);
    users(A, B);
  }

  /// 发表后立即编辑：没有「已编辑」，历史只有一个版本。
  @Test
  void should_not_record_versions_within_five_minutes() {
    long id = comment(A, PAPER, "初稿", null);

    JsonNode edited = json(edit(A, id, "改一下"));

    assertThat(edited.get("edited").asBoolean()).isFalse();
    assertThat(edited.get("body").asString()).isEqualTo("改一下");
    assertThat(revisions(null, id).get("versions")).hasSize(1);
  }

  /// 超过 5 分钟后每次编辑存一版：历史三版、最新在前，最后一版是发表时刻；匿名访客能读。
  @Test
  void should_record_each_edit_after_five_minutes() {
    long id = comment(A, PAPER, "初稿", null);
    jdbcTemplate.update(
        "UPDATE discussion.comment SET created_at = created_at - interval '6 minutes'"
            + " WHERE comment_id = ?",
        id);
    Instant created = createdAt(id);

    edit(A, id, "第二稿");
    JsonNode latest = json(edit(A, id, "第三稿"));

    assertThat(latest.get("edited").asBoolean()).isTrue();
    JsonNode versions = revisions(null, id).get("versions");
    assertThat(versions.valueStream().map(v -> v.get("body").asString()))
        .containsExactly("第三稿", "第二稿", "初稿");
    List<Instant> times =
        versions.valueStream().map(v -> Instant.parse(v.get("at").asString())).toList();
    assertThat(times.get(0)).isAfter(times.get(1));
    assertThat(times.get(1)).isAfter(times.get(2));
    assertThat(times.get(2)).isEqualTo(created);
  }

  /// 有回复的评论删除后变占位：修改历史 404。
  @Test
  void should_drop_history_with_the_comment() {
    long id = comment(A, PAPER, "初稿", null);
    comment(B, PAPER, "回复", id);
    call(HttpMethod.DELETE, A, "/comments/" + id, null);

    EntityExchangeResult<String> result =
        call(HttpMethod.GET, null, "/comments/" + id + "/revisions", null);

    assertThat(status(result)).isEqualTo(404);
    assertThat(traits(result)).contains("COMMENT_NOT_FOUND");
  }

  /// 邮箱变成未验证：编辑 409，删除照常。
  @Test
  void should_block_ineligible_editors_but_let_them_delete() {
    long id = comment(A, PAPER, "初稿", null);
    IDENTITY_DATA.put(A, "ACTIVE", false, "PUBLIC");

    EntityExchangeResult<String> edited = edit(A, id, "改");
    assertThat(status(edited)).isEqualTo(409);
    assertThat(traits(edited)).contains("EMAIL_UNVERIFIED");
    assertThat(status(call(HttpMethod.DELETE, A, "/comments/" + id, null))).isEqualTo(200);
  }

  /// 编辑。
  ///
  /// @param user 作者
  /// @param id 评论
  /// @param body 新正文
  /// @return 响应
  private EntityExchangeResult<String> edit(long user, long id, String body) {
    return call(
        HttpMethod.PUT, user, "/comments/" + id, JSON.writeValueAsString(Map.of("body", body)));
  }

  /// 修改历史。
  ///
  /// @param viewer 访客
  /// @param id 评论
  /// @return 响应体
  private JsonNode revisions(Long viewer, long id) {
    return get(viewer, "/comments/" + id + "/revisions");
  }

  /// 库里的发表时刻。
  ///
  /// @param id 评论
  /// @return 时刻
  private Instant createdAt(long id) {
    return jdbcTemplate
        .queryForObject(
            "SELECT created_at FROM discussion.comment WHERE comment_id = ?",
            OffsetDateTime.class,
            id)
        .toInstant();
  }
}
