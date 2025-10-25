package com.patra.starter.core.xml.autoconfig;

import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;

/**
 * Auto-configuration that exposes a project-level {@link XmlMapper} when the Jackson XML module is
 * available.
 *
 * <p>Design goals: - Provide a single, centrally-configured XmlMapper for modules that need XML
 * processing - Reuse Spring Boot's {@link Jackson2ObjectMapperBuilder} so global Jackson
 * customizations apply - Keep activation conditional (no hard dependency on XML in services that
 * don't need it)
 */
@Slf4j
@AutoConfiguration(
    after = org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration.class)
@ConditionalOnClass(XmlMapper.class)
public class XmlAutoConfiguration {

  /**
   * Registers a singleton {@link XmlMapper} built from the shared Jackson builder.
   *
   * <p>JSON customizations (modules, date/time, naming strategy) also apply to XML mapping through
   * the shared builder configuration.
   *
   * @param builder the shared Jackson object mapper builder
   * @return configured XML mapper instance
   */
  @Bean
  @ConditionalOnBean(Jackson2ObjectMapperBuilder.class)
  @ConditionalOnMissingBean(XmlMapper.class)
  public XmlMapper xmlMapper(Jackson2ObjectMapperBuilder builder) {
    log.debug("Loaded XmlAutoConfiguration.xmlMapper()");
    return builder.createXmlMapper(true).build();
  }
}
