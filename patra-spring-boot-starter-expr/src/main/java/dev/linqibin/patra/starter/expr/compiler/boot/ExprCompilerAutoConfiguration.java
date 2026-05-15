package dev.linqibin.patra.starter.expr.compiler.boot;

import com.patra.starter.httpinterface.config.HttpInterfaceAutoConfiguration;
import dev.linqibin.patra.registry.api.endpoint.ExprEndpoint;
import dev.linqibin.patra.registry.api.endpoint.ProvenanceEndpoint;
import dev.linqibin.patra.starter.expr.compiler.DefaultExprCompiler;
import dev.linqibin.patra.starter.expr.compiler.ExprCompiler;
import dev.linqibin.patra.starter.expr.compiler.check.CapabilityChecker;
import dev.linqibin.patra.starter.expr.compiler.check.DefaultCapabilityChecker;
import dev.linqibin.patra.starter.expr.compiler.function.FunctionRegistry;
import dev.linqibin.patra.starter.expr.compiler.metrics.ExprMetrics;
import dev.linqibin.patra.starter.expr.compiler.normalize.DefaultExprNormalizer;
import dev.linqibin.patra.starter.expr.compiler.normalize.ExprNormalizer;
import dev.linqibin.patra.starter.expr.compiler.render.DefaultExprRenderer;
import dev.linqibin.patra.starter.expr.compiler.render.ExprRenderer;
import dev.linqibin.patra.starter.expr.compiler.snapshot.RegistryRuleSnapshotLoader;
import dev.linqibin.patra.starter.expr.compiler.snapshot.RuleSnapshotLoader;
import dev.linqibin.patra.starter.expr.compiler.snapshot.convert.SnapshotAssembler;
import dev.linqibin.patra.starter.expr.compiler.transform.TransformRegistry;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import tools.jackson.databind.ObjectMapper;

/// 表达式编译器自动配置
///
/// 配置并装配表达式编译器核心组件,包括规则快照加载、能力检查、标准化、渲染和编译等。
///
/// ### 配置的 Bean
///
/// - {@link SnapshotAssembler} - 快照组装器,将 Registry API 数据转换为编译器内部快照
///   - {@link RuleSnapshotLoader} - 规则快照加载器,从 patra-registry 加载 Provenance 和表达式规则
///   - {@link CapabilityChecker} - 能力检查器,验证表达式编译请求的有效性
///   - {@link ExprNormalizer} - 表达式标准化器,规范化表达式语法
///   - {@link ExprMetrics} - 表达式编译指标,记录编译性能和结果
///   - {@link ExprRenderer} - 表达式渲染器,将内部表达式渲染为目标格式
///   - {@link ExprCompiler} - 表达式编译器主入口
///
/// ### 条件装配
///
/// - 依赖 {@link HttpInterfaceAutoConfiguration} 和 {@link ExprFunctionAutoConfiguration} 先完成
///   - Registry API 集成可通过 `patra.expr.compiler.registry-api.enabled=false` 禁用
///   - 编译器整体可通过 `patra.expr.compiler.enabled=false` 禁用
///
/// ### 配置属性
///
/// - {@link CompilerProperties} - 编译器行为配置(查询长度限制、参数数量限制等)
///   - {@link ExprModeProperties} - 各 Provenance 的表达式编译模式配置
///
/// @see CompilerProperties
/// @see ExprModeProperties
/// @see ExprCompiler
@AutoConfiguration
@AutoConfigureAfter({HttpInterfaceAutoConfiguration.class, ExprFunctionAutoConfiguration.class})
@EnableConfigurationProperties({CompilerProperties.class, ExprModeProperties.class})
public class ExprCompilerAutoConfiguration {

  /// 创建快照组装器 Bean。
  ///
  /// @param objectMapper Jackson ObjectMapper 实例
  /// @return 快照组装器实例
  @Bean
  @ConditionalOnMissingBean(RuleSnapshotLoader.class)
  @ConditionalOnProperty(
      prefix = "patra.expr.compiler.registry-api",
      name = "enabled",
      havingValue = "true",
      matchIfMissing = true)
  public SnapshotAssembler exprSnapshotAssembler(ObjectMapper objectMapper) {
    return new SnapshotAssembler(objectMapper);
  }

