package com.patra.starter.core.error.runtime;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.patra.starter.core.error.codec.JacksonPlatformErrorCodec;
import com.patra.starter.core.error.codec.PlatformErrorCodec;
import com.patra.starter.core.error.registry.Codebook;
import com.patra.starter.core.error.registry.CodebookParser;
import com.patra.starter.core.error.registry.ModuleRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ResourceLoaderAware;
import org.springframework.context.annotation.Bean;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

import java.io.IOException;
import java.net.URL;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

@Slf4j
@AutoConfiguration
@EnableConfigurationProperties(PatraErrorProperties.class)
@ConditionalOnClass({Problems.class, Codebook.class})
@ConditionalOnProperty(prefix = "patra.error", name = "enabled", havingValue = "true", matchIfMissing = true)
public class PatraErrorAutoConfiguration implements ResourceLoaderAware {

    private ResourceLoader resourceLoader;

    @Override
    public void setResourceLoader(ResourceLoader resourceLoader) {
        this.resourceLoader = resourceLoader;
    }

    @Bean
    @ConditionalOnMissingBean
    public ModuleRegistry moduleRegistry(PatraErrorProperties props) {
        var registry = new ModuleRegistry();
        // 让 registry 自己用 ClassLoader 合并所有同名资源（无需你前置扫描）
        ClassLoader cl = getBeanClassLoader();
        registry.loadFromClasspath(cl);

        if (props.isLogSummary()) {
            // 简单输出一下有哪些模块被登记（可按需精简）
            log.info("[patra-error] module-registry loaded, modules={}", registry.all().keySet());
        }
        return registry;
    }

    @Bean
    @ConditionalOnMissingBean
    public CodebookParser codebookParser() {
        return new CodebookParser();
    }

    @Bean
    @ConditionalOnMissingBean
    public Codebook codebook(PatraErrorProperties props, CodebookParser parser, ModuleRegistry moduleRegistry) {
        var resolver = new PathMatchingResourcePatternResolver(getBeanClassLoader());
        Codebook merged = new Codebook();
        int files = 0;

        try {
            // 1) properties
            Resource[] propRes = resolver.getResources("classpath*:/META-INF/patra/codebook-*.properties");
            files += mergeResourcesInOrder(merged, propRes, parser, props);

            // 2) json
            Resource[] jsonRes = resolver.getResources("classpath*:/META-INF/patra/codebook-*.json");
            files += mergeResourcesInOrder(merged, jsonRes, parser, props);

            if (props.isLogSummary()) {
                log.info("[patra-error] codebook loaded: files={}, entries={}", files, merged.all().size());
            }
            if (files == 0 && props.isFailFast()) {
                throw new IllegalStateException(
                        "[patra-error] No codebook resources found (expect META-INF/patra/codebook-*.properties/json)");
            }

            // 3) 基本校验：非法码形态
            var invalid = merged.validateLiterals();
            if (!invalid.isEmpty()) {
                var msg = "[patra-error] invalid error code literals: " + invalid;
                if (props.isFailFast()) throw new IllegalStateException(msg);
                log.warn(msg);
            }

            // 4) 基本校验：模块前缀是否登记（可开关）
            if (props.isValidateModulePrefix()) {
                var unknown = merged.all().keySet().stream()
                        .map(k -> k.substring(0, k.indexOf('-')))
                        .filter(prefix -> !moduleRegistry.isKnownModule(prefix))
                        .distinct()
                        .toList();
                if (!unknown.isEmpty()) {
                    var msg = "[patra-error] unknown module prefixes in codebook: " + unknown;
                    if (props.isFailFast()) throw new IllegalStateException(msg);
                    log.warn(msg);
                }
            }

        } catch (IOException e) {
            if (props.isFailFast()) throw new IllegalStateException("Load codebooks failed", e);
            log.warn("[patra-error] Load codebooks partially failed: {}", e.getMessage());
        }

        // 绑定到 Problems，全局生效
        Problems.setCodebookProvider(() -> merged);
        return merged;
    }


    @Bean
    @ConditionalOnMissingBean
    PlatformErrorCodec platformErrorCodec(ObjectMapper objectMapper) {
        return new JacksonPlatformErrorCodec(objectMapper);
    }

    /**
     * 合并资源并做稳定排序与冲突检测
     */
    private int mergeResourcesInOrder(Codebook target, Resource[] resources, CodebookParser parser, PatraErrorProperties props) {
        if (resources == null || resources.length == 0) return 0;

        // 稳定排序（按 URL 文本）
        List<Resource> list = Arrays.stream(resources)
                .sorted(Comparator.comparing(this::safeUrlString))
                .toList();

        int count = 0;
        for (Resource r : list) {
            try {
                URL url = r.getURL();
                Codebook part = url.getPath().endsWith(".json")
                        ? parser.loadFromJson(url)
                        : parser.loadFromProperties(url);

                // 冲突检测：同 code 不同 title/http（可配置 fail/warn/忽略）
                if (props.isDetectConflict()) {
                    detectConflicts(target, part, props.isFailOnConflict());
                }

                target.merge(part);
                count++;
                if (props.isLogSummary()) {
                    log.debug("[patra-error] merged codebook: {}", url);
                }
            } catch (IOException ex) {
                if (props.isFailFast()) throw new IllegalStateException("Read codebook resource failed: " + r, ex);
                log.warn("[patra-error] skip codebook: {} ({})", r, ex.getMessage());
            }
        }
        return count;
    }

    private void detectConflicts(Codebook base, Codebook part, boolean fail) {
        var baseMap = base.all();
        for (var e : part.all().entrySet()) {
            var code = e.getKey();
            var incoming = e.getValue();
            var existing = baseMap.get(code);
            if (existing == null) continue;

            boolean titleDiff = !Objects.equals(existing.title(), incoming.title());
            boolean httpDiff = !Objects.equals(existing.httpStatus(), incoming.httpStatus());
            if (titleDiff || httpDiff) {
                String msg = "[patra-error] code conflict: %s (title:%s -> %s, http:%s -> %s)".formatted(
                        code, existing.title(), incoming.title(), existing.httpStatus(), incoming.httpStatus()
                );
                if (fail) throw new IllegalStateException(msg);
                log.warn(msg);
            }
        }
    }

    private String safeUrlString(Resource r) {
        try {
            return r.getURL().toString();
        } catch (IOException e) {
            return r.getDescription();
        }
    }

    private ClassLoader getBeanClassLoader() {
        return (resourceLoader != null && resourceLoader.getClassLoader() != null)
                ? resourceLoader.getClassLoader()
                : Thread.currentThread().getContextClassLoader();
    }
}
