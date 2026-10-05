package dev.linqibin.starter.jpa.audit;

import static org.assertj.core.api.Assertions.assertThat;

import dev.linqibin.starter.jpa.id.SnowflakeIdGenerator;
import dev.linqibin.starter.jpa.support.JpaITAuditedNoteDao;
import dev.linqibin.starter.jpa.support.JpaITAuditedNoteEntity;
import dev.linqibin.starter.test.container.initializer.PostgreSQLContainerInitializer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ContextConfiguration;

/// JPA 审计集成测试：容器里没有操作人提供者时的行为。
///
/// 这是「没引安全 starter 的服务行为不变」的回归基线。
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ContextConfiguration(initializers = PostgreSQLContainerInitializer.class)
@DisplayName("JPA 审计集成测试（没有操作人提供者）")
class JpaAuditingIT {

  @Autowired private JpaITAuditedNoteDao noteDao;

  @Test
  @DisplayName("没有操作人提供者时，操作人两列为空，时间两列照常填充")
  void should_leave_auditor_columns_empty_and_fill_timestamps_when_no_provider() {
    JpaITAuditedNoteEntity note = new JpaITAuditedNoteEntity();
    note.setId(SnowflakeIdGenerator.getId());
    note.setContent("no provider");

    noteDao.saveAndFlush(note);

    JpaITAuditedNoteEntity saved = noteDao.findById(note.getId()).orElseThrow();
    assertThat(saved.getCreatedBy()).isNull();
    assertThat(saved.getUpdatedBy()).isNull();
    assertThat(saved.getCreatedAt()).isNotNull();
    assertThat(saved.getUpdatedAt()).isNotNull();
  }
}
