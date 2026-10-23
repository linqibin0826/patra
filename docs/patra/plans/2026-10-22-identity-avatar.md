# identity 头像 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task (patra 约定：计划在当前会话内联执行，不派实现者子代理). Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 让 identity 收头像上传、服务端重出 512 / 128 两档 WebP 存进 MinIO，按不可变缓存流式下发，并在 `/auth/me` 与公开主页里给出 `avatarUrl` 与 `avatarPalette`（PAP-97）。

**Architecture:** 领域层定 `Avatar` 值对象、色号算法和两个端口（出图、存储）；infra 用 TwelveMonkeys（纯 Java）解码、`webp-imageio`（JNI libwebp）编码，用 `patra-tool-object-storage` 写专用桶；app 层的上传处理器把图片处理与对象写入放在事务外，数据库部分走 `TransactionOperations`；adapter 层出三个接口与 URL。对象存储库补删除与按前缀删除，`patra-tool-web` 补 413 映射。

**Tech Stack:** Java 25、Spring Boot 4.0.8、TwelveMonkeys 3.15.2、`com.github.usefulness:webp-imageio` 0.10.0、MinIO SDK 8.6.0、PostgreSQL 17、Testcontainers（`pgsty/minio`）、JUnit 5 + AssertJ + Mockito。

**Spec:** `docs/patra/specs/2026-10-22-identity-avatar-design.md`（下称「spec」，章节号指它）。

## Global Constraints

- 分支 `feat/v0.12-user-center-api`，在主检出上做，不切分支、不建 worktree、不推送、不开 PR、不动 main。
- 每次提交前 `git diff --cached --stat` 核对只含本任务的路径；不 `git add -A`；release spec、设计简报目录、设计快照 zip 不是本 Issue 的，永不提交。
- 提交信息：`type(scope): 中文动词开头的描述 (PAP-97)`，scope 用 `identity` / `tool` / `gateway` / `infra`；body 每行 ≤ 100 字符；结尾 `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`。spotless 改了文件就 `git add -u` 后重提。
- 强制 TDD：每个任务先写失败测试、看它失败，再写实现。
- 代码规范：所有方法 `///` Markdown JavaDoc；不用全类名；≤ 4 参数的 record 用 `of()`，≥ 5 用 `@Builder`；record 集合字段紧凑构造器 `List.copyOf`；`byte[]` 字段在构造器与访问器里 `clone()`（SpotBugs `EI_EXPOSE_REP`）。
- 测试方法 snake_case；单元测试在 `src/test`，集成测试在 `src/integrationTest` 名 `*IT`；时间用注入的 `Clock`。
- 改 `*.gradle.kts` 后提醒用户在 IDEA 里点「加载 Gradle 更改」；新建 `src/integrationTest` 目录或 boot 新依赖后跑 `./gradlew dumpModuleGraph --no-configuration-cache` 并提交 `patra-infra/cd/module-graph.json`。
- 跑 Testcontainers 前确认 `docker info` 通（OrbStack 停着就 `open -a OrbStack`）。
- spec 定死的值：桶 `patra-identity-avatar`；键 `avatar/{userId}/{hash}-{512|128}.webp`；`hash` = 512 档字节 SHA-256 前 16 位小写十六进制；短边 ≥ 128；像素 ≤ 50,000,000；WebP 有损 q=0.82；`Cache-Control: public, max-age=31536000, immutable`；ETag `"{hash}-{px}"`；`avatarUrl` = `/users/{username}/avatar?v={hash}`；multipart `max-file-size: 5MB`、`max-request-size: 6MB`、Tomcat `max-swallow-size: 8MB`；413 码 `IDN-0413`；原因码 `REQUIRED` / `UNSUPPORTED_TYPE` / `TOO_SMALL` / `TOO_MANY_PIXELS` / `UNREADABLE`，字段 `file`。

## 开工前检查（每次恢复执行都做）

1. `git status --short`：除 spec、plan 与不属于本 Issue 的未跟踪文档（`docs/patra/release-specs/v0.12-user-center.md`、`patra-portal/docs/design-briefs/v0.12/`、`docs/patra/design/snapshots/2026-10-22-user-center-hifi.zip`）外没有已修改或暂存的文件。
2. `git log -3 --format='%h %cr %s'`：最近一次提交不是几分钟内的 PAP-100 提交；共享笔记目录里有 `pap-100-account-deletion-implementation-handover.md`（PAP-100 收工标志）。
3. 不满足就停下问用户，不开工。

## Review Focus

1. **同一张图重复上传**：用户点两次「使用这张头像」。期望 200 且地址不变、对象还在（不能被当作「旧头像」删掉）。→ Task 10 `should_short_circuit_when_same_image_uploaded_again` + Task 13 流程 IT 里重复上传一次。
2. **冷静期中取消注销与到期任务交错**：扫描之后取消的用户，头像对象不能被删。→ Task 14 `should_not_delete_avatar_objects_when_user_cancelled_after_scan`。
3. **带透明通道的 PNG**（Logo 类头像）：期望输出保留透明，不变成黑底。→ Task 8 `should_keep_alpha_for_transparent_png`。
4. **超过 5 MB 的上传经真实 HTTP**：期望客户端拿到 413 而不是连接被重置。→ Task 13 `should_reject_upload_over_5mb_with_413`（真实端口、真实 Tomcat）。
5. **旧 `v` 地址**：换头像后浏览器还拿着旧地址。期望 404，而不是把新图按旧地址缓存。→ Task 11 `should_404_when_version_is_stale` + Task 13 流程 IT。

---

### Task 1: 对象存储库补 `delete` 与 `deleteByPrefix`

**Files:**
- Modify: `patra-tool/patra-tool-object-storage/src/main/java/dev/patra/tool/objectstorage/ObjectStorageOperations.java`、`provider/ObjectStorageProvider.java`、`ObjectStorageTemplate.java`、`provider/MinioStorageProvider.java`、`provider/S3StorageProvider.java`
- Test: `src/integrationTest/java/dev/patra/tool/objectstorage/provider/MinioStorageProviderIT.java`（扩展）、`src/test/java/dev/patra/tool/objectstorage/provider/S3StorageProviderTest.java`（新建）、`src/test/java/dev/patra/tool/objectstorage/ObjectStorageTemplateTest.java`（扩展）

**Interfaces:**
- Produces（`ObjectStorageOperations` 与 `ObjectStorageProvider` 同签名）：
  - `void delete(String bucket, String key)`：对象不存在不抛。
  - `int deleteByPrefix(String bucket, String prefix)`：返回删除个数；桶不存在返回 0；`prefix` 为 `null` 或空串抛 `IllegalArgumentException`。

- [ ] **Step 1: 写失败测试（MinIO IT）**

`MinioStorageProviderIT` 加：
- `should_delete_object_and_ignore_missing_key`：上传 `a/1` → `delete` → `statObject` 为空；再 `delete` 一次不抛。
- `should_delete_only_objects_under_prefix`：上传 `avatar/1/x`、`avatar/1/y`、`avatar/10/z`、`avatar/2/w` → `deleteByPrefix(bucket, "avatar/1/")` 返回 2，`avatar/10/z` 与 `avatar/2/w` 还在。
- `should_delete_more_than_one_batch`：上传 1001 个 `p/{i}`（每个 1 字节）→ 返回 1001，前缀下列不出对象。
- `should_return_zero_when_bucket_missing`：对不存在的桶 `deleteByPrefix` 返回 0。
- `should_reject_blank_prefix`：`""` → `IllegalArgumentException`。

