package com.patra.starter.expr.compiler.boot;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.patra.registry.api.client.ExprClient;
import com.patra.registry.api.client.ProvenanceClient;
import com.patra.starter.expr.compiler.DefaultExprCompiler;
import com.patra.starter.expr.compiler.ExprCompiler;
import com.patra.starter.expr.compiler.check.CapabilityChecker;
import com.patra.starter.expr.compiler.check.DefaultCapabilityChecker;
import com.patra.starter.expr.compiler.function.FunctionRegistry;
import com.patra.starter.expr.compiler.metrics.ExprMetrics;
import com.patra.starter.expr.compiler.normalize.DefaultExprNormalizer;
import com.patra.starter.expr.compiler.normalize.ExprNormalizer;
import com.patra.starter.expr.compiler.render.DefaultExprRenderer;
import com.patra.starter.expr.compiler.render.ExprRenderer;
import com.patra.starter.expr.compiler.snapshot.RegistryRuleSnapshotLoader;
import com.patra.starter.expr.compiler.snapshot.RuleSnapshotLoader;
import com.patra.starter.expr.compiler.snapshot.convert.SnapshotAssembler;
import com.patra.starter.expr.compiler.transform.TransformRegistry;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.openfeign.FeignAutoConfiguration;
import org.springframework.context.annotation.Bean;

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
/// - 依赖 {@link FeignAutoConfiguration} 和 {@link ExprFunctionAutoConfiguration} 先完成
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
@AutoConfigureAfter({FeignAutoConfiguration.class, ExprFunctionAutoConfiguration.class})
@EnableConfigurationProperties({CompilerProperties.class, ExprModeProperties.class})
public class ExprCompilerAutoConfiguration {

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

  @Bean
  @ConditionalOnMissingBean(RuleSnapshotLoader.class)
  @ConditionalOnClass(
      name = {
        "com.patra.registry.api.client.ProvenanceClient",
        "com.patra.registry.api.client.ExprClient"
      })
  @ConditionalOnProperty(
      prefix = "patra.expr.compiler.registry-api",
      name = "enabled",
      havingValue = "true",
      matchIfMissing = true)
  public RuleSnapshotLoader registryRuleSnapshotLoader(
      ProvenanceClient provenanceClient,
      ExprClient exprClient,
      SnapshotAssembler snapshotAssembler) {
    return new RegistryRuleSnapshotLoader(provenanceClient, exprClient, snapshotAssembler);
  }

  @Bean
  @ConditionalOnMissingBean(CapabilityChecker.class)
  public CapabilityChecker capabilityChecker() {
    return new DefaultCapabilityChecker();
  }

  @Bean
  @ConditionalOnMissingBean(ExprNormalizer.class)
  public ExprNormalizer exprNormalizer() {
    return new DefaultExprNormalizer();
  }

  @Bean
  @ConditionalOnMissingBean(ExprMetrics.class)
  public ExprMetrics exprMetrics(ObjectProvider<MeterRegistry> meterRegistryProvider) {
    MeterRegistry registry = meterRegistryProvider.getIfAvailable();
    return registry != null ? ExprMetrics.of(registry) : ExprMetrics.noop();
  }

  @Bean
  @ConditionalOnMissingBean(ExprRenderer.class)
  @ConditionalOnBean(FunctionRegistry.class)
  public ExprRenderer exprRenderer(FunctionRegistry functionRegistry, ExprMetrics exprMetrics) {
    return new DefaultExprRenderer(functionRegistry, exprMetrics);
  }

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
