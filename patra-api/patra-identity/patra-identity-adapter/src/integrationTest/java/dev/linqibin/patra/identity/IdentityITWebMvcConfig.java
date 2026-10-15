package dev.linqibin.patra.identity;

import dev.linqibin.starter.core.error.config.CoreErrorAutoConfiguration;
import dev.linqibin.starter.web.error.config.WebErrorAutoConfiguration;
import dev.linqibin.starter.web.json.config.WebJsonAutoConfiguration;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;

/// identity adapter 切片测试的配置根：统一错误格式，加上 starter-web 的 MVC 响应 JSON 定制（Long 输出成字符串）。
@SpringBootConfiguration
@EnableAutoConfiguration
@ImportAutoConfiguration({
  CoreErrorAutoConfiguration.class,
  WebErrorAutoConfiguration.class,
  WebJsonAutoConfiguration.class
})
public class IdentityITWebMvcConfig {}
