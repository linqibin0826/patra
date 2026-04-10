# LetPub 期刊封面图抓取与对象存储设计

- **日期**：2026-04-11
- **服务**：patra-catalog
- **作者**：Qibin Lin
- **状态**：Design approved，待实现

---

## 1. 背景与目标

patra-catalog 通过 LetPub 爬虫补全期刊元数据。此前 `VenueAggregate` 保留了一个 `imageUrl` 占位字段（Wikidata 封面枚举被移除后留下），本次任务是完成该字段对应的实际能力：**在 LetPub 详情页抓取封面图片 URL，下载后存入对象存储，数据库只保留对象键**。

### 1.1 目标

1. LetPub 详情页解析阶段识别封面图元素，提取其 `src`（LetPub 的 Aliyun OSS CDN URL）
2. 在 Spring Batch 的 `LetPubVenueItemProcessor` 内联下载并上传到自有对象存储（MinIO/S3）
3. 数据库 `venue.image_object_key` 存储对象键（相对桶路径），而非外链 URL
4. 封面下载失败不阻断主流程（降级为 WARN 日志，跳过该期刊的封面更新）
5. 重跑时只针对 `image_object_key IS NULL` 的记录下载，已有封面不覆盖

### 1.2 非目标

- **不做**前端渲染策略：Controller 代理 / 预签名 URL / 公网 URL 的讨论推迟到渲染需求出现时
- **不做**多尺寸缩略图生成
- **不做**封面图内容的去重（hash 比较）
- **不做**LetPub 反爬兜底：当前 LetPub 爬虫已有反爬策略，封面下载复用 `FileDownloadPort` 即可

---

## 2. 核心设计决策

| 编号 | 决策点 | 选择 | 理由 |
|------|--------|------|------|
| D1 | 存储策略 | 下载到自有对象存储 | 绿地项目，CDN 外链不稳定，自有存储可控 |
| D2 | 下载时机 | Processor 内联 + try-catch 隔离 | 简化链路，避免异步事件复杂度 |
| D3 | 重跑语义 | 仅当 `image_object_key IS NULL` | 幂等，避免重复下载/覆盖 |
| D4 | 选择器 | `img[src*=/cover/journal/]`（内容选择器） | 绑定路径特征而非位置，布局变化容忍度高 |
| D5 | 异常体系 | 复用 `FileDownloadException` + `StandardErrorTrait` | 遵守项目异常规范，无需新增类 |
| D6 | Port 分层 | 新建 `VenueCoverImageDownloadPort` 组合 `FileDownloadPort` + `ObjectStorageOperations` | 隐藏组合细节，向 Processor 暴露单一能力 |
| D7 | 桶配置键 | `patra.catalog.object-storage.buckets.venue-cover`（默认桶名 `patra-catalog`） | 通过键名区分用途，为未来多桶扩展留空间 |
| D8 | 对象键格式 | `catalog/venue-cover/{venueId}.jpg`（桶内前缀） | 稳定键，支持覆盖重写，避免垃圾对象 |
| D9 | 数据库列 | `image_object_key VARCHAR(512)` | 从 `image_url VARCHAR(2048)` 重命名 + 缩短 |
| D10 | Flyway | 直接修改 V1.0.0 | 绿地项目规范，不建 V1.0.1 |

---

## 3. 数据模型变更

### 3.1 数据库（Flyway V1.0.0）

**修改位置**：`patra-catalog-infra/src/main/resources/db/migration/V1.0.0__create_venue_aggregate.sql`

```sql
-- 原：
`image_url` VARCHAR(2048) NULL DEFAULT NULL COMMENT '封面图片 URL（来自 LetPub 期刊详情页）',

-- 改为：
`image_object_key` VARCHAR(512) NULL DEFAULT NULL COMMENT '封面图片对象存储键（相对于 venue-cover 桶）',
```

### 3.2 领域模型 `VenueAggregate`

**字段与方法重命名**：

| 原 | 新 |
|----|----|
| `private String imageUrl` | `private String imageObjectKey` |
| `enrichImageUrl(String imageUrl)` | `enrichImageObjectKey(String imageObjectKey)` |
| `restore(..., String imageUrl, ...)` | `restore(..., String imageObjectKey, ...)` |

