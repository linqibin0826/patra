package dev.patra.notification.infra.mail.persistence;

import dev.patra.notification.domain.mail.model.aggregate.MailDelivery;
import dev.patra.notification.domain.mail.port.repository.MailDeliveryRepository;
import java.sql.Timestamp;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/// 邮件发送记录仓储的 JDBC 实现。
///
/// 只追加、按主键查有没有，没有更新与关联，用 `JdbcClient` 直接写 SQL，不建 JPA 实体。
/// 追加时主键冲突就是重复处理的 bug，直接抛出，让那次处理回滚、重试时在查重那一步跳过。
@Repository
@RequiredArgsConstructor
public class MailDeliveryRepositoryAdapter implements MailDeliveryRepository {

  private final JdbcClient jdbcClient;

  /// 这个投递 ID 是否已有记录。
  ///
  /// @param deliveryId 投递 ID
  /// @return 有记录为 `true`
  @Override
  public boolean exists(UUID deliveryId) {
    return jdbcClient
        .sql("SELECT EXISTS (SELECT 1 FROM mail.mail_delivery WHERE delivery_id = ?)")
        .param(deliveryId)
        .query(Boolean.class)
        .single();
  }

  /// 追加一条记录；调用方没有事务时报错，不自己开事务。
  ///
  /// @param delivery 记录
  @Override
  @Transactional(propagation = Propagation.MANDATORY)
  public void add(MailDelivery delivery) {
    jdbcClient
        .sql(
            """
            INSERT INTO mail.mail_delivery (delivery_id, template, outcome, recorded_at)
            VALUES (?, ?, ?, ?)
            """)
        .param(delivery.deliveryId())
        .param(delivery.template())
        .param(delivery.outcome().name())
        .param(Timestamp.from(delivery.recordedAt()))
        .update();
  }
}
