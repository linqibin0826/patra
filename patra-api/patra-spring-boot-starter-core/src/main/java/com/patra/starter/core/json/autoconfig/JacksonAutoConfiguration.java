package com.patra.starter.core.json.autoconfig;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.patra.starter.core.json.ObjectMapperProvider;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

/**
 * Jackson 自动配置类,当 Jackson 存在时提供 {@link ObjectMapperProvider}。
 *
 * <p>配置内容:
 *
 * <ul>
 *   <li>{@link ObjectMapperProvider} - ObjectMapper 提供者,桥接 Spring 管理的 ObjectMapper 到非 Spring 代码路径
 * </ul>
 *
 * <p>启用条件:
 *
 * <ul>
 *   <li>classpath 中存在 {@link ObjectMapper}
 *   <li>不存在自定义的 {@link ObjectMapperProvider} Bean
 * </ul>
 *
 * <h3>与依赖注入的关系</h3>
 *
 * <p>应用程序代码应<b>优先使用依赖注入</b>来获取 {@code ObjectMapper}。此自动配置仅为无法使用 依赖注入的场景提供桥接,不应被用作服务定位器。
 *
 * @author Papertrace Team
 * @since 2.0
 */
@Slf4j
@AutoConfiguration
@ConditionalOnClass(ObjectMapper.class)
public class JacksonAutoConfiguration {

  /**
   * 注册 {@link ObjectMapperProvider},除非应用程序提供了自定义的 Bean。
   *
   * <p>该提供者在容器就绪后将 Spring 管理的 ObjectMapper 同步到 {@code JsonMapperHolder}, 使非 Spring 代码能够访问已配置的
   * ObjectMapper。
   *
   * @return ObjectMapper 提供者实例
   */
  @ConditionalOnMissingBean(ObjectMapperProvider.class)
  @Bean
  public ObjectMapperProvider jacksonProvider() {
    log.debug("已加载 JacksonAutoConfiguration.jacksonProvider()");
    return new ObjectMapperProvider();
  }
}
