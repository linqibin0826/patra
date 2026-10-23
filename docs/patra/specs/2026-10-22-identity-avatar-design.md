# identity 头像工程设计（PAP-97）

> 状态：已实施（2026-10-23，计划 `docs/patra/plans/2026-10-22-identity-avatar.md`）；第 17 节三个待拍板点 2026-10-22 用户确认都按推荐方案；实施时的实测与偏离见第 19 节
> 日期：2026-10-22
> Issue：PAP-97（GitHub #263），Linear Project P-PAP-9 v0.12 User Center
> 依据：release spec `docs/patra/release-specs/v0.12-user-center.md` 2.1.3、2.1.4、决策 G；PAP-95 在 Linear PAP-97 上留的 `avatarPalette` 契约；PAP-100 spec 第 14 节；设计简报 `patra-portal/docs/design-briefs/v0.12/user-center.md` §3.6、§3.7、§3.20、§4.6、§10

## 1. 要解决的问题

用户在设置页裁好一张正方形图片上传，之后导航栏、菜单头部、公开主页都显示它；删除后回到默认头像（门户按昵称首字素 + 色号画，后端不出图）。后端要做的事：

1. 收图并拒绝坏输入：类型按魔数判断，只收 JPEG / PNG / WebP，不收 GIF 和动图；原图 ≤ 5 MB；短边 ≥ 128 px；像素总数有上限（防解压炸弹）。
2. 服务端重新出图：居中裁方、生成 512 与 128 两档、重编码为 WebP、不带任何元数据（EXIF 含 GPS、XMP、ICC）。
3. 存 MinIO 专用桶，对象键带内容哈希，资料里只存对象键；换头像写新键、删旧键。
4. 读取接口按不可变长期缓存流式下发，经网关、门户同源转发（v0.11 决策 F 那条路）。
5. 默认头像色号 `avatarPalette`（0–11）按用户 ID 哈希算好，放进 `/auth/me`（及登录、注册、再认证、改密码响应里同构的 `user`）与 `GET /users/{username}`。
6. 接上 PAP-100：注销到期时删光该用户的头像对象，资料里的头像键随 `erase()` 清空。

## 2. 范围

**做**：

- identity：`POST /me/avatar`、`DELETE /me/avatar`、`GET /users/{username}/avatar`；`idn_user_profile` 加头像键列（改写 `V1`，决策 J）；`avatarUrl`、`avatarPalette` 填进两种响应；PAP-100 的两个接入点。
- `patra-tool-object-storage`：补 `delete`、`deleteByPrefix`（库里现在只有上传、下载、stat）。
- 网关：不改规则（第 10 节），补两条 IT。
- `patra-infra`：`minio-init` 建桶。
- 版本目录：加 `webp-imageio`、TwelveMonkeys JPEG 插件。
- identity README 新增一节。

**不做**：

- 门户的裁剪对话框、转发 Route Handler、默认头像渲染（PAP-101）。
- 后台审核、举报头像（release spec 边界 A）。
- 动图头像、GIF。
- 按 EXIF 方向旋转（第 6.4 节说明）。
- 孤儿对象的定时清扫（第 8.4 节说明）。

## 3. 已定的前提

| 来源 | 内容 |
|---|---|
| release spec 2.1.3 | JPEG / PNG / WebP，≤ 5 MB，短边 ≥ 128 px，不收 GIF 与动图；浏览器先裁方；服务端重编码 WebP、剥元数据、出 512 与 128；对象键 `avatar/{userId}/{哈希}-512.webp`；经门户同源转发，不开公开桶、不用预签名链接；删除回默认；`avatarPalette` 0–11 |
| 决策 G | 存对象键不存 URL；不接 Gravatar |
| 决策 J | identity 库改 `V1` 后重建，不写增量迁移 |
| PAP-95 契约（Linear PAP-97 评论） | `avatarPalette` 由本 Issue 加：`PublicUserResponse`（色号在领域层算好放进 `PublicUserView`，响应不暴露用户 ID）与 `CurrentUserResponse`（从 `UserAccountReadModel` 映射）；按用户 ID 哈希，稳定、大致均匀，有没有头像都返回 |
| PAP-92 契约 | 色号 0–11 对应门户 12 色板（服务端不需要知道颜色）；上传拒绝原因要能区分「类型」「过大」「过小」「读不出」 |
| PAP-100 spec 第 14 节 | `UserProfile.erase()` 清头像键；到期执行在**加锁事务之前**按前缀 `avatar/{userId}/` 删光对象；删除失败按单个用户失败处理（下次运行重试，删空前缀天然幂等） |
| 项目规则「用户名只存 identity」 | 对象键按用户 ID 命名 |
| 项目规则 | 冷静期账号的写接口 409（`requireActive()`） |

## 4. 业界参照

| 产品 | 做法 | 取了什么 |
|---|---|---|
| Discourse | 头像 URL `/user_avatar/{host}/{username}/{size}/{upload_id}_2.png`：用户名 + 尺寸 + 版本号都在路径里，响应长期缓存 | 用户名 + 版本号进 URL，换头像就换 URL |
| GitHub | `avatars.githubusercontent.com/u/{id}?v=4`，服务端多档尺寸 | 服务端出多档，客户端选尺寸 |
| Mastodon | 上传后服务端缩放、转码、剥元数据；原图不保留 | 不保留原图 |

## 5. 模块与改动面

| 模块 | 改动 |
|---|---|
| `patra-tool-web` | `HttpStdErrors.Group.CONTENT_TOO_LARGE()`；`MaxUploadSizeExceededException` 映射 413（8.5） |
| `patra-tool-object-storage` | `ObjectStorageOperations` / `ObjectStorageProvider` / `ObjectStorageTemplate` 加 `delete(bucket, key)`、`deleteByPrefix(bucket, prefix)`；MinIO 与 S3 两个 provider 都实现 |
| `patra-identity-domain` | `Avatar`、`AvatarSize`、`AvatarRenditions`、`AvatarRejection` 值对象；`AvatarPalette` 策略；`AvatarPolicy` 常量；`InvalidAvatarException`；`AvatarImagePort`、`AvatarStoragePort`；`UserProfile` 加头像；两个读模型加头像与色号；`UserReadPort.findAvatarOwner` |
| `patra-identity-app` | `ChangeAvatarHandler`、`RemoveAvatarHandler`、`AvatarQueryService`；PAP-100 的 `ExecuteDueAccountDeletionsHandler` 接删头像 |
| `patra-identity-infra` | `AvatarImageAdapter`（解码、裁方、缩放、编码）、`AvatarFormatSniffer`、`AvatarStorageAdapter`；资料实体与映射加列；读适配器加列与新查询；`V1` 加列；依赖 `patra-tool-object-storage`、TwelveMonkeys、`webp-imageio` |
| `patra-identity-adapter` | `MeAvatarController`、`PublicAvatarController`；`CurrentUserResponse`、`PublicUserResponse` 填 `avatarUrl`、加 `avatarPalette`；`AvatarUrls` |
| `patra-identity-boot` | `application.yml`：multipart 上限、桶名；bootJar 清单加 `Enable-Native-Access`；`module-graph.json` 重新生成 |
| `patra-gateway-boot` | 只加 IT |
| `patra-infra` | `docker-compose.storage.yaml` 的 `minio-init` 建 `patra-identity-avatar` |
| `gradle/libs.versions.toml` | `webp-imageio`、`twelvemonkeys-imageio-jpeg` |

