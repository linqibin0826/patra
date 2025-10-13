package com.patra.starter.web.autoconfig;

import com.patra.common.enums.ProvenanceCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.core.convert.converter.Converter;
import org.springframework.util.StringUtils;

/**
 * Auto-configuration for common Web converters.
 *
 * <p>Registers a global {@code String -> ProvenanceCode} converter so that {@code @PathVariable}
 * and {@code @RequestParam} bindings resolve provenance identifiers consistently.
 *
 * @author linqibin
 * @since 0.1.0
 */
@Slf4j
@AutoConfiguration
@ConditionalOnClass(Converter.class)
public class WebConversionAutoConfiguration {

  /**
   * Registers a converter that resolves textual provenance identifiers to {@link ProvenanceCode}
   * values, enabling Spring MVC to bind enum-friendly values for request parameters and path
   * variables.
   *
   * @return converter bean exposed to the Spring conversion service
   */
  @Bean
  @ConditionalOnMissingBean(name = "provenanceCodeConverter")
  public Converter<String, ProvenanceCode> provenanceCodeConverter() {
    log.info("[WEB][AUTO-CONFIG] Registering provenanceCodeConverter bean");
    return source -> {
      if (!StringUtils.hasText(source)) {
        return null;
      }
      return ProvenanceCode.parse(source);
    };
  }
}
