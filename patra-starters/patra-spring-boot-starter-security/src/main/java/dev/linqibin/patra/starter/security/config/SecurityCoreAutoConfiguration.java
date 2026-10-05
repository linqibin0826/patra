package dev.linqibin.patra.starter.security.config;

import dev.linqibin.patra.common.security.CurrentUserPort;
import dev.linqibin.patra.starter.security.context.SecurityContextCurrentUserAdapter;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

/// 安全 starter 的核心自动配置：注册取当前用户的端口实现。
///
/// 不带 Web 条件。安全上下文在没有 Web 环境的应用里同样可用，比如只跑定时任务的进程；
/// 这样依赖 `CurrentUserPort` 的审计配置在任何环境下都能装配。
@AutoConfiguration
public class SecurityCoreAutoConfiguration {

  /// 注册从安全上下文取当前用户的端口实现。
  ///
  /// @return 端口实现
  @Bean
  @ConditionalOnMissingBean
  public CurrentUserPort currentUserPort() {
    return new SecurityContextCurrentUserAdapter();
  }
}