## 6. 图片处理

### 6.1 库选型（实测结论）

**纯 Java 的 WebP 编码器目前不存在可用的开源实现**（2026-10-22 调研）：

| 候选 | 编码 WebP | 纯 Java | 许可 | 结论 |
|---|---|---|---|---|
| JDK ImageIO | 否 | 是 | — | 只做 PNG 解码 |
| TwelveMonkeys `imageio-webp` 3.15.2（catalog 已在用） | **否，只解码** | 是 | BSD-3 | 用来解码 WebP |
| TwelveMonkeys `imageio-jpeg` 3.15.2 | 否 | 是 | BSD-3 | 用来解码 JPEG（CMYK / YCCK、坏 APP 段比 JDK 自带的稳） |
| `com.github.usefulness:webp-imageio` 0.10.0（2025-05） | 是（有损 / 无损，带 alpha） | **否**：JNI 包装 libwebp，jar 内置 Linux x86_64 / aarch64、macOS x86_64 / aarch64、Windows 的 `.so` / `.dylib` / `.dll` | Apache-2.0；libwebp BSD-3 | **用来编码** |
| JDeli | 是 | 是 | 商业授权 | 否 |
| scrimage-webp | 是 | 否（调 `cwebp` 可执行文件） | Apache-2.0 | 否：要在镜像里装二进制、起子进程 |

**实测**（scratchpad 里的一次性程序，不进仓库）：1024 px 渐变 + 圆形图，缩到 512 / 128，`webp-imageio` 有损 q=0.82 编码，再用 TwelveMonkeys 解回：

| 环境 | 512 档 | 128 档 | 解回尺寸 |
|---|---|---|---|
| 本机 macOS arm64，Zulu 25.0.1 | 4892 B，首次 99 ms（含加载原生库） | 1090 B，1 ms | 512×512、128×128 |
| `eclipse-temurin:25-jdk` 容器（linux aarch64，与 mini 上的服务容器同架构同基础镜像） | 4892 B，63 ms | 1090 B，1 ms | 同上 |

输出文件头是 `RIFF....WEBPVP8 `（简单格式，没有 `VP8X`），即不含 EXIF / XMP / ICCP 块。JDK 25 在加载原生库时打印「restricted method」警告，未来版本会默认拦截，见 6.6。

**定**：解码用 TwelveMonkeys 与 JDK 自带的读取器，编码用 `webp-imageio`（只吃我们自己解码、缩放后的像素）。（实施评审更正：TwelveMonkeys 的 JPEG 插件把 DCT 解码委托给 JDK 的 `JPEGImageReader`，底层是 JDK 自带的原生 libjpeg；不可信的 JPEG 仍会进原生代码，但那是随 JDK 安全更新维护的代码，风险可接受。WebP 与 PNG 的解码是纯 Java。）这偏离了 Issue 上「纯 Java」的字面，原因是没有可用的纯 Java 编码器；替代方案是改出 JPEG（纯 Java，但偏离 release spec 的 WebP），见第 17 节已拍板 1。

### 6.2 读写器不走 ImageIO 注册表

ImageIO 注册表里同时有 TwelveMonkeys 与 `webp-imageio` 的 WebP 读取器，按注册表查找可能拿到原生的那个；Boot 胖 jar 里 SPI 还要 `scanForPlugins()`（catalog 的做法）。所以 `AvatarImageAdapter` 直接 `new` 各个 SPI 再 `createReaderInstance()` / `createWriterInstance()`：

| 用途 | 类 |
|---|---|
| 解码 JPEG | `com.twelvemonkeys.imageio.plugins.jpeg.JPEGImageReaderSpi` |
| 解码 PNG | JDK 的 `com.sun.imageio.plugins.png.PNGImageReaderSpi` 不是公开 API，改用 `ImageIO.getImageReadersByFormatName("png")` 取 JDK 自带的那个（注册表里只有它一个 PNG 读取器，断言类名以 `com.sun.imageio` 开头） |
| 解码 WebP | `com.twelvemonkeys.imageio.plugins.webp.WebPImageReaderSpi` |
| 编码 WebP | `com.luciad.imageio.webp.WebPImageWriterSpi` |

实现时以实际类名为准（上面的 TwelveMonkeys / luciad 类名照 jar 核对）。

### 6.3 处理流程（`AvatarImagePort.render(byte[] upload)`）

```
字节 ──sniff──▶ 类型（不是 JPEG/PNG/WebP、是 GIF、是动图 → UNSUPPORTED_TYPE）
     ──读头──▶ 宽 × 高（读不出 → UNREADABLE）
     ──校验──▶ 短边 < 128 → TOO_SMALL；宽 × 高 > 4096² → TOO_MANY_PIXELS（实施评审由 5000 万改小，见第 19 节）
     ──居中取方──▶ 源区域：边长 = 短边，左上 = ((w−s)/2, (h−s)/2)
     ──解码──▶ ImageReadParam.setSourceRegion(方形区域) + setSourceSubsampling(k, k)
               k = max(1, s / 1024)（整除），解出来的边长落在 [1024, 2047] 或原尺寸
               解码中抛异常 → UNREADABLE
     ──转 sRGB──▶ 色彩空间不是 sRGB（带 ICC 的 Display P3、CMYK 等）→ ColorConvertOp 转到 sRGB
     ──缩放──▶ 逐次减半（每步双线性）到 ≤ 2× 目标，再一步双三次到 512；128 从 512 出
               源边长 < 512 时直接双三次放大到 512（「-512」永远是 512×512）
     ──编码──▶ WebP 有损 q=0.82；有 alpha 时保留 alpha（TYPE_INT_ARGB），否则 TYPE_INT_RGB
     ──输出──▶ AvatarRenditions(large: byte[], small: byte[])
```

