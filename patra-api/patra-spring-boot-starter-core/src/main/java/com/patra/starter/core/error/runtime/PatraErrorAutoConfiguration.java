package com.patra.starter.core.error.runtime;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
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
import org.springframework.lang.NonNull;

import java.io.IOException;
import java.net.URL;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * Patra 平台错误处理框架自动配置类。
 * 
 * <p>该配置类负责自动装配平台错误处理框架的核心组件：
 * <ul>
 *   <li>ModuleRegistry：模块注册表，管理模块元数据</li>
 *   <li>CodebookParser：错误码解析器，从配置文件加载错误码定义</li>
 *   <li>Codebook：错误码册，聚合所有错误码定义</li>
 *   <li>PlatformErrorCodec：平台错误编解码器，处理序列化和反序列化</li>
 * </ul>
 * 
 * <p>自动装配条件：
 * <ul>
 *   <li>类路径中存在 PlatformErrorFactory 和 Codebook 类</li>
 *   <li>配置属性 patra.error.enabled 为 true（默认为 true）</li>
 *   <li>使用 @ConditionalOnMissingBean 避免重复装配</li>
 * </ul>
 * 
 * <p>配置加载策略：
 * <ul>
 *   <li>扫描 classpath*:/META-INF/patra/module-*.properties 加载模块定义</li>
 *   <li>扫描 classpath*:/META-INF/patra/codebook-*.properties 和 codebook-*.json 加载错误码</li>
 *   <li>按文件名排序加载，确保加载顺序的确定性</li>
 *   <li>支持冲突检测和验证，可配置失败策略</li>
 * </ul>
 * 
 * <p>使用示例：
 * <pre>{@code
 * # application.yml
 * patra:
 *   error:
 *     enabled: true
 *     log-summary: true
 *     fail-fast: false
 *     detect-conflict: true
 *     fail-on-conflict: false
 *     validate-module-prefix: true
 * }</pre>
 *
 * @author linqibin
 * @since 0.1.0
 * @see PatraErrorProperties
 * @see PlatformErrorFactory
 * @see Codebook
 * @see ModuleRegistry
 */
@Slf4j
@AutoConfiguration
@EnableConfigurationProperties(PatraErrorProperties.class)
@ConditionalOnClass({PlatformErrorFactory.class, Codebook.class})
@ConditionalOnProperty(prefix = "patra.error", name = "enabled", havingValue = "true", matchIfMissing = true)
public class PatraErrorAutoConfiguration implements ResourceLoaderAware {

    private ResourceLoader resourceLoader;

    /* ==================== ResourceLoaderAware 实现 ==================== */

    @Override
    public void setResourceLoader(@NonNull ResourceLoader resourceLoader) {
        this.resourceLoader = resourceLoader;
    }

    /* ==================== Bean 装配方法 ==================== */

    /**
     * 配置模块注册表 Bean。
     * 
     * <p>从类路径扫描 module-*.properties 文件，加载模块元数据定义。
     * 支持多个 JAR 包中的同名资源合并。
     * 
     * @param properties 配置属性
     * @return 模块注册表实例
     */
    @Bean
    @ConditionalOnMissingBean
    public ModuleRegistry moduleRegistry(PatraErrorProperties properties) {
        var registry = new ModuleRegistry();
        
        // 让注册表自己处理类路径扫描和资源合并
        ClassLoader classLoader = getBeanClassLoader();
        registry.loadFromClasspath(classLoader);

        if (properties.isLogSummary()) {
            var moduleNames = registry.all().keySet();
            log.info("[patra-error] Module registry loaded successfully, modules={}", moduleNames);
        }
        
        return registry;
    }

    /**
     * 配置错误码解析器 Bean。
     * 
     * @return 错误码解析器实例
     */
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
        PlatformErrorFactory.setCodebookProvider(() -> merged);
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
