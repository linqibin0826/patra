package com.patra.starter.web.autoconfig;

import com.patra.common.enums.ProvenanceCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.core.convert.converter.Converter;
import org.springframework.lang.NonNull;

/**
 * Web 层通用转换器自动配置。
 *
 * <p>注册 String -> ProvenanceCode 的全局转换器，覆盖 PathVariable / RequestParam 的绑定。
 *
 * @author linqibin
 * @since 0.1.0
 */
@Slf4j
@AutoConfiguration
@ConditionalOnClass(Converter.class)
public class WebConversionAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(name = "provenanceCodeConverter")
    public Converter<String, ProvenanceCode> provenanceCodeConverter() {
        log.info("loaded WebConversionAutoConfiguration.provenanceCodeConverter()");
        return new Converter<String, ProvenanceCode>() {
            @Override
            public ProvenanceCode convert(@NonNull String source) {
                return source == null ? null : ProvenanceCode.parse(source);
            }
        };
    }
}
