package dev.linqibin.patra.gateway;

import dev.linqibin.patra.starter.security.test.TestSigningKey;
import java.util.Map;
import org.springframework.boot.test.util.TestPropertyValues;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;

/// 把测试私钥写进网关的私钥配置项。
///
/// 公钥由安全 starter 测试支持的环境后处理器自动注入，两边是同一对密钥，启动自检才能过。
/// 用法：`@ContextConfiguration(initializers = {RedisContainerInitializer.class,
/// GatewayITSigningKeyInitializer.class})`。
public class GatewayITSigningKeyInitializer
    implements ApplicationContextInitializer<ConfigurableApplicationContext> {

  /// 私钥配置项的全名。
  static final String PRIVATE_KEY_PROPERTY = "patra.gateway.identity-assertion.private-key";

  /// 写入私钥 JWK JSON。用 `Map` 重载：JSON 里有冒号，字符串重载会在第一个分隔符处切开。
  ///
  /// @param applicationContext 正在初始化的上下文
  @Override
  public void initialize(ConfigurableApplicationContext applicationContext) {
    TestPropertyValues.of(Map.of(PRIVATE_KEY_PROPERTY, TestSigningKey.key().toJSONString()))
        .applyTo(applicationContext.getEnvironment());
  }
}