- [ ] **Step 2: 跑测试确认失败**

Run: `./gradlew :patra-tool:patra-tool-object-storage:integrationTest --tests "*MinioStorageProviderIT*"`
Expected: 编译失败（`delete` / `deleteByPrefix` 未定义）。

- [ ] **Step 3: 实现**

- MinIO：`delete` 调 `removeObject`，`ErrorResponseException` 的 `NoSuchKey` 静默；`deleteByPrefix` 用 `listObjects(ListObjectsArgs.builder().bucket(b).prefix(p).recursive(true))` 收集键，按 ≤ 1000 一批 `removeObjects`，**必须遍历返回的 `Iterable<Result<DeleteError>>`**（MinIO SDK 惰性执行，不遍历就不删），有错误汇总后抛 `ObjectStorageException` 类的现有运行时异常；列举时 `NoSuchBucket` 返回 0。
- S3：`deleteObject`；`listObjectsV2Paginator` + `deleteObjects`（每批 ≤ 1000，`Delete.objects(...)`），`NoSuchBucketException` 返回 0。
- 前缀校验放在 `AbstractObjectStorageProvider`（新 `protected void validatePrefix(String bucket, String prefix)`）。
- `ObjectStorageTemplate`：两个方法照 `statObject` 的写法包 `retryTemplate`，失败记 `metrics.recordDownloadFailure` 不新增指标类型。

- [ ] **Step 4: S3 与模板的单元测试**

- `S3StorageProviderTest`：mock `S3Client`，`deleteByPrefix` 在分页返回 1500 个键时调两次 `deleteObjects`（1000 + 500），返回 1500；`listObjectsV2Paginator` 抛 `NoSuchBucketException` 时返回 0。
- `ObjectStorageTemplateTest`：`delete` 委托 provider；provider 第一次抛可重试异常时重试（照现有用例的写法）。

- [ ] **Step 5: 跑测试确认通过**

Run: `./gradlew :patra-tool:patra-tool-object-storage:test :patra-tool:patra-tool-object-storage:integrationTest`
Expected: PASS

- [ ] **Step 6: 提交**

```bash
git add patra-tool/patra-tool-object-storage
git commit -m "feat(tool): 对象存储加删除与按前缀删除 (PAP-97)"
```

---

### Task 2: `patra-tool-web` 把超大上传映射成 413

**Files:**
- Modify: `patra-tool/patra-tool-core/src/main/java/dev/patra/tool/core/error/codes/HttpStdErrors.java`、`patra-tool/patra-tool-web/src/main/java/dev/patra/tool/web/error/handler/GlobalRestExceptionHandler.java`、`patra-tool-web` 里的 `WebMvcErrorMappingContributor`
- Test: `patra-tool-web/src/test/.../handler/GlobalRestExceptionHandlerTest.java`、`WebMvcErrorMappingContributorTest.java`

**Interfaces:**
- Produces：`HttpStdErrors.Group.CONTENT_TOO_LARGE()` → `code("0413", 413)`；任何服务的 `MaxUploadSizeExceededException` 渲染为 413 ProblemDetail，`code` 为 `{前缀}-0413`。

- [ ] **Step 1: 写失败测试**

- `WebMvcErrorMappingContributorTest.should_map_max_upload_size_exceeded_to_413`：`new MaxUploadSizeExceededException(5_242_880)` → 错误码 `0413`、状态 413。
- `GlobalRestExceptionHandlerTest.should_render_max_upload_size_exceeded_as_problem_detail`：照现有 `NoResourceFoundException` 用例，响应 413、`application/problem+json`、体里 `code` 以 `-0413` 结尾。

- [ ] **Step 2: 跑测试确认失败**

Run: `./gradlew :patra-tool:patra-tool-web:test --tests "*WebMvcErrorMappingContributorTest*" --tests "*GlobalRestExceptionHandlerTest*"`
Expected: FAIL

- [ ] **Step 3: 实现**

`HttpStdErrors.Group` 加 `CONTENT_TOO_LARGE()`；`WebMvcErrorMappingContributor` 认 `MaxUploadSizeExceededException`；`GlobalRestExceptionHandler` 重写 `handleMaxUploadSizeExceededException(MaxUploadSizeExceededException, HttpHeaders, HttpStatusCode, WebRequest)` → `renderThroughAdapter(ex, request)`。

- [ ] **Step 4: 跑测试确认通过**

Run: 同 Step 2，加 `:patra-tool:patra-tool-core:test`
Expected: PASS

- [ ] **Step 5: 提交**

```bash
git add patra-tool/patra-tool-core patra-tool/patra-tool-web
git commit -m "feat(tool): 超过上传上限的请求统一回 413 (PAP-97)"
```

---

### Task 3: 领域值对象 `Avatar`、`AvatarSize` 与色号 `AvatarPalette`

**Files:**
- Create: `patra-api/patra-identity/patra-identity-domain/src/main/java/dev/patra/identity/domain/model/vo/avatar/Avatar.java`、`AvatarSize.java`；`domain/policy/AvatarPalette.java`
- Test: `patra-identity-domain/src/test/java/dev/patra/identity/domain/model/vo/avatar/AvatarTest.java`、`domain/policy/AvatarPaletteTest.java`

**Interfaces:**
- Produces:
  - `enum AvatarSize { LARGE(512), SMALL(128); int pixels(); static Optional<AvatarSize> fromPixels(String raw) }`（`raw` 不是 `"512"` / `"128"` 时为空，`null` 也为空）
  - `record Avatar(long userId, String hash)`：`static Avatar of(long userId, String hash)`（`hash` 不匹配 `^[0-9a-f]{16}$` 抛 `IllegalArgumentException`）；`static Avatar forContent(long userId, byte[] largeWebp)`（SHA-256 前 16 位小写十六进制）；`static Avatar parse(long userId, String largeObjectKey)`；`String objectKey(AvatarSize size)`；`String etag(AvatarSize size)`（带引号）；`static String prefix(long userId)`
  - `final class AvatarPalette { static int of(long userId) }`

- [ ] **Step 1: 写失败测试**

`AvatarTest`：
- `objectKey(LARGE)` 为 `avatar/42/0123456789abcdef-512.webp`，`SMALL` 为 `…-128.webp`；`etag(SMALL)` 为 `"0123456789abcdef-128"`；`prefix(42)` 为 `avatar/42/`。
- `parse(42, "avatar/42/0123456789abcdef-512.webp")` 等于 `of(42, "0123456789abcdef")`；`parse(42, "avatar/43/…-512.webp")`、`-128.webp` 后缀、大写哈希、`avatar/42/x-512.webp` 都抛 `IllegalArgumentException`。
- `forContent(1, "abc".getBytes(UTF_8)).hash()` 为 `ba7816bf8f01cfea`（`abc` 的 SHA-256 前 16 位）。
- `AvatarSize.fromPixels("512")` 为 `LARGE`；`"256"`、`"abc"`、`null` 为空。

