package com.patra.starter.mybatis.autoconfig;

import com.baomidou.mybatisplus.autoconfigure.ConfigurationCustomizer;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.patra.common.error.codes.HttpStdErrors;
import com.patra.starter.mybatis.error.contributor.DataLayerErrorMappingContributor;
import com.patra.starter.mybatis.type.JsonToJsonNodeTypeHandler;
import lombok.extern.slf4j.Slf4j;
import org.apache.ibatis.session.SqlSessionFactory;
import org.mybatis.spring.mapper.MapperScannerConfigurer;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

/**
 * Patra MyBatis 公共自动配置
 *
 * <p>职责：
 * <ul>
 *     <li>约定基础设施层（infra）中的 Mapper 扫描路径，默认：{@code com.patra.**.infra.persistence.mapper}</li>
 *     <li>注册 Jackson {@link com.fasterxml.jackson.databind.JsonNode} 的处理器，DO 中直接使用 JsonNode 字段，自动序列化/反序列化</li>
 * </ul>
 *
 * <p>可扩展点：
 * <ul>
 *     <li>Mapper 扫描路径：业务可通过 {@code mybatis-plus.mapper-locations} 或 {@code mybatis-plus.type-aliases-package} 等配置追加</li>
 *     <li>类型处理器：业务可通过 {@code mybatis-plus.type-handlers-package} 配置注册自定义 TypeHandler</li>
 *     <li>上述默认处理器都可以在业务模块中通过 MyBatis 配置覆盖</li>
 * </ul>
 *
 * <p>注意：
 * <ul>
 *     <li>本配置类仅在类路径存在 MyBatis-Spring 的 {@link org.mybatis.spring.mapper.MapperScannerConfigurer} 时生效</li>
 *     <li>不涉及事务、数据源配置，这部分交由具体业务模块 infra/config 处理</li>
 * </ul>
 */
@Slf4j
@AutoConfiguration
@ConditionalOnClass(MapperScannerConfigurer.class)
public class PatraMybatisAutoConfiguration {

    /**
     * Mapper 扫描配置。
     * <p>默认扫描 {@code com.patra.**.infra.persistence.mapper}，保证各业务模块 infra 层的 Mapper 自动生效。</p>
     * <p>业务模块可通过 MyBatis-Plus 的配置项追加或覆盖扫描路径。</p>
     */
    @Bean
    public MapperScannerConfigurer mapperScannerConfigurer() {
        MapperScannerConfigurer c = new MapperScannerConfigurer();
        c.setBasePackage("com.patra.**.infra.persistence.mapper");
        return c;
    }

    @Bean
    @ConditionalOnMissingBean
    public DataLayerErrorMappingContributor dataLayerErrorMappingContributor(HttpStdErrors.Group http) {
        log.debug("Creating data layer error mapping contributor for MyBatis-Plus");
        return new DataLayerErrorMappingContributor(http);
    }


    /**
     * 在 MyBatis Configuration 初始化时注册自定义 TypeHandler。
     * <p>这是注册 TypeHandler 的正确时机，确保在解析 XML 和生成 autoResultMap 时 TypeHandler 已可用。</p>
     */
    @Bean
    public ConfigurationCustomizer configurationCustomizer(ObjectMapper objectMapper) {
        return configuration -> {
            // 注册 JsonNode TypeHandler（全局生效）
            configuration.getTypeHandlerRegistry()
                    .register(JsonNode.class, new JsonToJsonNodeTypeHandler(objectMapper));

            log.info("Custom TypeHandlers registered: JsonNode -> JsonToJsonNodeTypeHandler");
        };
    }

}
