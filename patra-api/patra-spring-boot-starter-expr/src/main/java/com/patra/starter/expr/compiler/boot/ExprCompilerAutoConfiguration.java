package com.patra.starter.expr.compiler.boot;

import com.patra.starter.expr.compiler.DefaultExprCompiler;
import com.patra.starter.expr.compiler.ExprCompiler;
import com.patra.starter.expr.compiler.checker.CapabilityChecker;
import com.patra.starter.expr.compiler.checker.DefaultCapabilityChecker;
import com.patra.starter.expr.compiler.normalize.DefaultExprNormalizer;
import com.patra.starter.expr.compiler.normalize.ExprNormalizer;
import com.patra.starter.expr.compiler.render.DefaultExprRenderer;
import com.patra.starter.expr.compiler.render.ExprRenderer;
import com.patra.starter.expr.compiler.slice.DefaultExprSlicer;
import com.patra.starter.expr.compiler.slice.ExprSlicer;
import com.patra.starter.expr.compiler.snapshot.RuleSnapshotLoader;
import com.patra.starter.expr.compiler.snapshot.RegistryRuleSnapshotLoader;
import com.patra.registry.api.rpc.client.LiteratureProvenanceClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

@Slf4j
@AutoConfiguration
@EnableConfigurationProperties(CompilerProperties.class)
public class ExprCompilerAutoConfiguration {

    /**
     * 当且仅当：
     * 1) 用户未自定义 RuleSnapshotLoader Bean
     * 2) 且 patra.expr.compiler.registry-api.enabled=true
     * 时，注册基于 Feign 的 Loader。
     */
    @Bean
    @ConditionalOnMissingBean(RuleSnapshotLoader.class)
    @ConditionalOnProperty(prefix = "patra.expr.compiler.registry-api", name = "enabled", havingValue = "true", matchIfMissing = true)
    public RuleSnapshotLoader feignRuleSnapshotLoader(LiteratureProvenanceClient feignClient) {
        return new RegistryRuleSnapshotLoader(feignClient);
    }

    @Bean
    @ConditionalOnMissingBean(ExprSlicer.class)
    public ExprSlicer exprSlicer() {
        return new DefaultExprSlicer();
    }

    @Bean
    @ConditionalOnMissingBean(CapabilityChecker.class)
    public CapabilityChecker capabilityChecker() {
        return new DefaultCapabilityChecker();
    }


    @Bean
    @ConditionalOnMissingBean(ExprRenderer.class)
    public ExprRenderer exprRenderer() {
        return new DefaultExprRenderer();
    }

    @Bean
    @ConditionalOnMissingBean(ExprNormalizer.class)
    public ExprNormalizer exprNormalizer() {
        // 目前未启用规范化，直接返回原对象
        return new DefaultExprNormalizer();
    }

    /**
     * 说明：
     * - 仅当容器里已经有 RuleSnapshotLoader（当前阶段即 Feign 版）时，才注册默认编译器。
     * - 若业务方自定义了 ExprCompiler Bean，则不会覆盖。
     */
    @Bean
    @ConditionalOnBean({RuleSnapshotLoader.class, CapabilityChecker.class, ExprRenderer.class, ExprSlicer.class})
    @ConditionalOnMissingBean(ExprCompiler.class)
    public ExprCompiler exprCompiler(RuleSnapshotLoader snapshotLoader,
                                     CompilerProperties props,
                                     CapabilityChecker checker,
                                     ExprRenderer renderer,
                                     ExprSlicer slicer,
                                     ExprNormalizer normalizer
    ) {
        log.info("loaded ExprCompilerAutoConfiguration.exprCompiler()");
        return new DefaultExprCompiler(snapshotLoader, props, checker, renderer, slicer, normalizer);
    }
}