`AvatarPaletteTest`：
- 固定向量：`of(0L)`、`of(1L)`、`of(352303128713027974L)`、`of(Long.MAX_VALUE)` 的值先按 spec 9.2 的算法手算（用 `jshell` 跑一次那 5 行），把结果写死在断言里。
- `should_spread_evenly_over_snowflake_ids`：从 `352303128713027974L` 起每次加 `4096L`（雪花序列位）取 120,000 个 ID，每个色号的计数在 `[9500, 10500]`。
- 所有结果在 `[0, 11]`。

- [ ] **Step 2: 跑测试确认失败**

Run: `./gradlew :patra-api:patra-identity:patra-identity-domain:test --tests "*AvatarTest*" --tests "*AvatarPaletteTest*"`
Expected: 编译失败

- [ ] **Step 3: 实现**

`AvatarPalette.of` 的算法照 spec 9.2 原样（SplitMix64 最终混合 + `Long.remainderUnsigned(z, 12)`），类注释写「上线后不可改：改了所有人的默认头像都会变色」。`Avatar.forContent` 用 `MessageDigest.getInstance("SHA-256")` + `HexFormat.of().formatHex` 截 16 位。

- [ ] **Step 4: 跑测试确认通过**

Run: 同 Step 2，再加 `:patra-api:patra-identity:patra-identity-domain:check`（R3 纯净性）
Expected: PASS

- [ ] **Step 5: 提交**

```bash
git add patra-api/patra-identity/patra-identity-domain
git commit -m "feat(identity): 加头像值对象与默认头像色号算法 (PAP-97)"
```

---

### Task 4: 资料聚合带头像，落库

**Files:**
- Modify: `patra-identity-domain/.../aggregate/UserProfile.java`；`patra-identity-infra/src/main/resources/db/migration/V1__init_identity_schema.sql`；`infra/adapter/persistence/entity/UserProfileEntity.java`；`infra/adapter/persistence/converter/mapper/UserProfileJpaMapper.java`
- Test: `patra-identity-domain/src/test/.../aggregate/UserProfileTest.java`；`patra-identity-infra/src/integrationTest/.../persistence/UserProfileRepositoryAdapterIT.java`

**Interfaces:**
- Consumes: Task 3 的 `Avatar`。
- Produces（`UserProfile`）：`Optional<Avatar> getAvatar()`；`Optional<Avatar> changeAvatar(Avatar avatar)`（返回被替换的旧头像；`avatar.userId() != userId` 抛 `IllegalArgumentException`）；`Optional<Avatar> removeAvatar()`；`erase()` 同时清头像；`restore()` 构建器多一个 `avatar(Avatar)`。

- [ ] **Step 1: 写失败测试**

`UserProfileTest`：
- `should_return_previous_avatar_when_changing`：先 `changeAvatar(a1)` 返回空，再 `changeAvatar(a2)` 返回 `a1`，`getAvatar()` 为 `a2`。
- `should_reject_avatar_of_another_user`。
- `should_return_removed_avatar`：有头像时 `removeAvatar()` 返回它，之后 `getAvatar()` 为空；再 `removeAvatar()` 返回空。
- PAP-100 已有的 `erase` 用例里补：有头像时 `erase()` 后 `getAvatar()` 为空。

`UserProfileRepositoryAdapterIT`：
- `should_round_trip_avatar_object_key`：保存带头像的资料，重读后 `getAvatar()` 相等；库里 `avatar_object_key` 为 `avatar/{userId}/{hash}-512.webp`。
- `should_reject_avatar_key_of_another_user_at_database`：`jdbcTemplate.update` 直接写 `avatar/999/0123456789abcdef-512.webp` 到别人的行 → `DataIntegrityViolationException`；写 `avatar/{userId}/XYZ-512.webp` 同样被拒。

- [ ] **Step 2: 跑测试确认失败**

Run: `./gradlew :patra-api:patra-identity:patra-identity-domain:test --tests "*UserProfileTest*"`
Expected: 编译失败

- [ ] **Step 3: 实现领域方法**

- [ ] **Step 4: 改 `V1` 与持久化**

`idn_user_profile` 加列与约束（spec 第 11 节）；约束表达式用：

```sql
CONSTRAINT ck_idn_user_profile_avatar_object_key CHECK (
    avatar_object_key IS NULL
    OR avatar_object_key ~ ('^avatar/' || user_id::text || '/[0-9a-f]{16}-512\.webp$'))
```

加 `COMMENT ON COLUMN idn_user_profile.avatar_object_key IS '512-px avatar object key in bucket patra-identity-avatar; the 128-px key shares the hash; NULL means default avatar'`。实体加 `@Column(name = "avatar_object_key", length = 64) private String avatarObjectKey;`；映射器：领域 → 实体用 `avatar.objectKey(LARGE)`，实体 → 领域用 `Avatar.parse(userId, key)`（`null` 映射为 `null`）。

- [ ] **Step 5: 跑测试确认通过**

Run: `./gradlew :patra-api:patra-identity:patra-identity-domain:test :patra-api:patra-identity:patra-identity-infra:integrationTest --tests "*UserProfileRepositoryAdapterIT*"`
Expected: PASS

- [ ] **Step 6: 提交**

```bash
git add patra-api/patra-identity/patra-identity-domain patra-api/patra-identity/patra-identity-infra
git commit -m "feat(identity): 资料聚合存头像对象键 (PAP-97)"
```

---

### Task 5: 读模型带头像与色号，按用户名查头像归属

**Files:**
- Modify: `domain/model/read/UserAccountReadModel.java`、`PublicUserReadModel.java`、`PublicUserView.java`、`domain/port/read/UserReadPort.java`；`infra/adapter/read/UserReadAdapter.java`
- Create: `domain/model/read/AvatarOwner.java`
- Test: `PublicUserReadModelTest.java`、新建 `domain/src/test/.../read/UserAccountReadModelTest.java`、`infra/src/integrationTest/.../read/UserReadAdapterIT.java`

**Interfaces:**
- Consumes: Task 3、Task 4。
- Produces:
  - `UserAccountReadModel` 加组件 `Avatar avatar`（可空，放在 `displayName` 之后），方法 `int avatarPalette()`。
  - `PublicUserReadModel` 加组件 `Avatar avatar`；`PublicUserView` 加组件 `Avatar avatar`、`int avatarPalette`（`viewFor` 填 `AvatarPalette.of(userId)`）。
  - `record AvatarOwner(long userId, UserStatus status, Avatar avatar)`（`of(...)`，`avatar` 可空）。
  - `UserReadPort.findAvatarOwner(Username username) → Optional<AvatarOwner>`：只按当前用户名，不查旧名。

- [ ] **Step 1: 写失败测试**

- `UserAccountReadModelTest.should_derive_avatar_palette_from_user_id`：`avatarPalette()` 等于 `AvatarPalette.of(userId)`。
- `PublicUserReadModelTest`：`viewFor` 的视图里 `avatar` 原样、`avatarPalette` 等于 `AvatarPalette.of(userId)`；对匿名、本人两种请求者都一样。
- `UserReadAdapterIT`：`findAccount` 与 `findPublicIdentity` 带出头像；`findAvatarOwner` 对当前名返回状态与头像、对没头像的用户 `avatar` 为空、对保留期内的旧名返回空、对不存在的名返回空。

- [ ] **Step 2: 跑测试确认失败**

Run: `./gradlew :patra-api:patra-identity:patra-identity-domain:test --tests "*ReadModelTest*"`
Expected: 编译失败

