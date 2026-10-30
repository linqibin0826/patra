package dev.patra.notification.infra.mail.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.patra.notification.domain.mail.model.aggregate.MailDelivery;
import dev.patra.notification.domain.mail.port.repository.MailDeliveryRepository;
import dev.patra.notification.infra.config.NotificationITPostgreSQLContainerInitializer;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.support.TransactionTemplate;

/// 邮件发送记录的仓储：查有没有发过、在调用方事务里追加记录；表里只有四列。
@SpringBootTest
@ContextConfiguration(initializers = NotificationITPostgreSQLContainerInitializer.class)
@ActiveProfiles("test")
@DisplayName("邮件发送记录的仓储")
class MailDeliveryRepositoryAdapterIT {

  private static final Instant AT = Instant.parse("2026-10-31T02:00:00Z");

  @Autowired private MailDeliveryRepository deliveries;
  @Autowired private TransactionTemplate transactions;
  @Autowired private JdbcClient jdbcClient;

  /// 追加后按投递 ID 查得到；别的 ID 查不到。
  @Test
  void should_report_existing_delivery() {
    UUID id = UUID.randomUUID();

    transactions.executeWithoutResult(
        status -> deliveries.add(MailDelivery.sent(id, "password-reset", AT)));

    assertThat(deliveries.exists(id)).isTrue();
    assertThat(deliveries.exists(UUID.randomUUID())).isFalse();
  }

  /// 模板名、结果、时间都按原样存下。
  @Test
  void should_store_template_outcome_and_time() {
    UUID id = UUID.randomUUID();

    transactions.executeWithoutResult(
        status -> deliveries.add(MailDelivery.linkExpired(id, "email-verification", AT)));

    Map<String, Object> row =
        jdbcClient
            .sql(
                "SELECT template, outcome, recorded_at FROM mail.mail_delivery WHERE delivery_id = ?")
            .param(id)
            .query()
            .singleRow();
    assertThat(row)
        .containsEntry("template", "email-verification")
        .containsEntry("outcome", "LINK_EXPIRED");
    assertThat(((Timestamp) row.get("recorded_at")).toInstant()).isEqualTo(AT);
  }

  /// 追加只能在调用方的事务里做（app 的短事务里与「已处理」一起写）。
  @Test
  void should_require_caller_transaction() {
    assertThatThrownBy(
            () -> deliveries.add(MailDelivery.sent(UUID.randomUUID(), "password-reset", AT)))
        .isInstanceOf(IllegalTransactionStateException.class);
  }

  /// 表里只有四列：不存正文、链接、收件地址与用户 ID。
  @Test
  void should_only_have_four_columns() {
    assertThat(
            jdbcClient
                .sql(
                    "SELECT column_name FROM information_schema.columns "
                        + "WHERE table_schema = 'mail' AND table_name = 'mail_delivery'")
                .query(String.class)
                .list())
        .containsExactlyInAnyOrder("delivery_id", "template", "outcome", "recorded_at");
  }
}
