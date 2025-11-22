package com.patra.starter.feign.runtime;

import feign.RequestInterceptor;
import feign.RequestTemplate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.env.Environment;
import org.springframework.util.StringUtils;

/// Feign 请求拦截器,向每个出站调用附加共享请求头
/// 
/// 当前转发调用者服务标识,未来可扩展跟踪传播功能,且无需依赖 Servlet API, 因此在非 Web 上下文中也是安全的。
@Slf4j
@RequiredArgsConstructor
public class PatraFeignRequestInterceptor implements RequestInterceptor {

  private final PatraFeignProperties props;
  private final Environment env;

  @Override
  public void apply(RequestTemplate template) {
    if (!props.isEnabled()) return;

    // 转发调用者服务名称,以便下游服务可以标记入站请求
    String serviceName = env.getProperty("spring.application.name", "UNKNOWN");
    if (StringUtils.hasText(serviceName)) {
      template.header(props.getServiceHeader(), serviceName);
    }
  }
}
