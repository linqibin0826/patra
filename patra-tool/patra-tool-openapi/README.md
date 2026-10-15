# patra-tool-openapi

## 能力

给 Servlet 服务提供 API 文档的基础信息。自动配置 `OpenApiAutoConfiguration` 注册 springdoc 用的 `OpenAPI` bean：标题优先取 `patra.openapi.title`，其次取 `spring.application.name`，都没有时退到 `"Patra API"`；服务自己声明了 `OpenAPI` bean 时让位。

## 配置（前缀 `patra.openapi`）

| 键 | 类型 | 默认值 | 说明 |
|---|---|---|---|
| `enabled` | boolean | `true` | 总开关 |
| `title` | String | 无 | 文档标题；空则用 `spring.application.name` |
| `version` | String | `0.1.0` | 文档版本 |
| `description` | String | 无 | 文档描述 |

## 怎么覆盖

```yaml
patra:
  openapi:
    title: Patra 目录服务
    description: 文献与期刊目录接口
```

## 出哪些默认值

无。