- [ ] **Step 3: 实现**

`UserReadAdapter`：`toAccount` / `toPublicIdentity` 从资料实体读 `avatarObjectKey` 并 `Avatar.parse`；`findAvatarOwner` 照 `findPublicIdentity` 查用户行（`userDao` 的按用户名查法）再查资料行。修编译：所有 `UserAccountReadModel.builder()` / `PublicUserReadModel.builder()` / `PublicUserView.builder()` 的调用点（含测试夹具）不用改，新组件默认 `null` / `0`。

- [ ] **Step 4: 跑测试确认通过**

Run: `./gradlew :patra-api:patra-identity:patra-identity-domain:test :patra-api:patra-identity:patra-identity-infra:integrationTest --tests "*UserReadAdapterIT*"`
Expected: PASS

- [ ] **Step 5: 提交**

```bash
git add patra-api/patra-identity/patra-identity-domain patra-api/patra-identity/patra-identity-infra
git commit -m "feat(identity): 读模型带头像与默认头像色号 (PAP-97)"
```

---

### Task 6: 出图端口与拒绝原因（领域）

**Files:**
- Create: `domain/model/vo/avatar/AvatarRenditions.java`、`AvatarRejection.java`；`domain/policy/AvatarPolicy.java`；`domain/exception/InvalidAvatarException.java`；`domain/port/avatar/AvatarImagePort.java`
- Test: `domain/src/test/.../exception/InvalidAvatarExceptionTest.java`、`.../vo/avatar/AvatarRenditionsTest.java`

**Interfaces:**
- Produces:
  - `record AvatarRenditions(byte[] large, byte[] small)`：`of(...)`，构造与访问器都 `clone()`，`equals` 按内容（`Arrays.equals`）。
  - `enum AvatarRejection { REQUIRED, UNSUPPORTED_TYPE, TOO_SMALL, TOO_MANY_PIXELS, UNREADABLE }`。
  - `final class AvatarPolicy { MIN_SHORT_SIDE = 128; MAX_PIXELS = 50_000_000L; WEBP_QUALITY = 0.82f }`。
  - `InvalidAvatarException extends DomainException implements HasFieldViolations`：构造参数 `AvatarRejection`，`RULE_VIOLATION`，字段错误 `FieldViolation.of("file", rejection.name(), …)`；`AvatarRejection getRejection()`。
  - `interface AvatarImagePort { AvatarRenditions render(byte[] upload); }`（拒绝时抛 `InvalidAvatarException`）。

- [ ] **Step 1: 写失败测试**

`InvalidAvatarExceptionTest.should_expose_file_field_violation_with_reason_code`：`TOO_SMALL` → 一条 `field = "file"`、`code = "TOO_SMALL"`；traits 含 `RULE_VIOLATION`。`AvatarRenditionsTest`：改动传入数组不影响记录内容、访问器返回副本。

- [ ] **Step 2–4: 失败 → 实现 → 通过**

Run: `./gradlew :patra-api:patra-identity:patra-identity-domain:check`
Expected: 先编译失败，实现后 PASS（含 SpotBugs）

- [ ] **Step 5: 提交**

```bash
git add patra-api/patra-identity/patra-identity-domain
git commit -m "feat(identity): 定义头像出图端口与拒绝原因 (PAP-97)"
```

---

### Task 7: 按魔数识别格式与动图

**Files:**
- Create: `patra-identity-infra/src/main/java/dev/patra/identity/infra/adapter/avatar/AvatarFormat.java`、`AvatarFormatSniffer.java`
- Test: `patra-identity-infra/src/test/java/dev/patra/identity/infra/adapter/avatar/AvatarFormatSnifferTest.java`

**Interfaces:**
- Produces: `enum AvatarFormat { JPEG, PNG, WEBP }`；`final class AvatarFormatSniffer { static AvatarFormat sniff(byte[] bytes) }`，不认识、GIF、APNG、动画 WebP、空或截断都抛 `InvalidAvatarException(UNSUPPORTED_TYPE)`。

- [ ] **Step 1: 写失败测试**（测试里按字节手工拼文件头，不依赖图片库）

- `FF D8 FF E0 …` → `JPEG`；PNG 签名 + `IHDR` + `IDAT` → `PNG`；`RIFF....WEBPVP8 ` → `WEBP`；`RIFF....WEBPVP8X`（标志字节 `0x00`）→ `WEBP`。
- 拒绝（都断言 `getRejection() == UNSUPPORTED_TYPE`）：`GIF89a…`；PNG 在 `IDAT` 前有 `acTL`；`VP8X` 标志字节 `0x02`；`VP8X` 标志 `0x00` 但后面有 `ANIM` 块；`"hello".getBytes()`；空数组；只有 `89 50 4E` 三个字节。

- [ ] **Step 2–4: 失败 → 实现 → 通过**

Run: `./gradlew :patra-api:patra-identity:patra-identity-infra:test --tests "*AvatarFormatSnifferTest*"`

实现要点：PNG 按「4 字节长度 + 4 字节类型 + 数据 + 4 字节 CRC」扫块，遇 `IDAT` 停；WebP 按 RIFF 块（4 字节类型 + 4 字节小端长度 + 数据，奇数长度补 1 字节）扫全部块。只看前 64 KiB 以内的块头，越界即视为截断。

- [ ] **Step 5: 提交**

```bash
git add patra-api/patra-identity/patra-identity-infra
git commit -m "feat(identity): 按魔数识别头像格式并拒收动图 (PAP-97)"
```

---

### Task 8: 出图适配器（解码、裁方、缩放、WebP 编码）

**Files:**
- Modify: `gradle/libs.versions.toml`（加 `webp-imageio = "0.10.0"`；库 `webp-imageio = { module = "com.github.usefulness:webp-imageio", version.ref = "webp-imageio" }`、`twelvemonkeys-imageio-jpeg = { module = "com.twelvemonkeys.imageio:imageio-jpeg", version.ref = "twelvemonkeys" }`）；`patra-identity-infra/build.gradle.kts`（`implementation(libs.twelvemonkeys.imageio.jpeg)`、`implementation(libs.twelvemonkeys.imageio.webp)`、`implementation(libs.webp.imageio)`）
- Create: `infra/adapter/avatar/AvatarImageAdapter.java`
- Test: `infra/src/test/.../avatar/AvatarImageAdapterTest.java`
- Create（测试夹具，Task 13 的 boot IT 也要用）: `patra-identity-infra/src/testFixtures/java/dev/patra/identity/infra/adapter/avatar/AvatarImageFixture.java`（infra 的 `build.gradle.kts` 加 `java-test-fixtures` 插件与 `testFixturesImplementation(libs.webp.imageio)`；boot 加 `testImplementation(testFixtures(project(":patra-api:patra-identity:patra-identity-infra")))`）