  /// 创建基于 Registry 的规则快照加载器 Bean。
  ///
  /// @param provenanceClient Provenance API 客户端
  /// @param exprClient 表达式 API 客户端
  /// @param snapshotAssembler 快照组装器
  /// @return 规则快照加载器实例
  @Bean
  @ConditionalOnMissingBean(RuleSnapshotLoader.class)
  @ConditionalOnClass(
      name = {
        "com.patra.registry.api.endpoint.ProvenanceEndpoint",
        "com.patra.registry.api.endpoint.ExprEndpoint"
      })
  @ConditionalOnProperty(
      prefix = "patra.expr.compiler.registry-api",
      name = "enabled",
      havingValue = "true",
      matchIfMissing = true)
  public RuleSnapshotLoader registryRuleSnapshotLoader(
      ProvenanceEndpoint provenanceEndpoint,
      ExprEndpoint exprEndpoint,
      SnapshotAssembler snapshotAssembler) {
    return new RegistryRuleSnapshotLoader(provenanceEndpoint, exprEndpoint, snapshotAssembler);
  }

  /// 创建默认能力检查器 Bean。
  ///
  /// @return 能力检查器实例
  @Bean
  @ConditionalOnMissingBean(CapabilityChecker.class)
  public CapabilityChecker capabilityChecker() {
    return new DefaultCapabilityChecker();
  }

  /// 创建默认表达式标准化器 Bean。
  ///
  /// @return 表达式标准化器实例
  @Bean
  @ConditionalOnMissingBean(ExprNormalizer.class)
  public ExprNormalizer exprNormalizer() {
    return new DefaultExprNormalizer();
  }

  /// 创建表达式编译指标收集器 Bean。
  ///
  /// @param meterRegistryProvider Micrometer MeterRegistry 提供器
  /// @return 表达式指标实例
  @Bean
  @ConditionalOnMissingBean(ExprMetrics.class)
  public ExprMetrics exprMetrics(ObjectProvider<MeterRegistry> meterRegistryProvider) {
    MeterRegistry registry = meterRegistryProvider.getIfAvailable();
    return registry != null ? ExprMetrics.of(registry) : ExprMetrics.noop();
  }

  /// 创建默认表达式渲染器 Bean。
  ///
  /// @param functionRegistry 函数注册表
  /// @param exprMetrics 表达式指标收集器
  /// @return 表达式渲染器实例
  @Bean
  @ConditionalOnMissingBean(ExprRenderer.class)
  @ConditionalOnBean(FunctionRegistry.class)
  public ExprRenderer exprRenderer(FunctionRegistry functionRegistry, ExprMetrics exprMetrics) {
    return new DefaultExprRenderer(functionRegistry, exprMetrics);
  }

  /// 创建默认表达式编译器 Bean。
  ///
  /// @param loader 规则快照加载器
  /// @param checker 能力检查器
  /// @param normalizer 表达式标准化器
  /// @param renderer 表达式渲染器
  /// @param transformRegistry 值转换注册表
  /// @param compilerProperties 编译器配置属性
  /// @param modeProperties 模式配置属性
  /// @param exprMetrics 表达式指标收集器
  /// @return 表达式编译器实例
  @Bean
  @ConditionalOnMissingBean(ExprCompiler.class)
  @ConditionalOnBean({
    RuleSnapshotLoader.class,
    CapabilityChecker.class,
    ExprNormalizer.class,
    ExprRenderer.class,
    TransformRegistry.class
  })
  @ConditionalOnProperty(
      prefix = "patra.expr.compiler",
      name = "enabled",
      havingValue = "true",
      matchIfMissing = true)
  public ExprCompiler exprCompiler(
      RuleSnapshotLoader loader,
      CapabilityChecker checker,
      ExprNormalizer normalizer,
      ExprRenderer renderer,
      TransformRegistry transformRegistry,
      CompilerProperties compilerProperties,
      ExprModeProperties modeProperties,
      ExprMetrics exprMetrics) {
    return new DefaultExprCompiler(
        loader,
        checker,
        normalizer,
        renderer,
        transformRegistry,
        compilerProperties,
        modeProperties,
        exprMetrics);
  }
}
