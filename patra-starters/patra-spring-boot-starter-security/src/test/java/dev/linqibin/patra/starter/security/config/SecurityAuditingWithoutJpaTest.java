package dev.linqibin.patra.starter.security.config;

import static org.assertj.core.api.Assertions.assertThat;

import dev.linqibin.patra.common.security.CurrentUserPort;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.context.annotation.ImportCandidates;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.ImportSelector;
import org.springframework.core.type.AnnotationMetadata;
import org.springframework.util.ClassUtils;

/// 没有 starter-jpa 时的自动配置测试。
///
/// 单元测试的 classpath 上没有 starter-jpa（它在主代码里是 compileOnly），正好是要验证的环境。
/// 审计自动配置必须按名字导入：真实应用从 imports 文件按名字加载它，那条路径不会加载类。
@DisplayName("没有 starter-jpa 时的自动配置测试")
class SecurityAuditingWithoutJpaTest {

  private static final String AUDITING_AUTO_CONFIGURATION =
      "dev.linqibin.patra.starter.security.config.SecurityAuditingAutoConfiguration";

  @Test
  @DisplayName("审计自动配置登记在 imports 文件里")
  void should_be_registered_in_auto_configuration_imports() {
    assertThat(
            ImportCandidates.load(AutoConfiguration.class, getClass().getClassLoader())
                .getCandidates())
        .contains(AUDITING_AUTO_CONFIGURATION);
  }

  @Test
  @DisplayName("classpath 上没有 starter-jpa：应用正常启动，不注册审计人提供者")
  void should_start_without_auditor_provider_when_starter_jpa_absent() {
    assertThat(
            ClassUtils.isPresent(
                "dev.linqibin.starter.jpa.audit.CurrentAuditorProvider",
                getClass().getClassLoader()))
        .as("前提：单元测试的 classpath 上没有 starter-jpa")
        .isFalse();

    new ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(SecurityCoreAutoConfiguration.class))
        .withUserConfiguration(ImportAuditingByName.class)
        .run(
            context -> {
              assertThat(context).hasNotFailed();
              assertThat(context).hasSingleBean(CurrentUserPort.class);
              assertThat(context).doesNotHaveBean("currentAuditorProvider");
            });
  }

  /// 像 Spring Boot 加载 imports 文件那样，按名字导入审计自动配置。
  @Configuration(proxyBeanMethods = false)
  @Import(AuditingByNameSelector.class)
  static class ImportAuditingByName {}

  /// 只返回类名，不加载类。
  static class AuditingByNameSelector implements ImportSelector {

    /// 返回要导入的配置类的名字。
    ///
    /// @param importingClassMetadata 发起导入的配置类的元数据
    /// @return 审计自动配置的类名
    @Override
    public String[] selectImports(AnnotationMetadata importingClassMetadata) {
      return new String[] {AUDITING_AUTO_CONFIGURATION};
    }
  }
}
