package com.patra.starter.feign.runtime;

import feign.RequestInterceptor;
import feign.RequestTemplate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.env.Environment;
import org.springframework.util.StringUtils;

/**
 * Feign request interceptor that attaches shared headers to every outbound call.
 *
 * <p>Currently forwards the caller service identity and can be extended with trace propagation without
 * relying on Servlet APIs, making it safe for non-web contexts.</p>
 */
@Slf4j
@RequiredArgsConstructor
public class PatraFeignRequestInterceptor implements RequestInterceptor {

    private final PatraFeignProperties props;
    private final Environment env;

    @Override
    public void apply(RequestTemplate template) {
        if (!props.isEnabled()) return;

        // Forward the caller service name so downstream services can tag inbound requests.
        String serviceName = env.getProperty("spring.application.name", "UNKNOWN");
        if (StringUtils.hasText(serviceName)) {
            template.header(props.getServiceHeader(), serviceName);
        }
    }

}
