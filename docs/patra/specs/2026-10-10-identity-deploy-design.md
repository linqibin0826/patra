# identity 部署接入与密钥注入工程设计（PAP-66）

> **Issue**：[PAP-66](https://linear.app/papertrace/issue/PAP-66)
> **版本**：[v0.8 Accounts](../release-specs/v0.8-accounts.md)（决策 A、C、G；Done 判定 D7、D10）
> **前置设计**：[identity 账号（PAP-63）](2026-10-05-identity-account-design.md) 第 16 节、[gateway 鉴权（PAP-65）](2026-10-09-gateway-auth-design.md) 第 15 节
> **日期**：2026-10-10
> **状态**：已实施，已上线（2026-10-10）

## 1. 要解决的问题

v0.8 后端的代码已经齐了：identity 能注册、登录、签会话，网关能查会话、签身份断言。但它们还跑不到 mini 上：

- identity 没有部署条目，mini 上也没有它的库。
- 带鉴权的网关启动时必须有私钥、公钥和 Redis 密码，mini 上一样都没有。
- Redis 没有密码。会话的真相在 Redis 里，任何能连上 16379 的人都能写进一条假会话，这是决策 G 说的硬前提。
- main 上的 CD 从 2026-10-09 起每次都失败（第 3 节第 1 条），mini 上的后端停在 10/05 的镜像。

本设计把这些补齐，并写清上线顺序和以后换密钥的做法。

**完成标准**：PAP-66 的 Acceptance Criteria；release spec D7（仓库里没有 Redis 密码和签名私钥、Redis 不带密码连不上）和 D10（mini 上跑通）里属于部署的部分。

## 2. 范围

做：

- 密钥文件的存放位置和加载方式，容器和本机进程用同一套（第 5 节）。
- Redis 服务端加密码；catalog、identity、gateway 三个客户端带密码连接；删掉 registry、ingest 里从未生效的 Redis 配置（第 6 节）。
- identity 接入 compose、新建 `.env.identity`、建库（第 7 节）。
- CD：`services.json` 加 identity；`deploy.sh` 改为按 `services.json` 的条目顺序部署（第 8 节）。
- 签名密钥：生成、存放、公钥入库、换密钥的顺序（第 9 节）。
- 上线到 mini、验收、runbook 与文档（第 11 至 14 节）。

不做：

- 按环境隔离 Nacos。本机实例和 mini 容器混在一起是已知限制，见第 4 节决定 1。
- 每个服务单独的 Redis ACL 账号，见第 4 节决定 2。
- 搬迁 tailscale 网关的 `.env.secret`：它只被手动启动的 tailnet 栈读取，不经过 CD，现在能用。
- 门户（PAP-67、PAP-68）。
- `.github/CI_OPTIMIZATION.md` 里的模块清单：这份文档描述的是早已替换的旧机制，自 #46 以来无人维护，不在本 Issue 同步。

## 3. 现状事实（2026-10-10 核对）

| # | 事实 | 怎么确认的 |
|---|---|---|
| 1 | main 上最近两次 CD（run 37894222797、37935715316）都失败在「Build images」：`detect-changes.sh` 的 `ALL_UNITS` 已含 identity，`services.json` 却没有它的条目，`docker build` 拿到空镜像名，报 `invalid tag ":<sha>"`。mini 上的后端容器都还是 `03aaa80`（10/05）的镜像 | `gh run view --log-failed`；mini 上 `docker ps` |
| 2 | CD 在 runner 工作区（`~/actions-runner/_work/patra/patra`）里执行 `deploy.sh`，compose 读的是那里的 `patra-infra/docker/`。`actions/checkout` 默认 `clean: true`，每次运行前执行 `git clean -ffdx`，被 git 忽略的文件全被删掉。mini 上另有一份手动用的稀疏检出 `~/Projects/patra`，停在 9/24 的 `49f8806`。mini 上一个 `.env.<服务>.secret` 都没有：这套机制在 CD 下从来没生效过 | mini 上 `ls`、`git status --ignored`、`git log` |
| 3 | runner 进程的 `HOME` 就是用户主目录：`deploy.sh` 的 last-good 记录写在 `$HOME/.patra/cd/`，那里有各服务 10/05 以来的记录 | mini 上 `ls ~/.patra/cd` |
| 4 | 真正连 Redis 的只有三个服务：catalog（经 batch starter 引入 Redisson 4.1.0）、identity 和 gateway（会话模块引入 spring-boot-starter-data-redis，用 Lettuce）。registry、ingest 配了 Redis 地址，但运行时类路径里没有任何 Redis 客户端 | `./gradlew :…:dependencies --configuration runtimeClasspath` |
| 5 | Nacos 没有按环境分命名空间或分组。本机以 dev profile 起的服务（用 `TAILSCALE_IP` 注册）和 mini 容器注册在一起，mini 网关会在两边实例之间轮询；mini 容器能直接访问 MacBook 的 tailscale 地址 | 各服务 `application.yml` 的 nacos 配置；`patra-infra/docs/mac-mini-connectivity.md` 第 6、7 节 |
| 6 | mini 的 Redis 是 `redis:7.0.15`，`redis.conf` 在仓库外（`~/.patra/docker/redis/redis.conf`，`init-volumes.sh` 只在缺失时生成）：`appendonly yes`、`appendfsync everysec`，RDB 快照 `3600 1 300 100 60 10000`。`default` 用户是 `nopass`，只用 0 号库 | mini 上 `cat`、`CONFIG GET`、`ACL LIST`、`INFO keyspace` |
| 7 | Spring Boot 4.0.8 只要配了 `spring.data.redis.url`，用户名和密码就只从 URL 里取，单独配的 `username`、`password` 被忽略（`PropertiesDataRedisConnectionDetails`）。Redisson 4.1.0 的自动配置（`RedissonAutoConfigurationV4`）先从连接详情取用户名，随后又用 `spring.data.redis.username` 覆盖，所以用 URL 时用户名恒为空，只发 `AUTH <密码>` | 两者的源码 |
| 8 | `redis:7.0.15` 实测：`default` 为 `nopass` 时，`AUTH default <任意密码>` 返回 OK，`AUTH <密码>` 报错；设了密码以后，`redis-cli ping` 被拒（`NOAUTH`）时退出码仍是 0；以 `--requirepass ""` 启动时 `default` 回到 `nopass` | 本机一次性容器 |
| 9 | compose 5.1.2（本机和 mini 同版本）实测：任一服务声明为必需（`required: true`）的 `env_file` 缺失时，对这个项目的任何命令都会失败，并报出完整路径；`env_file` 的路径支持 `${HOME}` 插值；不加引号的 JSON 值原样读出 | 本机一次性 compose 项目 |
| 10 | 安全 starter 的测试夹具以最低优先级（`addLast`）注入测试公钥；各服务的集成测试都用 `test` profile，不加载 dev、container 的配置；`RedisContainerInitializer` 只给地址、不给密码；catalog 的 e2e profile 排除了 Redis 和 Redisson | 源码 |
| 11 | PAP-64 之后注册也会签会话：identity 的注册、登录、登出、当前用户全部依赖 Redis | `RegisterUserHandler` |
| 12 | 自 `49f8806` 以来，`docker-compose.core.yaml`、`compose-all.sh`、`init-volumes.sh` 在 main 上都没有改动：mini 上 `git pull` 后只 up core 栈，只有本设计改的 redis 会重建 | `git log 49f8806..main -- …` |
| 13 | 镜像里的应用依赖在 `/app/lib/*.jar`，运行时是 `eclipse-temurin:25-jre`，没有编译器 | mini 上 `docker exec patra-catalog ls /app`；`service.Dockerfile` |

## 4. 已定的决定（2026-10-10）

| # | 决定 | 理由 |
|---|---|---|
| 1 | 本机开发和 mini 是同一个环境：共用 Redis 的 0 号库，共用一对签名密钥，私钥在两台机器上各放一份 | 第 3 节第 5 条：网关会把请求分给另一边的实例。本机换库或换钥匙，被分过去的请求就会查不到会话或验签失败。代价是本机手测会碰到 mini 上的会话和登录失败计数，和两边共用 mini 的 PG 是同一性质。按环境隔离 Nacos 会断掉本机服务调 mini 容器的现有用法，另做 |
| 2 | Redis 只设一个密码，设在 `default` 用户上；三个客户端显式带用户名 `default` | mini 上所有凭据都在同一个目录，分账号挡不住真实风险。带用户名的认证在加密码前后都能成功（第 3 节第 8 条），所以部署和改 Redis 谁先谁后都行 |
| 3 | gateway、identity 的健康检查保留 Redis | 部署检查能顺带发现 Redis 地址或密码配错；Docker 不会因为 unhealthy 重启容器；identity 离了 Redis 不可用（第 3 节第 11 条）；catalog 现在就包含 Redis。代价：Redis 故障期间部署会失败，gateway 回滚到上一个镜像，identity 第一次部署没有可回滚的版本 |
| 4 | 密钥放在两份检出之外的 `~/.patra/secrets/`（第 5 节） | 第 3 节第 2 条 |
| 5 | mini 上的操作由 Claude 经 ssh 执行，每步动手前先确认；推送 main 等用户开口 | 用户决定 |

## 5. 密钥文件

### 5.1 方案比较

| 方案 | 做法 | 问题 |
|---|---|---|
| **A（选定）** | 两台机器都在 `~/.patra/secrets/` 放同一批文件。容器由 compose 按绝对路径加载，本机进程由 dev profile 的 `spring.config.import` 读同一批文件 | 要在两台机器上各放一份 |
| B | 继续放在 compose 目录里，checkout 改成 `clean: false` | 密钥留在 git 工作区里；mini 上两份检出要各放一份；不清理会留下旧的构建产物 |
| C | 存进 GitHub Actions secrets，CD 部署时写成文件 | Redis 服务端由手动执行的 `compose-all.sh` 启动，本机开发也要密码，还得在本地再放一份，等于两个真相源 |

### 5.2 目录与文件

目录是 `~/.patra/secrets/`，权限 0700；文件权限 0600。两台机器上的内容相同（第 4 节决定 1）。

| 文件 | 内容 | 谁加载 | 缺失时 |
|---|---|---|---|
| `redis.env` | `REDIS_PASSWORD`：64 位十六进制（`openssl rand -hex 32`，URL 安全） | Redis 服务端；catalog、identity、gateway 的容器和本机进程 | 容器：compose 报出路径并拒绝执行；本机：启动失败并报出路径 |
| `gateway.env` | `PATRA_GATEWAY_IDENTITY_ASSERTION_PRIVATE_KEY`：含私钥的 EC P-256 JWK JSON，一行 | gateway 的容器和本机进程 | 同上 |
| `<服务名>.env` | 该服务以后需要的外部数据源密钥，例如 catalog 的 `SCOPUS_API_KEY` | 对应服务的容器 | 跳过（可选） |

`<服务名>.env` 取代现在 compose 目录里的 `.env.<服务名>.secret`。

### 5.3 容器加载

apps 栈每个服务的 `env_file` 按「共享坐标、服务专属、密钥」的顺序叠加，后加载的覆盖同名变量：

| 服务 | `env_file` |
|---|---|
| registry、object-storage、ingest | `.env.common`、`.env.<服务>`、`${HOME}/.patra/secrets/<服务>.env`（可选） |
| catalog | `.env.common`、`.env.catalog`、`${HOME}/.patra/secrets/redis.env`（必需）、`${HOME}/.patra/secrets/catalog.env`（可选） |
| identity | `.env.common`、`.env.identity`、`${HOME}/.patra/secrets/redis.env`（必需）、`${HOME}/.patra/secrets/identity.env`（可选） |
| gateway | `.env.common`、`.env.gateway`、`${HOME}/.patra/secrets/redis.env`（必需）、`${HOME}/.patra/secrets/gateway.env`（必需） |
| portal | `.env.portal`、`${HOME}/.patra/secrets/portal.env`（可选） |
| learn | `.env.learn`（不变） |

按第 3 节第 9 条，必需文件缺失时整个 apps 栈的部署都会被拦下，portal 也一样。这两个文件是环境的前提，直接拦下比带病启动好，compose 的报错里也有完整路径。

core 栈的 redis 加载 `${HOME}/.patra/secrets/redis.env`（必需），见第 6.1 节。

### 5.4 本机加载

catalog、identity、gateway 的 `application-dev.yml` 用 `spring.config.import` 导入对应的文件，按 properties 格式解析：

```yaml
spring:
  config:
    import:
      - file:${user.home}/.patra/secrets/redis.env[.properties]
      - file:${user.home}/.patra/secrets/gateway.env[.properties]   # 只有 gateway 导入这一行
```

不加 `optional:`：文件缺失时启动失败，并报出缺的是哪个文件。IDEA 的运行配置和 shell 都不用设置这些变量。测试用 `test` profile，不加载 dev 配置（第 3 节第 10 条），CI 上没有这些文件也不受影响。

`KEY=VALUE` 这种写法同时满足 compose 的 `env_file` 和 Java properties：键里没有 `=`、`:` 和空白，值是十六进制串或 JWK JSON，不含 `\`。

### 5.5 仓库里

- 模板：`patra-infra/docker/secrets/redis.env.example`、`patra-infra/docker/secrets/gateway.env.example`，写明生成命令和权限。`patra-infra/docker/.gitignore` 忽略 `secrets/` 下除 `*.example` 外的一切，防止有人把真文件放进工作区。
- 删掉 `patra-infra/docker/.env.registry.secret.example`：它写的「复制为 `.env.apps.secret`」早已不存在，由上面两个模板取代。
- `.gitignore` 里原有的 `.env.secret`、`.env.*.secret` 规则保留：前者仍在用，后者作为防误提交的兜底。
- `init-volumes.sh` 增加一步：建 `~/.patra/secrets`（0700），不生成任何密钥。

## 6. Redis

### 6.1 服务端

`docker-compose.core.yaml` 里的 redis 改成：

```yaml
  redis:
    image: redis:7.0.15
    container_name: patra-redis
    restart: unless-stopped
    ports: [ "16379:6379" ]
    env_file:
      - path: ${HOME}/.patra/secrets/redis.env
        required: true
    # 密码只来自 redis.env。为空时拒绝启动：--requirepass "" 会让 default 回到 nopass
    command: [ "sh", "-c", "[ -n \"$$REDIS_PASSWORD\" ] || { echo 'REDIS_PASSWORD 为空，拒绝无密码启动' >&2; exit 1; }; exec redis-server /usr/local/etc/redis/redis.conf --requirepass \"$$REDIS_PASSWORD\"" ]
    volumes:
      - ${HOME}/.patra/docker/redis/data:/data
      - ${HOME}/.patra/docker/redis/redis.conf:/usr/local/etc/redis/redis.conf:ro
    healthcheck:
      # redis-cli 被拒时退出码也是 0，所以要核对输出是 PONG
      test: [ "CMD-SHELL", "REDISCLI_AUTH=\"$$REDIS_PASSWORD\" redis-cli ping | grep -q PONG" ]
      interval: 10s
      timeout: 5s
      retries: 10
```

这段在本机用一次性容器实测过：带密码启动后 healthy，不带密码 ping 得到 `NOAUTH`；密码为空时容器以 1 退出并打出那行提示；容器的进程信息里看不到密码（Redis 改写了进程标题），`docker inspect` 的启动命令里只有 `$REDIS_PASSWORD` 这几个字。

mini 上的 `redis.conf` 不改，持久化维持第 3 节第 6 条的现状：AOF 每秒刷盘，正常重启和容器重建都不会丢会话。宿主端口 16379 继续映射，本机开发要用。

### 6.2 客户端

catalog、identity、gateway 从「每个服务一个 URL 变量」改成「共享地址加共享密码」，拆成四个配置项：

| profile | `host` | `port` | `username` | `password` |
|---|---|---|---|---|
| container | `${REDIS_HOST}` | `${REDIS_PORT}` | `default` | `${REDIS_PASSWORD}` |
| dev | `${PATRA_INFRA_HOST:127.0.0.1}` | `16379` | `default` | `${REDIS_PASSWORD}`，来自导入的 `redis.env` |

- `.env.common` 新增 `REDIS_HOST=redis`、`REDIS_PORT=6379`。Redis 只有一台、所有服务共用 0 号库，它和 Nacos 一样是共享的基建坐标。
- 删掉 `CATALOG_REDIS_URL`、`IDENTITY_REDIS_URL`、`GATEWAY_REDIS_URL`，配置和环境文件里都删。
- 这四项只写在 dev、container 两个 profile 的配置里，不写进 `application.yml`，测试 profile 不受影响。
- 超时不动：catalog 的 dev 超时（连接 10 秒、命令 30 秒），identity 的公共超时（10 秒、5 秒），gateway 的公共超时（5 秒、2 秒）。

不用 URL 写法的原因在第 3 节第 7 条：用 URL 时 Redisson 只发 `AUTH <密码>`，在 Redis 加密码之前会被拒绝。拆成四项后，Lettuce 和 Redisson 都以 `default` 认证，按第 3 节第 8 条，加密码前后都能连上。

### 6.3 registry 和 ingest

删掉两者 `application-dev.yml`、`application-container.yml` 里的 `spring.data.redis`，以及 `.env.registry` 的 `REGISTRY_REDIS_URL`、`.env.ingest` 的 `INGEST_REDIS_URL`。按第 3 节第 4 条，它们没有 Redis 客户端，这些配置从未生效。

### 6.4 键空间

第 4 节决定 1：本机和 mini 共用 0 号库，不做隔离。identity 的会话和登录失败计数、catalog 的 Redisson 锁，两边看到的都是同一份。

### 6.5 健康检查

第 4 节决定 3：不加 `management.health.redis.enabled=false`，也不配 readiness 分组。Redis 不可用时 gateway、identity、catalog 都报 DOWN。部署期间遇到这种情况就判失败并回滚（gateway、catalog 有上一个镜像；identity 第一次部署时没有），Redis 恢复后重跑部署。

## 7. identity 部署

### 7.1 compose

```yaml
  identity:
    image: ghcr.io/linqibin0826/patra-identity:${IDENTITY_IMAGE_TAG:-latest}
    container_name: patra-identity
    restart: unless-stopped
    ports: [ "6400:6400" ]
    env_file:
      - .env.common
      - .env.identity
      - path: ${HOME}/.patra/secrets/redis.env
        required: true
      - path: ${HOME}/.patra/secrets/identity.env
        required: false
    healthcheck:
      test: [ "CMD", "curl", "-fs", "http://localhost:6400/actuator/health" ]
      <<: *app-healthcheck
```

`IDENTITY_IMAGE_TAG` 是 `deploy.sh` 的 `tag_var` 按服务名推出来的变量名。

### 7.2 `.env.identity`

库连接三项，写法和其他服务一样：patra-net 里的服务名、容器内端口、随仓库提交的 dev 默认凭据。

```
IDENTITY_DB_URL=jdbc:postgresql://postgres:5432/patra_identity
IDENTITY_DB_USERNAME=postgres
IDENTITY_DB_PASSWORD=（与其他服务相同的 dev 默认值）
```

### 7.3 容器变量定稿

| 变量 | 来源 | 用途 |
|---|---|---|
| `IDENTITY_DB_URL`、`IDENTITY_DB_USERNAME`、`IDENTITY_DB_PASSWORD` | `.env.identity` | 库 |
| `REDIS_HOST`、`REDIS_PORT` | `.env.common` | Redis 地址 |
| `REDIS_PASSWORD` | `~/.patra/secrets/redis.env` | Redis 密码 |
| `PATRA_IDENTITY_ASSERTION_PUBLIC_KEYS` | `.env.common` | 网关的公钥（第 9 节） |
| Nacos、日志目录等 | `.env.common` | 同其他服务 |

### 7.4 建库

- `patra-infra/docker/postgres/init-scripts/02-create-databases.sql` 加一行 `CREATE DATABASE patra_identity;`，只在数据目录为空时执行。
- mini 上现有的 PG 要手工建库：`docker exec patra-postgres psql -U postgres -c 'CREATE DATABASE patra_identity'`。必须在推送 main 之前做，因为 identity 启动时 Flyway 要求库已经存在。

## 8. CD

### 8.1 `services.json`

加 identity 条目，并把 `services` 数组按部署顺序重排：object-storage、registry、identity、gateway、catalog、ingest、portal、learn。

```json
{
  "name": "identity",
  "gradleTask": ":patra-api:patra-identity:patra-identity-boot:bootJar",
  "context": "patra-api/patra-identity/patra-identity-boot",
  "srcPrefix": "patra-api/patra-identity/",
  "port": 6400,
  "image": "ghcr.io/linqibin0826/patra-identity",
  "healthPath": "/actuator/health",
  "healthMatch": "\"status\":\"UP\""
}
```

`_comment` 补一句：条目顺序就是 `deploy.sh` 的部署顺序。

### 8.2 `deploy.sh`

删掉写死的 `ORDER='object-storage registry gateway catalog ingest portal learn'`，改成从 `services.json` 按条目顺序取服务名。请求部署的服务只要在 `services.json` 里就一定会部署；不在的，部署前按「未知服务」以 2 退出，这个校验现在已经有了。Issue 里说的三处硬编码清单（`unitOf()`、`ALL_UNITS`、`ORDER`）少了一处，部署端不再可能出现「漏改清单导致新服务被静默跳过」。

identity 排在 gateway 前面，新网关起来时 identity 已经注册好。gateway 的启动不依赖 identity，这个顺序只是让第一次上线时少一段 503。

### 8.3 `deploy.test.sh`

先加场景 6 并看它失败：请求 `["gateway","identity","object-storage"]`，三个都健康；断言三个都执行了 `compose up`，顺序是 object-storage、identity、gateway，三个的 last-good 都记下新 tag。改 `deploy.sh` 之前 identity 不在 `ORDER` 里，会被静默跳过，场景失败；改完后通过。其余五个场景保持通过。

### 8.4 不改的部分

- `cd.yml`：构建循环按 `services.json` 查 `gradleTask`、`image`、`context`、`port`，有了 identity 条目就能构建。
- `detect-changes.sh`：`ALL_UNITS` 已含 identity。本次改动的路径归类：`docker-compose.apps.yaml`、`.env.common`、`.env.identity`、`patra-infra/cd/*` 判全量；`docker-compose.core.yaml`、`patra-infra/scripts/*`、`patra-infra/docker/*.example`（包括 `secrets/` 下的模板）判纯基建。`detect-changes.test.sh` 重跑。
- `module-graph.json`：本 Issue 不改模块依赖，重跑 `dumpModuleGraph` 核对没有变化。

## 9. 签名密钥

### 9.1 一对密钥

整个环境只有一对（第 4 节决定 1）。在本机生成，私钥写进 `~/.patra/secrets/gateway.env`，再复制到 mini 的同一路径：

```bash
umask 077 && mkdir -p ~/.patra/secrets
K="$(mktemp -u ~/.patra/secrets/key.XXXXXX)"   # 任务要求私钥文件事先不存在
./gradlew -q :patra-starters:patra-spring-boot-starter-security:generateIdentityAssertionKey -PkeyOut="$K" \
  | tail -1 > /tmp/patra-identity-assertion-public.jwks   # 前两行是提示，最后一行是公钥 JWK Set
{ printf 'PATRA_GATEWAY_IDENTITY_ASSERTION_PRIVATE_KEY='; cat "$K"; } > ~/.patra/secrets/gateway.env
rm -f "$K"
```

全程不打印私钥。复制到 mini 用 `scp -p`，保留 0600 权限。

### 9.2 公钥提交进仓库，共三处

| 位置 | 给谁 |
|---|---|
| `patra-infra/docker/.env.common` 的 `PATRA_IDENTITY_ASSERTION_PUBLIC_KEYS` | identity、gateway 的容器，两者的 container 配置本来就读这个变量 |
| `patra-identity-boot` 的 `application-dev.yml` | 本机的 identity |
| `patra-gateway-boot` 的 `application-dev.yml` | 本机的 gateway：启动自检和安全 starter 的验签器 |

dev 配置里直接写值，取代现在要求本机设置的 `${PATRA_IDENTITY_ASSERTION_PUBLIC_KEYS}`。不写进 `application.yml` 的原因：测试夹具以最低优先级注入测试公钥（第 3 节第 10 条），`application.yml` 里有值就会盖过它，集成测试里用测试私钥签的断言会验不过。

决策 G 说「公钥随各下游的配置给出」，这三处就是它的落地。

### 9.3 私钥的加载

容器经第 5.3 节的 `gateway.env` 拿到私钥；本机由 gateway 的 dev profile 导入同一个文件（第 5.4 节）。gateway 的配置项不变：`patra.gateway.identity-assertion.private-key: ${PATRA_GATEWAY_IDENTITY_ASSERTION_PRIVATE_KEY}`。

### 9.4 启动自检

PAP-65 的签名器在启动时用私钥签一个探针断言，再用配置的公钥验它。私钥解析失败、和公钥不配对或 kid 不一致，都会让启动失败，错误信息里只有配置项名和 kid。部署时这会被健康检查发现并回滚。启动成功时日志打出「身份断言签名密钥就绪，kid=…」，可以用来核对。

### 9.5 换密钥的顺序

写进 `patra-infra/docker/README.md`：

1. 按第 9.1 节生成一对新密钥，新私钥先存成别的文件名。
2. 把新公钥加进第 9.2 节三处的 `keys` 数组，旧公钥保留；提交、推送，让 CD 部署 identity 和 gateway。此时新旧两把都被信任，网关仍用旧私钥签。
3. 两台机器上把 `gateway.env` 换成新私钥。mini 上在 runner 工作区的 compose 目录里执行 `GATEWAY_IMAGE_TAG="$(cat ~/.patra/cd/last-good-gateway)" docker compose -f docker-compose.apps.yaml up -d --force-recreate gateway`，不要在 CD 运行期间执行；`docker restart` 不会重新读环境文件。本机的网关重启一次。
4. 等至少 60 秒：断言有效期是 60 秒，旧私钥签出的断言全部过期。
5. 从三处删掉旧公钥，提交、推送。

## 10. 配置改动清单

| 文件 | 改动 |
|---|---|
| `patra-infra/docker/docker-compose.core.yaml` | redis：`env_file`、启动命令、健康检查（第 6.1 节） |
| `patra-infra/docker/docker-compose.apps.yaml` | 新增 identity；各服务的 `env_file` 改成第 5.3 节；改写头部关于环境文件叠加的注释 |
| `patra-infra/docker/.env.common` | 新增 `REDIS_HOST`、`REDIS_PORT`、`PATRA_IDENTITY_ASSERTION_PUBLIC_KEYS`；头部注释 |
| `patra-infra/docker/.env.identity` | 新建（第 7.2 节） |
| `patra-infra/docker/.env.catalog`、`.env.registry`、`.env.ingest` | 删 Redis 地址；注释里密钥文件的位置改成 `~/.patra/secrets/<服务>.env` |
| `patra-infra/docker/.env.gateway` | 改写注释：网关已不只是路由，密钥在 `~/.patra/secrets/` |
| `patra-infra/docker/.env.object-storage`、`.env.portal` | 注释里密钥文件的位置同上 |
| `patra-infra/docker/secrets/redis.env.example`、`gateway.env.example` | 新建 |
| `patra-infra/docker/.env.registry.secret.example` | 删除 |
| `patra-infra/docker/.gitignore` | 忽略 `secrets/` 下除 `*.example` 外的一切；改写注释 |
| `patra-infra/docker/postgres/init-scripts/02-create-databases.sql` | 加 `patra_identity` |
| `patra-infra/scripts/init-volumes.sh` | 建 `~/.patra/secrets` |
| `patra-infra/cd/services.json` | identity 条目、按部署顺序重排、`_comment` |
| `patra-infra/cd/deploy.sh`、`deploy.test.sh` | 第 8.2、8.3 节 |
| catalog、identity、gateway 的 `application-dev.yml` | 导入密钥文件；Redis 四项；identity、gateway 的公钥直接写值 |
| catalog、identity、gateway 的 `application-container.yml` | Redis 四项；注释 |
| registry、ingest 的 `application-dev.yml`、`application-container.yml` | 删 `spring.data.redis` |
| `.run/PatraIdentityApplication.run.xml` | 新建，写法照抄现有服务 |

## 11. 上线顺序

1. 分支上的改动全部完成，本地门禁全绿（第 12.1 节）。密钥在写代码阶段就生成，因为公钥要和配置一起提交。
2. mini：建 `~/.patra/secrets`，把 `redis.env`、`gateway.env` 复制过去。
3. mini：建 `patra_identity` 库。
4. 本机冒烟（第 12.2 节）：以 dev profile 起 identity 和 gateway，连 mini 的 Nacos、PG、Redis，经本机网关走一遍注册、登录、当前用户、登出。此时 mini 的 Redis 还没有密码，只有带用户名 `default` 的认证才会成功（第 3 节第 8 条），所以连得上本身就证明 Lettuce 按设计发出了用户名。
5. 用户开口后推送 main。CD 构建六个后端服务，按 `services.json` 的顺序部署。catalog 的新镜像能在无密码的 Redis 上通过健康检查，同样证明 Redisson 发出了用户名；如果不通过，它会被回滚到旧镜像，而 Redis 此时还没加密码，旧镜像照常工作。
6. mini：在 `~/Projects/patra` 里 `git pull`，然后执行 `bash patra-infra/scripts/compose-all.sh up core`。按第 3 节第 12 条，只有 redis 会重建并带密码重启；客户端自动重连并认证。
7. mini 验收（第 12.3 节）。

退路：

- 第 5 步 gateway 起不来：`deploy.sh` 自动回滚到 last-good，也就是 10/05 那版不带鉴权的网关，匿名浏览照常。identity 第一次部署没有 last-good，会停在 unhealthy，不影响其他服务。
- 第 6 步之后有客户端连不上：执行 `docker exec patra-redis sh -c 'REDISCLI_AUTH="$REDIS_PASSWORD" redis-cli CONFIG SET requirepass ""'`，立即撤掉密码，不重启、不丢数据；排查完再重做第 6 步。

## 12. 测试与验证

### 12.1 分支上

- `bash patra-infra/cd/deploy.test.sh`：场景 6 先红后绿，全部场景通过。
- `bash patra-infra/cd/detect-changes.test.sh` 通过；用 `detect-changes.sh classify` 核对第 8.4 节的归类。
- compose 静态检查：建一个临时 `HOME`，在里面按模板放假的密钥文件，对 apps 栈和 core 栈执行 `docker compose config`，确认路径、插值和 JSON 都能解析；再删掉一个必需文件，确认报出路径。
- `./gradlew spotlessApply`，然后 `./gradlew check --no-configuration-cache`。
- 集成测试：gateway、identity（boot、session）、catalog-boot。本 Issue 只改配置，跑这几个是为了确认测试 profile 不受影响。
- 执行 `dumpModuleGraph` 后 `module-graph.json` 没有变化。

### 12.2 本机冒烟（上线第 4 步）

- identity、gateway 以 dev profile 启动，日志里有「身份断言签名密钥就绪，kid=…」，kid 与提交的公钥一致。
- 经本机网关：匿名请求 `/patra-identity/auth/me` 得到 401，注册得到 201，带令牌请求 `/auth/me` 得到 200，登出得到 204，再用同一令牌请求得到 401。
- 把 `redis.env` 临时改名后再起 identity：启动失败并报出文件路径。改回原名。

### 12.3 mini 验收（上线第 7 步，对应 AC）

| AC | 验证 |
|---|---|
| CD 构建并部署 identity；容器在运行并已注册到 Nacos | CD 运行成功；`docker ps`；Nacos 的实例列表里有 `patra-identity` |
| `module-graph.json` 与构建配置一致 | CI 的过期检查通过 |
| `patra_identity` 库存在，Flyway 执行成功 | `psql` 查 `flyway_schema_history` |
| Redis 不带密码连不上；带密码的服务正常 | 从 MacBook 执行 `redis-cli -h <mini> -p 16379 ping` 得到 `NOAUTH`；catalog、identity、gateway 的健康检查都是 UP |
| 仓库里没有 Redis 密码和签名私钥；`.example` 模板齐全 | `git grep` 查不到 `REDIS_PASSWORD=` 后跟值、查不到 JWK 的 `"d":`；两个模板都在 |
| Redis 重启后，重启前登录的会话仍然有效 | 登录，`docker restart patra-redis`，再用同一令牌请求 `/auth/me` 得到 200 |
| 经 mini 的网关能调通注册、登录、当前用户 | curl 请求 `http://<mini>:9528/patra-identity/auth/…`；顺带确认 `/patra-identity/admin/**`、`/patra-identity/actuator/**` 得到 403 |
| runbook 覆盖建库、Redis 加密码、签名密钥三项手工步骤 | 第 14 节的 runbook |

另外两项：

- 门户匿名路径：Playwright 指向 mini 网关跑一遍（`--workers=1`）。mini 的网关这次会从 10/05 的 WebFlux 版直接换成带鉴权的 WebMVC 版，这一步用来兜底。
- Argon2 单次耗时：镜像里没有编译器（第 3 节第 13 条），所以在本机预编译一个计时类，复制进 identity 容器，用镜像自带的 JRE 和 `/app/lib` 下的依赖运行。参数和生产一致，即 `Argon2PasswordEncoder(16, 32, 1, 19456, 2)`，预热后取 20 次的中位数，结果写回 identity 设计第 14 节第 1 条。

## 13. 实施时要实测的点

实施后把结果写回本节：

1. Spring Boot 4.0.8 会处理 `application-dev.yml`（带 `spring.config.activate.on-profile`）里声明的 `spring.config.import`；`[.properties]` 能读 `.env` 文件；`${user.home}` 能展开。
2. 上线第 4、5 步：Lettuce 和 Redisson 带用户名 `default`，在无密码的 Redis 上连接成功。
3. 上线第 6 步：Redis 带密码重建后，三个服务的连接自动恢复；记下期间请求的实际表现，包括出现多少 503、持续多久。
4. Redis 重启后会话保留。
5. Argon2 在 mini 上的单次耗时。
6. 门户 Playwright 指向 mini 网关的结果。

实测结果（2026-10-10）：

| # | 结果 | 怎么测的 |
|---|---|---|
| 1 | 成立：导入会被处理，`${user.home}` 会展开，`[.properties]` 能读 `.env` 文件，导入的值能用在占位符里。文件缺失时启动失败，报 `Config data resource 'file [...]' via location 'file:...[.properties]' does not exist`。同一组 import 从后往前加载：gateway 两个文件都缺时先报 `gateway.env`，只放 `gateway.env` 时报 `redis.env` | 写计划时用 identity 启动包实测；Task 5 在空的 `user.home` 下起 gateway、identity 的启动包 |
| 2 | 成立：Lettuce（本机 identity、gateway，dev profile）在无密码的 mini Redis 上完成注册、取当前用户、登出；Redisson（catalog 新镜像）在无密码的 Redis 上通过健康检查，部署日志里没有自动回滚。加密码后三个服务照常工作，日志里没有 `NOAUTH` / `WRONGPASS` | Lettuce：本机冒烟 401/201/200/204/401；Redisson：CD run 38032078855；加密码后查 `docker logs` |
| 3 | 三个服务自动恢复，0 个 503：切换期间每 0.5 秒带令牌请求一次 `/auth/me`，160 次全部 200。identity 的 Lettuce 在 redis 重建时打出两条 `Cannot reconnect ... Connection refused` 告警后自行重连；重连后的客户端为 catalog 25、gateway 2、identity 2，都以 `default` 认证 | 在 mini 上执行 `compose-all.sh up core` 时并行请求；`CLIENT LIST` |
| 4 | 成立：重启前登录的会话，重启后 `/auth/me` 仍为 200；redis 约 12 秒恢复 healthy | runbook 6.5（`docker restart patra-redis`） |
| 5 | Argon2id m=19456 t=2 p=1：中位数 19.8 毫秒，最快 16.8 毫秒，最慢 30.5 毫秒（20 次，预热 5 次） | 在 mini 的 identity 容器里用镜像自带的 JRE 跑一次性计时类，参数与 `PasswordHashingAdapter` 一致 |
| 6 | 14 个用例全部通过，真实数据用例没有被跳过 | `PATRA_GATEWAY_BASE_URL=http://100.103.73.27:9528` 跑 `patra-portal` 的 `tests/e2e/` |

## 14. 文档

| 文档 | 改动 |
|---|---|
| `docs/patra/runbooks/v0.8-accounts-go-live-runbook.md` | 新建：第 11 节的一次性上线步骤，以及每一步的验证命令和退路 |
| `patra-infra/docker/README.md` | 首次部署加一步「准备密钥目录」；新增「密钥」一节，写文件、生成、复制、换密钥的顺序，以及改了环境文件要重建容器；服务访问里注明 Redis 要密码；目录结构加 `secrets/` |
| `patra-infra/CLAUDE.md` | apps 列表加 identity；「5 个后端应用」和「重建全部 5 个」改成 6 个；「env 三层 + 密钥二分」改成第 5 节的做法；非显性约束补一条：CD 在 runner 工作区里执行，被 git 忽略的文件每次都会被清掉；scripts 表里 `init-volumes.sh` 的说明 |
| `.claude/rules/project-info.md` | 核心服务加 `patra-identity` |
| `README.md`（仓库根） | 「后端微服务总览」那一行加 identity |
| `patra-api/README.md` | 服务表加 identity，顺带补上缺的 catalog、object-storage 两行 |
| `patra-api/patra-identity/README.md` | 本地运行：用密钥文件代替环境变量；容器变量 |
| `patra-api/patra-gateway-boot/README.md` | 环境变量表；密钥生成与本地启动步骤 |
| `patra-starters/patra-spring-boot-starter-security/README.md` | 不动：它的说法是通用的，环境变量名只是示例，换密钥顺序与第 9.5 节一致 |
| release spec 决策 G | 「通过 `.env.*.secret` 注入」改成 `~/.patra/secrets/`，并写明原因 |
| gateway 鉴权设计第 15 节第一行 | 旧变量名 `GATEWAY_REDIS_URL` 改为指向本设计第 6.2 节 |
| identity 设计第 14 节第 1 条 | 写回 mini 上的 Argon2 耗时 |

## 15. 交给其他 Issue 的约束

| 约束 | 交给 |
|---|---|
| 门户本机开发连本机网关时，本机网关需要 `~/.patra/secrets/` 下的两个文件；连 mini 网关则不需要 | PAP-67 |
| 按环境隔离 Nacos，让本机实例不进 mini 的轮询，要一并解决本机服务调 mini 容器的路径 | 需要时再立 |
| tailscale 网关的 `.env.secret` 可以搬进 `~/.patra/secrets/` 统一管理 | 需要时再立 |

## 16. 要同步改的 Issue

- Linear PAP-66：To-Do 的 Tech Design 指向本设计；第 4 节的决定和第 3 节第 1、2 条写进描述；To-Do 和 AC 随实施勾掉。
- Linear PAP-65：不动，AC 留给用户。
