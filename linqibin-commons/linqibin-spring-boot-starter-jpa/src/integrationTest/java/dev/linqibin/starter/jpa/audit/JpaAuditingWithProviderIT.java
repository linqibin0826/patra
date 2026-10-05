package dev.linqibin.starter.jpa.audit;

import static org.assertj.core.api.Assertions.assertThat;

import dev.linqibin.starter.jpa.id.SnowflakeIdGenerator;
import dev.linqibin.starter.jpa.support.JpaITAuditedNoteDao;
import dev.linqibin.starter.jpa.support.JpaITAuditedNoteEntity;
import dev.linqibin.starter.test.container.initializer.PostgreSQLContainerInitializer;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.ContextConfiguration;

/// JPA 审计集成测试：容器里有操作人提供者时的行为。
///
/// 测试应用会像真实服务一样把 `JpaAuditingConfig` 提前扫描进来，
/// 这里验证后注册的提供者仍然生效。
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ContextConfiguration(initializers = PostgreSQLContainerInitializer.class)
@DisplayName("JPA 审计集成测试（有操作人提供者）")
class JpaAuditingWithProviderIT {

  private static final long AUDITOR_ID = 42L;

  @Autowired private JpaITAuditedNoteDao noteDao;

  @Test
  @DisplayName("有操作人提供者时，操作人两列是它返回的 ID")
  void should_fill_auditor_columns_from_provider() {
    JpaITAuditedNoteEntity note = new JpaITAuditedNoteEntity();
    note.setId(SnowflakeIdGenerator.getId());
    note.setContent("with provider");

    noteDao.saveAndFlush(note);

    JpaITAuditedNoteEntity saved = noteDao.findById(note.getId()).orElseThrow();
    assertThat(saved.getCreatedBy()).isEqualTo(AUDITOR_ID);
    assertThat(saved.getUpdatedBy()).isEqualTo(AUDITOR_ID);
  }

  /// 提供一个固定的操作人。
  @TestConfiguration
  static class FixedAuditorConfig {

    /// 固定返回同一个操作人 ID 的提供者。
    ///
    /// @return 操作人提供者
    @Bean
    CurrentAuditorProvider fixedAuditorProvider() {
      return () -> Optional.of(AUDITOR_ID);
    }
  }
}
