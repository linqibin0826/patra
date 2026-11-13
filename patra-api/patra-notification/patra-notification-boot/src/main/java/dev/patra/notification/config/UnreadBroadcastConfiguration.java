package dev.patra.notification.config;

import dev.patra.notification.domain.realtime.model.vo.InstanceId;
import dev.patra.notification.domain.realtime.port.broadcast.UnreadCountBroadcastPort;
import dev.patra.notification.infra.realtime.broadcast.DiscardOldestBroadcastPolicy;
import dev.patra.notification.infra.realtime.broadcast.UnreadCountBroadcastAdapter;
import java.time.Clock;
import java.util.concurrent.Executor;
import org.springframework.amqp.core.AnonymousQueue;
import org.springframework.amqp.core.Base64UrlNamingStrategy;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.ExchangeBuilder;
import org.springframework.amqp.core.FanoutExchange;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.amqp.autoconfigure.RabbitTemplateCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/// 未读数广播的装配（#406，spec `docs/patra/specs/2026-11-13-notification-unread-broadcast-design.md`）。
///
/// 推送连接只活在建立它的实例里。未读数变了，变化所在的实例直推自己的连接，再把用户 ID 广播给其他实例：
///
/// - 专用线程池 {@value #EXECUTOR}：发送隔离在这里，RabbitMQ 卡住时不占 Boot 的异步线程池（spec 5.2）。
/// - fanout 交换机 + 每实例一条临时队列：fanout 把一条消息复制进每条绑定的队列，所以每个实例都收到；队列随实例
///   断开删除，下线的实例不会留下堆积消息的队列（spec 2.1、6.1）。广播监听按 bean 名 `unreadBroadcastQueue` 引用
///   这条队列。
/// - 发布走单独的连接：RabbitMQ 内存或磁盘告警时会阻塞发布过消息的连接。Boot 的 `RabbitTemplate` 默认和监听容器
///   共用一条连接，通知服务以前只收不发，这是第一次在它上面发布；共用的话信箱、安全邮件的消费会跟着停（spec 5.3）。
@Configuration(proxyBeanMethods = false)
public class UnreadBroadcastConfiguration {

  /// 广播线程池的 bean 名。
  public static final String EXECUTOR = "unreadBroadcastExecutor";

  /// 本实例的身份：每个 Spring 上下文启动时随机生成一个，发送方写进消息，接收方据此跳过自己发的（spec 6.2）。
  ///
  /// @return 实例 ID
  @Bean
  public InstanceId realtimeInstanceId() {
    return InstanceId.random();
  }

  /// 广播专用线程池：1 个线程、排队 500，满了丢最旧的一条，停机不等队列里的任务。
  ///
  /// 一个线程足够：发送正常时是毫秒级；不正常时线程再多也只是一起卡住。排队上限防止 RabbitMQ 长时间不可用时
  /// 堆积；广播丢了能补，所以满了直接丢。
  ///
  /// @param clock 时钟（丢弃告警限频用）
  /// @return 线程池
  @Bean(EXECUTOR)
  public ThreadPoolTaskExecutor unreadBroadcastExecutor(Clock clock) {
    ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
    executor.setCorePoolSize(1);
    executor.setMaxPoolSize(1);
    executor.setQueueCapacity(500);
    executor.setThreadNamePrefix("unread-broadcast-");
    executor.setRejectedExecutionHandler(new DiscardOldestBroadcastPolicy(clock));
    return executor;
  }

  /// 广播交换机：fanout、持久化（只是让交换机在 broker 重启后还在，和消息持不持久无关）。
  ///
  /// @return 交换机
  @Bean
  public FanoutExchange unreadBroadcastExchange() {
    return ExchangeBuilder.fanoutExchange(UnreadCountBroadcastAdapter.EXCHANGE)
        .durable(true)
        .build();
  }

  /// 本实例收广播的临时队列：非持久、独占、断开即删，名字是前缀加随机串。不配死信：广播丢了不需要补。
  ///
  /// @return 队列
  @Bean
  public AnonymousQueue unreadBroadcastQueue() {
    return new AnonymousQueue(new Base64UrlNamingStrategy("notification.unread."));
  }

  /// 把本实例的队列绑到广播交换机。
  ///
  /// @param unreadBroadcastQueue 本实例的队列
  /// @param unreadBroadcastExchange 广播交换机
  /// @return 绑定
  @Bean
  public Binding unreadBroadcastBinding(
      AnonymousQueue unreadBroadcastQueue, FanoutExchange unreadBroadcastExchange) {
    return BindingBuilder.bind(unreadBroadcastQueue).to(unreadBroadcastExchange);
  }

  /// 发布走单独的连接（见类注释）。和事件工具库给所有发送加 mandatory 的定制叠加，不冲突。
  ///
  /// @return 模板定制
  @Bean
  public RabbitTemplateCustomizer publisherConnectionCustomizer() {
    return template -> template.setUsePublisherConnection(true);
  }

  /// 广播端口：发送交给专用线程池。
  ///
  /// @param rabbitTemplate Boot 建的发送模板
  /// @param realtimeInstanceId 本实例的身份
  /// @param executor 广播专用线程池
  /// @return 端口
  @Bean
  public UnreadCountBroadcastPort unreadCountBroadcastPort(
      RabbitTemplate rabbitTemplate,
      InstanceId realtimeInstanceId,
      @Qualifier(EXECUTOR) Executor executor) {
    return new UnreadCountBroadcastAdapter(rabbitTemplate, realtimeInstanceId, executor);
  }
}