- **魔数**（`AvatarFormatSniffer`，纯函数，读前 64 KiB 足够）：
  - JPEG：`FF D8 FF`。
  - PNG：`89 50 4E 47 0D 0A 1A 0A`；按块扫描到第一个 `IDAT` 之前出现 `acTL` 即 APNG → 动图。
  - WebP：`RIFF????WEBP`；首块是 `VP8X` 且标志字节的动画位（`0x02`）为 1，或任何位置出现 `ANIM` / `ANMF` 块 → 动图。
  - GIF（`GIF87a` / `GIF89a`）与其他一切 → `UNSUPPORTED_TYPE`。
  - 扩展名与 `Content-Type` 一概不看。
- **像素上限 4096²**（约 1677 万）：门户上传的是裁好的方图（边长 ≤ 1024），上限只挡直接调接口的超大图。原定 50 MP 并认为「有了下采样，解码内存按 ≤ 2047² × 4 B ≈ 16 MB 封顶」，实施评审实测不成立：TwelveMonkeys 的 WebP 读取器先解整张图再取区域（49 MP 的有损 WebP 堆峰值约 300 MB），渐进式 JPEG 在原生 libjpeg 里缓存整张系数图（RSS 约 257 MB）；只有 PNG 与基线 JPEG 符合原结论。
- **短边按源图算，不按裁方后算**：裁方后边长就等于短边，同一个数。
- **为什么服务端还要裁方**：门户已经裁成正方形，服务端再裁一次在方图上是恒等操作；直接调接口传长方形图时也能得到确定结果。
- **质量 0.82**：常见头像服务的取值区间；512 档实测约 5 KB（纯色渐变），照片类预计 20–40 KB。

### 6.4 元数据

重编码天然丢掉源文件里的全部元数据：编码器只拿到像素（`IIOImage(image, null, null)`），`webp-imageio` 不写 `EXIF` / `XMP ` / `ICCP` 块。ICC：JPEG 带的 ICC 由 TwelveMonkeys 应用后转 sRGB，WebP 不带 ICC 时浏览器按 sRGB 解释，颜色一致；JDK 的 PNG 读取器忽略 `iCCP`（实施评审实测），Display P3 的 PNG 直接调接口会偏淡，门户 canvas 导出的已是 sRGB，不受影响。灰度图按 sRGB 值直接复制成 r = g = b（解码器标成线性 `CS_GRAY`，走色彩转换会被提亮）。

**不按 EXIF 方向旋转**：门户的裁剪在 canvas 上完成，浏览器绘制时已经应用了方向，导出的 blob 不带 EXIF；直接调接口传带方向标记的 JPEG 会得到未旋转的头像，这是调用方的问题，不为不存在的调用方写代码。

### 6.5 并发与资源

上传最大 5 MB，在内存里处理（`MultipartFile.getBytes()`）。出图最多 2 路并发，等名额最多 10 秒，等不到回 503（实施评审补：WebP 与渐进式 JPEG 解码内存不受下采样约束，原「不限并发」的依据不成立）。

### 6.6 原生访问

`webp-imageio` 用 `System.load` 加载 jar 里解出的原生库。JDK 25 对 classpath（未命名模块）上的调用只告警，未来版本默认拦截。在 identity 的 bootJar 清单里加 `Enable-Native-Access: ALL-UNNAMED`（JDK 24 起可执行 jar 支持的清单属性，`java -jar` 启动时生效）。`service.Dockerfile` 是先 `java -Djarmode=tools -jar … extract --layers` 再 `java -jar app.jar` 启动，解包重写过的 `app.jar` 清单里还在不在这个属性要实测：本机构建 identity 镜像起一次，看启动日志里有没有「restricted method」告警。属性丢了就改在 `docker-compose.apps.yaml` 的 identity 条目里加 `JAVA_TOOL_OPTIONS` 片段 `--enable-native-access=ALL-UNNAMED`（改 apps 栈会触发 CI 全量，预期内）。

## 7. 存储

### 7.1 桶与对象键

| 项 | 值 |
|---|---|
| 桶 | `patra-identity-avatar`（配置键 `patra.identity.avatar.bucket`，默认值即此；私有，不开匿名访问） |
| 对象键 | `avatar/{userId}/{hash}-512.webp`、`avatar/{userId}/{hash}-128.webp` |
| `hash` | 512 档 WebP 字节的 SHA-256，取前 16 个十六进制小写字符（64 位，同一用户名下足够） |
| Content-Type | `image/webp` |

两档共用一个哈希：哈希只是版本号，按 512 档算就行。同一张图重复上传，编码是确定的，得到同一个键（第 8.1 节据此短路）。

### 7.2 资料里存什么

`idn_user_profile.avatar_object_key`：512 档的对象键，没有头像为 `NULL`。128 档的键由它推出（同哈希换后缀）。领域值对象 `Avatar(long userId, String hash)`：

| 方法 | 行为 |
|---|---|
| `of(userId, hash)` | 校验 `hash` 匹配 `^[0-9a-f]{16}$` |
| `parse(userId, objectKey)` | 从 512 档的键还原；前缀里的用户 ID 必须等于 `userId`，否则 `IllegalArgumentException` |
| `objectKey(AvatarSize)` | `avatar/{userId}/{hash}-{px}.webp` |
| `prefix(userId)`（静态） | `avatar/{userId}/`，给 PAP-100 的按前缀删 |
| `etag(AvatarSize)` | `"{hash}-{px}"`（带引号的强 ETag） |

`AvatarSize`：`LARGE(512)`、`SMALL(128)`；`fromPixels(int)` 只认这两个数。

### 7.3 端口

`AvatarStoragePort`（`domain/port/avatar/`，实现 `AvatarStorageAdapter` 在 `infra/adapter/avatar/`）：

| 方法 | 行为 |
|---|---|
| `store(Avatar, AvatarRenditions)` | 两档都上传；任一失败抛 `AvatarStorageException`（`StandardErrorTrait` 映射 503） |
| `open(Avatar, AvatarSize)` → `StoredAvatar(InputStream content, long length)` | 对象不存在抛 `AvatarNotFoundException`（NOT_FOUND） |
| `delete(Avatar)` | 删两档；对象不存在不算错 |
| `deleteAll(long userId)` | `deleteByPrefix(bucket, Avatar.prefix(userId))`；桶不存在视为已删光 |

