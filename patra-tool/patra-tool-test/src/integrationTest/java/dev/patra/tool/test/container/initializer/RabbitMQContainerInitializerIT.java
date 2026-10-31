package dev.patra.tool.test.container.initializer;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.net.Socket;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.core.env.ConfigurableEnvironment;

/// RabbitMQContainerInitializer 集成测试，需要本机 Docker。
@DisplayName("RabbitMQContainerInitializer 集成测试")
class RabbitMQContainerInitializerIT {

  /// AMQP 0-9-1 的协议头：客户端连上后先发这 8 个字节。
  private static final byte[] AMQP_PROTOCOL_HEADER = {'A', 'M', 'Q', 'P', 0, 0, 9, 1};

  /// AMQP 帧类型：方法帧。
  private static final int METHOD_FRAME = 1;

  @Test
  @DisplayName("启动 RabbitMQ 容器，并把连接参数写进环境")
  void should_start_rabbitmq_and_publish_connection_properties() throws IOException {
    try (GenericApplicationContext context = new GenericApplicationContext()) {
      new RabbitMQContainerInitializer().initialize(context);

      ConfigurableEnvironment environment = context.getEnvironment();
      String host = environment.getProperty("spring.rabbitmq.host");
      Integer port = environment.getProperty("spring.rabbitmq.port", Integer.class);
      assertThat(environment.getProperty("spring.rabbitmq.username")).isNotBlank();
      assertThat(environment.getProperty("spring.rabbitmq.password")).isNotBlank();
      try (Socket socket = new Socket(host, port)) {
        socket.getOutputStream().write(AMQP_PROTOCOL_HEADER);
        InputStream input = socket.getInputStream();
        // broker 认这个协议头时回 connection.start，第一个字节是方法帧类型
        assertThat(input.read()).isEqualTo(METHOD_FRAME);
      }
    }
  }

  @Test
  @DisplayName("多次获取拿到的是同一个容器")
  void should_reuse_the_same_container() {
    assertThat(RabbitMQContainerInitializer.getRabbitMQContainer())
        .isSameAs(RabbitMQContainerInitializer.getRabbitMQContainer());
  }
}
