package dev.linqibin.patra.starter.security;

import static org.assertj.core.api.Assertions.assertThat;

import dev.linqibin.patra.starter.security.support.SecurityITNoteDao;
import dev.linqibin.patra.starter.security.support.SecurityITNoteEntity;
import dev.linqibin.patra.starter.security.test.TestIdentity;
import dev.linqibin.starter.test.container.initializer.PostgreSQLContainerInitializer;
import java.util.function.Consumer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.client.RestTestClient;

/// 审计列的集成测试：在「扫描整个 dev.linqibin」的启动方式下，登录用户写入的记录带上他的用户 ID。
@SpringBootTest(
    classes = SecurityITBootstrap.class,
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureRestTestClient
@ContextConfiguration(initializers = PostgreSQLContainerInitializer.class)
@DisplayName("审计列 集成测试")
class SecurityAuditingIT {

  @Autowired private RestTestClient restClient;
  @Autowired private SecurityITNoteDao noteDao;

  private SecurityITNoteEntity createNote(Consumer<HttpHeaders> headers) {
    String noteId =
        restClient
            .post()
            .uri("/probe/notes")
            .headers(headers)
            .exchange()
            .expectStatus()
            .isOk()
            .expectBody(String.class)
            .returnResult()
            .getResponseBody();
    return noteDao.findById(Long.valueOf(noteId)).orElseThrow();
  }

  @Test
  @DisplayName("登录用户写入的记录，创建人和更新人是他的用户 ID")
  void should_fill_auditor_columns_with_user_id_when_logged_in() {
    SecurityITNoteEntity note = createNote(headers -> headers.addAll(TestIdentity.headers(4242L)));

    assertThat(note.getCreatedBy()).isEqualTo(4242L);
    assertThat(note.getUpdatedBy()).isEqualTo(4242L);
  }

  @Test
  @DisplayName("匿名写入的记录，创建人和更新人为空")
  void should_leave_auditor_columns_empty_when_anonymous() {
    SecurityITNoteEntity note = createNote(headers -> {});

    assertThat(note.getCreatedBy()).isNull();
    assertThat(note.getUpdatedBy()).isNull();
  }
}