`AvatarImagePort`（`domain/port/avatar/`，实现 `AvatarImageAdapter`）：`AvatarRenditions render(byte[] upload)`，拒绝时抛 `InvalidAvatarException(AvatarRejection)`。

### 7.4 `patra-tool-object-storage` 增补

| 方法 | MinIO | S3 |
|---|---|---|
| `void delete(String bucket, String key)` | `removeObject`；`NoSuchKey` 静默 | `deleteObject`（S3 本来就幂等） |
| `int deleteByPrefix(String bucket, String prefix)` | `listObjects(recursive)` 分批 `removeObjects`（每批 ≤ 1000），返回删除个数；`NoSuchBucket` 返回 0；`removeObjects` 的逐个错误汇总后抛 | `listObjectsV2` 分页 + `deleteObjects` |

`prefix` 为空串时拒绝（`IllegalArgumentException`），防止误删整桶。`ObjectStorageTemplate` 照现有方法包重试与指标。

### 7.5 建桶

- `docker-compose.storage.yaml` 的 `minio-init` 加 `mc mb --ignore-existing patra/patra-identity-avatar` + `mc anonymous set none`。
- 运行时兜底：`MinioStorageProvider.upload` 本来就按需建桶（`ensureBucketExists`），第一次上传时桶不存在也能写进去。读取只在资料里有键时才碰 MinIO（有键就一定上传过、桶一定在）；按前缀删遇到桶不存在返回 0。所以服务对建桶时机没有硬依赖。
- **CD 不跑存储栈**：`patra-infra` 规则是 apps 以外的 compose 栈靠 mini 上手动 `compose-all.sh` 生效，`deploy.sh` 只起应用容器。AC「由 CD 保证存在」的实际保证是上面的运行时兜底；mini 上手动跑一次 `minio-init` 作为显式建桶（第 17 节已拍板 3）。

## 8. 接口与流程

### 8.1 `POST /me/avatar`

- 请求：`multipart/form-data`，part 名 `file`。
- 200：`{ "avatarUrl": "/users/chen-yu/avatar?v=3f9a1c0d2b4e5f60" }`。
- 错误：

| 情形 | 状态 | 体 |
|---|---|---|
| 没登录；`BANNED` / `DELETED` | 401 | 现有 |
| `PENDING_DELETION` | 409 | 现有（`traits` 含 `PENDING_DELETION`） |
| 文件超过 5 MB | 413 | ProblemDetail（Spring multipart 上限，见 8.5） |
| 没有 `file` 或为空 | 422 | `errors: [{ field: "file", code: "REQUIRED" }]` |
| 类型不对（含 GIF、动图、伪造扩展名） | 422 | `code: "UNSUPPORTED_TYPE"` |
| 短边 < 128 | 422 | `code: "TOO_SMALL"` |
| 像素超过 50 MP | 422 | `code: "TOO_MANY_PIXELS"` |
| 读不出（头坏、解码失败） | 422 | `code: "UNREADABLE"` |
| MinIO 写失败 | 503 | 不改资料 |

`ChangeAvatarHandler`（**类上不加** `@Transactional`，数据库部分用 `TransactionOperations`）：

```
userId ← currentUserPort.require()
预检（只读）：user ← users.findById；user.requireActive()；current ← profile 的头像
renditions ← avatarImagePort.render(bytes)            // 422 在这里
avatar ← Avatar.of(userId, sha256Prefix(renditions.large))
若 avatar == current → 直接返回（同一张图重复上传：不写对象、不动库、不删）
avatarStorage.store(avatar, renditions)               // 503 在这里
try:
  事务 { user ← users.findByIdForUpdate(userId)；user.requireActive()
         profile ← profiles.findByUserId；previous ← profile.changeAvatar(avatar)；profiles.save }
catch 任何异常:
  best-effort avatarStorage.delete(avatar)（失败只记 warn）；重新抛出
提交后：previous 存在且 ≠ avatar → best-effort avatarStorage.delete(previous)（失败 warn）
log.info("头像已更换: userId={}, hash={}")
返回 avatarUrl（用加锁时读到的当前用户名）
```

- **图片处理与对象写入都在事务外**：慢操作不占用户行锁与数据库连接（identity 已关 OSIV）。
- **先写对象、后提交**：提交失败时删掉新对象；提交成功后删旧对象失败，旧对象成了孤儿，只在 `avatar/{userId}/` 前缀下，注销到期时按前缀一并删掉（8.4）。
- **同一用户并发上传**：两边都写了各自的对象，事务被行锁串行，后提交的把前一个当 `previous` 删掉；结果是最后提交的那张生效，没有残留。
- **预检里读到的 `current` 只用于短路**：真正的 `previous` 以加锁后读到的为准。

### 8.2 `DELETE /me/avatar`

204；没有头像也是 204。401 / 409 同上。`RemoveAvatarHandler`：事务 { 锁用户 → `requireActive()` → `previous ← profile.removeAvatar()` → 保存 } → 提交后 best-effort 删 `previous` 的两档。

### 8.3 `GET /users/{username}/avatar?size=512|128&v={hash}`

公开（网关上 `/users/**` 已公开）。`PublicAvatarController` → `AvatarQueryService`：

| 步骤 | 不满足时 |
|---|---|
| 用户名去空白、转小写，语法合法 | 404 |
| `size` 是 `512` 或 `128`（控制器手工判，不用 Bean Validation：`@Validated` 下会变 422） | 404 |
| `v` 匹配 `^[0-9a-f]{16}$` | 404 |
| `UserReadPort.findAvatarOwner(username)`：**当前**用户名（旧名不跳转）对应的用户存在，状态是 `ACTIVE` 或 `PENDING_DELETION` | 404 |
| 资料里有头像且哈希等于 `v` | 404 |
| `If-None-Match` 命中 `"{hash}-{size}"` | 304（不碰 MinIO） |
| 打开对象 | 对象不存在 404 + `log.warn`（库与对象不一致） |

200 响应头：`Content-Type: image/webp`、`Content-Length`、`ETag: "{hash}-{size}"`、`Cache-Control: public, max-age=31536000, immutable`。返回 `ResponseEntity<Resource>` + `InputStreamResource`，不用 `StreamingResponseBody`（catalog 的 `PortalFullTextFileController` 同理：异步分派有 Tomcat 30 秒超时，`RestTestClient` 也读不到它的体）。

