package dev.linqibin.patra.starter.security.config;

import dev.linqibin.patra.common.security.CurrentUserPort;
import dev.linqibin.patra.starter.security.audit.CurrentUserAuditorProvider;
import dev.linqibin.starter.jpa.audit.CurrentAuditorProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

/// 安全 starter 的审计自动配置：把当前用户接到 JPA 审计上。
///
/// classpath 上有 starter-jpa 时才生效。不带 Web 条件：只跑定时任务的进程里，
/// 用 `CurrentUserRunner` 带上身份写入的记录同样会填上用户 ID。
@AutoConfiguration(after = SecurityCoreAutoConfiguration.class)
@ConditionalOnClass(CurrentAuditorProvider.class)
public class SecurityAuditingAutoConfiguration {

  /// 注册用当前用户 ID 作操作人的提供者。应用自己提供了就让位。
  ///
  /// @param currentUserPort 取当前用户的端口
  /// @return 操作人提供者
  @Bean
  @ConditionalOnMissingBean
  public CurrentAuditorProvider currentAuditorProvider(CurrentUserPort currentUserPort) {
    return new CurrentUserAuditorProvider(currentUserPort);
  }
}
