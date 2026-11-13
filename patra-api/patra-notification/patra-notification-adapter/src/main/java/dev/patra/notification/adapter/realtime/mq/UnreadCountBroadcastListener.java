package dev.patra.notification.adapter.realtime.mq;

import dev.patra.notification.app.realtime.service.UnreadCountStreamRegistry;
import dev.patra.notification.domain.realtime.model.vo.InstanceId;
import dev.patra.notification.domain.realtime.model.vo.UnreadCountBroadcast;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/// 收别的实例发来的未读数广播，推给本机手上的连接（#406）。
///
/// 每个实例有一条自己的临时队列绑在广播交换机上（`UnreadBroadcastConfiguration`），所以每条广播每个实例都收到一份。
/// 发起变化的实例已经直推过本机连接，它自己发的广播回到自己队列时跳过，否则同一次变化会推两次。
///
/// 推送出错只打 WARN、不往外抛，丢一次推送可以接受，下一次变化或重连时会补齐。监听用的容器工厂不重试
/// （`UnreadBroadcastConfiguration`），转换不了的坏消息直接丢掉，不会把这条队列卡住。
@Slf4j
@Component
@RequiredArgsConstructor
public class UnreadCountBroadcastListener {

  private final UnreadCountStreamRegistry registry;

  /// 本实例的身份。
  private final InstanceId instanceId;

  /// 收一条广播。
  ///
  /// @param broadcast 广播
  @RabbitListener(
      id = "unreadBroadcast",
      queues = "#{unreadBroadcastQueue.name}",
      containerFactory = "unreadBroadcastContainerFactory")
  public void on(UnreadCountBroadcast broadcast) {
    if (broadcast.sentBy(instanceId)) {
      return;
    }
    try {
      registry.push(broadcast.userIds());
    } catch (RuntimeException ex) {
      log.warn("按未读数广播推送失败，丢弃：{}", ex.getMessage());
    }
  }
}
