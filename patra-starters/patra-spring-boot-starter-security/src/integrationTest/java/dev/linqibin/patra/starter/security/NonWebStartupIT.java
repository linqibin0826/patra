package dev.linqibin.patra.starter.security;

import static org.assertj.core.api.Assertions.assertThat;

import dev.linqibin.patra.common.security.AccountType;
import dev.linqibin.patra.common.security.ClientType;
import dev.linqibin.patra.common.security.CurrentUser;
import dev.linqibin.patra.common.security.CurrentUserPort;
import dev.linqibin.patra.starter.security.context.CurrentUserRunner;
import dev.linqibin.patra.starter.security.support.SecurityITNoteDao;
import dev.linqibin.patra.starter.security.support.SecurityITNoteEntity;
import dev.linqibin.starter.jpa.id.SnowflakeIdGenerator;
import dev.linqibin.starter.test.container.initializer.PostgreSQLContainerInitializer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.context.ContextConfiguration;

/// 非 Web 方式启动的集成测试：安全 starter 加 starter-jpa，没有 Web 环境。
///
/// 对应只跑定时任务的进程。这里没有过滤器链，身份靠 `CurrentUserRunner` 带上。
@SpringBootTest(
    classes = NonWebStartupIT.NonWebApplication.class,
    webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ContextConfiguration(initializers = PostgreSQLContainerInitializer.class)
@DisplayName("非 Web 方式启动 集成测试")
class NonWebStartupIT {

  @Autowired private SecurityITNoteDao noteDao;
  @Autowired private CurrentUserPort currentUserPort;
  @Autowired private ApplicationContext applicationContext;

  private static SecurityITNoteEntity newNote() {
    SecurityITNoteEntity note = new SecurityITNoteEntity();
    note.setId(SnowflakeIdGenerator.getId());
    note.setContent("non-web");
    return note;
  }

  @Test
  @DisplayName("应用正常启动，没有安全过滤器链")
  void should_start_without_security_filter_chain() {
    assertThat(applicationContext.getBeansOfType(SecurityFilterChain.class)).isEmpty();
    assertThat(currentUserPort.current()).isEmpty();
  }

  @Test
  @DisplayName("以某个用户的身份执行时，写入的记录带上他的用户 ID")
  void should_fill_auditor_columns_when_write_runs_as_user() {
    CurrentUser user = CurrentUser.of(4343L, 9001L, AccountType.PORTAL, ClientType.WEB);

    Long noteId = CurrentUserRunner.callAs(user, () -> noteDao.save(newNote()).getId());

    SecurityITNoteEntity saved = noteDao.findById(noteId).orElseThrow();
    assertThat(saved.getCreatedBy()).isEqualTo(4343L);
    assertThat(saved.getUpdatedBy()).isEqualTo(4343L);
    assertThat(currentUserPort.current()).isEmpty();
  }

  @Test
  @DisplayName("不带身份的线程写入时，创建人为空")
  void should_leave_auditor_columns_empty_without_identity() {
    Long noteId = noteDao.save(newNote()).getId();

    assertThat(noteDao.findById(noteId).orElseThrow().getCreatedBy()).isNull();
  }

  /// 只用自动配置的最小应用，不做大范围的组件扫描。
  @SpringBootConfiguration
  @EnableAutoConfiguration
  static class NonWebApplication {}
}
