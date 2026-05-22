package dev.linqibin.patra.catalog.adapter.config;

import dev.linqibin.starter.core.error.config.CoreErrorAutoConfiguration;
import dev.linqibin.starter.core.json.autoconfig.JacksonAutoConfiguration;
import dev.linqibin.starter.web.error.config.WebErrorAutoConfiguration;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.web.bind.annotation.RestController;

/// 测试配置类。
///
/// 用于解决 @WebMvcTest 找不到 @SpringBootConfiguration 的问题。
///
/// 职责：
///
/// - 提供 @SpringBootConfiguration，使 @WebMvcTest 能够加载 Spring 上下文
/// - 扫描非 Controller 组件（如 MapStruct Converter），排除 @RestController 避免交叉依赖
/// - 显式导入错误处理自动配置（@WebMvcTest 默认不加载）
/// - 显式导入 Jackson 自动配置，确保 Long → String 序列化模块在切片测试中生效
///
/// 注意：Controller 的加载由 `@WebMvcTest(controllers = ...)` 负责，
/// `@ComponentScan` 仅加载 MapStruct 转换器等辅助组件。
///
/// @author linqibin
/// @since 0.1.0
@SpringBootConfiguration
@EnableAutoConfiguration
@ImportAutoConfiguration({
  CoreErrorAutoConfiguration.class,
  WebErrorAutoConfiguration.class,
  JacksonAutoConfiguration.class
})
@ComponentScan(
    basePackages = "dev.linqibin.patra.catalog.adapter.rest",
    excludeFilters =
        @ComponentScan.Filter(type = FilterType.ANNOTATION, classes = RestController.class))
public class TestConfiguration {
  // 当前无额外 Bean 配置，后续可按需添加
}
