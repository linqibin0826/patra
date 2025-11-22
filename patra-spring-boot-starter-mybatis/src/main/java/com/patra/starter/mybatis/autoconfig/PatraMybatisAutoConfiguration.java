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

/// Patra MyBatis 集成的通用自动配置类。
///
/// **职责说明:**
///
/// - 为基础设施层(infra)定义约定的 mapper 扫描路径,默认为 `com.patra.**.infra.persistence.mapper`。
///   - 为 Jackson 的 {@link JsonNode} 注册类型处理器,支持数据对象(DO)中 JSON 字段的自动序列化和反序列化。
///
/// **扩展性:**
///
/// - 业务模块可以使用 `mybatis-plus.mapper-locations` 或 `mybatis-plus.type-aliases-package`
///       等属性扩展 mapper 扫描路径。
///   - 可以通过 `mybatis-plus.type-handlers-package` 属性注册自定义 TypeHandler。
///   - 业务模块可以通过 MyBatis 配置覆盖此处提供的默认处理器。
///
/// **注意事项:**
///
/// - 仅当 MyBatis-Spring 的 {@link MapperScannerConfigurer} 在 classpath 中时,此配置才会激活。
///   - 不处理事务或数据源配置,这些配置委托给特定业务模块的 infra/config 层。
///
@Slf4j
@AutoConfiguration
@ConditionalOnClass(MapperScannerConfigurer.class)
public class PatraMybatisAutoConfiguration {

  /// 配置 mapper 扫描器以自动检测和注册 mapper。
  ///
  /// 默认扫描 `com.patra.**.infra.persistence.mapper` 包,确保识别每个业务模块 infra 层中的 mapper。
  ///
  /// 业务模块可以使用标准 MyBatis-Plus 配置属性添加或覆盖扫描路径。
  ///
  /// @return 配置好的 mapper 扫描器实例
  @Bean
  public MapperScannerConfigurer mapperScannerConfigurer() {
    log.info("配置 MyBatis mapper 扫描器,扫描包: com.patra.**.infra.persistence.mapper");
    MapperScannerConfigurer configurer = new MapperScannerConfigurer();
    configurer.setBasePackage("com.patra.**.infra.persistence.mapper");
    return configurer;
  }

  /// 创建数据层异常到标准 HTTP 错误码的映射贡献器。
  ///
  /// @param http 标准 HTTP 错误定义组
  /// @return 配置好的错误映射贡献器
  @Bean
  @ConditionalOnMissingBean
  public DataLayerErrorMappingContributor dataLayerErrorMappingContributor(
      HttpStdErrors.Group http) {
    log.info("创建数据层错误映射贡献器,用于 MyBatis-Plus 异常处理");
    return new DataLayerErrorMappingContributor(http);
  }

  /// 自定义 MyBatis 配置,在初始化期间注册自定义 TypeHandler。
  ///
  /// 这是推荐的方法,可确保 MyBatis 解析 XML mapper 和生成 autoResultMap 时 TypeHandler 可用。
  ///
  /// @param objectMapper Spring 管理的对象映射器,用于一致的 JSON 处理
  /// @return 注册类型处理器的配置自定义器
  @Bean
  public ConfigurationCustomizer configurationCustomizer(ObjectMapper objectMapper) {
    log.info("为 JSON 字段映射注册自定义 TypeHandler");
    return configuration -> {
      configuration
          .getTypeHandlerRegistry()
          .register(JsonNode.class, new JsonToJsonNodeTypeHandler(objectMapper));
      log.debug("已为 JsonNode 字段注册 JsonToJsonNodeTypeHandler");
    };
  }
}