**Interfaces:**
- Consumes: Task 6 的端口与类型、Task 7 的 sniffer。
- Produces:
  - `@Component class AvatarImageAdapter implements AvatarImagePort`。
  - `AvatarImageFixture`：`static byte[] jpeg(int w, int h)`、`static byte[] png(int w, int h, boolean alpha)`、`static byte[] webp(int w, int h)`、`static byte[] withExifGps(byte[] jpeg)`（在 SOI 后插 `APP1 "Exif\0\0"` + 含 GPS IFD 的最小 TIFF）、`static byte[] withPngMetadata(byte[] png)`（在 `IDAT` 前插 `iCCP` 与 `eXIf` 块，CRC 用 `java.util.zip.CRC32`）、`static byte[] pngHeaderOnly(int w, int h)`（IHDR 声明尺寸 + 一段 `Deflater` 压的零字节 IDAT）、`static List<String> riffChunks(byte[] webp)`、`static BufferedImage decodeWebp(byte[] webp)`（用 TwelveMonkeys 读取器）。图片内容：左 1/4 纯红、中间纯绿、右 1/4 纯蓝的竖条，便于断言裁方。

- [ ] **Step 1: 写失败测试**

`AvatarImageAdapterTest`：
- `should_render_both_sizes_as_webp_from_jpeg` / `_from_png` / `_from_webp`：输出两份字节都以 `RIFF` 开头、第 8–11 字节为 `WEBP`；`decodeWebp` 尺寸分别为 512×512、128×128。
- `should_center_crop_landscape_image`：输入 1600×800 竖条图 → 512 档的左上、右上像素是绿色（红蓝两边被裁掉；按通道差 ≤ 24 判颜色）。
- `should_keep_alpha_for_transparent_png`：输入全透明背景中间不透明方块的 PNG → 512 档 `(0,0)` 像素 alpha < 16，中心 alpha > 240。
- `should_upscale_small_square_to_512`：输入 200×200 → 512 档为 512×512。
- `should_accept_short_side_128` / `should_reject_short_side_127_as_too_small`（128×300 通过，127×300 `TOO_SMALL`）。
- `should_reject_too_many_pixels_without_decoding`：`pngHeaderOnly(8000, 8000)` → `TOO_MANY_PIXELS`。
- `should_reject_truncated_jpeg_as_unreadable`：合法 JPEG 截掉后一半 → `UNREADABLE`。
- `should_reject_text_named_jpg`：`"not an image".getBytes()` → `UNSUPPORTED_TYPE`。
- `should_strip_exif_xmp_and_icc`：`withExifGps(jpeg(600, 600))` 与 `withPngMetadata(png(600, 600, false))` 的两档输出，`riffChunks` 都不含 `EXIF`、`XMP `、`ICCP`。

- [ ] **Step 2: 跑测试确认失败**

Run: `./gradlew :patra-api:patra-identity:patra-identity-infra:test --tests "*AvatarImageAdapterTest*"`
Expected: 编译失败

- [ ] **Step 3: 实现 `AvatarImageAdapter.render(byte[] upload)`**

流程照 spec 6.3。读写器照 spec 6.2 直接 `new` SPI（类名以 jar 里实际的为准：`com.twelvemonkeys.imageio.plugins.jpeg.JPEGImageReaderSpi`、`com.twelvemonkeys.imageio.plugins.webp.WebPImageReaderSpi`、`com.luciad.imageio.webp.WebPImageWriterSpi`；PNG 取 `ImageIO.getImageReadersByFormatName("png")` 里类名以 `com.sun.imageio` 开头的那个）。下采样系数与缩放：

```java
int k = Math.max(1, side / 1024);           // side = min(w, h)
// 解码：param.setSourceRegion(new Rectangle((w - side) / 2, (h - side) / 2, side, side));
//       param.setSourceSubsampling(k, k, 0, 0);
// 缩放：while (current > 2 * target) 双线性减半；最后一步双三次到 target（512）；128 由 512 出。
```

编码参数：`ImageWriteParam.MODE_EXPLICIT`、`setCompressionType("Lossy")`、`setCompressionQuality(AvatarPolicy.WEBP_QUALITY)`；输出缓冲 `TYPE_INT_ARGB`（源有 alpha）或 `TYPE_INT_RGB`。源色彩空间不是 sRGB 时先 `ColorConvertOp` 到 `ColorSpace.CS_sRGB`。读头或解码抛 `IOException` / `RuntimeException` → `UNREADABLE`。

- [ ] **Step 4: 跑测试确认通过**

Run: 同 Step 2
Expected: PASS（输出里出现 JDK「restricted method」告警属预期，Task 15 处理）

- [ ] **Step 5: 提交**

```bash
git add gradle/libs.versions.toml patra-api/patra-identity/patra-identity-infra
git commit -m "feat(identity): 头像服务端裁方缩放并重编码为 WebP (PAP-97)"
```

提醒用户在 IDEA 里点「加载 Gradle 更改」。

---

### Task 9: 头像存储适配器

**Files:**
- Modify: `patra-identity-infra/build.gradle.kts`（`implementation(project(":patra-tool:patra-tool-object-storage"))`）
- Create: `domain/port/avatar/AvatarStoragePort.java`、`domain/model/vo/avatar/StoredAvatar.java`、`domain/exception/AvatarNotFoundException.java`、`domain/exception/AvatarStorageException.java`；`infra/adapter/avatar/AvatarStorageAdapter.java`、`infra/config/AvatarStorageProperties.java`（`@ConfigurationProperties("patra.identity.avatar")`，`bucket` 默认 `patra-identity-avatar`）
- Test: `infra/src/integrationTest/.../avatar/AvatarStorageAdapterIT.java`、`infra/src/integrationTest/.../config/IdentityITMinIOContainerInitializer.java`

**Interfaces:**
- Consumes: Task 1、Task 3、Task 6。
- Produces:
  - `interface AvatarStoragePort { void store(Avatar a, AvatarRenditions r); StoredAvatar open(Avatar a, AvatarSize s); void delete(Avatar a); void deleteAll(long userId); }`
  - `record StoredAvatar(InputStream content, long length)`（`of(...)`）
  - `AvatarNotFoundException`（`NOT_FOUND`）；`AvatarStorageException`（`StandardErrorTrait` 中映射 503 的那个，照 `TemporarilyUnavailableException` 选）。
  - `IdentityITMinIOContainerInitializer extends MinIOContainerInitializer`，`getBucketName()` 返回 `patra-identity-avatar`（照 `CatalogITMinIOContainerInitializer`）。

- [ ] **Step 1: 写失败测试**

`AvatarStorageAdapterIT`（照 catalog 的 `FullTextObjectStorageAdapterIT`：`@SpringBootTest(classes = AvatarStorageAdapter.class)` + `@ImportAutoConfiguration(ObjectStorageAutoConfiguration.class)` + `@EnableConfigurationProperties(AvatarStorageProperties.class)` + 初始化器）：
- `should_store_both_sizes_as_webp`：`store` 后 `statObject` 两个键都在、`contentType` 为 `image/webp`、长度等于输入。
- `should_open_stored_bytes`：`open(LARGE)` 读回的字节与输入相同、`length` 正确。
- `should_throw_not_found_when_missing`。
- `should_delete_both_sizes`、`should_delete_all_only_for_that_user`（用户 1 与 10 各存一个，`deleteAll(1)` 后用户 10 的还在）。
- `should_wrap_upload_failure`：桶名配置成非法值（`patra.identity.avatar.bucket=Bad_Bucket`）时 `store` 抛 `AvatarStorageException`。

- [ ] **Step 2–4: 失败 → 实现 → 通过**

Run: `./gradlew :patra-api:patra-identity:patra-identity-infra:integrationTest --tests "*AvatarStorageAdapterIT*"`

