package com.patra.starter.core.json.autoconfig;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.patra.starter.core.json.ObjectMapperProvider;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

/**
 * 为 {@link com.patra.starter.core.json.ObjectMapperProvider} 提供自动装配。
 * <p>
 * 设计意图：
 * <ul>
 *   <li>当类路径存在 Jackson 的 {@link com.fasterxml.jackson.databind.ObjectMapper} 时，
 *       自动注册一个 {@code ObjectMapperProvider} Bean；</li>
 *   <li>该 Provider 会在容器就绪后将 <b>Spring 管理的</b> {@code ObjectMapper}
 *       桥接到非 Spring 代码可见的 {@code JsonMapperHolder}，统一两侧配置。</li>
 * </ul>
 *
 * <h3>与 Spring 注入的区别</h3>
 * <p>
 * 在业务代码中，<b>优先通过依赖注入（DI）</b>获取 {@code ObjectMapper}。本自动装配仅负责
 * 提供一个桥接器（Provider），用于 <em>无法</em> 注入的静态/公共库/非 Spring 路径。
 * 因此它不是“服务定位器”的替代物，而是 DI 的补丁带。</p>
 */
@Slf4j
@AutoConfiguration
@ConditionalOnClass(ObjectMapper.class)
public class JacksonAutoConfiguration {

    /**
     * 注册一个 {@link ObjectMapperProvider}。当用户已自定义同名 Bean 时不生效。
     * 该 Provider 将在容器就绪后把容器内的 {@link ObjectMapper} 注册进 JsonMapperHolder，
     * 以便非 Spring 路径获得与容器一致的配置。
     */
    @ConditionalOnMissingBean(ObjectMapperProvider.class)
    @Bean
    public ObjectMapperProvider jacksonProvider() {
        log.debug("loaded JacksonAutoConfiguration.jacksonProvider()");
        return new ObjectMapperProvider();
    }
}
