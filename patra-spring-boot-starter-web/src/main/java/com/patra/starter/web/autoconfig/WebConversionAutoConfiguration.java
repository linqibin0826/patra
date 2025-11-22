package com.patra.starter.web.autoconfig;

import com.patra.common.enums.ProvenanceCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.core.convert.converter.Converter;
import org.springframework.util.StringUtils;

/// 通用 Web 转换器的自动配置类。
/// 
/// 注册全局 `String -> ProvenanceCode` 转换器,使 `@PathVariable` 和 `@RequestParam`
/// 绑定能够一致地解析 Provenance 标识符。
/// 
/// @author linqibin
/// @since 0.1.0
@Slf4j
@AutoConfiguration
@ConditionalOnClass(Converter.class)
public class WebConversionAutoConfiguration {

  /// 注册转换器,将文本形式的 Provenance 标识符解析为 {@link ProvenanceCode} 值, 使 Spring MVC 能够为请求参数和路径变量绑定枚举友好的值。
/// 
/// @return 暴露给 Spring 转换服务的转换器 Bean
  @Bean
  @ConditionalOnMissingBean(name = "provenanceCodeConverter")
  public Converter<String, ProvenanceCode> provenanceCodeConverter() {
    log.info("注册 String-to-ProvenanceCode 转换器,用于请求参数绑定");
    return new Converter<String, ProvenanceCode>() {
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