实现：`store` 依次上传 LARGE、SMALL（`ObjectMetadata` 的 `contentType("image/webp")`、`contentLength`）；第二档失败时 best-effort 删第一档再抛；`ObjectStorageOperations` 抛的运行时异常包成 `AvatarStorageException`。`open` 捕 `ObjectNotFoundException` 抛 `AvatarNotFoundException`。

- [ ] **Step 5: 提交**

```bash
git add patra-api/patra-identity/patra-identity-domain patra-api/patra-identity/patra-identity-infra
git commit -m "feat(identity): 头像对象写入专用桶 (PAP-97)"
```

---

### Task 10: 上传与删除的处理器

**Files:**
- Create: `patra-identity-app/src/main/java/dev/patra/identity/app/usecase/avatar/ChangeAvatarCommand.java`、`ChangeAvatarHandler.java`、`AvatarChanged.java`、`RemoveAvatarCommand.java`、`RemoveAvatarHandler.java`
- Test: `patra-identity-app/src/test/.../usecase/avatar/ChangeAvatarHandlerTest.java`、`RemoveAvatarHandlerTest.java`

**Interfaces:**
- Consumes: Task 4、Task 6、Task 9；现有 `CurrentUserPort`、`UserRepository`（`findById`、`findByIdForUpdate`）、`UserProfileRepository`、`TransactionOperations`。
- Produces:
  - `record ChangeAvatarCommand(byte[] content) implements Command<AvatarChanged>`（`of(byte[])`，`clone()`；`content` 为 `null` 或空 → 处理器抛 `InvalidAvatarException(REQUIRED)`）
  - `record AvatarChanged(String username, Avatar avatar)`（`of`）
  - `record RemoveAvatarCommand() implements Command<Void>`（`of()`）

- [ ] **Step 1: 写失败测试**（照 `UpdatePrivacyHandlerTest` 的 mock 写法，`TransactionOperations` 用直接执行回调的桩）

`ChangeAvatarHandlerTest`：
- `should_store_then_save_then_delete_previous`：资料原有 `a1`；`render` 返回固定字节；顺序验证 `store(a2)` → `profiles.save` → `delete(a1)`；返回 `AvatarChanged("chen-yu", a2)`。
- `should_reject_pending_deletion_before_processing`：预检 `requireActive()` 抛 → `render`、`store` 都没被调。
- `should_reject_empty_content_as_required`。
- `should_short_circuit_when_same_image_uploaded_again`：资料已有的头像就是 `Avatar.forContent(userId, large)` → 不调 `store`、不调 `save`、不调 `delete`，返回同一个头像。
- `should_delete_new_objects_when_transaction_fails`：锁内 `requireActive()` 抛 → `delete(a2)` 被调、异常原样抛出、`delete(a1)` 没被调。
- `should_still_return_when_deleting_previous_fails`：`delete(a1)` 抛 → 仍返回 `AvatarChanged`。

`RemoveAvatarHandlerTest`：有头像时保存后删对象；没头像时不调 `delete`；冷静期 `requireActive()` 抛且不删。

- [ ] **Step 2–4: 失败 → 实现 → 通过**

Run: `./gradlew :patra-api:patra-identity:patra-identity-app:test --tests "*AvatarHandlerTest*"`

实现照 spec 8.1 / 8.2 的伪代码；类上不加 `@Transactional`；日志 `头像已更换: userId={}, hash={}`、`头像已移除: userId={}`、删除失败 `log.warn("头像旧对象删除失败: userId={}, hash={}", …, e)`。

- [ ] **Step 5: 提交**

```bash
git add patra-api/patra-identity/patra-identity-app
git commit -m "feat(identity): 加换头像与移除头像的处理器 (PAP-97)"
```

---

### Task 11: 头像读取查询服务

**Files:**
- Create: `patra-identity-app/src/main/java/dev/patra/identity/app/usecase/avatar/AvatarQueryService.java`、`AvatarRef.java`
- Test: `patra-identity-app/src/test/.../usecase/avatar/AvatarQueryServiceTest.java`

**Interfaces:**
- Consumes: Task 5 的 `findAvatarOwner`、Task 9 的端口。
- Produces:
  - `record AvatarRef(Avatar avatar, AvatarSize size)`（`of`；`String etag()` 委托 `avatar.etag(size)`）
  - `AvatarRef locate(String rawUsername, String size, String version)`：任一条件不满足抛 `AvatarNotFoundException`
  - `StoredAvatar open(AvatarRef ref)`：对象不存在时 `log.warn("头像对象缺失: userId={}, hash={}")` 后抛 `AvatarNotFoundException`

- [ ] **Step 1: 写失败测试**

`AvatarQueryServiceTest` 逐行对应 spec 8.3 表：非法用户名、`size` 为 `256` / `null`、`v` 为 `null` / 大写 / 15 位、用户不存在、`BANNED`、`DELETED`、没头像、`should_404_when_version_is_stale`（`v` 是旧哈希）都抛 `AvatarNotFoundException`；`ACTIVE` 与 `PENDING_DELETION` 且 `v` 匹配返回 `AvatarRef`；`" Chen-Yu "` 规范化后命中；`open` 在端口抛 `AvatarNotFoundException` 时原样抛出。

- [ ] **Step 2–4: 失败 → 实现 → 通过**

Run: `./gradlew :patra-api:patra-identity:patra-identity-app:test --tests "*AvatarQueryServiceTest*"`

用户名校验照 `UserQueryService.publicIdentity`（`Username.violation(raw)` 有值即 404，再 `Username.of`）。

- [ ] **Step 5: 提交**

```bash
git add patra-api/patra-identity/patra-identity-app
git commit -m "feat(identity): 加头像读取的查询服务 (PAP-97)"
```

---

### Task 12: 接口与响应字段

**Files:**
- Create: `patra-identity-adapter/src/main/java/dev/patra/identity/adapter/rest/me/MeAvatarController.java`、`rest/me/response/AvatarResponse.java`、`rest/users/PublicAvatarController.java`、`rest/AvatarUrls.java`
- Modify: `rest/auth/response/CurrentUserResponse.java`、`rest/users/response/PublicUserResponse.java`
- Test: `patra-identity-adapter/src/integrationTest/.../rest/me/MeAvatarControllerIT.java`、`rest/users/PublicAvatarControllerIT.java`；改 `AuthControllerIT`、`PublicUserControllerIT`

**Interfaces:**
- Consumes: Task 5、Task 10、Task 11。
- Produces:
  - `AvatarUrls.of(String username, Avatar avatar) → String`（`avatar` 为 `null` 返回 `null`）：`/users/{username}/avatar?v={hash}`
  - `POST /me/avatar`（`consumes = MULTIPART_FORM_DATA_VALUE`，`@RequestPart(name = "file", required = false) MultipartFile file`）→ 200 `AvatarResponse(String avatarUrl)`；`DELETE /me/avatar` → 204
  - `GET /users/{username}/avatar`（`@RequestParam(required = false) String size, v`）→ `ResponseEntity<Resource>`
  - `CurrentUserResponse`、`PublicUserResponse` 加 `int avatarPalette`（紧跟 `avatarUrl`），`avatarUrl` 改由 `AvatarUrls.of` 生成

- [ ] **Step 1: 写失败测试**（`@WebMvcTest(controllers = X)` + `@Import(X)`，照现有 `MeProfileController` 的 IT；`RestTestClient`）

