package com.patra.starter.mybatis.autoconfig;

import com.baomidou.mybatisplus.autoconfigure.ConfigurationCustomizer;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.patra.starter.core.error.config.ErrorProperties;
import com.patra.starter.mybatis.error.contributor.DataLayerErrorMappingContributor;
import com.patra.starter.mybatis.type.CodeEnumTypeHandler;
import com.patra.starter.mybatis.type.JsonToJsonNodeTypeHandler;
import com.patra.starter.mybatis.type.JsonToMapTypeHandler;
import lombok.extern.slf4j.Slf4j;
import org.mybatis.spring.mapper.MapperScannerConfigurer;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

import java.util.Map;

/**
 * Patra MyBatis 公共自动配置
 *
 * <p>职责：
 * <ul>
 *     <li>约定基础设施层（infra）中的 Mapper 扫描路径，默认：{@code com.patra.**.infra.persistence.mapper}</li>
 *     <li>注册统一的 {@link com.patra.common.enums.CodeEnum} 枚举处理器，保证 DO 中直接使用领域枚举即可持久化</li>
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

    /**
     * MyBatis Configuration 定制：
     * <ol>
     *     <li>设置 {@link CodeEnumTypeHandler} 为全局默认枚举处理器</li>
     *     <li>为 {@link JsonNode} 注册 {@link JsonToJsonNodeTypeHandler}，统一 JSON 映射</li>
     * </ol>
     *
     * <p>使用 Spring 注入的 {@link ObjectMapper}，避免重复实例化，保证与全局 Jackson 配置一致。</p>
     */
    @Bean
    public ConfigurationCustomizer enumAndJsonCustomizer(
            ObjectMapper objectMapper) {

        return (MybatisConfiguration cfg) -> {
            cfg.setDefaultEnumTypeHandler(CodeEnumTypeHandler.class);

            // 原有：JsonNode 的处理器
            cfg.getTypeHandlerRegistry()
                    .register(JsonNode.class, new JsonToJsonNodeTypeHandler(objectMapper));

            // 新增：Map<String,Object> 的处理器
            cfg.getTypeHandlerRegistry()
                    .register(Map.class, new JsonToMapTypeHandler(objectMapper));
        };
    }
    
    /**
     * Creates the data layer error mapping contributor for handling MyBatis-Plus and database exceptions.
     * 
     * @param errorProperties error configuration properties, must not be null
     * @return data layer error mapping contributor instance, never null
     */
    @Bean
    @ConditionalOnMissingBean
    public DataLayerErrorMappingContributor dataLayerErrorMappingContributor(
            ErrorProperties errorProperties) {
        
        log.debug("Creating data layer error mapping contributor for MyBatis-Plus");
        return new DataLayerErrorMappingContributor(errorProperties);
    }
}