- **`v` 为什么必须带、必须匹配**：URL 上带版本，同一个 URL 的内容才永远不变，`immutable` 才成立；旧版本的 URL 直接 404，不会把新内容缓存在旧地址下。
- **为什么不看主页可见性**（第 17 节已拍板 2）：响应是 `public` 长缓存，不能因请求者而异；门户的转发也不带会话令牌（`gatewayStream` 只带 `If-None-Match`）。能拿到 `v` 的人一定在某个可见的响应里见过它（主页、`/auth/me`），`v` 是 64 位随机量，猜不到。代价：主页改成「仅自己」之后，之前见过地址的人仍能取到那张图，直到用户换头像。
- **邮箱未验证、冷静期的账号**：主页只有本人可见（`audience() == SELF`），`v` 只出现在本人的 `/auth/me` 与本人视角的主页里，别人拿不到地址，「不看可见性」对他们实际上不外泄。
- **为什么 `PENDING_DELETION` 照常下发**：冷静期里本人还能登录（进取消页），导航栏要显示头像。`BANNED` 与 `DELETED` 404：封禁账号的主页本来就 404，头像跟着不出。
- **旧用户名**：404，不跳转。`avatarUrl` 总是用当前用户名生成，改名后响应里的地址随之变化。

### 8.4 PAP-100 接入点

照 PAP-100 spec 第 14 节，不另起一套：

1. `UserProfile.erase()` 里把头像置空（PAP-100 已实现 `erase()`，本 Issue 在同一方法里补一行，并在 `UserProfileTest` 的 `erase` 用例里补断言）。
2. `ExecuteDueAccountDeletionsHandler` 每个用户那一步，在**加锁事务之前**先不加锁复读一次用户，仍是 `PENDING_DELETION` 且已到期才调 `avatarStorage.deleteAll(userId)`；抛异常按该用户失败处理（PAP-100 第 9.3 节的既有分支），下次运行重试。
   - **复读的原因**：`User.cancelDeletion()` 不看是否过期，到期任务先扫出一批 ID 再逐个处理，扫描之后用户仍可能取消注销；不复读就会删光一个已恢复账号的头像对象，而资料里的键还在。复读后剩下「复读与加锁之间」的毫秒级窗口：真撞上时该用户头像读取 404（8.3 的「对象不存在」分支记 warn），重新上传即恢复，不再为它加机制。
3. PAP-100 的到期执行集成测试补断言：执行后 `avatar/{userId}/` 前缀下没有对象。

接入顺序：PAP-100 会话收工、代码提交后再改它的处理器（第 15 节）。

### 8.5 上传大小与 multipart 配置

identity `application.yml`：

```yaml
spring:
  servlet:
    multipart:
      max-file-size: 5MB      # 头像原图上限（release spec 2.1.3），超过 413
      max-request-size: 6MB   # 留出 multipart 边界与表单字段
server:
  tomcat:
    max-swallow-size: 32MB    # 超限时把请求体读完再回 413，客户端看到状态码而不是连接被重置
```

`5MB` 按 Spring `DataSize` 是 5 × 1024 × 1024 字节。超限时 Spring 抛 `MaxUploadSizeExceededException`，`GlobalRestExceptionHandler` 没有重写 `handleMaxUploadSizeExceededException`，框架默认回的 413 不带 Patra 错误码。照它对 `NoResourceFoundException` 的做法在 `patra-tool-web` 里补齐：`HttpStdErrors.Group` 加 `CONTENT_TOO_LARGE()`（`0413`），`WebMvcErrorMappingContributor` 把 `MaxUploadSizeExceededException` 映射过去，`GlobalRestExceptionHandler` 重写 `handleMaxUploadSizeExceededException` 交给 `renderThroughAdapter`。identity 回 `IDN-0413`（`patra.web.error.context-prefix` 是 `IDN`），IT 钉住状态码与 `code`。这是公共面改动，以后任何服务的上传都受益。门户在选文件时已做本地校验（简报 §3.6），413 只是兜底，门户按状态码显示「图片不能超过 5 MB」。

## 9. 响应里的 `avatarUrl` 与 `avatarPalette`

### 9.1 `avatarUrl`

- 形态：identity 自己的相对路径 `/users/{当前用户名}/avatar?v={hash}`，没有头像为 `null`。和 301 的 `Location` 一样是 identity 的相对路径，不含尺寸，不含任何基地址（决策 G）。
- 门户（PAP-101）把它换成自己的同源地址并追加尺寸：例如 `/api/users/{username}/avatar?v={hash}&size=128`，Route Handler 转发到网关 `/patra-identity/users/{username}/avatar?v=…&size=…`，照搬 `fulltext-files.ts` 的头部白名单（`ETag`、`Cache-Control`、`Content-Type`、`Content-Length`）与 304 透传。尺寸选择：36 / 40 px 用 128 档，72 px 及以上用 512 档。
- 生成位置：adapter 层的 `AvatarUrls.of(String username, Avatar avatar)`（URL 是 REST 关注点，不进领域）。

| 响应 | 来源 |
|---|---|
| `CurrentUserResponse`（`/auth/me`，登录、注册、再认证、改密码响应里的 `user`） | `UserAccountReadModel.avatar()`（`Avatar`，可空）+ `username` |
| `PublicUserResponse`（`GET /users/{username}`） | `PublicUserView.avatar()` + `username` |
| `POST /me/avatar` | 处理器返回的 `Avatar` + 当前用户名 |

`GET /me/profile` 不加 `avatarUrl`：头像不随资料表单保存（简报 §3.6「即时生效」），设置页的头像卡片读 `/auth/me` 的 `avatarUrl`，上传成功后用 `POST` 的返回值更新并让 `/auth/me` 的查询失效。

### 9.2 `avatarPalette`

- 领域策略 `AvatarPalette.of(long userId) → int`（0–11）：
  ```java
  long z = userId + 0x9E3779B97F4A7C15L;           // SplitMix64 的最终混合
  z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
  z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
  z = z ^ (z >>> 31);
  return (int) Long.remainderUnsigned(z, 12);
  ```
  用户 ID 是雪花式的长整数，低位随序列号变化、高位随时间变化，直接取模分布会偏；混合一次再取模。**这段算法一经上线不能改**：改了所有人的默认头像都会变色。单元测试钉住几个固定向量。
- `UserAccountReadModel` 加方法 `avatarPalette()`（由 `userId` 算，不加字段）；`PublicUserView` 加字段 `avatarPalette`（`PublicUserReadModel.viewFor` 里算好放进去，视图里仍没有用户 ID）。
- 有没有头像都返回。

响应示例（`/auth/me`）：

```json
{
  "userId": "352303128713027974", "email": "chen.yu@example.com", "emailVerified": true,
  "username": "chen-yu", "displayName": "陈宇",
  "avatarUrl": "/users/chen-yu/avatar?v=3f9a1c0d2b4e5f60", "avatarPalette": 7,
  "status": "ACTIVE", "deletionDueAt": null, "nextUsernameChangeAt": null, "accountType": "user"
}
```