**语义保持不变**：null-safe 幂等 —— 传 null 表示"不更新，不清空"。

### 3.3 JPA 实体 `VenueEntity`

```java
// 原
@Column(name = "image_url", length = 2048)
private String imageUrl;

// 新
@Column(name = "image_object_key", length = 512)
private String imageObjectKey;
```

### 3.4 LetPub 契约 `LetPubVenueData`

**新增字段**：`String coverImageSourceUrl` —— 承载从 LetPub 详情页解析出的 Aliyun OSS 绝对 URL。

### 3.5 读模型与 API 契约

| 文件 | 变更 |
|------|------|
| `VenueSummaryReadModel` | 字段 `imageUrl` → `imageObjectKey` |
| `VenueReadModelMapper` | 映射字段名同步 |
| `VenueItemResponse` | API 响应字段 `imageUrl` → `imageObjectKey` |
| `VenueJpaMapper` (MapStruct) | 自动名称匹配，生成代码自动同步 |

> 契约破坏性变更：API 客户端需要同步更新字段名。绿地期直接重写，不考虑兼容层。

---

## 4. Port 与适配器设计

### 4.1 Port 接口

**新建**：`patra-catalog-domain/src/main/java/com/patra/catalog/domain/port/storage/VenueCoverImageDownloadPort.java`

```java
package com.patra.catalog.domain.port.storage;

import com.patra.catalog.domain.exception.FileDownloadException;
import java.net.URI;

/// 期刊封面图下载与对象存储端口
///
/// 职责：将外部 URL 的封面图下载后上传至对象存储，返回对象键。
/// 实现方在 Infra 层通过组合 `FileDownloadPort` 与 `ObjectStorageOperations` 完成。
public interface VenueCoverImageDownloadPort {

  /// 下载远端封面图并上传到对象存储
  ///
  /// @param sourceUrl LetPub 提供的封面图原始 URL（Aliyun OSS CDN）
  /// @param targetObjectKey 目标对象键（调用方决定，保证稳定与唯一）
  /// @return 实际写入的对象键（通常等于 `targetObjectKey`）
  /// @throws FileDownloadException 下载失败、大小越界或上传失败时抛出
  String downloadAndStore(URI sourceUrl, String targetObjectKey);
}
```

### 4.2 Adapter 实现

**新建**：`patra-catalog-infra/src/main/java/com/patra/catalog/infra/adapter/storage/VenueCoverImageDownloadAdapter.java`

```java
@Slf4j
@Component
@RequiredArgsConstructor
public class VenueCoverImageDownloadAdapter implements VenueCoverImageDownloadPort {

  private static final long MAX_COVER_BYTES = 16L * 1024 * 1024; // 16 MiB
  private static final String COVER_CONTENT_TYPE = "image/jpeg";

  private final FileDownloadPort fileDownloadPort;
  private final ObjectStorageOperations objectStorage;
  private final VenueCoverImageProperties properties;

  @Override
  public String downloadAndStore(URI sourceUrl, String targetObjectKey) {
    FileDownloadResult downloadResult = null;
    try {
      downloadResult = fileDownloadPort.download(sourceUrl);
      if (downloadResult.fileSize() <= 0) {
        throw new FileDownloadException(
            "封面响应为空: " + sourceUrl, StandardErrorTrait.DEP_UNAVAILABLE);
      }
      if (downloadResult.fileSize() > MAX_COVER_BYTES) {
        throw new FileDownloadException(
            "封面大小超限: " + downloadResult.fileSize() + " bytes (limit=" + MAX_COVER_BYTES + ")",
            StandardErrorTrait.RULE_VIOLATION);
      }
      try (InputStream stream = Files.newInputStream(downloadResult.filePath())) {
        ObjectMetadata metadata = ObjectMetadata.builder()
            .contentType(COVER_CONTENT_TYPE)
            .contentLength(downloadResult.fileSize())
            .build();
        objectStorage.upload(properties.venueCover(), targetObjectKey, stream, metadata);
      }
      log.info("封面上传成功: venueKey={} size={} source={}",
          targetObjectKey, downloadResult.fileSize(), sourceUrl);
      return targetObjectKey;
    } catch (FileDownloadException e) {
      throw e;
    } catch (IOException e) {
      throw new FileDownloadException(
          "读取临时封面文件失败: " + (downloadResult == null ? "null" : downloadResult.filePath()),
          e, StandardErrorTrait.DEP_UNAVAILABLE);
    } catch (RuntimeException e) {
      throw new FileDownloadException(
          "上传封面到对象存储失败: " + targetObjectKey, e, StandardErrorTrait.DEP_UNAVAILABLE);
    } finally {
      if (downloadResult != null) {
        try {
          Files.deleteIfExists(downloadResult.filePath());
        } catch (IOException cleanup) {
          log.warn("删除临时封面文件失败: {}", downloadResult.filePath(), cleanup);
        }
      }
    }
  }
}
```

