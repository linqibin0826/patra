package dev.linqibin.patra.starter.security.config;

import dev.linqibin.patra.starter.security.forward.IdentityAssertionForwardingInterceptor;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

/// 转发断言的自动配置：classpath 上有 http-interface starter 时，注册内部客户端的拦截器。
///
/// 单独一个类，条件写类名字符串：拦截器实现了 http-interface starter 的接口，没有那个 starter
/// 时连拦截器的类都加载不了，只有类级别的条件能在加载前挡住。不带 Web 条件：拦截器本身
/// 在任何环境都能装配，只是没有请求线程时拿不到断言，什么都不转发。
@AutoConfiguration(after = SecurityCoreAutoConfiguration.class)
@ConditionalOnClass(name = "dev.linqibin.starter.httpinterface.interceptor.InternalCallInterceptor")
public class SecurityForwardingAutoConfiguration {

  /// 注册转发断言的拦截器。
  ///
  /// @return 拦截器
  @Bean
  @ConditionalOnMissingBean
  public IdentityAssertionForwardingInterceptor identityAssertionForwardingInterceptor() {
    return new IdentityAssertionForwardingInterceptor();
  }
}
