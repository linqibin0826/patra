package com.patra.starter.feign.runtime;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;

/**
 * Feign Starter 自动装配。
 *
 * <p>全局注册：
 * - RequestInterceptor：统一 Accept/Trace/Service/透传头
 * - ErrorDecoder：将远端错误解析为 PlatformError 并抛出 PatraRemoteException
 * - PlatformErrorCodec：若上游未提供则兜底提供 Jackson 实现
 * - 可选：Web Advice 将 PatraRemoteException 序列化为 problem+json（默认关闭）
 *
 * @author linqibin
 * @since 0.1.0
 */
@Slf4j
@AutoConfiguration
@EnableConfigurationProperties(PatraFeignProperties.class)
@ConditionalOnClass(name = {
        "feign.Feign",
})
@ConditionalOnProperty(prefix = "patra.feign", name = "enabled", havingValue = "true", matchIfMissing = true)
public class PatraFeignAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public PatraFeignRequestInterceptor patraFeignRequestInterceptor(PatraFeignProperties props,
                                                                     Environment env) {
        return new PatraFeignRequestInterceptor(props, env);
    }
}

