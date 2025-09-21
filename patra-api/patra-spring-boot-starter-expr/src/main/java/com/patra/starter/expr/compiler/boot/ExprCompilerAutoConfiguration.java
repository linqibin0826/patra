package com.patra.starter.expr.compiler.boot;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.patra.registry.api.rpc.client.ExprClient;
import com.patra.registry.api.rpc.client.ProvenanceClient;
import com.patra.starter.expr.compiler.DefaultExprCompiler;
import com.patra.starter.expr.compiler.ExprCompiler;
import com.patra.starter.expr.compiler.check.CapabilityChecker;
import com.patra.starter.expr.compiler.check.DefaultCapabilityChecker;
import com.patra.starter.expr.compiler.normalize.DefaultExprNormalizer;
import com.patra.starter.expr.compiler.normalize.ExprNormalizer;
import com.patra.starter.expr.compiler.render.DefaultExprRenderer;
import com.patra.starter.expr.compiler.render.ExprRenderer;
import com.patra.starter.expr.compiler.snapshot.RegistryRuleSnapshotLoader;
import com.patra.starter.expr.compiler.snapshot.RuleSnapshotLoader;
import com.patra.starter.expr.compiler.snapshot.convert.SnapshotAssembler;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
@EnableConfigurationProperties(CompilerProperties.class)
public class ExprCompilerAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(RuleSnapshotLoader.class)
    @ConditionalOnBean({ProvenanceClient.class, ExprClient.class})
    @ConditionalOnProperty(prefix = "patra.expr.compiler.registry-api", name = "enabled", havingValue = "true", matchIfMissing = true)
    public SnapshotAssembler exprSnapshotAssembler(ObjectMapper objectMapper) {
        return new SnapshotAssembler(objectMapper);
    }

    @Bean
    @ConditionalOnMissingBean(RuleSnapshotLoader.class)
    @ConditionalOnBean({ProvenanceClient.class, ExprClient.class, SnapshotAssembler.class})
    @ConditionalOnProperty(prefix = "patra.expr.compiler.registry-api", name = "enabled", havingValue = "true", matchIfMissing = true)
    public RuleSnapshotLoader registryRuleSnapshotLoader(ProvenanceClient provenanceClient,
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
    @ConditionalOnMissingBean(ExprRenderer.class)
    public ExprRenderer exprRenderer() {
        return new DefaultExprRenderer();
    }

    @Bean
    @ConditionalOnMissingBean(ExprCompiler.class)
    @ConditionalOnBean({RuleSnapshotLoader.class, CapabilityChecker.class, ExprNormalizer.class, ExprRenderer.class})
    @ConditionalOnProperty(prefix = "patra.expr.compiler", name = "enabled", havingValue = "true", matchIfMissing = true)
    public ExprCompiler exprCompiler(RuleSnapshotLoader loader,
                                     CapabilityChecker checker,
                                     ExprNormalizer normalizer,
                                     ExprRenderer renderer) {
        return new DefaultExprCompiler(loader, checker, normalizer, renderer);
    }
}
