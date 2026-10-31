package dev.patra.notification.infra.mail.persistence;

import dev.patra.notification.domain.kernel.model.enums.NotificationCategory;
import dev.patra.notification.domain.mail.model.aggregate.ReminderDelivery;
import dev.patra.notification.domain.mail.model.enums.DeliveryKind;
import dev.patra.notification.domain.mail.model.enums.DeliveryStatus;
import dev.patra.notification.domain.mail.model.enums.SkipReason;
import dev.patra.notification.domain.mail.port.repository.ReminderDeliveryRepository;
import java.sql.Array;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/// 投递记录的原生 SQL 实现。汇总的「一人一窗口」靠部分唯一索引，`ON CONFLICT` 带同样的谓词。
@Repository
@RequiredArgsConstructor
public class ReminderDeliveryRepositoryAdapter implements ReminderDeliveryRepository {

  /// 插入的列与值。
  private static final String INSERT =
      """
      INSERT INTO mail.reminder_delivery
        (delivery_id, recipient_id, kind, category, notification_ids, window_end, status, skip_reason,
         attempts, next_attempt_at, created_at, first_sent_at, finished_at)
      VALUES (:deliveryId, :recipientId, :kind, :category, :notificationIds, :windowEnd, :status,
              :skipReason, :attempts, :nextAttemptAt, :createdAt, :firstSentAt, :finishedAt)
      """;

  /// 一行映射成投递记录。
  private static final RowMapper<ReminderDelivery> ROW =
      (rs, rowNum) ->
          ReminderDelivery.restore()
              .deliveryId(rs.getObject("delivery_id", UUID.class))
              .recipientId(rs.getLong("recipient_id"))
              .kind(DeliveryKind.valueOf(rs.getString("kind")))
              .category(enumOrNull(NotificationCategory.class, rs.getString("category")))
              .notificationIds(ids(rs.getArray("notification_ids")))
              .windowEnd(instant(rs, "window_end"))
              .status(DeliveryStatus.valueOf(rs.getString("status")))
              .skipReason(enumOrNull(SkipReason.class, rs.getString("skip_reason")))
              .attempts(rs.getInt("attempts"))
              .nextAttemptAt(instant(rs, "next_attempt_at"))
              .createdAt(instant(rs, "created_at"))
              .firstSentAt(instant(rs, "first_sent_at"))
              .finishedAt(instant(rs, "finished_at"))
              .build();

  private final JdbcClient jdbc;

  /// 新建一条。
  ///
  /// @param delivery 投递记录
  @Override
  public void insert(ReminderDelivery delivery) {
    bind(jdbc.sql(INSERT), delivery).update();
  }

  /// 新建一条汇总投递，这个人这个窗口已有就跳过。
  ///
  /// @param delivery 汇总投递
  /// @return 真插入了为 `true`
  @Override
  public boolean insertDigestIfAbsent(ReminderDelivery delivery) {
    return bind(
                jdbc.sql(
                    INSERT
                        + " ON CONFLICT (recipient_id, window_end) WHERE kind = 'DIGEST' DO NOTHING"),
                delivery)
            .update()
        == 1;
  }

  /// 待发且已到下一次尝试时刻的投递。
  ///
  /// @param now 此刻
  /// @param limit 最多几条
  /// @return 投递记录
  @Override
  public List<ReminderDelivery> findDue(Instant now, int limit) {
    return jdbc.sql(
            """
            SELECT * FROM mail.reminder_delivery
            WHERE status = 'PENDING' AND next_attempt_at <= :now
            ORDER BY next_attempt_at, created_at LIMIT :limit
            """)
        .param("now", utc(now))
        .param("limit", limit)
        .query(ROW)
        .list();
  }

  /// 写回会变的列。
  ///
  /// @param delivery 投递记录
  @Override
  public void update(ReminderDelivery delivery) {
    jdbc.sql(
            """
            UPDATE mail.reminder_delivery
            SET status = :status, skip_reason = :skipReason, attempts = :attempts,
                next_attempt_at = :nextAttemptAt, first_sent_at = :firstSentAt, finished_at = :finishedAt
            WHERE delivery_id = :deliveryId
            """)
        .param("status", delivery.getStatus().name())
        .param("skipReason", nameOrNull(delivery.getSkipReason()))
        .param("attempts", delivery.getAttempts())
        .param("nextAttemptAt", utc(delivery.getNextAttemptAt()))
        .param("firstSentAt", utc(delivery.getFirstSentAt()))
        .param("finishedAt", utc(delivery.getFinishedAt()))
        .param("deliveryId", delivery.getDeliveryId())
        .update();
  }

  /// 删除结束得早于截止时刻的记录。
  ///
  /// @param cutoff 截止时刻
  /// @return 删掉的行数
  @Override
  public int deleteFinishedBefore(Instant cutoff) {
    return jdbc.sql("DELETE FROM mail.reminder_delivery WHERE finished_at < :cutoff")
        .param("cutoff", utc(cutoff))
        .update();
  }

  /// 绑定插入语句的全部参数。
  ///
  /// @param spec 语句
  /// @param d 投递记录
  /// @return 绑好参数的语句
  private static JdbcClient.StatementSpec bind(JdbcClient.StatementSpec spec, ReminderDelivery d) {
    boolean instant = d.getKind() == DeliveryKind.INSTANT;
    return spec.param("deliveryId", d.getDeliveryId())
        .param("recipientId", d.getRecipientId())
        .param("kind", d.getKind().name())
        .param("category", nameOrNull(d.getCategory()))
        .param("notificationIds", instant ? d.getNotificationIds().toArray(Long[]::new) : null)
        .param("windowEnd", utc(d.getWindowEnd()))
        .param("status", d.getStatus().name())
        .param("skipReason", nameOrNull(d.getSkipReason()))
        .param("attempts", d.getAttempts())
        .param("nextAttemptAt", utc(d.getNextAttemptAt()))
        .param("createdAt", utc(d.getCreatedAt()))
        .param("firstSentAt", utc(d.getFirstSentAt()))
        .param("finishedAt", utc(d.getFinishedAt()));
  }

  /// 读一个可空的时刻列。
  ///
  /// @param rs 结果集
  /// @param column 列名
  /// @return 时刻，可空
  /// @throws SQLException 读取失败
  private static Instant instant(ResultSet rs, String column) throws SQLException {
    OffsetDateTime value = rs.getObject(column, OffsetDateTime.class);
    return value == null ? null : value.toInstant();
  }

  /// 读提醒 ID 数组列。
  ///
  /// @param array 数组，可空
  /// @return ID 列表；为空时是空列表
  /// @throws SQLException 读取失败
  private static List<Long> ids(Array array) throws SQLException {
    return array == null ? List.of() : Arrays.asList((Long[]) array.getArray());
  }

  /// 可空的枚举列。
  ///
  /// @param type 枚举类型
  /// @param value 列值，可空
  /// @param <E> 枚举
  /// @return 枚举值，可空
  private static <E extends Enum<E>> E enumOrNull(Class<E> type, String value) {
    return value == null ? null : Enum.valueOf(type, value);
  }

  /// 可空枚举的名字。
  ///
  /// @param value 枚举值，可空
  /// @return 名字，可空
  private static String nameOrNull(Enum<?> value) {
    return value == null ? null : value.name();
  }

  /// 可空时刻按 UTC 绑定。
  ///
  /// @param instant 时刻，可空
  /// @return 带时区偏移的时间，可空
  private static OffsetDateTime utc(Instant instant) {
    return instant == null ? null : instant.atOffset(ZoneOffset.UTC);
  }
}
