package dev.patra.notification.infra.kernel.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import dev.patra.notification.infra.config.NotificationITPostgreSQLContainerInitializer;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;

/// 在站记录：每人一行，只往后挪；批量读；注销清理。
@SpringBootTest(classes = KernelPersistenceITBootstrap.class)
@ContextConfiguration(initializers = NotificationITPostgreSQLContainerInitializer.class)
@ActiveProfiles("test")
class PresenceRepositoryAdapterIT {

  private static final Instant T1 = Instant.parse("2026-10-31T10:00:00Z");
  private static final Instant T2 = Instant.parse("2026-10-31T10:05:00Z");

  @Autowired private PresenceRepositoryAdapter presence;
  @Autowired private JdbcTemplate jdbcTemplate;

  /// 每例从空表开始。
  @BeforeEach
  void truncate() {
    jdbcTemplate.update("TRUNCATE kernel.presence");
  }

  /// 晚到的旧时刻（心跳与轮询并发）不把记录往回拨。
  @Test
  void should_keep_latest_seen_at() {
    presence.touch(7L, T2);
    presence.touch(7L, T1);

    assertThat(presence.find(List.of(7L))).containsExactly(Map.entry(7L, T2));
  }

  /// 没有记录的人不在结果里。
  @Test
  void should_find_only_known_users() {
    presence.touch(7L, T1);

    assertThat(presence.find(List.of(7L, 8L))).containsOnlyKeys(7L);
  }

  /// 按用户 ID 升序分批列出、删一个人的记录。
  @Test
  void should_page_users_and_delete_one() {
    presence.touch(9L, T1);
    presence.touch(3L, T1);
    presence.touch(5L, T1);

    assertThat(presence.findUsersAfter(3L, 10)).containsExactly(5L, 9L);
    assertThat(presence.deleteAllOf(5L)).isEqualTo(1);
    assertThat(presence.findUsersAfter(0L, 10)).containsExactly(3L, 9L);
  }
}
