package dev.patra.identity;

import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;

/// identity adapter 切片测试的配置根：统一错误格式，加上 starter-web 的 MVC 响应 JSON 定制（Long 输出成字符串）。
@SpringBootConfiguration
@EnableAutoConfiguration
public class IdentityITWebMvcConfig {}
