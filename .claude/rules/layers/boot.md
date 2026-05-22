---
paths: patra-*/*-boot/**/*.java
---

# Boot 模块开发规范

## 核心职责

- 唯一启动入口：`@SpringBootApplication`
- 组装所有依赖，环境配置

## 启动类命名

`Patra{Service}Application`

## 配置管理

- 使用 `@ConfigurationProperties` 绑定配置
- 禁止硬编码配置值