## 10. 网关

不改规则：`IDENTITY_PUBLIC_PATHS` 已含 `/patra-identity/users/**`（注释里就写了头像），`/patra-identity/**` 其余需登录，`/me/avatar` 落在需登录里；代理链已 `headers(disable)`，下游的 `Cache-Control` 原样透传（`GatewayRoutingIT` 已钉）。

**大请求体**：网关（Spring Cloud Gateway WebMVC + Tomcat）对流式转发的请求体没有大小限制，也不缓冲；上限由 identity 自己守（8.5）。不在网关加按路由的上限：只有一个上传接口，两层上限只会出现「网关 413 与服务 413 形状不同」的问题。

补两条 `GatewayAuthenticationIT` / `GatewayRoutingIT` 用例：

1. 匿名 `GET /patra-identity/users/chen-yu/avatar?size=128&v=…` 被转发；WireMock 回 `image/webp` + `Cache-Control: public, max-age=31536000, immutable` + `ETag`，三个头原样到客户端。
2. `POST /patra-identity/me/avatar`：不带令牌 401；带令牌时一个约 5 MB 的 multipart 体被转发，WireMock 收到的 `Content-Length` 与体字节数与发出的一致。

## 11. 表结构（改写 `V1`）

`idn_user_profile` 加列：

```sql
avatar_object_key  VARCHAR(64)  NULL,
…
CONSTRAINT ck_idn_user_profile_avatar_object_key CHECK (
    avatar_object_key IS NULL
    OR avatar_object_key = 'avatar/' || user_id || '/' || substring(avatar_object_key from '[0-9a-f]{16}-512\.webp$')
)
```

（约束要点：前缀里的用户 ID 等于本行 `user_id`，后缀是 16 位小写十六进制哈希加 `-512.webp`；实现时用 IT 验证合法键能写、错用户 ID 与错格式被拒，表达式以实际通过为准。）`COMMENT ON COLUMN` 说明「512 档对象键；128 档同哈希换后缀；NULL 表示默认头像」。

## 12. 领域模型改动

| 位置 | 改动 |
|---|---|
| `UserProfile` | 字段 `avatar`（`Avatar`，可空）；`changeAvatar(Avatar) → Optional<Avatar>`（返回被替换的旧头像，校验 `avatar.userId == this.userId`）；`removeAvatar() → Optional<Avatar>`；`getAvatar() → Optional<Avatar>`；`erase()` 清头像；`restore()` 构建器加 `avatar` |
| `UserAccountReadModel` | 加 `Avatar avatar`（可空）、方法 `avatarPalette()` |
| `PublicUserReadModel` | 加 `Avatar avatar`；`viewFor` 把 `avatar` 与 `AvatarPalette.of(userId)` 放进视图 |
| `PublicUserView` | 加 `Avatar avatar`、`int avatarPalette` |
| `UserReadPort` | 加 `Optional<AvatarOwner> findAvatarOwner(Username)`：`AvatarOwner(long userId, UserStatus status, Avatar avatar)`，沿用 `findPublicIdentity` 的取法（按当前用户名查用户行，再按用户 ID 查资料行），不读链接与关注领域 |
| `AvatarPolicy` | 常量：`MIN_SHORT_SIDE = 128`、`MAX_PIXELS = 50_000_000L`、`WEBP_QUALITY = 0.82f` |
| `AvatarRejection` | 枚举：`REQUIRED`、`UNSUPPORTED_TYPE`、`TOO_SMALL`、`TOO_MANY_PIXELS`、`UNREADABLE`（`name()` 即原因码） |
| `InvalidAvatarException` | `DomainException` + `HasFieldViolations`，字段 `file`，`RULE_VIOLATION` → 422 |

5 MB 不进 `AvatarPolicy`：它由 multipart 配置守（8.5），代码里没有第二处检查。

## 13. 测试策略

命名与位置按 `.claude/rules/testing/conventions.md`；时间走注入的 `Clock`。

### 13.1 单元测试

| 测试 | 覆盖 |
|---|---|
| `AvatarTest` | 键格式、`parse` 拒绝别人的用户 ID 与坏格式、`prefix`、`etag` |
| `AvatarPaletteTest` | 固定向量（几个 ID → 固定色号）；10 万个连续雪花式 ID 每个色号占比在 1/12 ± 5% 内；结果在 0–11 |
| `UserProfileTest` | `changeAvatar` 返回旧值、拒绝别人的头像；`removeAvatar`；`erase` 清头像 |
| `PublicUserReadModelTest` | 视图带头像与色号 |
| `AvatarFormatSnifferTest` | JPEG / PNG / WebP 认得；GIF、APNG（`acTL` 在 `IDAT` 前）、动画 WebP（`VP8X` 动画位、`ANIM`）、纯文本伪装 `.jpg`、空字节、截断头都拒 |
| `AvatarImageAdapterTest` | 测试里现生成图片（ImageIO 写 JPEG / PNG，`webp-imageio` 写 WebP 源图）：三种格式都出 512 / 128 WebP；长方形居中裁方（左右两条色带被裁掉）；带 alpha 的 PNG 输出保留 alpha；短边 127 → `TOO_SMALL`、128 通过；IHDR 声明 8000×8000（IDAT 用 `Deflater` 压一段零）→ `TOO_MANY_PIXELS` 且不解码；头正常、数据截断 → `UNREADABLE`；输入 JPEG 插入含 GPS 的 `APP1 Exif` 段、PNG 带 `iCCP` / `eXIf` 块，输出的 RIFF 块列表里没有 `EXIF` / `XMP ` / `ICCP` |
| `ChangeAvatarHandlerTest` | 预检 409 时不处理图片、不写对象；同图短路；事务失败删新对象；提交后删旧对象；删旧失败不影响返回 |
| `RemoveAvatarHandlerTest` | 没头像 204 语义；提交后删对象 |
| `AvatarQueryServiceTest` | 8.3 表里每一行的 404 / 304 |

### 13.2 集成测试

