package dev.linqibin.patra.starter.provenance.autoconfig;

import dev.linqibin.patra.common.enums.ProvenanceCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.core.convert.converter.Converter;
import org.springframework.util.StringUtils;

/// `String → ProvenanceCode` 转换器自动配置。
///
/// 注册全局 `String -> ProvenanceCode` 转换器,使 `@PathVariable` 和 `@RequestParam`
/// 绑定能够一致地解析 Provenance 标识符。
///
/// @author linqibin
/// @since 0.1.0
@Slf4j
@AutoConfiguration
@ConditionalOnClass(Converter.class)
public class ProvenanceCodeConverterAutoConfiguration {

  /// 注册转换器,将文本形式的 Provenance 标识符解析为 {@link ProvenanceCode} 值, 使 Spring MVC 能够为请求参数和路径变量绑定枚举友好的值。
  ///
  /// @return 暴露给 Spring 转换服务的转换器 Bean
  @Bean
  @ConditionalOnMissingBean(name = "provenanceCodeConverter")
  public Converter<String, ProvenanceCode> provenanceCodeConverter() {
    log.info("注册 String-to-ProvenanceCode 转换器,用于请求参数绑定");
    return new Converter<String, ProvenanceCode>() {
      /// 将字符串形式的数据源标识符转换为 {@link ProvenanceCode} 枚举值。
      ///
      /// @param source 数据源标识符字符串
      /// @return 对应的 {@link ProvenanceCode},如果源为空则返回 null
      @Override
      public ProvenanceCode convert(String source) {
        if (!StringUtils.hasText(source)) {
          return null;
        }
        return ProvenanceCode.parse(source);
      }
    };
  }
}
