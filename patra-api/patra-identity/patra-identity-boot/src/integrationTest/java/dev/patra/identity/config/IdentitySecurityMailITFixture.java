package dev.patra.identity.config;

import dev.patra.identity.api.account.event.SecurityMailEvent;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.jdbc.core.JdbcTemplate;

/// 捕获队列与事件发布登记表的测试操作：按收件地址与事件类型取安全邮件事件、等登记行全部发完、清场。
///
/// 登记表的完成模式是 `update`：发成功的行标完成后留在表里，同一上下文里前面用例的行都还在，所以「发完」看的是
/// `completion_date` 为空的行，不是表的行数。
///
/// 外送在事务提交后异步进行，所以取事件都要等。拉取而不挂监听器：消息在队列里待到用例来取，用例开头清队列时
/// 上一个用例的事件已经全部进了队列（排空监听器保证），不会晚到。
@RequiredArgsConstructor
public class IdentitySecurityMailITFixture {

  /// 等某个事件的上限：比 broker 确认超时（5 秒）宽得多，上下文第一次连 broker、声明队列都算在里面，机器忙时也不抖。
  private static final Duration EVENT_TIMEOUT = Duration.ofSeconds(15);

  /// 等登记行全部发完的上限。
  private static final Duration REGISTRY_TIMEOUT = Duration.ofSeconds(10);

  /// 单次拉取等待的毫秒数。
  private static final long POLL_MILLIS = 200;

  private final RabbitTemplate rabbitTemplate;

  private final AmqpAdmin amqpAdmin;

  private final JdbcTemplate jdbcTemplate;

  /// 本用例已经从队列里拉到的事件。
  private final List<SecurityMailEvent> received = new CopyOnWriteArrayList<>();

  /// 清空捕获队列和已拉到的事件。
  public void purge() {
    amqpAdmin.purgeQueue(IdentitySecurityMailITSpringConfig.QUEUE, false);
    received.clear();
  }

  /// 发给某个地址的某种事件，最多等 15 秒。
  ///
  /// @param type 事件类型
  /// @param recipient 收件地址
  /// @param <T> 事件类型
  /// @return 第一个匹配的事件
  /// @throws AssertionError 15 秒内没等到
  public <T extends SecurityMailEvent> T await(Class<T> type, String recipient) {
    long deadline = System.nanoTime() + EVENT_TIMEOUT.toNanos();
    while (true) {
      Optional<T> found = find(type, recipient).stream().findFirst();
      if (found.isPresent()) {
        return found.get();
      }
      if (System.nanoTime() >= deadline) {
        throw new AssertionError("没有发给 " + recipient + " 的 " + type.getSimpleName());
      }
      pull(POLL_MILLIS);
    }
  }

  /// 发给某个地址的某种事件全部：先等登记行全部发完（这时已登记的事件都进了队列），再把队列拉空。
  ///
  /// @param type 事件类型
  /// @param recipient 收件地址
  /// @param <T> 事件类型
  /// @return 匹配的事件，按到达先后
  public <T extends SecurityMailEvent> List<T> all(Class<T> type, String recipient) {
    awaitRegistryDrained();
    while (pull(0)) {
      // 拉到队列为空
    }
    return find(type, recipient);
  }

  /// 等登记行全部发完：每个已登记的事件都被 broker 确认收下、登记行已标完成。最多等 10 秒。
  ///
  /// @throws IllegalStateException 超时，列出还没完成的行
  public void awaitRegistryDrained() {
    long deadline = System.nanoTime() + REGISTRY_TIMEOUT.toNanos();
    while (pendingRegistryRows() > 0) {
      if (System.nanoTime() >= deadline) {
        List<Map<String, Object>> rows =
            jdbcTemplate.queryForList(
                "SELECT event_type, status FROM event_publication WHERE completion_date IS NULL");
        throw new IllegalStateException("事件发布登记表 " + REGISTRY_TIMEOUT + " 内还有没发完的行: " + rows);
      }
      sleep(50);
    }
  }

  /// 事件发布登记表的行数，含已完成的。
  ///
  /// @return 行数
  public int registryRows() {
    return count("SELECT count(*) FROM event_publication");
  }

  /// 还没发完（`completion_date` 为空）的登记行数。
  ///
  /// @return 行数
  public int pendingRegistryRows() {
    return count("SELECT count(*) FROM event_publication WHERE completion_date IS NULL");
  }

  /// 已完成（状态 `COMPLETED` 且写了完成时间）、事件内容里含某段文本的登记行数，用来确认发成功的行留在表里且内容可查。
  ///
  /// @param text 事件内容里要含的文本，如收件地址、事件 ID
  /// @return 行数
  public int completedRegistryRowsContaining(String text) {
    return count(
        """
        SELECT count(*) FROM event_publication
         WHERE status = 'COMPLETED' AND completion_date IS NOT NULL
           AND strpos(serialized_event, ?) > 0
        """,
        text);
  }

  /// 执行一条计数查询。
  ///
  /// @param sql 计数 SQL
  /// @param args 参数
  /// @return 行数
  private int count(String sql, Object... args) {
    Integer rows = jdbcTemplate.queryForObject(sql, Integer.class, args);
    return rows == null ? 0 : rows;
  }

  /// 已拉到的事件里匹配的那些。
  ///
  /// @param type 事件类型
  /// @param recipient 收件地址
  /// @param <T> 事件类型
  /// @return 匹配的事件
  private <T extends SecurityMailEvent> List<T> find(Class<T> type, String recipient) {
    return received.stream()
        .filter(type::isInstance)
        .map(type::cast)
        .filter(event -> event.to().equals(recipient))
        .toList();
  }

  /// 从捕获队列拉一条。
  ///
  /// @param timeoutMillis 等待毫秒数，0 表示不等
  /// @return 拉到了为 `true`
  private boolean pull(long timeoutMillis) {
    Object message =
        rabbitTemplate.receiveAndConvert(IdentitySecurityMailITSpringConfig.QUEUE, timeoutMillis);
    if (message == null) {
      return false;
    }
    received.add((SecurityMailEvent) message);
    return true;
  }

  /// 睡一会儿。
  ///
  /// @param millis 毫秒数
  private static void sleep(long millis) {
    try {
      Thread.sleep(millis);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("等待时被中断", e);
    }
  }
}
