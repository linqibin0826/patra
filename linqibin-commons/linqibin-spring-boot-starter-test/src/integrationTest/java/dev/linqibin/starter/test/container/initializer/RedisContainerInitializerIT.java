package dev.linqibin.starter.test.container.initializer;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.core.env.ConfigurableEnvironment;

/// RedisContainerInitializer 集成测试，需要本机 Docker。
@DisplayName("RedisContainerInitializer 集成测试")
class RedisContainerInitializerIT {

  @Test
  @DisplayName("启动 Redis 容器，并把连接参数写进环境")
  void should_start_redis_and_publish_connection_properties() throws IOException {
    try (GenericApplicationContext context = new GenericApplicationContext()) {
      new RedisContainerInitializer().initialize(context);

      ConfigurableEnvironment environment = context.getEnvironment();
      String host = environment.getProperty("spring.data.redis.host");
      Integer port = environment.getProperty("spring.data.redis.port", Integer.class);
      assertThat(environment.getProperty("spring.data.redis.url"))
          .isEqualTo("redis://" + host + ":" + port);
      try (Socket socket = new Socket(host, port)) {
        socket.getOutputStream().write("PING\r\n".getBytes(StandardCharsets.US_ASCII));
        BufferedReader reader =
            new BufferedReader(
                new InputStreamReader(socket.getInputStream(), StandardCharsets.US_ASCII));
        assertThat(reader.readLine()).isEqualTo("+PONG");
      }
    }
  }

  @Test
  @DisplayName("多次获取拿到的是同一个容器")
  void should_reuse_the_same_container() {
    assertThat(RedisContainerInitializer.getRedisContainer())
        .isSameAs(RedisContainerInitializer.getRedisContainer());
  }
}