### 4.3 配置属性

**新建**：`VenueCoverImageProperties`

```java
@ConfigurationProperties("patra.catalog.object-storage.buckets")
public record VenueCoverImageProperties(
    @DefaultValue("patra-catalog") String venueCover
) {}
```

**注册**：在 `patra-catalog-boot` 的 `@ConfigurationPropertiesScan` 或对应 `@EnableConfigurationProperties` 处暴露。

### 4.4 依赖关系图

```
LetPubVenueItemProcessor (infra/batch)
        │
        │ depends on
        ▼
VenueCoverImageDownloadPort (domain/port/storage)
        │
        │ implemented by
        ▼
VenueCoverImageDownloadAdapter (infra/adapter/storage)
        │
        ├──> FileDownloadPort (已存在)
        └──> ObjectStorageOperations (starter-object-storage)
```

---

## 5. 解析器与 Fixture 更新

### 5.1 `LetPubDetailPageParser` 新增方法

```java
private static final String COVER_IMG_SELECTOR = "img[src*=/cover/journal/]";

private void parseCoverImageUrl(Document doc, LetPubVenueData.LetPubVenueDataBuilder builder) {
  Element img = doc.selectFirst(COVER_IMG_SELECTOR);
  if (img == null) {
    return;
  }
  String src = img.attr("src");
  if (src.isBlank()) {
    return;
  }
  builder.coverImageSourceUrl(src);
}
```

**调用时机**：在 `parse()` 内部，`parseJournalName(doc, builder)` 之后、`parseBasicInfo(fieldMap, builder)` 之前。

### 5.2 Fixture 替换

**文件**：`patra-catalog-infra/src/test/resources/letpub/detail-page.html`

原 fixture 使用了虚构的 `<TR>/<TD>` 结构 + 相对路径 src。**已通过 WebFetch 核实真实 LetPub 页面结构**：封面图包在 `<div class="layui-form-item">` 内，src 是绝对 Aliyun OSS URL，路径形如 `/statics/images/comment_center/cover/journal/{journalId}.jpg`。

**替换为**：

```html
<!-- 期刊封面图（真实 LetPub 页面使用 layui 框架，封面图独立于基本信息表格） -->
<div class="layui-form-item">
  <img src="https://media-cdn.oss-cn-hangzhou.aliyuncs.com/statics/images/comment_center/cover/journal/6054.jpg?ver=1775839295" title="NATURE">
</div>
```

---

## 6. Processor 编排变更

### 6.1 `LetPubVenueItemProcessor` 注入新 Port

```java
private final VenueCoverImageDownloadPort coverImageDownloadPort;
```

### 6.2 新增 `downloadCoverIfNeeded` 方法

```java
private void downloadCoverIfNeeded(VenueAggregate aggregate, LetPubVenueData data) {
  // 幂等：已有 object_key 不覆盖
  if (aggregate.getImageObjectKey() != null) {
    return;
  }
  // 数据缺失：LetPub 未返回封面 URL
  if (data.coverImageSourceUrl() == null) {
    return;
  }
  String stableKey = "catalog/venue-cover/" + aggregate.getId().value() + ".jpg";
  try {
    URI sourceUri = URI.create(data.coverImageSourceUrl());
    String key = coverImageDownloadPort.downloadAndStore(sourceUri, stableKey);
    aggregate.enrichImageObjectKey(key);
  } catch (FileDownloadException e) {
    log.warn("venue 封面下载失败（主流程继续）: venueId={} sourceUrl={} trait={} reason={}",
        aggregate.getId().value(), data.coverImageSourceUrl(),
        e.getErrorTraits(), e.getMessage());
  } catch (IllegalArgumentException e) {
    log.warn("venue 封面 URL 格式非法: venueId={} sourceUrl={}",
        aggregate.getId().value(), data.coverImageSourceUrl());
  }
}
```