`MeAvatarControllerIT`：
- 上传 200 返回 `{"avatarUrl": "/users/chen-yu/avatar?v=0123456789abcdef"}`；命令里的字节等于上传的文件。
- 没有 `file` part → 422，`errors[0].field = "file"`、`code = "REQUIRED"`（处理器抛 `InvalidAvatarException(REQUIRED)`）。
- 处理器抛 `InvalidAvatarException(TOO_SMALL)` → 422 `TOO_SMALL`；抛 `UserPendingDeletionException` → 409。
- `DELETE` → 204。

`PublicAvatarControllerIT`：
- 200：`Content-Type: image/webp`、`Content-Length`、`ETag: "0123456789abcdef-128"`、`Cache-Control` 恰为 `public, max-age=31536000, immutable`，体是桩返回的字节。
- `If-None-Match: "0123456789abcdef-128"` → 304，`open` 没被调。
- `locate` 抛 `AvatarNotFoundException` → 404；`size=abc` 不回 422 而是走 `locate` 的 404。

改 `AuthControllerIT`：`avatarUrl` 有头像时为 `/users/chen-yu/avatar?v=…`、没头像时为 `null`；`avatarPalette` 等于 `AvatarPalette.of(userId)`。改 `PublicUserControllerIT` 同理，且断言响应里仍没有 `userId`。

- [ ] **Step 2–4: 失败 → 实现 → 通过**

Run: `./gradlew :patra-api:patra-identity:patra-identity-adapter:integrationTest --tests "*AvatarControllerIT*" --tests "*AuthControllerIT*" --tests "*PublicUserControllerIT*"`

读取接口照 catalog `PortalFullTextFileController`：先 `locate`，`webRequest.checkNotModified(ref.etag())` 为真返回 `null`，再 `open`，`new InputStreamResource(stored.content())`；缓存头 `CacheControl.maxAge(Duration.ofDays(365)).cachePublic().immutable()`，断言实际输出的值是 `max-age=31536000, public, immutable` 还是 spec 的顺序——**以 spec 的字面 `public, max-age=31536000, immutable` 为准**，Spring 输出顺序不同就用 `.cacheControl(CacheControl.empty())` + `header(HttpHeaders.CACHE_CONTROL, "public, max-age=31536000, immutable")`。上传接口取 `file.getBytes()`（`null` 或 `isEmpty()` 传空数组）。

- [ ] **Step 5: 提交**

```bash
git add patra-api/patra-identity/patra-identity-adapter
git commit -m "feat(identity): 加头像上传、删除、读取接口与响应里的色号 (PAP-97)"
```

---

### Task 13: 组装与完整流程

**Files:**
- Modify: `patra-identity-boot/src/main/resources/application.yml`（spec 8.5 的 multipart 与 Tomcat 配置；`patra.identity.avatar.bucket: patra-identity-avatar`）；`patra-identity-boot/build.gradle.kts`（`testImplementation(testFixtures(...infra))`）；`patra-identity-boot/src/integrationTest/resources/application-test.yml`（`patra.object-storage.providers.minio.{endpoint: http://127.0.0.1:1, access-key: unused, secret-key: unused}`，让不带 MinIO 的独立 IT 上下文能起）；`IdentityFlowITSupport.java`（`initializers` 加 `IdentityITMinIOContainerInitializer`）
- Create: `patra-identity-boot/src/integrationTest/java/dev/patra/identity/config/IdentityITMinIOContainerInitializer.java`（同 Task 9，boot 侧一份）、`AvatarFlowIT.java`

**Interfaces:**
- Consumes: 前面全部任务。

- [ ] **Step 1: 写失败测试 `AvatarFlowIT extends IdentityFlowITSupport`**

- `should_upload_read_replace_and_remove_avatar`：注册登录 → `POST /me/avatar` 上传 `AvatarImageFixture.withExifGps(jpeg(900, 600))` → 200；直接用 `MinioClient` 读回 `avatar/{userId}/{hash}-512.webp` 与 `-128.webp`：`RIFF…WEBP`、`decodeWebp` 尺寸 512 / 128、`riffChunks` 不含 `EXIF` / `XMP ` / `ICCP` → `/auth/me` 与 `GET /users/{u}` 的 `avatarUrl` 都等于上传响应的值、`avatarPalette` 在 0–11 且两处相同 → `GET {avatarUrl}&size=128` 200，头齐全；带 `If-None-Match` 304 → 再上传同一张图：200 且地址不变、两档对象还在 → 上传另一张（`png(400, 400, true)`）：旧两档对象不在、`idn_user_profile.avatar_object_key` 换了、旧地址 404 → `DELETE /me/avatar` 204：库里键为 `NULL`、新两档对象不在、地址 404、`/auth/me` 的 `avatarUrl` 为 `null`。
- `should_reject_upload_over_5mb_with_413`：用 JDK `HttpClient` 打真实端口，multipart 体里放 5 MiB + 1 字节 → 413、体里 `code` 为 `IDN-0413`；5 MiB 整（内容是随机字节）→ 不是 413（是 422 `UNSUPPORTED_TYPE`）。
- `should_reject_upload_during_pending_deletion_with_409`：发起注销后上传 → 409。

- [ ] **Step 2: 跑测试确认失败**

Run: `./gradlew :patra-api:patra-identity:patra-identity-boot:integrationTest --tests "*AvatarFlowIT*"`
Expected: FAIL（413 前没有 multipart 配置时 Spring 默认上限 1MB，900×600 的 JPEG 也可能先撞上；桶名配置缺失）

- [ ] **Step 3: 补配置让它通过；再跑 identity-boot 全部 IT**

Run: `./gradlew :patra-api:patra-identity:patra-identity-boot:integrationTest`
Expected: 全绿（其他 IT 因为 `IdentityFlowITSupport` 多了 MinIO、独立 IT 因为占位端点都能起）。IT 上下文数量变化时看 identity 的 IT 配置有没有限 Hikari 池（计划常见坑）。

- [ ] **Step 4: 重生成模块图**

Run: `./gradlew dumpModuleGraph --no-configuration-cache`，确认 `module-graph.json` 里 identity-infra / boot 多了 `patra-tool-object-storage`。

- [ ] **Step 5: 提交**

```bash
git add patra-api/patra-identity/patra-identity-boot patra-infra/cd/module-graph.json
git commit -m "feat(identity): 接通头像的配置与完整流程测试 (PAP-97)"
```

---

### Task 14: 接入注销到期执行

**Files:**
- Modify（`erase()` 清头像已在 Task 4 完成）: `patra-identity-app/.../usecase/deletion/ExecuteDueAccountDeletionsHandler.java`；`patra-identity-boot/src/integrationTest/java/dev/patra/identity/AccountDeletionFlowIT.java`
- Test: `patra-identity-app/src/test/.../deletion/ExecuteDueAccountDeletionsHandlerTest.java`

**Interfaces:**
- Consumes: Task 9 的 `AvatarStoragePort.deleteAll`；PAP-100 的 `users.findById`、`User.isDeletionDue(Instant)`。

- [ ] **Step 1: 读 PAP-100 交接笔记与它最终提交的处理器**，以实际代码为准调整下面的插入点。

- [ ] **Step 2: 写失败测试**（`ExecuteDueAccountDeletionsHandlerTest` 加）

