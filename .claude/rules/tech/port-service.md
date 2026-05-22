# Port 与 Service 命名规范

## 命名规范表

| 类型 | 定义层 | 实现层 | 接口命名 | 实现命名 |
|------|--------|--------|----------|----------|
| Repository | Domain | Infra | `{Entity}Repository` | `{Entity}RepositoryAdapter` |
| Driven Port | Domain | Infra | `{Function}Port` | `{Function}Adapter` |
| LookupPort | Domain | Infra | `{Entity}LookupPort` | `Default{Entity}LookupAdapter` + `Caching{Entity}LookupDecorator` + `Batch{Entity}LookupAdapter` |
| ReadPort | Domain | Infra | `{Entity}ReadPort` | `{Entity}ReadAdapter` |
| Gateway | Domain | App | `{Entity}Gateway` | `{Entity}GatewayImpl` |
| QueryService | — | App | 无接口 | `{Domain}QueryService` |

## 选择指南

```
需要定义的接口是什么类型？
│
├── 聚合根持久化 ─────────→ Repository（Domain → Infra）
├── 外部能力（解析/下载等）→ {Function}Port（Domain → Infra）
├── 实体 ID 查找（带缓存）→ {Entity}LookupPort（Domain → Infra，装饰器模式）
├── CQRS 读端查询 ────────→ {Entity}ReadPort（Domain → Infra）
├── 应用层服务供其他层调用 → {Entity}Gateway（Domain → App）
└── 纯查询服务 ────────────→ {Domain}QueryService（App 层直接实现）
```

## 包位置速查

| 类型 | 接口位置 | 实现位置 |
|------|----------|----------|
| Repository | `domain/port/repository/` | `infra/adapter/persistence/` |
| Driven Port | `domain/port/{function}/` | `infra/adapter/{function}/` |
| LookupPort | `domain/port/lookup/` | `infra/adapter/lookup/` |
| ReadPort | `domain/port/read/` | `infra/adapter/read/` |
| Gateway | `domain/port/gateway/` | `app/usecase/{domain}/service/` |
| QueryService | — | `app/usecase/{domain}/query/` |

## 禁止行为

1. 禁止 Repository 使用 `Port` 后缀（如 ~~`PublicationPort`~~）
2. 禁止驱动端口使用 `Port` 后缀（应使用 `Gateway`）
3. 禁止 QueryService 定义接口（CQRS 读端不需要抽象）
4. 禁止在 Domain 层定义接口但在 Domain 层实现

> 详细说明、依赖方向图、注入方式和目录结构示例见 `patra-backend:patra-hexagonal` skill 内的 `resources/port-service-guide.md`