**调用位置**：在其他 `enrichXxx` 调用之后、`return aggregate` 之前。

---

## 7. 测试策略

### 7.1 测试层级

| 层级 | 测试类 | 位置 | 验证目标 |
|------|--------|------|----------|
| 单元 | `LetPubDetailPageParserTest$CoverImageTests` | infra/test | DOM 选择器正确性（4 用例） |
| 单元 | `VenueCoverImageDownloadAdapterTest` | infra/test | Port 组合逻辑、异常转译、清理（5 用例） |
| 单元 | `LetPubVenueItemProcessorTest` | infra/test | 封面失败隔离、幂等（3 用例） |
| 单元 | `VenueAggregateTest` | domain/test | `enrichImageObjectKey` null-safe（2 用例） |
| 集成 | `VenueRepositoryAdapterIT` | infra/test | 列名持久化与查询 |
| 集成 | `VenueControllerIT` | adapter/test | API 字段契约 |

**不做**：`VenueCoverImageDownloadAdapter` 的 MinIO + HTTP 真实集成测试（下游 starter 已覆盖）；LetPub 真实外网 E2E。

### 7.2 新增测试用例

**`LetPubDetailPageParserTest$CoverImageTests`**
1. `shouldExtractCoverImageUrlFromLayuiFormItem`
2. `shouldReturnNullWhenNoCoverImageElement`
3. `shouldReturnNullWhenSrcAttributeIsBlank`
4. `shouldIgnoreUnrelatedImagesWithoutCoverJournalPath`

**`VenueCoverImageDownloadAdapterTest`**（纯 Mockito）
1. `shouldDownloadAndUploadThenReturnObjectKey`
2. `shouldThrowWhenDownloadedFileIsEmpty`（trait = DEP_UNAVAILABLE）
3. `shouldThrowWhenDownloadedFileExceedsMaxBytes`（trait = RULE_VIOLATION）
4. `shouldWrapIOExceptionAsFileDownloadException`
5. `shouldDeleteTempFileEvenWhenUploadFails`（finally 清理断言）

**`LetPubVenueItemProcessorTest`**（补充）
1. `shouldSkipCoverDownloadWhenImageObjectKeyAlreadyExists`
2. `shouldContinueProcessingWhenCoverDownloadFails`
3. `shouldContinueProcessingWhenCoverUrlIsMalformed`

**`VenueAggregateTest`**（补充）
1. `enrichImageObjectKey_shouldUpdateWhenPreviousIsNull`
2. `enrichImageObjectKey_shouldIgnoreWhenInputIsNull`

### 7.3 既有测试同步修改

| 文件 | 修改 |
|------|------|
| `VenueSummaryReadModelTest` | 构造参数字段重命名 |
| `VenueControllerIT` | JsonPath `$.imageUrl` → `$.imageObjectKey` |
| `VenueRepositoryAdapterIT` | 列名断言同步 |
| `LetPubDetailPageParserTest`（既有 fixture 用例） | Fixture 替换后回归验证 |

### 7.4 TDD 执行顺序（10 个循环）

严格 Red → Green → Refactor，每个循环一个最小测试：

