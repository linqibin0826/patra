package dev.linqibin.starter.web;

import dev.linqibin.starter.core.error.config.CoreErrorAutoConfiguration;
import dev.linqibin.starter.web.error.config.WebErrorAutoConfiguration;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;

/// starter-web 切片测试的配置根，导入统一错误格式的两个自动配置。
@SpringBootConfiguration
@EnableAutoConfiguration
@ImportAutoConfiguration({CoreErrorAutoConfiguration.class, WebErrorAutoConfiguration.class})
public class WebErrorITWebMvcConfig {}
