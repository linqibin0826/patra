# patra-identity

前台用户的账号服务：注册、登录时的凭据校验、登录失败限制、封禁与解封。会话令牌、登录记录、登出、「当前用户」接口在 PAP-64。

工程设计：`docs/patra/specs/2026-10-05-identity-account-design.md`。

## 1. 模块

| 模块 | 内容 |
|---|---|
| `patra-identity-domain` | `User`、`UserPasswordCredential` 两个聚合，邮箱、密码值对象，密码规则、失败限制规则，端口，领域异常 |
| `patra-identity-app` | 注册、登录校验、封禁、解封四个处理器，走 CommandBus |
| `patra-identity-infra` | JPA 持久化、Argon2id 哈希、常见密码名单、Redis 失败限制、Flyway 脚本 |
| `patra-identity-adapter` | 前台的 `AuthController`，后台的 `AdminUserController` |
| `patra-identity-boot` | 启动类、配置 |

## 2. 接口

| 路径 | 成功 | 网关规则（PAP-65） |
|---|---|---|
| `POST /auth/register` | 201 `{ userId, email }` | 公开 |
| `POST /auth/login` | 200 `{ userId, email }` | 公开 |
| `POST /admin/users/{userId}/ban` | 204 | 拒绝外部访问 |
| `POST /admin/users/{userId}/unban` | 204 | 拒绝外部访问 |

`/admin/**` 本版没有身份校验，只能在内网直连调用。

## 3. 账号模型

账号分前台用户（`USER`）和后台账号（`STAFF`，以后建）两类，分开建表；密码和用户也分开，各一张表。理由和调研见工程设计第 4 节。

| 表 | 内容 |
|---|---|
| `idn_user` | ID、规范化之后的邮箱（唯一、只能小写）、状态（`ACTIVE` / `BANNED`）、封禁时间 |
| `idn_user_password_credential` | 用户 ID（唯一）、Argon2id 编码串 |

## 4. 密码

- 规则：8 到 64 个码点，不去空格，不能在常见密码名单里。登录只要求非空、不含孤立代理项；超过 1024 个码点的密码直接按密码错误处理，不做哈希。
- 哈希：Argon2id，`m=19456 KiB, t=2, p=1`，盐 16 字节、输出 32 字节；哈希和校验前都做 NFKC。开发机上单次约 20 毫秒（mini 上的耗时等 PAP-66 部署后实测）。
- 并发：同时进行的哈希计算最多 4 个，排队超过 3 秒返回 503。
- 常见密码名单：`infra/src/main/resources/password/common-passwords.txt.gz`，取自 Django 6.0 的 `django/contrib/auth/common-passwords.txt.gz`（Royce Williams 整理，19640 行），SHA-256 `3c1baed62596de36860824eb3f436d5932d37ca8b06e59df78f5a44ec175afe4`。许可证见同目录的 `common-passwords.LICENSE`（BSD 3-Clause）。比较时对密码做 NFKC、转小写、去掉首尾空白。

## 5. 登录失败限制

- 按「账号类型 + 邮箱」计数，没注册过的邮箱也一样。15 分钟内失败 5 次锁 15 分钟，成功一次清零。
- 只有确认的失败才计数；正在校验的尝试单独登记，「失败数 + 在途数」到上限时返回 429（1 秒），不上锁。
- Redis 键：`idn:login-failures:user:{邮箱 SHA-256}`、`idn:login-inflight:…`、`idn:login-lock:…`，脚本在 `infra/src/main/resources/redis/`。
- 配置：

```yaml
patra:
  identity:
    login-throttle:
      max-failures: 5
      window: 15m
      lock-duration: 15m
      in-flight-ttl: 30s
    password-hashing:
      max-concurrent: 4
      wait-timeout: 3s
```

## 6. 错误码

| 场景 | 状态 | 错误码 |
|---|---|---|
| 字段不合法（`errors[]` 带字段名和原因码） | 422 | `IDN-0422` |
| 邮箱已注册 | 409 | `IDN-0409` |
| 邮箱或密码错误 | 401 | `IDN-0401` |
| 被暂时限制（`Retry-After`、`retryAfterSeconds`） | 429 | `IDN-0429` |
| 账号已被封禁 | 403 | `IDN-0403` |
| 用户不存在（后台接口） | 404 | `IDN-0404` |
| Redis 不可用、哈希排队超时 | 503 | `IDN-0503` |

原因码：邮箱 `REQUIRED`、`TOO_LONG`、`INVALID_FORMAT`；密码 `REQUIRED`、`INVALID_CHARACTER`、`TOO_SHORT`、`TOO_LONG`、`TOO_COMMON`。

## 7. 本地运行和测试

- dev 配置连 mini 上的 `patra_identity` 库和 Redis（`PATRA_INFRA_HOST`）。库由 PAP-66 建，建好之前本地起不来。
- 测试全部用 Testcontainers（PostgreSQL 17、Redis 7.0.15），本机要有 Docker：

```bash
./gradlew spotlessApply && ./gradlew :patra-api:patra-identity:patra-identity-boot:check :patra-api:patra-identity:patra-identity-boot:integrationTest
```

`check` 不包含集成测试，两个任务都要写。
