package dev.patra.ingest.expr.autoconfigure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import dev.patra.ingest.expr.compiler.snapshot.RegistryRuleSnapshotLoader;
import dev.patra.ingest.expr.compiler.snapshot.RuleSnapshotLoader;
import dev.patra.registry.api.expr.endpoint.ExprEndpoint;
import dev.patra.registry.api.provenance.endpoint.ProvenanceEndpoint;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import tools.jackson.databind.json.JsonMapper;

/// 表达式编译器自动配置的条件装配。
class ExprCompilerAutoConfigurationTest {

  private final ApplicationContextRunner runner =
      new ApplicationContextRunner()
          .withConfiguration(AutoConfigurations.of(ExprCompilerAutoConfiguration.class))
          .withBean(JsonMapper.class, JsonMapper::new)
          .withBean(ProvenanceEndpoint.class, () -> mock(ProvenanceEndpoint.class))
          .withBean(ExprEndpoint.class, () -> mock(ExprEndpoint.class));

  /// registry 的两个端点契约在类路径上时，装配基于 registry 的规则快照加载器。
  ///
  /// `@ConditionalOnClass` 写的是字符串全类名，契约改包名后编译不报错、装配静默失效，由这条测试兜住。
  @Test
  void should_create_registry_rule_snapshot_loader_when_registry_endpoints_on_classpath() {
    runner.run(
        context ->
            assertThat(context)
                .getBean(RuleSnapshotLoader.class)
                .isInstanceOf(RegistryRuleSnapshotLoader.class));
  }
}
