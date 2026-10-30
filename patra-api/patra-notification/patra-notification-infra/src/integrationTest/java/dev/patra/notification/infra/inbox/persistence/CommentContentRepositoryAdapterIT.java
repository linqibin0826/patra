package dev.patra.notification.infra.inbox.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import dev.patra.notification.domain.inbox.model.enums.NotificationType;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/// 评论快照与墓碑：按版本只保留最新、墓碑之后不再写正文、保留期清理只删没人引用的。
class CommentContentRepositoryAdapterIT extends InboxPersistenceITSupport {

  @Autowired private CommentContentRepositoryAdapter comments;
  @Autowired private NotificationRepositoryAdapter notifications;

  /// 先到 v2，晚到的 v1 不覆盖。
  @Test
  void should_keep_newer_version() {
    assertThat(comments.saveIfNewer(500L, 40L, 2, "新", NOW)).isTrue();
    assertThat(comments.saveIfNewer(500L, 40L, 1, "旧", NOW)).isTrue();

    assertThat(row(500L)).containsEntry("version", 2).containsEntry("plain_text", "新");
  }

  /// v1 之后来 v2：覆盖。
  @Test
  void should_overwrite_older_version() {
    comments.saveIfNewer(500L, 40L, 1, "旧", NOW);
    comments.saveIfNewer(500L, 40L, 2, "新", NOW);

    assertThat(row(500L)).containsEntry("version", 2).containsEntry("plain_text", "新");
  }

  /// 墓碑之后：写正文返回 false，正文仍为空。
  @Test
  void should_report_tombstone() {
    comments.saveIfNewer(500L, 40L, 1, "正文", NOW);
    comments.markDeleted(500L, 40L, NOW, NOW);

    assertThat(comments.isDeleted(500L)).isTrue();
    assertThat(comments.saveIfNewer(500L, 40L, 3, "改过", NOW)).isFalse();
    assertThat(row(500L)).containsEntry("plain_text", null);
  }

  /// 删除先到：没有快照也留下墓碑；重复删除保留第一次的删除时刻。
  @Test
  void should_create_tombstone_when_absent_and_keep_first_deleted_at() {
    comments.markDeleted(500L, 40L, NOW, NOW);
    comments.markDeleted(500L, 40L, NOW.plusSeconds(60), NOW.plusSeconds(60));

    assertThat(comments.isDeleted(500L)).isTrue();
    assertThat(comments.isDeleted(501L)).isFalse();
    assertThat(comments.saveIfNewer(500L, 40L, 1, "正文", NOW)).isFalse();
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT deleted_at = ?::timestamptz FROM inbox.comment WHERE comment_id = 500",
                Boolean.class,
                NOW.toString()))
        .isTrue();
  }

  /// 经验删除：对象下的快照与墓碑一起删。
  @Test
  void should_delete_by_subject() {
    comments.saveIfNewer(500L, 40L, 1, "a", NOW);
    comments.markDeleted(501L, 40L, NOW, NOW);
    comments.saveIfNewer(502L, 41L, 1, "b", NOW);

    assertThat(comments.deleteBySubject(40L)).isEqualTo(2);
    assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM inbox.comment", Long.class))
        .isEqualTo(1);
  }

  /// 保留期：只删写入早于截止、且没有提醒引用的。
  @Test
  void should_purge_only_unreferenced() {
    Instant old = Instant.parse("2026-04-01T00:00:00Z");
    comments.saveIfNewer(500L, 40L, 1, "有人引用", old);
    comments.saveIfNewer(501L, 40L, 1, "没人引用", old);
    comments.saveIfNewer(502L, 40L, 1, "新的", NOW);
    notifications.insertAll(List.of(draft(2L, NotificationType.COMMENT_REPLY, 500L)));

    assertThat(comments.purgeUnreferencedBefore(Instant.parse("2026-05-31T00:00:00Z")))
        .isEqualTo(1);
    assertThat(jdbcTemplate.queryForList("SELECT comment_id FROM inbox.comment", Long.class))
        .containsExactlyInAnyOrder(500L, 502L);
  }

  /// 一行快照。
  ///
  /// @param commentId 评论
  /// @return 列 → 值
  private Map<String, Object> row(long commentId) {
    return jdbcTemplate.queryForMap(
        "SELECT version, plain_text FROM inbox.comment WHERE comment_id = ?", commentId);
  }
}
