package dev.patra.tool.jpa.audit;

import static org.assertj.core.api.Assertions.assertThat;

import dev.patra.tool.jpa.id.SnowflakeIdGenerator;
import dev.patra.tool.jpa.support.JpaITAuditedNoteDao;
import dev.patra.tool.jpa.support.JpaITAuditedNoteEntity;
import dev.patra.tool.test.container.initializer.PostgreSQLContainerInitializer;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.transaction.support.TransactionTemplate;

/// 主表更新时 `updated_by` 怎么填：有当前用户写他的 ID，没有（定时任务、导入、消息消费）写空。
///
/// 当前用户由一个可切换的提供者给出，测试里先以用户身份插入，再换身份更新同一行。
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ContextConfiguration(initializers = PostgreSQLContainerInitializer.class)
@DisplayName("主表更新时的最后修改人")
class UpdatedByAuditingIT {

  private static final long CREATOR_ID = 42L;

  /// 当前用户，空表示系统写入。
  private static final AtomicReference<Long> CURRENT_USER = new AtomicReference<>();

  @Autowired private JpaITAuditedNoteDao noteDao;
  @Autowired private TransactionTemplate transactions;

  @AfterEach
  void resetCurrentUser() {
    CURRENT_USER.set(null);
  }

  @Test
  @DisplayName("系统按聚合重建实体再合并保存时，最后修改人写空")
  void system_merge_of_rebuilt_entity_clears_last_modifier() {
    JpaITAuditedNoteEntity created = insertAsUser(CREATOR_ID);

    JpaITAuditedNoteEntity rebuilt = new JpaITAuditedNoteEntity();
    rebuilt.setId(created.getId());
    rebuilt.setVersion(created.getVersion());
    rebuilt.setContent("rebuilt by import");
    noteDao.saveAndFlush(rebuilt);

    JpaITAuditedNoteEntity saved = noteDao.findById(created.getId()).orElseThrow();
    assertThat(saved.getCreatedBy()).isEqualTo(CREATOR_ID);
    assertThat(saved.getUpdatedBy()).isNull();
  }

  @Test
  @DisplayName("系统加载实体就地修改时，最后修改人写空，不留上一个用户的 ID")
  void system_update_of_loaded_entity_clears_last_modifier() {
    JpaITAuditedNoteEntity created = insertAsUser(CREATOR_ID);

    transactions.executeWithoutResult(
        status -> noteDao.findById(created.getId()).orElseThrow().setContent("changed by job"));

    JpaITAuditedNoteEntity saved = noteDao.findById(created.getId()).orElseThrow();
    assertThat(saved.getContent()).isEqualTo("changed by job");
    assertThat(saved.getCreatedBy()).isEqualTo(CREATOR_ID);
    assertThat(saved.getUpdatedBy()).isNull();
  }

  @Test
  @DisplayName("另一个用户修改时，最后修改人是他")
  void user_update_records_that_user_as_last_modifier() {
    JpaITAuditedNoteEntity created = insertAsUser(CREATOR_ID);

    CURRENT_USER.set(7L);
    transactions.executeWithoutResult(
        status -> noteDao.findById(created.getId()).orElseThrow().setContent("changed by user"));

    JpaITAuditedNoteEntity saved = noteDao.findById(created.getId()).orElseThrow();
    assertThat(saved.getCreatedBy()).isEqualTo(CREATOR_ID);
    assertThat(saved.getUpdatedBy()).isEqualTo(7L);
  }

  @Test
  @DisplayName("系统只读取不修改时，不发更新，最后修改人保持原样")
  void system_read_without_change_keeps_last_modifier() {
    JpaITAuditedNoteEntity created = insertAsUser(CREATOR_ID);

    transactions.executeWithoutResult(
        status -> noteDao.findById(created.getId()).orElseThrow().getContent());

    JpaITAuditedNoteEntity saved = noteDao.findById(created.getId()).orElseThrow();
    assertThat(saved.getUpdatedBy()).isEqualTo(CREATOR_ID);
    assertThat(saved.getVersion()).isEqualTo(created.getVersion());
  }

  private JpaITAuditedNoteEntity insertAsUser(long userId) {
    CURRENT_USER.set(userId);
    JpaITAuditedNoteEntity note = new JpaITAuditedNoteEntity();
    note.setId(SnowflakeIdGenerator.getId());
    note.setContent("written by user");
    noteDao.saveAndFlush(note);
    CURRENT_USER.set(null);
    return noteDao.findById(note.getId()).orElseThrow();
  }

  /// 提供可切换的当前用户。
  @TestConfiguration
  static class SwitchableAuditorConfig {

    /// 返回测试当前设定的用户，未设定时返回空。
    ///
    /// @return 操作人提供者
    @Bean
    CurrentAuditorProvider switchableAuditorProvider() {
      return () -> Optional.ofNullable(CURRENT_USER.get());
    }
  }
}
