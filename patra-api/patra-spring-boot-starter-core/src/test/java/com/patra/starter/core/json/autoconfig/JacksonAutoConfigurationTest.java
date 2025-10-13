package com.patra.starter.core.json.autoconfig;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.patra.common.json.JsonMapperHolder;
import com.patra.starter.core.json.ObjectMapperProvider;
import java.lang.reflect.Field;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

class JacksonAutoConfigurationTest {

  private ObjectMapper previousHolderMapper;
  private ObjectMapper previousProviderMapper;

  @BeforeEach
  void setUp() throws Exception {
    previousHolderMapper = JsonMapperHolder.getObjectMapper();
    previousProviderMapper = readStaticObjectMapper();
  }

  @AfterEach
  void tearDown() throws Exception {
    JsonMapperHolder.register(previousHolderMapper);
    writeStaticObjectMapper(previousProviderMapper);
  }

  @Test
  void jacksonProvider_shouldRegisterBeanAndBridgeToJsonMapperHolder() {
    try (AnnotationConfigApplicationContext ctx = new AnnotationConfigApplicationContext()) {
      ctx.register(BaseMapperConfig.class, JacksonAutoConfiguration.class);
      ctx.refresh();

      ObjectMapperProvider provider = ctx.getBean(ObjectMapperProvider.class);
      ObjectMapper contextMapper = ctx.getBean(ObjectMapper.class);

      assertThat(provider).isNotNull();
      assertThat(ObjectMapperProvider.getObjectMapper()).isSameAs(contextMapper);
    }
  }

  @Test
  void jacksonProvider_shouldRespectExistingBean() {
    try (AnnotationConfigApplicationContext ctx = new AnnotationConfigApplicationContext()) {
      ctx.register(
          BaseMapperConfig.class, CustomProviderConfig.class, JacksonAutoConfiguration.class);
      ctx.refresh();

      ObjectMapperProvider custom = ctx.getBean("customProvider", ObjectMapperProvider.class);
      assertThat(ctx.getBeansOfType(ObjectMapperProvider.class)).hasSize(1);
      assertThat(ObjectMapperProvider.getObjectMapper()).isSameAs(ctx.getBean(ObjectMapper.class));
      assertThat(custom).isInstanceOf(ObjectMapperProvider.class);
    }
  }

  @Configuration(proxyBeanMethods = false)
  static class BaseMapperConfig {
    @Bean
    ObjectMapper objectMapper() {
      return new ObjectMapper();
    }
  }

  @Configuration(proxyBeanMethods = false)
  static class CustomProviderConfig {
    @Bean("customProvider")
    ObjectMapperProvider customProvider() {
      return new ObjectMapperProvider();
    }
  }

  private static ObjectMapper readStaticObjectMapper() throws Exception {
    Field field = ObjectMapperProvider.class.getDeclaredField("objectMapper");
    field.setAccessible(true);
    return (ObjectMapper) field.get(null);
  }

  private static void writeStaticObjectMapper(ObjectMapper mapper) throws Exception {
    Field field = ObjectMapperProvider.class.getDeclaredField("objectMapper");
    field.setAccessible(true);
    field.set(null, mapper);
  }
}
