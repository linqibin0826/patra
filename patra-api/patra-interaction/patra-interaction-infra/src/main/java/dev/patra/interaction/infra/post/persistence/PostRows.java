package dev.patra.interaction.infra.post.persistence;

import dev.patra.interaction.domain.post.model.aggregate.PostAggregate;
import dev.patra.interaction.domain.post.model.enums.ArticleType;
import dev.patra.interaction.domain.post.model.enums.FinalOutcome;
import dev.patra.interaction.domain.post.model.enums.FirstDecision;
import dev.patra.interaction.domain.post.model.enums.PostStatus;
import dev.patra.interaction.domain.post.model.enums.PostType;
import dev.patra.interaction.domain.post.model.vo.ExperienceFields;
import dev.patra.interaction.domain.post.model.vo.PostBody;
import dev.patra.interaction.domain.post.model.vo.PostId;
import dev.patra.interaction.domain.post.model.vo.PostRevision;
import dev.patra.interaction.domain.subject.model.vo.SubjectId;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/// post 三张表的行映射与参数转换，两个适配器共用；修改历史的字段按 jsonb 存，用注入的 `JsonMapper` 编解码。
@Component
@RequiredArgsConstructor
class PostRows {

  /// 基表与扩展表连接后的 FROM 子句（别名 `p`、`e`）。
  static final String FROM =
      " FROM post.post p JOIN post.submission_experience e ON e.post_id = p.post_id";

  /// 读回聚合要的列。
  static final String COLUMNS =
      "p.post_id, p.post_type, p.subject_id, p.author_user_id, p.anonymous, p.body, p.status,"
          + " p.created_at, p.revised_at, e.submitted_on, e.first_decision_on, e.first_decision,"
          + " e.final_outcome, e.final_decision_on, e.article_type, e.review_rounds,"
          + " e.review_quality, e.editor_communication, e.apc_paid, e.previously_rejected";

  private final JsonMapper jsonMapper;

  /// 行 → 聚合。
  ///
  /// @return 行映射器
  RowMapper<PostAggregate> postMapper() {
    return (rs, rowNum) ->
        PostAggregate.builder()
            .postId(PostId.of(rs.getLong("post_id")))
            .type(PostType.valueOf(rs.getString("post_type")))
            .subjectId(SubjectId.of(rs.getLong("subject_id")))
            .authorId(rs.getLong("author_user_id"))
            .anonymous(rs.getBoolean("anonymous"))
            .fields(fields(rs))
            .body(body(rs.getString("body")))
            .status(PostStatus.valueOf(rs.getString("status")))
            .createdAt(instant(rs, "created_at"))
            .revisedAt(instant(rs, "revised_at"))
            .build();
  }

  /// 修改历史的一行 → 一版（列 `body`、`fields`（文本）、`written_at`、`replaced_at`）。
  ///
  /// @return 行映射器
  RowMapper<PostRevision> revisionMapper() {
    return (rs, rowNum) ->
        PostRevision.of(
            readFields(rs.getString("fields")),
            body(rs.getString("body")),
            instant(rs, "written_at"),
            instant(rs, "replaced_at"));
  }

  /// 结构化字段 → jsonb 文本。
  ///
  /// @param fields 字段
  /// @return JSON
  String writeFields(ExperienceFields fields) {
    return jsonMapper.writeValueAsString(fields);
  }

  /// jsonb 文本 → 结构化字段。
  ///
  /// @param json JSON
  /// @return 字段
  ExperienceFields readFields(String json) {
    return jsonMapper.readValue(json, ExperienceFields.class);
  }

  /// 读扩展表的列。
  ///
  /// @param rs 结果集（当前行）
  /// @return 字段
  /// @throws SQLException 读列失败
  private static ExperienceFields fields(ResultSet rs) throws SQLException {
    return ExperienceFields.builder()
        .submittedOn(rs.getObject("submitted_on", LocalDate.class))
        .firstDecisionOn(rs.getObject("first_decision_on", LocalDate.class))
        .firstDecision(FirstDecision.valueOf(rs.getString("first_decision")))
        .finalOutcome(FinalOutcome.valueOf(rs.getString("final_outcome")))
        .finalDecisionOn(rs.getObject("final_decision_on", LocalDate.class))
        .articleType(ArticleType.valueOf(rs.getString("article_type")))
        .reviewRounds(rs.getObject("review_rounds", Integer.class))
        .reviewQuality(rs.getObject("review_quality", Integer.class))
        .editorCommunication(rs.getObject("editor_communication", Integer.class))
        .apcPaid(rs.getObject("apc_paid", Boolean.class))
        .previouslyRejected(rs.getObject("previously_rejected", Boolean.class))
        .build();
  }

  /// 正文列 → 正文。
  ///
  /// @param value 列值，可空
  /// @return 正文，可空
  private static PostBody body(String value) {
    return value == null ? null : new PostBody(value);
  }

  /// 读一个 `timestamptz` 列。
  ///
  /// @param rs 结果集
  /// @param column 列名
  /// @return 时刻，列为空时 `null`
  /// @throws SQLException 读列失败
  static Instant instant(ResultSet rs, String column) throws SQLException {
    OffsetDateTime value = rs.getObject(column, OffsetDateTime.class);
    return value == null ? null : value.toInstant();
  }

  /// 时间参数（UTC）。
  ///
  /// @param instant 时刻，可空
  /// @return 带偏移的时间
  static OffsetDateTime utc(Instant instant) {
    return instant == null ? null : instant.atOffset(ZoneOffset.UTC);
  }
}
