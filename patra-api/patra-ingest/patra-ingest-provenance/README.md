# patra-ingest-provenance

## 能力

ingest 对外部数据源的客户端与处理器：PubMed（ESearch / EFetch / EPost）、Europe PMC、Crossref 的请求组装、宽松 XML 解析、
`CanonicalPublication` 规范化模型与各数据源的转换器；`api/` 包放这些数据源的参数名、操作常量与枚举。
自动配置 `ProvenanceAutoConfiguration` 注册 `pubMedClient`、`epmcClient`、XML 解析定制器与各数据源的处理器，
`ProvenanceCodeConverterAutoConfiguration` 注册 `ProvenanceCode` 的 MVC 转换器。HTTP 客户端用 patra-tool-http-client 出的
`defaultRestClient`（运行期依赖，编译期不引用）。

## 配置（前缀 `patra.ingest.provenance`）

配置类 `ProvenanceProperties`（可变类，不是 record）：`defaults` 给所有数据源的默认值，`sources.<code>` 按数据源覆盖，
运行期再与 registry 下发的配置合并。每个数据源可配的段：`http.*`（默认请求头、连接 / 读 / 总超时毫秒数）、
`pagination.*`、`window-offset.*`、`batching.*`、`retry.*`、`rate-limit.*`，字段与默认值以
`ProvenanceProperties.SourceProperties` 为准。

| 键 | 类型 | 默认值 | 说明 |
|---|---|---|---|
| `enabled` | boolean | `true` | 总开关 |
| `defaults.*` | SourceProperties | 见 `SourceProperties.defaults()` | 所有数据源共用的默认值 |
| `sources.<code>.*` | SourceProperties | 无 | 按数据源代码（`pubmed`、`epmc`、`crossref`…）覆盖 |

## 怎么覆盖

```yaml
patra:
  ingest:
    provenance:
      sources:
        pubmed:
          http:
            timeout-read-millis: 30000
```

## 出哪些默认值

无。