1. **领域幂等**：`VenueAggregateTest.enrichImageObjectKey_shouldIgnoreWhenInputIsNull` → 重命名字段与方法
2. **持久化层**：`VenueRepositoryAdapterIT` 列断言 → Flyway + Entity + JpaMapper
3. **读模型契约**：`VenueControllerIT` → ReadModel + Mapper + Response
4. **Parser happy path**：`CoverImageTests.shouldExtractCoverImageUrlFromLayuiFormItem` → Fixture 替换 + `parseCoverImageUrl`
5. **Parser 边界**：剩余 3 个 null/blank/无关图片用例
6. **Adapter 核心**：`shouldDownloadAndUploadThenReturnObjectKey` → 创建 Port + Adapter + Properties
7. **Adapter 异常与清理**：剩余 4 个 adapter 用例
8. **Processor 隔离**：`shouldContinueProcessingWhenCoverDownloadFails` → `downloadCoverIfNeeded` 方法
9. **Processor 幂等**：剩余 2 个 processor 用例
10. **`LetPubVenueData` 契约**：字段存在性断言（大多在循环 4 已自然通过）

---

## 8. 可观测性

### 8.1 日志策略

| 位置 | 级别 | 内容 |
|------|------|------|
| `VenueCoverImageDownloadAdapter` 成功 | INFO | `venueKey + size + source` |
| `VenueCoverImageDownloadAdapter` finally 清理失败 | WARN | `tempPath + 异常` |
| `LetPubVenueItemProcessor` 捕获 `FileDownloadException` | WARN | `venueId + sourceUrl + errorTraits + message` |
| `LetPubVenueItemProcessor` 捕获 `IllegalArgumentException` | WARN | `venueId + sourceUrl` |
| `LetPubDetailPageParser.parseCoverImageUrl` | 不打日志 | 解析器保持静默 |

### 8.2 Metrics

**不新增**。Spring Batch `jobExecution/stepExecution` 指标已覆盖整体成功率；封面失败被设计为"可容忍跳过"，不值得单独打点。

### 8.3 Trace

复用 OTel Agent 自动追踪。`FileDownloadPort` 与 `ObjectStorageOperations` 的 span 由下游 starter 提供。遵守 `rules/tech/observability.md`：不直接使用 OpenTelemetry SDK，不手动管理 span。

---

## 9. 风险与假设

| 风险 | 影响 | 缓解 |
|------|------|------|
| LetPub 页面结构变更 | Parser 选中元素失败 | 单元测试 + 真实 fixture 每次修改校验；失败降级为 WARN 不阻断 |
| 原始 URL 失效（CDN 清理） | 下载 4xx/5xx | `FileDownloadException` 捕获后 WARN 跳过，下次重跑自动重试 |
| 对象存储不可用 | 所有封面上传失败 | 批次级影响，降级为 WARN，不影响其他字段更新 |
| 文件体积超限（> 16 MiB） | 单条失败 | RULE_VIOLATION 跳过该记录，历史上期刊封面均 < 100 KiB，阈值有足够余量 |
| ISSN-L / journalId 变更导致键冲突 | 对象键覆盖历史数据 | 键基于 `venueId`（我们自有稳定 ID），不依赖 LetPub journalId |

### 9.1 假设

- LetPub 封面图总是 JPEG（真实页面验证如此，Content-Type 固定为 `image/jpeg`）
- `FileDownloadPort.download` 返回的临时文件由调用方负责清理（已由 try-finally 保证）
- 对象存储桶 `patra-catalog` 在部署前已存在（由 `patra-infra` 管理）

---

## 10. 不做的事（Out of Scope）

- 前端渲染策略（Controller 代理、公网 URL、预签名）—— 有渲染需求时再决策
- 封面多尺寸缩略图
- 封面内容 hash 去重
- LetPub 抓取反爬（复用现有能力）
- 封面版本管理与历史回溯

---

## 11. 总结

本设计在**不引入新异常类、不新建 RestClient、不新增 Metric** 的前提下，通过组合既有能力（`FileDownloadPort` + `ObjectStorageOperations`）完成 LetPub 封面图抓取到对象存储的闭环，并将数据库字段从 URL 外链切换为对象键。关键约束：

1. **幂等**：只下载 `image_object_key IS NULL` 的记录
2. **隔离**：封面失败不阻断批次主流程
3. **稳定键**：`catalog/venue-cover/{venueId}.jpg`，重跑覆盖
4. **绿地**：直接修改 V1.0.0，重命名所有相关字段，不保留兼容层

下一步：通过 `superpowers:writing-plans` 生成 10 循环 TDD 实施计划。
