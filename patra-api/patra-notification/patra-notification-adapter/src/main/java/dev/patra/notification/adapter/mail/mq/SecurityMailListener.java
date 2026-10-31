package dev.patra.notification.adapter.mail.mq;

import dev.patra.identity.api.account.event.SecurityMailEvent;
import dev.patra.notification.app.mail.usecase.security.SendSecurityMailCommand;
import dev.patra.tool.cqrs.CommandBus;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.stereotype.Component;

/// 收 identity 的安全邮件事件，转成发信命令。
///
/// 收原始消息、自己用事件模块的转换器按类型头 `__TypeId__` 还原（只认 `patra.events.consumer.trusted-packages`
/// 里的包），还原出来不是 {@link SecurityMailEvent} 就拒收、进死信。不让 Spring 按方法参数类型去转：
/// 类型头写成 `java.lang.Object` 这类时，正文会被读成 Map，Spring 报「Cannot convert from …」时会把整个正文
/// （收件地址、令牌链接）写进异常消息，跟着重试日志进日志系统。
///
/// 处理抛异常时由监听容器按事件模块的默认值重试，耗尽后进死信队列 `notification.security-mail.dlq`。
@Component
@RequiredArgsConstructor
public class SecurityMailListener {

  /// 队列名，也是去重表里的消费方名；队列与绑定在 boot 的 `MailQueueConfiguration` 声明。
  public static final String QUEUE = "notification.security-mail";

  private final CommandBus commandBus;
  private final MessageConverter messageConverter;

  /// 处理一条消息。
  ///
  /// @param message 原始消息
  /// @throws AmqpRejectAndDontRequeueException 还原出来不是安全邮件事件
  @RabbitListener(queues = QUEUE)
  public void on(Message message) {
    if (!(messageConverter.fromMessage(message) instanceof SecurityMailEvent event)) {
      throw new AmqpRejectAndDontRequeueException(
          "不是安全邮件事件，拒收: messageId="
              + message.getMessageProperties().getMessageId()
              + ", __TypeId__="
              + message.getMessageProperties().getHeaders().get("__TypeId__"));
    }
    commandBus.handle(
        SendSecurityMailCommand.of(QUEUE, SecurityMailEventConverter.toSecurityMail(event)));
  }
}
