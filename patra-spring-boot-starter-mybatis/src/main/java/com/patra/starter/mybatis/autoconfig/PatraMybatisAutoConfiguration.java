package com.patra.starter.mybatis.autoconfig;

import com.baomidou.mybatisplus.autoconfigure.ConfigurationCustomizer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.patra.common.error.codes.HttpStdErrors;
import com.patra.starter.mybatis.error.contributor.DataLayerErrorMappingContributor;
import com.patra.starter.mybatis.type.JsonToJsonNodeTypeHandler;
import lombok.extern.slf4j.Slf4j;
import org.mybatis.spring.mapper.MapperScannerConfigurer;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

/**
 * Common auto-configuration for Patra MyBatis integration.
 *
 * <p><b>Responsibilities:</b>
 *
 * <ul>
 *   <li>Defines a conventional mapper scanning path for the infrastructure layer (infra),
 *       defaulting to {@code com.patra.**.infra.persistence.mapper}.
 *   <li>Registers a type handler for Jackson's {@link JsonNode}, enabling automatic serialization
 *       and deserialization of JSON fields in Data Objects (DOs).
 * </ul>
 *
 * <p><b>Extensibility:</b>
 *
 * <ul>
 *   <li>The mapper scanning path can be extended by business modules using properties like {@code
 *       mybatis-plus.mapper-locations} or {@code mybatis-plus.type-aliases-package}.
 *   <li>Custom TypeHandlers can be registered via the {@code mybatis-plus.type-handlers-package}
 *       property.
 *   <li>The default handlers provided here can be overridden in business modules through MyBatis
 *       configuration.
 * </ul>
 *
 * <p><b>Notes:</b>
 *
 * <ul>
 *   <li>This configuration is activated only if MyBatis-Spring's {@link MapperScannerConfigurer} is
 *       on the classpath.
 *   <li>It does not handle transaction or data source configuration, which are delegated to the
 *       specific business module's infra/config layer.
 * </ul>
 */
@Slf4j
@AutoConfiguration
@ConditionalOnClass(MapperScannerConfigurer.class)
public class PatraMybatisAutoConfiguration {

  /**
   * Configures the mapper scanner to automatically detect and register mappers.
   *
   * <p>By default, it scans the {@code com.patra.**.infra.persistence.mapper} package, ensuring
   * that mappers in each business module's infra layer are recognized.
   *
   * <p>Business modules can add or override scan paths using standard MyBatis-Plus configuration
   * properties.
   *
   * @return a configured mapper scanner instance
   */
  @Bean
  public MapperScannerConfigurer mapperScannerConfigurer() {
    log.info(
        "Configuring MyBatis mapper scanner for package: com.patra.**.infra.persistence.mapper");
    MapperScannerConfigurer configurer = new MapperScannerConfigurer();
    configurer.setBasePackage("com.patra.**.infra.persistence.mapper");
    return configurer;
  }

  /**
   * Creates a contributor for mapping data layer exceptions to standard HTTP error codes.
   *
   * @param http a group of standard HTTP error definitions
   * @return a configured error mapping contributor
   */
  @Bean
  @ConditionalOnMissingBean
  public DataLayerErrorMappingContributor dataLayerErrorMappingContributor(
      HttpStdErrors.Group http) {
    log.info("Creating data layer error mapping contributor for MyBatis-Plus exceptions");
    return new DataLayerErrorMappingContributor(http);
  }

  /**
   * Customizes the MyBatis configuration to register custom TypeHandlers during initialization.
   *
   * <p>This is the recommended approach to ensure TypeHandlers are available when MyBatis parses
   * XML mappers and generates autoResultMaps.
   *
   * @param objectMapper the Spring-managed object mapper for consistent JSON processing
   * @return a configuration customizer that registers type handlers
   */
  @Bean
  public ConfigurationCustomizer configurationCustomizer(ObjectMapper objectMapper) {
    log.info("Registering custom TypeHandlers for JSON field mapping");
    return configuration -> {
      configuration
          .getTypeHandlerRegistry()
          .register(JsonNode.class, new JsonToJsonNodeTypeHandler(objectMapper));
      log.debug("Registered JsonToJsonNodeTypeHandler for JsonNode fields");
    };
  }
}