- `should_delete_avatar_objects_before_locking`：到期用户 → `inOrder(avatarStorage, transactions)`：`deleteAll(id)` 先于 `transactions.execute`。
- `should_not_delete_avatar_objects_when_user_cancelled_after_scan`：扫出 ID，但 `users.findById` 返回已取消（不到期）的用户 → `deleteAll` 没被调。
- `should_count_failure_when_avatar_deletion_fails`：`deleteAll` 抛 → 该用户计入 `failed`，事务没执行，下一个用户照常处理。

`AccountDeletionFlowIT` 的到期执行用例：注销前先上传一张头像，执行后 `MinioClient` 列 `avatar/{userId}/` 前缀为空。

- [ ] **Step 3–4: 失败 → 实现 → 通过**

实现：循环里 `completeOne` 之前加

```java
if (users.findById(id).filter(u -> u.isDeletionDue(now)).isPresent()) {
  avatarStorage.deleteAll(id);
}
```

（在现有 `try` 块内，异常走现有失败分支）；类注释补一句「头像对象在加锁事务之前按前缀删，删之前不加锁复核仍到期」。

Run: `./gradlew :patra-api:patra-identity:patra-identity-app:test --tests "*ExecuteDueAccountDeletionsHandlerTest*" :patra-api:patra-identity:patra-identity-boot:integrationTest --tests "*AccountDeletionFlowIT*"`
Expected: PASS

- [ ] **Step 5: 提交**

```bash
git add patra-api/patra-identity/patra-identity-app patra-api/patra-identity/patra-identity-boot
git commit -m "feat(identity): 注销到期时先删光头像对象 (PAP-97)"
```

---

### Task 15: 网关用例、建桶与原生访问

**Files:**
- Modify: `patra-api/patra-gateway-boot/src/integrationTest/java/dev/patra/gateway/GatewayAuthenticationIT.java`（或 `GatewayRoutingIT`，放到已有同类用例旁边）；`patra-infra/docker/docker-compose.storage.yaml`；`patra-api/patra-identity/patra-identity-boot/build.gradle.kts`

- [ ] **Step 1: 网关两条用例**（spec 第 10 节）

- `should_forward_anonymous_avatar_read_with_cache_headers`：匿名 `GET /patra-identity/users/chen-yu/avatar?size=128&v=0123456789abcdef`，WireMock 回 200 + 三个头 → 客户端收到同样的 `Content-Type`、`Cache-Control`、`ETag`。
- `should_require_login_and_stream_large_avatar_upload`：不带令牌 `POST /patra-identity/me/avatar` → 401；带令牌发 5,000,000 字节的 multipart → WireMock 收到的体长度与发出的一致（`verify(postRequestedFor(...).withHeader("Content-Length", equalTo(String.valueOf(n))))`）。

Run: `./gradlew :patra-api:patra-gateway-boot:integrationTest --tests "*GatewayAuthenticationIT*"`
Expected: PASS（规则不改，测试应直接通过；不通过说明网关有未知的体限制，停下按 systematic-debugging 查）

- [ ] **Step 2: `minio-init` 建桶**

在 `docker-compose.storage.yaml` 的 entrypoint 里 `patra-ingest-cache` 那两行后面加 `mc mb --ignore-existing patra/patra-identity-avatar;` 与 `mc anonymous set none patra/patra-identity-avatar;`，结尾的说明 `echo` 也补上。验证：`docker compose -f patra-infra/docker/docker-compose.storage.yaml config -q` 退出码 0。

- [ ] **Step 3: 原生访问**

`patra-identity-boot/build.gradle.kts` 加：

```kotlin
tasks.bootJar {
    manifest { attributes("Enable-Native-Access" to "ALL-UNNAMED") }
}
```

验证（spec 6.6）：`./gradlew :patra-api:patra-identity:patra-identity-boot:bootJar`，在 scratchpad 里 `java -Djarmode=tools -jar <jar> extract --layers --destination <dir>`，`unzip -p <dir>/application/app.jar META-INF/MANIFEST.MF` 含 `Enable-Native-Access: ALL-UNNAMED`。不含就撤掉这段，改在 `docker-compose.apps.yaml` 的 identity `environment` 加 `JAVA_TOOL_OPTIONS: --enable-native-access=ALL-UNNAMED`（若该条目已有 `JAVA_TOOL_OPTIONS` 则追加），并在提交说明里写明原因。

- [ ] **Step 4: 提交**

```bash
git add patra-api/patra-gateway-boot patra-infra/docker/docker-compose.storage.yaml patra-api/patra-identity/patra-identity-boot/build.gradle.kts
git commit -m "feat(identity): 建头像桶、允许原生编码库并补网关用例 (PAP-97)"
```

（若 Step 3 改的是 `docker-compose.apps.yaml`，`git add` 换成它。）

---

### Task 16: 文档、全量门控与 Linear

**Files:**
- Modify: `patra-api/patra-identity/README.md`；`docs/patra/specs/2026-10-22-identity-avatar-design.md`（状态行改「已实施」，加「实施时实测与偏离」一节）

- [ ] **Step 1: README**（spec 第 14 节）：接口表三行；`/auth/me` 示例带 `avatarUrl` 与 `avatarPalette`；删「`avatarUrl` 本版恒为 `null`」；新增「头像」一节（处理流程、库与原生访问、桶与键、`v` 与缓存、色号算法不可改、与注销的接入、本地运行）。`patra-tool-object-storage` 有 README 就补两个方法。

- [ ] **Step 2: 全量门控**

```bash
./gradlew spotlessApply
./gradlew check
./gradlew :patra-tool:patra-tool-object-storage:integrationTest :patra-tool:patra-tool-web:integrationTest :patra-api:patra-identity:patra-identity-infra:integrationTest :patra-api:patra-identity:patra-identity-adapter:integrationTest :patra-api:patra-identity:patra-identity-boot:integrationTest :patra-api:patra-identity:patra-identity-session:integrationTest :patra-api:patra-gateway-boot:integrationTest
```

Expected: 全部 BUILD SUCCESSFUL；把各模块 IT 用例数记进 spec 的实施一节。

- [ ] **Step 3: 提交**

```bash
git add patra-api/patra-identity/README.md docs/patra/specs/2026-10-22-identity-avatar-design.md docs/patra/plans/2026-10-22-identity-avatar.md
git commit -m "docs(identity): 加头像的设计、计划与 README 一节 (PAP-97)"
```

（spotless 若改了别的文件，`git add -u` 只加本 Issue 的路径后再提。）

- [ ] **Step 4: Linear**

- PAP-97：开工时推到 BE；此时推到 Review，评论逐条附 AC 证据（spec 第 16 节的表，填上测试名与门控输出），AC 5 按已拍板 3 改措辞，To-Do / AC 勾选。
- PAP-101：评论「来自 PAP-97 的契约」（spec 第 18 节那一行的内容）。
- PAP-100：评论「PAP-97 已接入第 14 节三条」，附提交 SHA。

- [ ] **Step 5: 交接**：在共享笔记目录写 `pap-97-identity-avatar-implementation-handover.md`（产物、提交范围、回归数字、要在 mini 上做的：重建 `patra_identity`、跑一次 `compose-all.sh` 的 storage 栈或确认 `mc ls` 有桶、部署后经网关 `curl -I` 头像地址看三个头、看 identity 启动日志没有 restricted method 告警）。