| 测试 | 环境 | 覆盖 |
|---|---|---|
| `MinioStorageProvider` 的 IT（tool 模块） | MinIO Testcontainers（`pgsty/minio`） | `delete` 幂等；`deleteByPrefix` 只删前缀下的、返回个数、超过 1000 个分批、桶不存在返回 0、空前缀拒绝 |
| `AvatarStorageAdapterIT`（identity-infra） | MinIO | `store` 后两档都在且 `Content-Type: image/webp`；`open` 读回字节一致；`delete`；`deleteAll` 只删本用户 |
| `UserProfileRepositoryAdapterIT` / `UserReadAdapterIT` 扩展 | PG17 | 头像键往返；约束拒绝错用户 ID 与坏格式；`findAvatarOwner` 只认当前用户名、带状态 |
| `MeAvatarControllerIT`、`PublicAvatarControllerIT`（identity-adapter，`@WebMvcTest` + `@Import`） | 切片 | 8.1 / 8.3 的状态码、原因码、响应头、304；`AuthControllerIT`、`PublicUserControllerIT` 改断言：`avatarUrl` 形态、`avatarPalette` 存在且在 0–11 |
| `AvatarFlowIT`（identity-boot，`RANDOM_PORT`） | PG17 + Redis + MinIO | 注册登录 → 上传带 EXIF GPS 的 JPEG → 从 MinIO 直接读回两档：WebP 魔数、512×512 与 128×128（TwelveMonkeys 解码）、RIFF 块里没有 `EXIF` / `XMP ` / `ICCP` → `/auth/me` 与 `/users/{u}` 的 `avatarUrl` 一致 → `GET` 头像 200 头齐全、带 `If-None-Match` 304 → 换一张：旧两档对象不在、资料键更新、旧 `v` 404 → `DELETE`：资料键为空、`GET` 404、对象不在 → 超过 5 MB 的真实 HTTP 上传收到 413；冷静期账号上传 409 |
| PAP-100 到期执行 IT 补断言 | PAP-100 的环境 + MinIO | 执行后前缀下无对象 |
| 网关两条（第 10 节） | WireMock | 公开读、需登录写、大体透传 |

IT 容器：identity 的各 IT 初始化器按 `CatalogITMinIOContainerInitializer` 的写法子类化 `MinIOContainerInitializer`，桶名 `patra-identity-avatar`；新增 IT 上下文较多时查 identity 的 IT 配置是否已限 Hikari 池（计划常见坑：CI 才报 too many clients）。

### 13.3 门控

`./gradlew spotlessApply` → `./gradlew check` → identity 各模块与 gateway-boot、`patra-tool-object-storage` 的 `integrationTest`。新建 `src/integrationTest` 目录或 boot 新依赖后重跑 `dumpModuleGraph` 并提交 `module-graph.json`。`patra-tool-object-storage` 是公共面，CI 会全量重建 6 个服务（预期内）。

## 14. README

identity README：

- 第 1 节模块表：infra 加图片处理与对象存储适配器。
- 第 2 节接口表加三行；`/auth/me` 示例换成带 `avatarUrl`、`avatarPalette` 的；删掉「`avatarUrl` 本版恒为 `null`」。
- 新增一节「头像」（编号接在 PAP-100 新增的那一节之后）：处理流程、库选型与原生访问、桶与对象键、读取的缓存与 `v`、`avatarPalette` 算法不可改、与注销的接入、本地运行（MinIO 来自 `patra-storage` 栈，`minio-init` 建桶）。

`patra-tool-object-storage` 的 README（如有）补两个方法。

## 15. 实施顺序与并发约束

1. PAP-100 会话正在同一检出写 identity 并提交。本 Issue 只有 spec 与 plan 可以先写；**实现前**确认：`git status` 里除本 Issue 的新文件和不属于本 Issue 的未跟踪文档外没有已修改文件，最近一次提交不是几分钟内的 PAP-100 提交；不确定就停下问用户。
2. 第 8.4 节的 PAP-100 接入放在实现的最后，以 PAP-100 实际提交的 `erase()` 与 `ExecuteDueAccountDeletionsHandler` 为准（2026-10-22 写本 spec 时 `erase()` 已提交，处理器还只在暂存区）。
3. 改 `*.gradle.kts` 后提醒用户在 IDEA 里点「加载 Gradle 更改」。

## 16. AC 对照

| AC（Linear PAP-97） | 证据 |
|---|---|
| 超大、非图片、伪造扩展名、短边不足、像素过大分别被拒且有原因码 | 413（`AvatarFlowIT` 真实 HTTP）；`UNSUPPORTED_TYPE` / `TOO_SMALL` / `TOO_MANY_PIXELS` / `UNREADABLE`（`AvatarImageAdapterTest`、`MeAvatarControllerIT`） |
| 上传 JPEG 后对象为 WebP、两档都存在、EXIF（含 GPS）已剥除 | `AvatarFlowIT` 从 MinIO 读回校验 |
| 换头像后旧对象被删、资料键更新；删除后资料键为空、读取 404 | `AvatarFlowIT` |
| 读取响应头含不可变缓存、ETag、`image/webp`，经网关可取到 | `PublicAvatarControllerIT` + 网关 IT 第 1 条；mini 上部署后 `curl` 经网关实测 |
| mini 上桶已创建并由 CD 保证存在 | 第 7.5 节：运行时按需建桶 + `minio-init`；mini 上 `mc ls` 截图（已拍板 3） |
| `./gradlew check` + `integrationTest`（MinIO Testcontainers）全绿 | 第 13.3 节门控输出 |

## 17. 由我定的点与待拍板的点

**已拍板**（2026-10-22 用户定，都按推荐方案）：

1. **WebP 编码用原生库**：Issue 写「纯 Java」，但没有可用的纯 Java WebP 编码器（6.1）。方案：解码纯 Java，编码用 `webp-imageio`（JNI + libwebp，Apache-2.0 / BSD），jar 自带 mini 的 linux aarch64 与 CI 的 linux x86_64 原生库，已实测两种架构可用。替代：输出 JPEG（全纯 Java，体积大约 1.5–2 倍，偏离 release spec 的 WebP）。**定：用 `webp-imageio`。**
2. **头像读取不看主页可见性**（8.3）：按 `v` 版本号当凭证，响应可以 `public` 长缓存。代价是主页改成「仅自己」后，之前见过地址的人仍能取到旧图直到换头像。替代：按请求者判可见性、改 `private` 缓存，门户转发要带会话令牌。**定：不看可见性。**
3. **建桶的「CD 保证」**（7.5）：CD 不跑存储栈；实际保证是对象存储库在第一次上传时按需建桶，`minio-init` 负责新环境的显式建桶。**定：AC 改成「mini 上桶存在（`minio-init` 建，服务首次上传时也会按需建）」。**

**由我定的**（审阅时可推翻）：

