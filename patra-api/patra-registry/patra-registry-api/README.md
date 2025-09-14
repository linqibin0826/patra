# patra-registry-api · LiteratureProvenanceConfigApiResp 使用说明

本文件面向使用方，说明如何通过 patra-registry-api 获取“文献来源（Provenance）配置”以及响应体 LiteratureProvenanceConfigApiResp 的字段含义。

---

## 1. 访问接口

- Feign 客户端
  - 接口：`com.patra.registry.api.rpc.client.LiteratureProvenanceClient`
  - 继承：`com.patra.registry.api.rpc.contract.LiteratureProvenanceHttpApi`
  - 方法：`LiteratureProvenanceConfigApiResp getConfigByCode(ProvenanceCode provenanceCode)`

- HTTP 契约（由 LiteratureProvenanceHttpApi 定义）
  - 路径前缀：`/_internal/literature-provenances`
  - 方法与路径：`GET /_internal/literature-provenances/{code}/config`
  - 路径变量：`{code}` 为 `ProvenanceCode`（数据源代码枚举）
  - 返回：`LiteratureProvenanceConfigApiResp`

---

## 2. 返回体：LiteratureProvenanceConfigApiResp 字段

- `provenanceId: Long` 数据源在注册表中的唯一 ID
- `provenanceCode: ProvenanceCode` 数据源代码（如 PUBMED 等）
- `timezone: String` 推荐时区（如 `UTC`、`Asia/Shanghai`）
- `retryMax: Integer` 最大重试次数
- `backoffMs: Integer` 重试基准回退时长（毫秒）
- `rateLimitPerSec: Integer` 每秒最大请求数（限流）
- `searchPageSize: Integer` 搜索接口建议页大小
- `fetchBatchSize: Integer` 批量抓取建议批量大小
- `maxSearchIdsPerWindow: Integer` 单时间窗最大查询 ID 数建议
- `overlapDays: Integer` 时间窗重叠天数（防漏数）
- `retryJitter: Double` 重试抖动系数（0~1）
- `enableAccess: Boolean` 是否允许访问该来源
- `dateFieldDefault: String` 默认日期字段（未显式指定时使用）
- `baseUrl: String` 供应商基础 API 地址
- `headers: Map<String,String>` 公共请求头（不下发敏感认证头）

---

## 3. 快速示例

- Feign 方式

```java
import com.patra.common.enums.ProvenanceCode;
import com.patra.registry.api.rpc.client.LiteratureProvenanceClient;
import com.patra.registry.api.rpc.dto.LiteratureProvenanceConfigApiResp;
import org.springframework.beans.factory.annotation.Autowired;

public class Demo {
  @Autowired LiteratureProvenanceClient client;
  public LiteratureProvenanceConfigApiResp load() {
    return client.getConfigByCode(ProvenanceCode.PUBMED);
  }
}
```

- HTTP 方式（示例）

```bash
curl -s \
  http://<registry-host>/_internal/literature-provenances/PUBMED/config
```

---

## 4. 相关接口（同一契约）

- 查询能力（字段级）
  - `GET /_internal/literature-provenances/{code}/query-capabilities`
- API 参数映射
  - `GET /_internal/literature-provenances/{code}/api-param-mappings`
- 查询渲染规则
  - `GET /_internal/literature-provenances/{code}/query-render-rules`
- 规则快照（指定 operation，单次返回聚合快照）
  - `GET /_internal/literature-provenances/{code}/snapshot/{operation}`

---

## 5. 备注

- DTO 定义：`com.patra.registry.api.rpc.dto.LiteratureProvenanceConfigApiResp`
- 契约接口：`com.patra.registry.api.rpc.contract.LiteratureProvenanceHttpApi`
- Feign 客户端：`com.patra.registry.api.rpc.client.LiteratureProvenanceClient`
- 敏感认证信息不会通过 `headers` 字段直出，请在实际调用时由调用方注入。

