# Patra Spring Boot Starter - OpenAPI

API 文档 Starter，基于 SpringDoc OpenAPI 3.0.1 + Scalar UI，提供零配置的 API 文档生成。

## 模块概述

本模块提供统一的 API 文档基础设施，支持：

- **Scalar UI**：现代化 API 文档界面，替代 Swagger UI
- **Javadoc 自动映射**：Controller 的 `///` 注释自动映射为 API 文档描述
- **零配置启用**：添加依赖即生效，标题自动从 `spring.application.name` 推导
- **完全可覆盖**：用户可通过自定义 `OpenAPI` bean 完全替换默认配置

## 核心功能

### 自动配置

| 配置类 | 功能 | 条件 |
|--------|------|------|
| `OpenApiAutoConfiguration` | 创建默认 `OpenAPI` bean，配置文档标题、版本、描述 | Servlet Web 应用，`linqibin.starter.openapi.enabled=true`（默认启用） |

### Javadoc → API 文档流程

```
编译期                                 运行时
┌────────────────────────────┐       ┌─────────────────────────────┐
│ therapi-javadoc-scribe     │       │ therapi-runtime-javadoc     │
│ （adapter convention plugin │  ──→  │ （本 starter 传递依赖）       │
│   自动添加的注解处理器）      │       │                             │
│                            │       │ SpringDoc 读取 Javadoc       │
│ 提取 /// 注释到 classpath   │       │ → 映射为 API 描述            │
│ 资源文件                    │       │ → Scalar UI 展示             │
└────────────────────────────┘       └─────────────────────────────┘
```

> **注意**：`therapi-javadoc-scribe` 注解处理器已在 `patra.hexagonal-adapter` convention plugin 中统一声明，
> 所有 adapter 模块编译时自动提取 Javadoc，无需手动配置。

### 标题解析优先级

1. `linqibin.starter.openapi.title` 属性（显式配置）
2. `spring.application.name` 属性（自动推导）
3. `"Patra API"`（兜底默认值）

## 快速开始

### 1. 添加依赖

在 `patra-{service}-boot` 模块中添加：

```kotlin
implementation(project(":patra-spring-boot-starter-openapi"))
```

### 2. 配置属性（可选）

```yaml
linqibin:
  starter:
    openapi:
      enabled: true              # 全局开关（默认启用）
      title: Catalog Service API # 默认从 spring.application.name 推导
      version: 0.1.0             # API 版本号
      description: 目录服务 API   # API 描述
```

### 3. 访问地址

启动服务后访问：

| 地址 | 说明 |
|------|------|
| `http://localhost:8080/scalar/index.html` | Scalar UI 文档界面 |
| `http://localhost:8080/v3/api-docs` | OpenAPI JSON 规范 |

## 配置参考

| 属性 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| `linqibin.starter.openapi.enabled` | boolean | true | 全局开关 |
| `linqibin.starter.openapi.title` | String | null（自动推导） | API 文档标题 |
| `linqibin.starter.openapi.version` | String | 0.1.0 | API 版本号 |
| `linqibin.starter.openapi.description` | String | null | API 文档描述 |

## 自定义 OpenAPI 配置

通过声明自定义 `OpenAPI` bean 完全覆盖默认配置（`@ConditionalOnMissingBean`）：

```java
@Configuration
public class CustomOpenApiConfig {

    @Bean
    public OpenAPI customOpenApi() {
        return new OpenAPI()
            .info(new Info()
                .title("Custom API")
                .version("2.0.0")
                .description("自定义 API 文档"));
    }
}
```

## 依赖关系

```
patra-spring-boot-starter-openapi
├── springdoc-openapi-starter-webmvc-scalar  # SpringDoc + Scalar UI
└── therapi-runtime-javadoc                  # Javadoc 运行时读取
```

## 包结构

```
dev.linqibin.starter.openapi
├── autoconfigure/
│   └── OpenApiAutoConfiguration   # 自动配置（创建 OpenAPI bean）
└── config/
    └── OpenApiProperties          # 配置属性（linqibin.starter.openapi.*）
```

## 设计原则

1. **零配置启用**：添加依赖即可使用，标题自动从应用名推导
2. **完全可覆盖**：用户自定义 `OpenAPI` bean 时不会冲突
3. **编译期提取**：Javadoc 在编译期提取，运行时零反射开销
4. **Convention over Configuration**：注解处理器由 convention plugin 统一管理
