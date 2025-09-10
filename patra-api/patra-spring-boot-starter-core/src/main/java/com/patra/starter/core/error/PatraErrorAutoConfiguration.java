package com.patra.starter.core.error;

import com.patra.error.core.Problems;
import com.patra.error.registry.Codebook;
import com.patra.error.registry.CodebookParser;
import com.patra.error.registry.ModuleRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

import java.io.IOException;
import java.net.URL;

@AutoConfiguration
@EnableConfigurationProperties(PatraErrorProperties.class)
@ConditionalOnClass({Problems.class, Codebook.class})
@ConditionalOnProperty(prefix = "patra.error", name = "enabled", havingValue = "true", matchIfMissing = true)
public class PatraErrorAutoConfiguration {
    private static final Logger log = LoggerFactory.getLogger(PatraErrorAutoConfiguration.class);

    @Bean
    @ConditionalOnMissingBean
    public ModuleRegistry moduleRegistry(PatraErrorProperties props) {
        // 使用 Spring 的 resolver 扫描 module-registry.properties
        var resolver = new PathMatchingResourcePatternResolver();
        var registry = new ModuleRegistry();
        try {
            Resource[] res = resolver.getResources("classpath*:/META-INF/patra/module-registry.properties");
            for (Resource r : res) {
                registry.loadFromClasspath(r.getClass().getClassLoader());
                break; // 该方法内部会用 classLoader 合并所有同名资源
            }
            if (props.isLogSummary()) {
                log.info("[patra-error] module-registry files: {}", res.length);
            }
        } catch (IOException e) {
            log.warn("[patra-error] load module-registry failed: {}", e.getMessage());
        }
        return registry;
    }


    @Bean
    @ConditionalOnMissingBean
    public CodebookParser codebookLoaderParser() {
        // 由 common-error 提供的解析器：保留 URL→Codebook 的解析能力即可
        return new CodebookParser();
    }

    @Bean
    @ConditionalOnMissingBean
    public Codebook codebook(PatraErrorProperties props, CodebookParser parser) {
        var resolver = new PathMatchingResourcePatternResolver();
        Codebook merged = new Codebook();
        int files = 0;
        try {
            Resource[] propsResources = resolver.getResources("classpath*:/META-INF/patra/codebook-*.properties");
            for (Resource r : propsResources) {
                URL url = r.getURL();
                merged = merged.merge(parser.loadFromProperties(url)); // ← 只做解析与合并
                files++;
            }
            // 如需 json（可选）：同理扫描 *.json 然后 parser.loadFromJson(url)

            if (props.isLogSummary()) {
                log.info("[patra-error] loaded codebook files: {}, entries: {}", files, merged.all().size());
            }
            if (files == 0 && props.isFailFast()) {
                throw new IllegalStateException(
                    "[patra-error] No codebook resources found (expect META-INF/patra/codebook-*.properties)");
            }
        } catch (IOException e) {
            if (props.isFailFast()) throw new IllegalStateException("Load codebooks failed", e);
            log.warn("[patra-error] Load codebooks partially failed: {}", e.getMessage());
        }

        // 绑定给 Problems 的全局提供者，后续 Problems.of(...) 自动从 codebook 补全 title/http
        Codebook finalMerged = merged;
        Problems.setCodebookProvider(() -> finalMerged);
        return merged;
    }

}
