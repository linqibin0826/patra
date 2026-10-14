# Nacos 加固与升级工程设计（PAP-71）

> **Issue**：[PAP-71](https://linear.app/papertrace/issue/PAP-71)
> **版本**：[v0.9 Config Center](../release-specs/v0.9-config-center.md)（决策 E、G；Done 判定 D6、D9）
> **日期**：2026-10-11（2026-10-12 修订：密钥从 `PATRA_SECRETS` 分组改放独立 namespace `dev-secrets`，见 5.1）
> **状态**：已上线（2026-10-13）

> **2026-10-13 更新**：`dev-secrets` namespace 已在 PAP-74 撤销，密钥不再进 Nacos，见 [Nacos 配置接入 starter 设计](2026-10-13-nacos-config-starter-design.md) 第 4 节。下文关于 `dev-secrets`、`patra-secrets.yaml` 的内容只作历史记录。

## 1. 要解决的问题

v0.9 要把全部配置和密钥放进 Nacos（release spec 决策 E）。但 mini 上的 Nacos 现在不具备存放密钥的条件：

- token 签名密钥和 identity 随公开仓库提交在 `patra-infra/docker/.env` 里。任何人都能用它们签出有效令牌，或者用 identity 请求头跳过全部鉴权。
- 只有一个账号，就是全局管理员 `nacos`，密码还是默认的 `nacos`。6 个服务都用这个管理员账号连接，账号密码明文写在 `.env.common` 里。
- 服务端是 3.0.2，用内嵌 Derby 存储。

本设计在任何配置进入 Nacos 之前完成四件事：
- 把服务端升到 3.2.4，存储改到 PG；
- 轮换已公开的密钥；
- 建立按用途划分的账号和权限；
- 把这些改动安全地切换到 mini 上。

**完成标准**：PAP-71 的 Acceptance Criteria，见第 11.3 节的对照表。

## 2. 范围

做：

- Nacos 服务端从 3.0.2 升到 3.2.4。
- 存储从内嵌 Derby 改为业务 PG 里的 `patra_nacos` 库。
- 重新生成 token 签名密钥和 identity，移到仓库外。
- 管理员改用强密码；新建应用账号 `patra-app` 和推送账号 `patra-push`，配好角色和权限。
- 建 `dev`、`dev-secrets` 两个 namespace：非敏感配置放 `dev`，密钥放 `dev-secrets`。
- 两个脚本：幂等的初始化脚本 `nacos-init.sh`、验收脚本 `nacos-verify.sh`。
- 6 个服务改用 `patra-app` 连接：容器和本机进程都改。
- 文档：patra-infra 的 CLAUDE.md 和 README，根 CLAUDE.md 安全红线。

不做：

- 配置导入、`spring.config.import`、starter（PAP-74）。
- 推送脚本和 CD 接入（PAP-75）。本 Issue 只建好推送账号。
- 往 Nacos 写任何配置或密钥（PAP-76、PAP-77）。
- 备份（release spec 范围边界 I）。
- 更换 PG 超级用户密码（第 4 节决定 4）。

## 3. 现状事实（2026-10-10 核对）

1. mini 上的 Nacos：版本 3.0.2，`datasource_platform` 为空，也就是内嵌 Derby。数据在 bind mount 的 `~/.patra/docker/nacos/data`。
2. Nacos 里只有一个用户 `nacos`（`ROLE_ADMIN`），默认密码能登录。没有任何权限记录、配置，只有 `public` 一个 namespace。注册着 6 个服务的临时实例。没有需要迁移的数据。
3. `docker-compose.core.yaml` 用 `environment` 把 `.env` 里的 `NACOS_AUTH_TOKEN`、`NACOS_AUTH_IDENTITY_KEY`、`NACOS_AUTH_IDENTITY_VALUE` 插值进容器。这三个值随仓库提交。
4. `.env.common` 提交了 `NACOS_USERNAME=nacos`、`NACOS_PASSWORD=nacos`。6 个服务的 `application.yml` 读 `${NACOS_USERNAME:nacos}`、`${NACOS_PASSWORD:nacos}`。
5. 本机进程的密钥由 dev profile 用 `spring.config.import` 从 `~/.patra/secrets/*.env` 导入，不加 `optional:`。catalog、identity、gateway 已经这样导入 `redis.env`；registry、object-storage、ingest 的 dev profile 还没有 import 段。
6. 业务 PG 的超级用户是 `postgres`，密码取 `${POSTGRES_PASSWORD:-123456}`，`.env` 没有定义这个变量，所以实际是公开的 dev 默认值。init 脚本只在数据目录为空时执行。
7. SCA 2025.1.0.0 带的客户端是 nacos-client 3.1.1。

## 4. 已定的决定（2026-10-10）

1. **服务端升到 3.2.4**，取代原定的 3.1.x。原因：3.1 线已停止维护，3.1.2 按 id 操作配置时不检查 namespace。release spec 决策 G 已改定。
2. **账号模型**：
   - 应用账号只给服务注册读写和配置只读；
   - 推送账号只能写 `dev` 的 `DEFAULT_GROUP`；
   - 密钥放进单独的 namespace `dev-secrets`：应用账号只读，推送账号没有任何权限，只有管理员能改。原定的「单独分组 `PATRA_SECRETS`」挡不住推送账号（第 5.1 节），2026-10-12 改定。
3. **存储改为 PostgreSQL**，用现有业务 PG，新建 `patra_nacos` 库。
4. **Nacos 用 `postgres` 超级用户连 PG**，和其他服务一样；**PG 超级用户密码本版不换**。代价是密钥进入 Nacos 以后，在 tailnet 内可以用公开的 PG 密码绕过 Nacos 鉴权直接读到。这一条已写进 release spec 决策 E 的已知代价。
5. **不做备份**。
6. **PAP-71 完成后直接推送 main 并上线**，之后的 Issue 继续在 `feat/v0.9-config-center-api` 上开发。

## 5. 本机实测结论（2026-10-10）

在本机用 `nacos/nacos-server:v3.2.4` 加临时 `postgres:17` 实测，以下都已确认：

| 项 | 结果 |
|---|---|
| 只用环境变量指定 PG 存储 | 能启动；状态接口返回 `datasource_platform=postgresql`；连接数受连接池上限约束（5） |
| 删掉容器后重建 | 账号、权限、配置都还在，数据确实在 PG 里 |
| 换 token 签名密钥后重建 | 旧令牌返回 `Code: 401, Message: Invalid signature` |
| 用默认密码登录管理员 | 失败 |
| 已有管理员时再调初始化接口 | 响应体是 `{"code":409,...}`，但 **HTTP 状态码是 200**；管理员密码不受影响 |
| 应用账号，走 nacos-client 3.1.1 的 gRPC | 在 public 注册和查询服务成功；读 dev 配置成功（含 `PATRA_SECRETS`）；`publishConfig` 返回 `false`；在 dev 注册返回 403 |
| 应用账号，走 HTTP | 发布 dev 或 public 配置都返回 403 |
| 推送账号 | 能发布 `DEFAULT_GROUP`；改写、删除 `PATRA_SECRETS` 返回 403；不带 group 发布返回 403；写 public 返回 403；按 id 批量删除返回 403（带不带 namespace 都一样）；能读 `PATRA_SECRETS`。以上请求都没带 `groupName`；导入和批量删除带上它就能绕过分组限制，见 5.1 |
| 启动脚本的 `set -x` | token 签名密钥出现在 `docker logs` 里 |

由此定下两条脚本约定：
- 返回 `Result` 的接口，判断成败看响应体的 `code`，HTTP 状态码不可靠；登录接口例外，没有 `code`，看 HTTP 200 且 `accessToken` 非空（见 11.1）。
- 推送时必须显式带 `groupName`。

### 5.1 补充实测：分组挡不住推送账号，namespace 能挡住（2026-10-12）

最终评审发现、本机 3.2.4 实测确认：Nacos 鉴权取的是请求参数里的 namespace 和 `groupName`，而下面两个接口实际操作的分组不来自这个参数。推送账号只要在 `dev` 有 `DEFAULT_GROUP` 的写权限，带上 `groupName=DEFAULT_GROUP` 就能动 `dev` 里任何分组：

| 推送账号的请求 | 结果 |
|---|---|
| `POST /v3/admin/cs/config/import`，`namespaceId=dev`、`groupName=DEFAULT_GROUP`，zip 里放 `PATRA_SECRETS/patra-secrets.yaml`，覆盖模式 | HTTP 200，密钥被改写（不带 `groupName` 时 403） |
| `DELETE /v3/admin/cs/config/batch`，`namespaceId=dev`、`groupName=DEFAULT_GROUP`，`ids` 为密钥的 id | HTTP 200，密钥被删除（不带 `groupName` 时 403） |

所以密钥改放独立的 namespace `dev-secrets`。这几个接口按 namespace 做鉴权，也按 namespace 取数据或写入，namespace 级的隔离是可靠的。按第 7 节的权限实测，密钥放在 `dev-secrets / DEFAULT_GROUP / patra-secrets.yaml`：

| 请求 | 结果 |
|---|---|
| 应用账号读密钥 | 成功 |
| 应用账号写密钥；从 `dev-secrets` 克隆到 `dev` | 403 |
| 推送账号对 `dev-secrets` 的读、写、删、列表、导出、按 namespace 查历史 | 403 |
| 推送账号导入，`namespaceId=dev-secrets` 带 `groupName` | 403 |
| 推送账号导入，`namespaceId=dev` 带 `groupName` | 200，只写进 `dev`，`dev-secrets` 不变 |
| 推送账号批量删除，`namespaceId=dev` 带 `groupName`、`ids` 为密钥的 id | 200，按 namespace 过滤后什么也没删 |
| 推送账号克隆，源 `dev-secrets`、目标 `dev` | 403（克隆会检查源 namespace 的读权限） |
| 推送账号导出，`namespaceId=dev`、`ids` 为密钥的 id | 200，zip 里没有密钥 |
| 推送账号按 `nid` 查密钥的历史版本，`namespaceId=dev` | 403 |

## 6. 服务端

### 6.1 compose

`docker-compose.core.yaml` 的 nacos 服务改成下面这样，postgres 和 redis 不变：

```yaml
  nacos:
    image: nacos/nacos-server:v3.2.4
    container_name: patra-nacos
    # 固定 hostname：容器重建后 hostname 一变，jraft 身份对不上，会选不出 leader
    hostname: patra-nacos
    restart: unless-stopped
    depends_on:
      postgres:
        condition: service_healthy
    env_file:
      # token 签名密钥与 identity，放在仓库外（见 README「密钥」）。缺失时 compose 拒绝执行
      - path: ${HOME}/.patra/secrets/nacos-server.env
        required: true
    environment:
      - MODE=standalone
      - PREFER_HOST_MODE=hostname
      - NACOS_AUTH_ENABLE=true
      # 存储：业务 PG 里的 patra_nacos 库（官方 PG 插件，镜像自带）
      - SPRING_DATASOURCE_PLATFORM=postgresql
      - DB_URL_0=jdbc:postgresql://postgres:5432/patra_nacos?currentSchema=public&ApplicationName=nacos&connectTimeout=5&socketTimeout=30&tcpKeepAlive=true&sslmode=disable
      - DB_USER_0=postgres
      - DB_PASSWORD_0=${POSTGRES_PASSWORD:-123456}
      # 不显式指定时回落到 MySQL 驱动，启动失败
      - DB_POOL_CONFIG_DRIVERCLASSNAME=org.postgresql.Driver
      - DB_POOL_CONFIG_MAXIMUMPOOLSIZE=5
      - DB_POOL_CONFIG_MINIMUMIDLE=1
      - DB_POOL_CONNECTION_TIMEOUT=5000
      - JVM_XMS=256m
      - JVM_XMX=256m
      - TZ=Asia/Shanghai
    ports: （不变：8848、9848、8080）
    volumes: （不变：data、logs）
    healthcheck: （不变：8080 的 /v3/console/health/readiness）
```

要点：

- **`environment` 里不能再写三项鉴权变量。** compose 中 `environment` 的优先级高于 `env_file`，插值为空的 `NACOS_AUTH_TOKEN=` 会盖掉文件里的值，镜像脚本发现为空就直接 `exit 255`。
- **PG 密码沿用 `POSTGRES_PASSWORD`**，和 postgres 服务的默认值写法一致，以后换 PG 密码时一起生效。
- 数据卷继续挂载：外部存储模式下，`data` 目录仍会存放服务发现的持久化数据和 jraft 元数据。
- 3.2.4 的健康检查在服务未就绪时返回 500，原来的 `curl -f` 写法终于有效了。PG 断开时，readiness 也会返回 500。
- 鉴权缓存保持默认的关闭状态：权限改动立即生效。代价是 PG 短暂不可用时，鉴权也会失败。

### 6.2 建库建表

Nacos 不会自动建表，要执行官方的 `pg-schema.sql`。这个脚本每张表都是先 `DROP TABLE IF EXISTS` 再建，在已有数据的库上重跑会清空数据。

**全新部署**（PG 数据目录为空时，init 脚本自动执行）：

- `postgres/init-scripts/02-create-databases.sql` 增加一行 `CREATE DATABASE patra_nacos;`。
- 新增 `postgres/init-scripts/nacos/pg-schema-3.2.4.sql`：从 3.2.4 镜像的 `/home/nacos/conf/pg-schema.sql` 原样复制，Apache 2.0 许可，文件头注明来源和版本。
  - 放在子目录里，是因为 postgres 镜像只执行 init 目录顶层的文件，子目录会被忽略。
  - 顶层的 `.sql` 会对默认库执行，所以不能直接放在顶层。
- 新增 `postgres/init-scripts/03-nacos-schema.sh`：
  - **先判断库的状态**：从 schema 文件里解析出全部 `CREATE TABLE` 的表名（3.2.4 共 13 张），再查 `patra_nacos` 里已有其中几张：
    - 0 张：空库，执行建表；
    - 全部都有：已建好，打印提示并跳过，防止误跑清空数据；
    - 只有一部分：报错并返回非零，提示恢复方法（`DROP DATABASE patra_nacos;` 后重建库、再执行本脚本）。正常流程下不会出现这种状态，见下一条；出现了说明有人手动改过库，或者 schema 文件换了版本。
  - **建表整体放在一个事务里**：先用 `grep -v -E '^(BEGIN|COMMIT);[[:space:]]*$'` 去掉官方脚本里空的 `BEGIN;`/`COMMIT;` 行，再交给 `psql -v ON_ERROR_STOP=1 --single-transaction -U "${POSTGRES_USER:-postgres}" -d patra_nacos`。PG 的 DDL 支持事务，中途失败会整体回滚，库回到空状态，直接重跑即可。
    - 不去掉那些行是不行的：文件里的 `COMMIT;` 会提前提交外层事务，后面的语句又回到逐条自动提交。本机实测，在建 `users` 表前注入一条错误：不过滤时留下 9 张表；过滤后为 0 张。正常执行时建出 13 张表。
    - 过滤只发生在执行时，仓库里的 schema 文件保持与镜像一致。
  - schema 文件路径默认是 `nacos/pg-schema-3.2.4.sql`，可用环境变量 `NACOS_SCHEMA_FILE` 覆盖，只供第 11.3 节的中途失败测试使用。
  - 文件提交为可执行（git 记 755），各分支用 `if` 包住，不写 `exit`：postgres 镜像对不可执行的 `.sh` 是用 source 方式执行的，脚本里一个 `exit` 就会终止整个初始化。报错分支用 `return 1 2>/dev/null || exit 1`，source 和直接执行两种方式下都能返回非零。

**mini**：PG 已有数据，init 脚本不会执行，所以手动执行一次（第 10 节第 4 步）：

```bash
docker exec patra-postgres psql -U postgres -c 'CREATE DATABASE patra_nacos;'
docker exec patra-postgres bash /docker-entrypoint-initdb.d/03-nacos-schema.sh
```

直接复用 03 脚本，不另写一份建表命令。它自带的状态判断让重复执行也是安全的。

### 6.3 升级 Nacos 时

以后升级 Nacos 版本，要对比新旧两个 tag 的 `pg-schema.sql`，手动执行差异部分。升级到 3.3 时，数据库相关的环境变量要改名为 `NACOS_PLUGIN_DATASOURCE_DB_*`：3.3 镜像的配置文件改用新键，新键优先于旧的 `db.*`。这两条写进 README 的 Nacos 一节。

## 7. 账号与权限

| 账号 | 角色 | 权限（resource / action） | 用途 |
|---|---|---|---|
| `nacos` | `ROLE_ADMIN` | 全部 | 初始化脚本、应急控制台；写入和修改密钥 |
| `patra-app` | `PATRA_APP` | `public:*:naming/*` / `rw`<br>`dev:*:config/*` / `r`<br>`dev-secrets:*:config/*` / `r` | 6 个服务注册与发现、读配置、读密钥 |
| `patra-push` | `PATRA_PUSH` | `dev:DEFAULT_GROUP:config/*` / `rw`<br>`dev:*:config/*` / `r` | PAP-75 的推送；只读权限用来读回校验。在 `dev-secrets` 没有任何权限 |

- 3.x 的管理员用户名固定为 `nacos`，只能改密码。
- 资源串按 `{namespace}:{group}:{config|naming}/{name}` 做正则匹配。`public:*:naming/*` 这类写法只能通过 API 写入，控制台的权限页可能显示不正常，但不影响生效（第 5 节）。
- **namespace**：`dev` 放非敏感配置，`dev-secrets` 放密钥，ID 和名称相同。namespace 按「环境 × 敏感度」划分。服务发现留在 `public`（release spec 范围边界 G）。
- **分组约定**：两个 namespace 都只用 `DEFAULT_GROUP`；密钥只有一份 `patra-secrets.yaml`。不要给推送账号或以后的任何非管理员账号在 `dev-secrets` 里加写权限：分组级的限制挡不住导入和批量删除（第 5.1 节）。
- 改密码或删用户，不会作废已经签出的令牌，令牌在过期前一直有效（默认 18000 秒）。要立刻撤销，只能换 token 签名密钥后重建容器。

## 8. 密钥文件

目录 `~/.patra/secrets/`，权限 0700；文件权限 0600。沿用 PAP-66 的 `KEY=VALUE` 格式，同时满足 compose 的 `env_file` 和 Java properties。

| 文件 | 内容与生成方式 | 放在哪 | 谁加载 |
|---|---|---|---|
| `nacos-server.env` | `NACOS_AUTH_TOKEN`：`openssl rand -base64 32`（解码后必须至少 32 字节）<br>`NACOS_AUTH_IDENTITY_KEY`：`openssl rand -hex 16`<br>`NACOS_AUTH_IDENTITY_VALUE`：`openssl rand -hex 32` | mini | core 栈的 nacos（必需） |
| `nacos-admin.env` | `NACOS_ADMIN_PASSWORD`：`openssl rand -hex 24` | mini；另存一份在密码管理器 | `nacos-init.sh`、`nacos-verify.sh` |
| `nacos.env` | `NACOS_USERNAME=patra-app`<br>`NACOS_PASSWORD`：`openssl rand -hex 24` | 两台机器，内容相同 | 6 个应用容器（必需）；6 个服务的本机进程；两个脚本 |
| `nacos-push.env` | `NACOS_PUSH_USERNAME=patra-push`<br>`NACOS_PUSH_PASSWORD`：`openssl rand -hex 24` | 两台机器，内容相同 | 两个脚本；PAP-75 的推送 |

- identity 请求头匹配上就会跳过全部鉴权，等同于万能钥匙，所以 key 和 value 都用随机值，和 token 签名密钥同等对待。
- `nacos.env` 沿用应用现在就在读的变量名 `NACOS_USERNAME`、`NACOS_PASSWORD`，所以 `application.yml` 不用改。
- 仓库里放四个模板 `patra-infra/docker/secrets/nacos-*.env.example`，写明生成命令和权限。现有的 `.gitignore` 已经忽略 `secrets/` 下除 `*.example` 外的所有文件。
- 旧的 token 签名密钥和 identity 永远留在公开仓库的 git 历史里。轮换以后它们就作废了，不清理历史。

## 9. 应用侧

### 9.1 容器

`docker-compose.apps.yaml` 里 6 个后端服务的 `env_file`，都在 `.env.common` 之后加一项：

```yaml
      - path: ${HOME}/.patra/secrets/nacos.env
        required: true
```

文件头注释里的密钥清单补上 `nacos.env`。

`.env.common` 删掉 `NACOS_USERNAME`、`NACOS_PASSWORD`，保留 `NACOS_HOST`、`NACOS_PORT`。注释说明账号来自 `nacos.env`。

### 9.2 本机进程

6 个服务的 `application-dev.yml` 都导入 `nacos.env`，不加 `optional:`：

```yaml
spring:
  config:
    import:
      - file:${user.home}/.patra/secrets/nacos.env[.properties]
```

- catalog、identity、gateway 已经有 import 段，加一行即可。
- registry、object-storage、ingest 新增 import 段。
- 测试用 `test` profile，不加载 dev 配置，CI 不受影响。

`application.yml` 里的 `${NACOS_USERNAME:nacos}`、`${NACOS_PASSWORD:nacos}` 这次不改：
- 容器缺 `nacos.env` 时 compose 会拒绝执行，本机缺文件时导入就会失败，默认值根本用不到。
- 这几段配置会被 PAP-74 的 starter 整体取代。

## 10. 切换步骤

推送 main 会触发 CD：apps compose 和 6 个服务的 dev profile 都有改动，CD 会重新构建并部署这 6 个服务，它们会立刻用 `patra-app` 去连 Nacos。所以**先在旧的 3.0.2 上把账号建好**，让 CD 部署出来的服务能正常注册，再切换服务端。3.0.2 的 v3 用户、角色、权限、namespace 接口和 3.2.4 一致（第 3 节第 2 条的查询就是用 v3 接口做的）。

1. **生成密钥文件**：在本机按第 8 节生成 4 个文件，用 scp 放到 mini。管理员密码另存进密码管理器。
2. **在旧服务端上预建账号**：在本机对 mini 现有的 3.0.2 运行 `nacos-init.sh`，地址指向 mini，管理员密码临时用默认的 `nacos` 覆盖。建好 `dev` namespace、两个账号和权限。同时以旧管理员登录一次，把令牌记在当前终端的变量里，留给第 7 步验证。
3. **推送 main**：CD 用 `patra-app` 重新部署 6 个服务，它们注册到旧 Nacos，确认 6 个服务都在。
4. **在 mini 上切换服务端**：
   1. `git pull`；
   2. 按 6.2 节建库建表；
   3. 停掉 nacos，清空 `~/.patra/docker/nacos/data`，里面的旧 Derby 和 jraft 数据都不要了；
   4. `compose-all.sh up core`，以 3.2.4、PG 存储、新密钥重建 nacos。
5. **初始化**：在 mini 上运行 `nacos-init.sh`。新服务端没有管理员，脚本用 `nacos-admin.env` 里的密码初始化，再建 namespace、账号和权限。
6. **重启应用**：重启 6 个应用容器，再重启本机正在跑的服务进程。客户端在令牌失效后会自动重新登录、重新注册，统一重启是为了让结果确定。
7. **验收**：在 mini 上运行 `nacos-verify.sh`，再补做两项脚本做不了的检查：
   - 第 2 步记下的旧令牌访问被拒；
   - 门户的浏览、登录走一遍。

从第 4 步开始到第 6 步结束，服务发现不可用，网关会找不到下游，这在 dev 环境可以接受。

## 11. 脚本、测试与验证

### 11.1 `nacos-init.sh`

在 `patra-infra/scripts/` 下，bash 编写，幂等。

- **输入**：地址取 `NACOS_ADDR`，默认 `http://127.0.0.1:8848`，按惯例不用 localhost。凭据从 `~/.patra/secrets/` 下的三个文件读取：`nacos-admin.env`、`nacos.env`、`nacos-push.env`。环境变量里已有同名变量时优先用环境变量，对应的文件可以不存在；第 10 节第 2 步在本机运行，就靠这个提供管理员密码（本机没有 `nacos-admin.env`）。
- **步骤**：
  1. 等待就绪：探测控制台端口的 readiness，超时报错退出。控制台端口由 `NACOS_CONSOLE_ADDR` 指定，默认 `http://127.0.0.1:8080`。
  2. 初始化管理员：调 `POST /nacos/v3/auth/user/admin`。返回 `code` 为 0 表示初始化成功，409 表示已经有管理员，其他值报错。
  3. 用管理员登录，拿到令牌。
  4. 建 `dev`、`dev-secrets` 两个 namespace，已存在则跳过。
  5. 建两个账号。已存在时先用文件里的密码登录，登录不了才用 `PUT /nacos/v3/auth/user` 把密码重置为文件里的值，这样以后换密码只需要改文件、再跑一遍脚本。先试登录是因为 3.0.2 上这个接口对管理员也返回 403（见第 12 节第 2 条），密码没变时跳过重置，脚本在旧版上也能重跑。
  6. 绑定角色，已绑定则跳过。
  7. 同步权限：两个角色的权限以第 7 节为准，缺的补上，多的删掉。
- **约定**：
  - **参数放哪里**：镜像开着 access log，会记录查询串，所以按敏感程度分：
    - 令牌放在 `Authorization: Bearer` 请求头里；
    - 密码、配置正文这类敏感值只走 POST / PUT / DELETE 的表单请求体；
    - GET 查询的条件（如权限列表的 `pageNo`、`pageSize`、`role`，namespace 列表）放在 URL 上。Spring 不解析 GET 的表单请求体，`GET /nacos/v3/auth/permission/list` 的 `pageNo`、`pageSize` 又是必填，不放 URL 查询会失败。这些值不敏感，进 access log 也没关系。
  - **判断成败**分两类：
    - 登录接口 `POST /nacos/v3/auth/user/login` 不返回 `Result` 包装，成功时响应体是 `accessToken`、`tokenTtl`、`globalAdmin`、`username` 几个字段，没有 `code`。判断条件是 HTTP 200 且 `accessToken` 非空。3.2.4 密码错误返回 HTTP 403；3.0.2 的实现直接抛异常，状态码以第 12 节的实测为准。脚本只认"200 且有令牌"为成功，其余一律算失败。
    - 其他接口都返回 `Result`，看响应体的 `code`：很多错误的 HTTP 状态码仍是 200，只看状态码会漏判。
  - 任何一步失败都立即退出，返回非零。
  - 不打印任何密码和令牌。

### 11.2 `nacos-verify.sh`

把第 5 节的实测固化下来，逐条打印 PASS 或 FAIL，任意一条 FAIL 就返回非零。输入和 `nacos-init.sh` 相同。

| # | 检查 | 期望 |
|---|---|---|
| 1 | 状态接口（不需要鉴权） | `version=3.2.4`、`datasource_platform=postgresql`、`auth_enabled=true` |
| 2 | 管理员用默认密码登录 | 失败 |
| 3 | 管理员用文件里的密码登录 | 成功 |
| 4 | namespace 列表 | 包含 `dev`、`dev-secrets` |
| 5 | `patra-app` 读 `dev`、`dev-secrets` 里各一份探针配置 | 成功 |
| 6 | `patra-app` 发布 `dev`、`dev-secrets`、`public` 的配置 | 都被拒绝 |
| 7 | `patra-app` 在 public 注册、再注销一个探针实例 | 成功 |
| 8 | `patra-app` 在 dev 注册探针实例 | 被拒绝 |
| 9 | `patra-push` 发布、再删除 `DEFAULT_GROUP` 里的探针配置 | 成功 |
| 10 | `patra-push` 读、写、删 `dev-secrets` 里的探针；带 `groupName=DEFAULT_GROUP` 导入到 `dev-secrets`；在 `dev` 批量删除探针的 id；把探针从 `dev-secrets` 克隆到 `dev` | 都被拒绝或无效，探针内容不变、`dev` 里没有它的副本 |
| 11 | 可选参数 `--expect-services`：public 下的服务列表 | 包含 6 个 patra 服务 |

- 探针配置名为 `nacos-verify-probe.yaml`，探针服务名为 `nacos-verify-probe`。第 5、10 项需要的探针配置由脚本先用管理员发布，最后删除。
- 第 10 项覆盖第 5.1 节发现的绕过路径。导出、查历史两条路径在第 5.1 节实测过，不放进验收。
- 第 7、8 项走 HTTP 管理接口。gRPC 路径已在第 5 节用真实客户端实测，切换后再由 6 个服务实际注册来确认。

### 11.3 测试方式

这次改的是基础设施脚本和配置，不涉及应用运行时代码，所以不走 TDD 的单元测试。脚本先在本机临时容器上验证通过，再上 mini：

1. 本机起临时的 `postgres:17` 和 `nacos/nacos-server:v3.2.4`，用 03 脚本建表。
2. `nacos-init.sh` 连跑两次，两次都成功，而且第二次没有产生新的权限记录。
3. `nacos-verify.sh` 全部 PASS。
4. **反向检查**：手动把 `PATRA_PUSH` 的权限放宽成 `dev:*:*` / `rw`，`nacos-verify.sh` 必须报出第 10 项 FAIL。再跑一次 `nacos-init.sh`，它应当删掉多余的权限、恢复原状。
5. 03 脚本的三种库状态都要验证：
   - 已建好的库上重复执行：跳过，表里的数据不变；
   - 中途失败：把 schema 文件换成一份在建 `users` 表前插入错误语句的副本来执行，脚本返回非零，库里一张 Nacos 表都没有；换回原文件重跑，建表成功；
   - 部分状态：在已建好的库上手动 `DROP TABLE users`，再执行脚本，必须报错、返回非零并打印恢复方法，其余表不被改动。
6. 后端测试通过。6 个服务的 dev profile 有改动，要确认 test profile 不受影响：
   - `./gradlew check`；
   - 再跑带 `@SpringBootTest` 启动测试的两个 boot 模块：`./gradlew :patra-api:patra-gateway-boot:integrationTest :patra-api:patra-identity:patra-identity-boot:integrationTest`。`check` 不包含 `integrationTest`，只跑 `check` 证明不了应用能用 test profile 启动。其余 4 个 boot 模块没有 `integrationTest` source set。
7. `bash patra-infra/cd/detect-changes.test.sh` 通过。还要确认新增和修改的路径路由符合预期：
   - core compose、postgres init 脚本、`secrets/*.example`、`scripts/*` 只算基础设施改动，不触发构建；
   - apps compose、`.env.common` 触发 6 个服务全量重建。

临时容器和测试密钥都放在会话的 scratchpad 里，不进仓库。

**AC 对照**

| AC | 怎么验证 |
|---|---|
| mini 上 Nacos 是 3.2.4，6 个服务注册与发现正常 | `nacos-verify.sh --expect-services` 第 1、11 项；门户能走通 |
| 仓库里没有 token 签名密钥和 identity；旧密钥签出的令牌被拒绝 | `git grep NACOS_AUTH_TOKEN=` 只剩模板里的空值；第 10 节第 7 步的旧令牌检查 |
| 默认管理员密码登录不了；应用账号发布配置被拒；推送账号能发布 `DEFAULT_GROUP`、动不了 `dev-secrets` | 第 2、6、9、10 项 |
| `dev`、`dev-secrets` 两个 namespace 存在；数据在 PG 的 `patra_nacos` 库里 | 第 1、4 项；`psql -d patra_nacos -c 'select count(*) from users'` 返回 3 |

## 12. 实施时要实测的点

第 1～3 条已在本机临时容器上实测（PAP-71 实施，2026-10-12），第 4 条在上线时观察（2026-10-13）。

1. 3.0.2 上 `nacos-init.sh` 可用：管理员初始化接口在已有管理员时返回 409；namespace、用户、角色、权限接口与 3.2.4 一致。登录成功时返回的字段相同（`accessToken`、`globalAdmin`、`tokenTtl`、`username`）；密码错误时 3.0.2 返回 HTTP 500，3.2.4 返回 HTTP 403。脚本只认「HTTP 200 且 `accessToken` 非空」，两种都判为失败。按第 10 节第 2 步的方式（本机只有 `nacos.env`、`nacos-push.env`，管理员密码用默认值）执行后，`nacos-verify.sh` 只有第 1、2 项失败（版本、默认密码），这两项在旧服务端上本就该失败。密钥改放 `dev-secrets` 后（第 5.1 节），第 10 项在 3.0.2 上也失败：3.0.2 的批量删除不检查 namespace，推送账号能删掉 `dev-secrets` 里的探针。2026-10-13 在 mini 的 3.0.2 上执行，结果 7 通过 / 3 失败，与此一致。
2. 不依赖「重复建用户」的返回：脚本先查用户列表，已存在的账号先用文件里的密码登录，登录不了才用 `PUT /v3/auth/user`（`username`、`newPassword`）重置。3.2.4 上实测：PUT 的表单请求体会被解析，新密码生效、旧密码失效。3.0.2 上这个 PUT 对管理员令牌也返回 403：`hasPermission` 在上下文里已有用户时不调用 `hasGlobalAdminRole`，全局管理员标记一直是 false，3.2.4 已修。所以在 3.0.2 上脚本可以重跑（密码没变），但改不了密码，改密码要等升级之后。
3. `DELETE /v3/auth/permission` 的参数是 `role`、`resource`、`action`，与源码一致。3.2.4 上实测删除多余权限成功。
4. 切换后，SCA 的服务发现健康指示器会不会让应用的 actuator health 变成 DOWN，进而触发 CD 回滚。结论：不会。CD 部署时连的是已建好账号的旧服务端，6 个容器全部 healthy，没有回滚；切换到 3.2.4、初始化并 `docker restart` 后，6 个服务的 actuator health 都是 `UP`，`nacos-verify.sh --expect-services` 11 项全部通过。服务发现从停掉旧服务端到应用恢复中断约 3 分钟。
   - 另见：mini 从 Docker Hub 拉 `nacos-server:v3.2.4` 时，compose 预演第一次 i/o timeout，单独 `docker pull` 一次即成功，属间歇性超时。

## 13. 已知风险（接受）

- **`set -x` 泄露**：镜像启动脚本开着 `set -x`，token 签名密钥和 identity 会出现在 `docker logs` 里，能登录 mini 的人都看得到。nacos-docker 在 3.3 已改为 `set -e`。
- **PG 超级用户密码公开**：第 4 节决定 4。PAP-76 往 Nacos 写入密钥以后，这个风险才真正出现。
- **3.2.4 上的 PG 插件小问题**：
  - 控制台的用户、权限列表不分页，我们只有三个账号，没影响；
  - 并发发布同一份配置可能报 500，推送是串行的，碰不到。
- **不做备份**：Nacos 数据丢了，密钥就要重新生成后写回。

## 14. 文档

- **`patra-infra/CLAUDE.md`**：
  - 非显性约束第 4 条改写：Nacos 鉴权密钥和账号不再随仓库提交，改放 `~/.patra/secrets/`；
  - 「环境文件三层」一条补上 `nacos.env`；
  - scripts 表加上 `nacos-init.sh`、`nacos-verify.sh`。
- **`patra-infra/docker/README.md`**：
  - 首次部署的第 7 步，从「curl 初始化管理员为 nacos」改为运行 `nacos-init.sh`；
  - 「MacBook 应用访问 Nacos」一节说明本机进程需要 `nacos.env`；
  - 「密钥」表加上 4 个文件；
  - 服务清单里的 Nacos 控制台注明用管理员登录；
  - Nacos 一节写明 PG 存储、建库建表和升级注意事项（6.2、6.3 节）。
- **`.env`、`.env.example`**：删掉 Nacos 鉴权一节。
- **`.env.dev`**：第 10 行注释「NACOS_AUTH_* 三件套在 .env」改为指向 `~/.patra/secrets/nacos-server.env`。这个文件本身由 PAP-77 删除。
- **根 `CLAUDE.md` 安全红线第 3 条**：写明 Nacos 的鉴权密钥和账号不在「随仓库提交的 dev 凭据」这个例外之内。
- **`patra-hexagonal` 的 configuration 指南**（「Nacos 仅用于服务发现」）这次不改，由 PAP-77 统一更新。

## 15. 交给其他 Issue 的约束

- **PAP-74（starter）**：
  - 连接凭据来自 `NACOS_USERNAME`、`NACOS_PASSWORD`：容器经 `nacos.env` 注入，本机经 dev profile 导入。PAP-76、PAP-77 去掉 dev profile 后，本机的导入方式由 PAP-74 决定。
  - 非敏感配置从 namespace `dev` 导入，密钥 `patra-secrets.yaml` 从 namespace `dev-secrets` 导入，分组都是 `DEFAULT_GROUP`。starter 要支持按导入项指定 namespace。
  - 应用账号写不了配置，starter 不能依赖任何写操作。
- **PAP-75（推送）**：
  - 用 `nacos-push.env` 里的 `NACOS_PUSH_USERNAME`、`NACOS_PUSH_PASSWORD`；
  - 每次发布都显式带 `groupName=DEFAULT_GROUP`；
  - 删除按 dataId 和 group 逐个删，不用批量删除和导入：这两个接口的鉴权不看实际分组（第 5.1 节）；
  - 判断成败看响应体的 `code`，登录接口例外，看 HTTP 200 且 `accessToken` 非空；
  - 令牌放请求头，密码和配置正文放表单请求体，GET 查询条件放 URL（同 11.1）。
- **PAP-76、PAP-77（迁移）**：
  - 密钥由管理员写进 `dev-secrets / DEFAULT_GROUP / patra-secrets.yaml`。
  - 清理 `~/.patra/secrets` 时保留 4 个 `nacos-*.env`，它们是 Nacos 自己的凭据，正是 release spec D6 说的例外。
