package dev.patra.notification.infra.realtime.broadcast;

import dev.patra.notification.domain.realtime.model.vo.InstanceId;
import dev.patra.notification.domain.realtime.model.vo.UnreadCountBroadcast;
import dev.patra.notification.domain.realtime.port.broadcast.UnreadCountBroadcastPort;
import java.util.Set;
import java.util.concurrent.Executor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

/// 未读数广播的 RabbitMQ 实现：发到 fanout 交换机 {@value #EXCHANGE}，每个实例各有一条临时队列绑在上面，都能收到。
///
/// 发送交给一个专用的小线程池（spec 5.2，舱壁隔离）：RabbitMQ 网络不通时每次发送要等满连接超时，资源告警时发送会
/// 一直挂着；直接在推送任务里发，会把 Boot 的异步线程池占满，本机推送跟着排队。隔开以后最多耗光这一个池。
///
/// 用普通 `RabbitTemplate`，不等 broker 确认、不进事件登记表：广播丢了能补，不值得为它多等。事件工具库给所有发送
/// 加了 mandatory，停机或断线重连的瞬间本实例的队列可能已经删掉，这时广播被 broker 退回，Spring 只打一行日志，
/// 不会让发送失败。
@Slf4j
@RequiredArgsConstructor
public final class UnreadCountBroadcastAdapter implements UnreadCountBroadcastPort {

  /// 广播交换机（fanout、持久化）。
  public static final String EXCHANGE = "notification.unread.broadcast";

  private final RabbitTemplate rabbitTemplate;

  /// 本实例的身份，写进消息，接收方据此跳过自己发的。
  private final InstanceId instanceId;

  /// 专用于广播发送的线程池。
  private final Executor executor;

  /// 把发送交给线程池后立即返回。
  ///
  /// @param userIds 未读数变了的用户
  @Override
  public void broadcast(Set<Long> userIds) {
    if (userIds.isEmpty()) {
      return;
    }
    UnreadCountBroadcast message = UnreadCountBroadcast.of(instanceId, userIds);
    try {
      executor.execute(() -> send(message));
    } catch (RuntimeException ex) {
      // 停机时线程池先关了，Spring 的线程池抛 TaskRejectedException
      log.warn("未读数广播没能交给线程池，丢弃：{}", ex.getMessage());
    }
  }

  /// 发一条；失败只打 WARN，广播丢了能补。
  ///
  /// @param message 广播
  private void send(UnreadCountBroadcast message) {
    try {
      rabbitTemplate.convertAndSend(EXCHANGE, "", message);
    } catch (RuntimeException ex) {
      log.warn("未读数广播发送失败，丢弃：{}", ex.getMessage());
    }
  }
}
