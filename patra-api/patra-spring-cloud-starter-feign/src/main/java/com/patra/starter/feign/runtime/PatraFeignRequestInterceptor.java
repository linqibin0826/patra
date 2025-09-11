package com.patra.starter.feign.runtime;

import feign.RequestInterceptor;
import feign.RequestTemplate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.env.Environment;
import org.springframework.util.StringUtils;

/**
 * Feign 请求拦截器：统一设置 Accept、透传 TraceId/Service 以及白名单请求头。
 *
 * <p>对非 Web 上下文安全降级，不依赖 Servlet API 执行。
 *
 * @author linqibin
 * @since 0.1.0
 */
@Slf4j
@RequiredArgsConstructor
public class PatraFeignRequestInterceptor implements RequestInterceptor {

    private final PatraFeignProperties props;
    private final Environment env;

    @Override
    public void apply(RequestTemplate template) {
        if (!props.isEnabled()) return;

        // Service header
        String serviceName = env.getProperty("spring.application.name", "UNKNOWN");
        if (StringUtils.hasText(serviceName)) {
            template.header(props.getServiceHeader(), serviceName);
        }
    }

}