1. 桶名 `patra-identity-avatar`；哈希取 512 档字节 SHA-256 前 16 个十六进制字符。
2. 资料列存 512 档的完整对象键（照 release spec「资料里存对象键」），128 档由它推出。
3. `avatarUrl` 是 identity 相对路径 `/users/{u}/avatar?v={hash}`，不含尺寸，门户追加尺寸并换成自己的同源地址。
4. `v` 与 `size` 必填，不匹配一律 404（不回 422）；旧用户名 404 不跳转；`BANNED` 404，`PENDING_DELETION` 照常下发。
5. 像素上限 50 MP；解码按区域 + 下采样封顶内存；源图小于 512 时放大到 512。
6. WebP 有损 q=0.82；有 alpha 保留 alpha；非 sRGB 先转 sRGB。
7. 不按 EXIF 方向旋转（门户 canvas 导出已应用方向）。
8. 超过 5 MB 回 413（multipart 上限），其余拒绝回 422 + 原因码；5 MB 不在代码里再查一遍。
9. 网关不加请求体上限，由 identity 守。
10. 同一张图重复上传短路，不写对象不动库。
11. `GET /me/profile` 不加 `avatarUrl`，设置页用 `/auth/me` 的。
12. 注销到期删对象前不加锁复读一次状态（8.4），剩余的毫秒级窗口接受。
13. 不做孤儿对象定时清扫：只在「提交后删旧对象失败」时出现，都在本用户前缀下，注销时一并删。
14. 不限上传并发。

## 18. 要同步改的文档和 Issue

| 位置 | 改什么 |
|---|---|
| Linear PAP-97 | 开工推到 BE；完成推 Review，评论附 AC 证据；AC 5 措辞按已拍板 3 改 |
| Linear PAP-101 | 「来自 PAP-97 的契约」：三个接口、错误码与简报 §4.6 文案的对应（413 → 过大，`UNSUPPORTED_TYPE` → 类型，`TOO_SMALL` → 过小，`UNREADABLE` / `TOO_MANY_PIXELS` → 读不出），`avatarUrl` 的换址与尺寸选择，转发头白名单，门户导出裁剪结果的建议（边长 min(裁剪边长, 1024)，JPEG q≥0.9 或 PNG） |
| Linear PAP-100 | 本 Issue 已接入第 14 节三条 |
| identity README | 新增「头像」一节 |

## 19. 实施时实测与偏离

| # | 计划 / spec 原写法 | 实际 | 原因 |
|---|---|---|---|
| 1 | 6.2 读取器直接 `new` SPI | JPEG / WebP / PNG 读取器都从 ImageIO 注册表按类名前缀挑，构造时 `scanForPlugins()`；写出器仍直接 `new` | TwelveMonkeys 的 `JPEGImageReaderSpi` 未注册时委托为空（NPE） |
| 2 | 6.3 解码抛异常 → `UNREADABLE` | 解码出现任何告警也算 `UNREADABLE` | 截断的 JPEG 只告警不抛（实测「Truncated File - Missing EOI marker」「premature end of data segment」），会返回半张图 |
| 3 | 6.3 缩放类型 | 有透明时中间图用 `TYPE_INT_ARGB_PRE`，编码前转回 `TYPE_INT_ARGB` | 避免透明边缘发黑 |
| 4 | 7.4 模板记指标 | 删除只包重试、不记指标；新增 `DeleteFailedException` | 现有指标只有上传 / 下载两类，记成下载失败会误导 |
| 5 | 魔数识别「只看前 64 KiB」 | 按块头跳着走全文件，块越过末尾即停；偏移用 `long` | 跳块不读数据；补了块长度溢出的用例（原实现会越界） |
| 6 | 8.5 在 boot 加 `ErrorMappingContributor` | 在 `patra-tool-web` 补齐（第 8.5 节已改） | 框架默认的 413 不经适配器 |
| 7 | `AvatarQueryService` 放 `usecase/avatar/` | 放 `usecase/avatar/query/` | 项目规则 QueryService 在 `app/usecase/{domain}/query/` |
| 8 | 上传切片测试用 `RestTestClient` | 用 `MockMvc.multipart` | `RestTestClient` 走 MockMvc 时不拆 multipart |
| 9 | — | infra 的 IT 补 `spring-boot-starter-validation`；boot 直接依赖 `patra-tool-object-storage`；boot 的 IT 配置给对象存储一个占位端点 | 对象存储配置 `@Validated`；流程 IT 要注入 `ObjectStorageOperations`；不继承 `IdentityFlowITSupport` 的独立 IT 上下文没有 MinIO 也要能装配 |

**原生访问实测**：分层解包后 app jar 的清单仍带 `Enable-Native-Access: ALL-UNNAMED`；同样结构的探针 jar 用 `java -jar` 启动，有属性无告警、无属性有「restricted method」告警。不用改 compose。

**网关**：两条新用例在规则不改的前提下直接通过（公开读透传三个头；上传不带令牌 401，带令牌 5,000,000 字节原样到下游）。

**最终评审修正**（opus 评审，0 Critical / 3 Important 全修、7 Minor 延后）：

| # | 发现 | 修正 | 钉住的用例 |
|---|---|---|---|
| 10 | 灰度图整体发白（中灰 128 → 189）：解码器给 `CS_GRAY`（线性），`ColorConvertOp` 做了 gamma 提升 | 灰度按 sRGB 值复制成 r = g = b，保留 alpha、支持 16 位 | `should_keep_gray_level_of_grayscale_images`（灰度 PNG、灰度加 alpha、灰度 JPEG） |
| 11 | WebP 与渐进式 JPEG 解码内存不受下采样约束（49 MP 的 WebP 堆峰值约 300 MB），「不限并发」的依据不成立 | 像素上限 50 MP → 4096²；出图 2 路并发，等 10 秒不到回 503 | `should_reject_more_than_4096_squared_pixels`、`should_answer_unavailable_when_render_slots_are_busy` |
| 12 | 超过 8 MB 的上传、会话过期的大上传被重置连接，拿不到 413 / 401 | identity 与网关的 `server.tomcat.max-swallow-size` 都设 32MB；超过 32 MB 只靠门户本地校验 | `should_reject_upload_of_20mb_with_413`；网关 20 MB 匿名上传 401 |

延后的 Minor：告警即 `UNREADABLE` 偏宽（直接调接口的带 ICC 告警的 JPEG 会被拒）；PNG 的 `iCCP` 被忽略；并发上传同一张图时提交失败的一方可能删到在用对象；同图短路基于事务外预读；一次上传约三份 5 MB 拷贝；一处测试沿用全类名；两份 MinIO IT 初始化器可合并。
