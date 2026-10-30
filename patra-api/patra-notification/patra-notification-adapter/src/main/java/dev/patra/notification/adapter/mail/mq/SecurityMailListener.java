package dev.patra.notification.adapter.mail.mq;

import dev.patra.identity.api.account.event.SecurityMailEvent;
import dev.patra.notification.app.mail.usecase.security.SendSecurityMailCommand;
import dev.patra.tool.cqrs.CommandBus;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/// 收 identity 的安全邮件事件，转成发信命令。
///
/// 参数是 sealed 接口：转换器按消息头 `__TypeId__` 还原成具体 record，只认
/// `patra.events.consumer.trusted-packages` 里的包。处理抛异常时由监听容器按事件模块的默认值重试，
/// 耗尽后进死信队列 `notification.security-mail.dlq`。
@Component
@RequiredArgsConstructor
public class SecurityMailListener {

  /// 队列名，也是去重表里的消费方名；队列与绑定在 boot 的 `MailQueueConfiguration` 声明。
  public static final String QUEUE = "notification.security-mail";

  private final CommandBus commandBus;

  /// 处理一个安全邮件事件。
  ///
  /// @param event identity 的事件
  @RabbitListener(queues = QUEUE)
  public void on(SecurityMailEvent event) {
    commandBus.handle(
        SendSecurityMailCommand.of(QUEUE, SecurityMailEventConverter.toSecurityMail(event)));
  }
}
