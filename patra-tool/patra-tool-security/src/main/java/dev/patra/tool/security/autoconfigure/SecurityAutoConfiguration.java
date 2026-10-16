package dev.patra.tool.security.autoconfigure;

import dev.patra.tool.core.security.CurrentUserPort;
import dev.patra.tool.security.context.SecurityContextCurrentUserAdapter;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;

/// 安全 starter 的核心自动配置：注册取当前用户的端口实现。
///
/// 不带 Web 条件。安全上下文在没有 Web 环境的应用里同样可用，比如只跑定时任务的进程；
/// 这样依赖 `CurrentUserPort` 的审计配置在任何环境下都能装配。
@AutoConfiguration
@ConditionalOnProperty(
    prefix = "patra.security",
    name = "enabled",
    havingValue = "true",
    matchIfMissing = true)
public final class SecurityAutoConfiguration {

  /// 注册从安全上下文取当前用户的端口实现。
  ///
  /// @return 端口实现
  @Bean
  @ConditionalOnMissingBean
  public CurrentUserPort currentUserPort() {
    return new SecurityContextCurrentUserAdapter();
  }
}
