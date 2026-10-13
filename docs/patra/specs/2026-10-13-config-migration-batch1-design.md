# 配置迁移（一）：registry、object-storage、identity、gateway（PAP-76）

> **状态**：设计已确认（2026-10-13），待实现
> **Issue**：PAP-76 [BE] 配置迁移（一）：registry、object-storage、identity、gateway
> **版本**：v0.9 配置中心（`docs/patra/release-specs/v0.9-config-center.md` 决策 A、C、E、F）
> **前置**：
> - PAP-72：统一访问路径，本地变量约定（`docs/patra/specs/2026-10-13-infra-access-unification-design.md` 第 6 节）
> - PAP-74：starter 与启动器（`docs/patra/specs/2026-10-13-nacos-config-starter-design.md`，尤其第 12 节）
> - PAP-75：配置目录与推送（`docs/patra/specs/2026-10-13-nacos-config-push-design.md`）

## 1. 目标

这 4 个服务改为从 Nacos 读配置。本机 IDE 和 mini 容器两种运行方式读到的配置，只差 PAP-72 定下的 3 个本地变量：`PATRA_INFRA_HOST`、`PATRA_ADVERTISE_IP`、`PATRA_LOG_DIR`。

**验收**（Issue AC）：

1. 4 个服务在 IDE 与 mini 容器中都能启动，配置只差上述本地变量。
2. 门户的注册、登录、登出与 v0.8 行为一致；网关路由正常。
3. 4 个服务的 `./gradlew :<module>:check` 通过。

## 2. 现状事实（2026-10-13 核对）

1. 两种运行方式的差异全在两个 profile 文件里：
   - 数据库连接：IDE 写在 `application-dev.yml`，容器从 `.env.registry`、`.env.object-storage`、`.env.identity` 注入（`*_DB_URL`、`*_DB_USERNAME`、`*_DB_PASSWORD`）。
   - Redis 地址（identity、gateway）：IDE 写在 `application-dev.yml`，容器从 `.env.common` 的 `REDIS_HOST`、`REDIS_PORT` 注入。
   - 身份断言公钥（identity、gateway）：同一个值有 3 份，分别在两个服务的 `application-dev.yml` 和 `.env.common`（`PATRA_IDENTITY_ASSERTION_PUBLIC_KEYS`）。
   - 日志：gateway 在 IDE 里把路由和负载均衡打成 DEBUG；identity、object-storage 在 IDE 里的日志目录多一层服务名子目录。
   - 本机密钥导入：`application-dev.yml` 里的 `file:${user.home}/.patra/secrets/*.env[.properties]`。
2. 4 个服务的 `application.yml` 各有一段相同的 `spring.cloud.nacos` 服务发现配置，PAP-74 的 starter 已经注入了同样的内容。
3. identity、gateway 的集成测试是 `@SpringBootTest` + `@ActiveProfiles("test")`，读 jar 里的 `application.yml`，不连 Nacos。数据库、Redis 由 Testcontainers 提供，身份断言密钥由测试 fixture 提供。registry 只有 infra 层的 `@DataJpaTest` 集成测试；object-storage 的 boot 模块没有 Spring 测试。
4. 只有 identity、gateway 依赖 Redis；gateway 没有数据源。
5. 日志文件名已经按服务名取（`linqibin-spring-boot-starter-core` 的 `logback-spring.xml`：`${LOG_PATH}/${appName}.log`），不需要再按服务分子目录。
6. `.env.common` 的 `SPRING_PROFILES_ACTIVE=container`、`REDIS_HOST`、`REDIS_PORT`、MinIO 坐标还被 ingest、catalog 使用；身份断言公钥只有 identity、gateway 使用。
7. 构建约定插件 `linqibin.hexagonal-boot` 直接依赖 `spring-cloud-starter-alibaba-nacos-discovery`，6 个服务都经它引入。

## 3. 决定

| # | 问题 | 决定 | 理由 |
|---|------|------|------|
| 1 | jar 和 Nacos 怎么分工 | Nacos 只放随环境变化的值：基础设施坐标、账号、公钥、密钥引用。端口、路由、连接池、超时、限流参数等和代码一起变的默认值留在 jar | 测试照常读 jar 的默认值，不用在测试 profile 里补一份；Nacos 文件读起来就是「这个环境和代码默认值哪里不同」。某个参数要动态调或按环境调时，再在 Nacos 里写一行覆盖（决策 C 的 `preference=remote`） |
| 2 | 共享的值放哪 | 数据库账号、Redis 坐标、身份断言公钥放 `patra-common.yaml`；库地址、网关私钥引用放各服务文件 | 公钥从 3 份变 1 份；4 个库在同一个 PG，账号相同 |
| 3 | 数据库密码怎么写 | `${PATRA_DB_PASSWORD:123456}` | PAP-75 的检查不允许 `password` 结尾的键写字面量；`123456` 是已公开的 dev 默认值；以后换真实密码只需在 `~/.patra/secrets/` 设这个变量 |
| 4 | gateway 在 IDE 里的 DEBUG 日志 | 去掉，两种运行方式都用 jar 里的 INFO | 两种运行方式只差本地变量；要看 DEBUG 时在 Nacos 里临时覆盖，PAP-78 之后不重启即可生效 |
| 5 | 服务专属环境文件 | 删除 `.env.registry`、`.env.object-storage`、`.env.identity`、`.env.gateway` 及 compose 里的引用；`.env.common` 删掉身份断言公钥 | 内容已搬进 Nacos；`.env.gateway` 原本就是空占位 |
| 6 | mini 验收时机 | PAP-76 做完就在本分支手动触发 CD 部署到 mini 验收，不等整个版本合入 main | 快照日志等问题可能影响 starter 或共享 Dockerfile，要在 PAP-77 开工前暴露 |
| 7 | TDD 怎么驱动 | 每个 boot 模块加 `ApplicationConfigTest`，先失败再迁移，之后长期保留 | 迁移以配置为主，这个测试是能先失败的那一条，也防止 profile 文件和 Nacos 配置段被加回来 |

## 4. 每个服务的改动

4 个服务做法相同，顺序是 registry → object-storage → identity → gateway。registry 没有 Redis，最简单，先用它打通整套机制。

1. **启动类**：`SpringApplication.run(X.class, args)` 改为 `PatraApplication.run("<服务名>", X.class, args)`。服务名分别是 `patra-registry`、`patra-object-storage`、`patra-identity`、`patra-gateway`。
2. **`application.yml` 删掉**：
   - `spring.application.name`：服务名由启动类传入；
   - `spring.profiles.active: ${SPRING_PROFILES_ACTIVE:dev}`；
   - `spring.cloud.nacos` 整段及其注释：starter 已注入。

   其余内容按决定 1 原样保留，包括 `logging.file.path: ${PATRA_LOG_DIR:logs}`、registry 的 `classpath:registry-error-config.yaml` 导入、引用 profile 文件的注释（改写为指向 Nacos）。
3. **删除** `application-dev.yml`、`application-container.yml`。
4. **测试**：测试 profile 原则上不改。某个测试因为缺 `spring.application.name` 失败时，再在它的测试 profile 里补。
5. **`ApplicationConfigTest`**（boot 模块 `src/test`，只读 classpath，不起 Spring）：
   - classpath 上没有 `application-dev.yml`、`application-container.yml`；
   - `application.yml` 里没有 `spring.application.name`、`spring.profiles.active`、`spring.cloud.nacos`。

**构建约定插件**：`linqibin.hexagonal-boot` 把 `spring-cloud-starter-alibaba-nacos-discovery` 换成 `project(":patra-starters:patra-spring-boot-starter-nacos")`。6 个服务都会带上 starter，ingest、catalog 暂时不受影响：

- 它们没用启动器，不导入 Nacos 配置，starter 会关掉配置中心（PAP-74 spec 5.2）；
- starter 注入的服务发现默认值优先级最低，它们 `application.yml` 里的 Nacos 段照样生效；
- 它们会额外读到 `~/.patra/secrets/` 下全部 `*.env`，与现有的显式导入不冲突。

以上由 ingest、catalog 的现有测试与全量 `./gradlew check` 兜底。改了 `*.gradle.kts` 后提醒用户在 IDEA 里点「加载 Gradle 更改」。

## 5. Nacos 配置

`patra-infra/nacos/dev/` 下 5 份文件的内容（PAP-75 已建好只有签到行的骨架）：

```yaml
# patra-common.yaml
patra.config-source.common: patra-common.yaml

spring:
  datasource:
    username: postgres
    password: ${PATRA_DB_PASSWORD:123456}
  data:
    redis:
      host: ${PATRA_INFRA_HOST:127.0.0.1}
      port: 16379
      username: default

patra:
  security:
    identity-assertion:
      public-keys: '<现有的 JWK Set JSON，原样搬过来>'
```

```yaml
# patra-registry.yaml
patra.config-source.service: patra-registry.yaml

spring:
  datasource:
    url: jdbc:postgresql://${PATRA_INFRA_HOST:127.0.0.1}:15432/patra_registry
```

- `patra-object-storage.yaml` 写法与 registry 相同，库名是 `patra_storage`。
- `patra-identity.yaml` 写法与 registry 相同，库名是 `patra_identity`，另加 `spring.data.redis.password: ${REDIS_PASSWORD}`。
- `patra-gateway.yaml`：

  ```yaml
  patra.config-source.service: patra-gateway.yaml

  spring:
    data:
      redis:
        password: ${REDIS_PASSWORD}

  patra:
    gateway:
      identity-assertion:
        private-key: ${PATRA_GATEWAY_IDENTITY_ASSERTION_PRIVATE_KEY}
  ```

说明：

- 用不到的键不影响服务：registry、object-storage 没有 Redis，gateway 没有数据源，对应的属性不会被绑定。
- 主机默认值 `127.0.0.1` 与 starter 的 Nacos 地址写法一致；两种运行方式都会设 `PATRA_INFRA_HOST`，默认值只作应急。
- 两个真正的密钥只写引用：本机由 starter 从 `~/.patra/secrets/` 读取，容器由 compose 的 `env_file` 注入，来源不变。
- 密钥引用只写在用到它的服务文件里，公共配置里不写没有默认值的引用。starter 的签到检查会解析 Nacos 配置里的每个值，引用的变量没有值就启动失败并报出变量名；registry、object-storage 的容器不加载 `redis.env`，`${REDIS_PASSWORD}` 放在公共配置里会让它们启动不了。
- 容器里原来的 `REGISTRY_DB_*`、`STORAGE_DB_*`、`IDENTITY_DB_*` 不再使用。
- 文件内容须通过 PAP-75 的 `NacosConfigFilesTest`。

## 6. 容器侧

- `docker-compose.apps.yaml`：registry、object-storage、identity、gateway 去掉 `.env.<svc>` 那一行 `env_file`。`~/.patra/secrets/` 的加载不变：
  - 4 个服务都加载 `nacos.env`；
  - identity、gateway 加载 `redis.env`；
  - gateway 加载 `gateway.env`；
  - 可选的 `<svc>.env` 保留。

  mini 上不用新建文件。
- 删除 `.env.registry`、`.env.object-storage`、`.env.identity`、`.env.gateway`。
- `.env.common`：
  - 删除 `PATRA_IDENTITY_ASSERTION_PUBLIC_KEYS` 及其注释；
  - `SPRING_PROFILES_ACTIVE`、`REDIS_HOST`、`REDIS_PORT`、MinIO 坐标留给 PAP-77 清理（ingest、catalog 还在用；迁完的 4 个服务没有 `container` profile 文件，`SPRING_PROFILES_ACTIVE` 对它们不起作用）；
  - `PATRA_INFRA_HOST`、`PATRA_LOG_DIR` 是本地变量，长期保留。
- **快照日志**（PAP-74 spec 第 9 节第 5 条）：mini 部署后看 registry 容器日志，nacos-client 写不了快照时会不会反复打错误日志。不会，就记录结论；会刷屏，就在共享的 `service.Dockerfile` 里给快照一个可写目录（例如 `JM.SNAPSHOT.PATH`），6 个服务一起生效，以 mini 实测为准。

## 7. 测试与验收

按顺序进行。**凡动 mini 的步骤，执行前先征得用户同意。**

1. **本机测试**：
   - 每个服务先写 `ApplicationConfigTest`，看它失败，再迁移；
   - 4 个服务各跑 `./gradlew :<module>:check`；
   - PR 前跑全量 `./gradlew check`，覆盖 ingest、catalog。
2. **本机 IDE 连 mini 的 Nacos 启动**：
   - 本机运行 `nacos-push.sh`，把本分支的 5 份文件推到 mini 的 Nacos。mini 上现有的旧镜像不读这些文件，不受影响。
   - IDE 里逐个启动 4 个服务，启动日志里要看到：两份配置都已导入、签到通过、数据库已连上；identity、gateway 还要看到 Redis 已连上。
   - Nacos 控制台能看到 4 个服务的实例。
   - registry 的 `classpath:registry-error-config.yaml` 导入与启动器加的 Nacos 导入能并存，这一步实测确认。
3. **mini 部署**：在本分支手动触发 `cd.yml`（`workflow_dispatch`，service 留空），流程是推送 Nacos 配置 → 构建并部署 6 个服务。验收标准：6 个容器都健康；按第 6 节观察快照日志。
4. **门户行为**：本机 Playwright 把门户注册、登录、登出的 e2e 用例对准 mini 网关运行；再用 curl 经网关各访问一次 4 个服务，确认路由正常。
5. **两种运行方式一致**：`docker exec <容器> env` 列出 4 个容器的环境变量，逐条确认只剩本地变量、Nacos 账号、密钥，以及 ingest、catalog 还在用、尚未清理的 `.env.common` 共享项。

## 8. 已知代价

- **部署后回滚不到迁移前的旧镜像**：旧镜像靠 `container` profile 和 `REGISTRY_DB_URL` 等变量启动，这些变量删除后无法直接回滚。与 `patra-infra/CLAUDE.md`「触发与回滚」里的先例相同，接受。出问题就修代码或改 Nacos 配置后重新部署。
- **mini 上会短暂运行未合入 main 的代码**：本分支部署后，main 上的下一次部署会把它覆盖回去。
- **本机 IDE 里 identity、object-storage 的日志位置变了**：从 `logs/<服务名>/<服务名>.log` 变成 `logs/<服务名>.log`。

## 9. 文档

- `patra-infra/CLAUDE.md`：环境文件分层的描述；「加新服务」不再需要建 `.env.<svc>`。
- `patra-infra/docker/README.md`：
  - 「密钥」一节：更换身份断言密钥时，公钥从改 3 处变为改 Nacos 公共配置 1 处，私钥仍在 `gateway.env`；
  - 环境文件清单同步。
- `patra-api/patra-identity/README.md`、`patra-api/patra-gateway-boot/README.md`：配置来源改为 Nacos，去掉 profile 文件的描述。
- `patra-infra/nacos/dev/README.md`：补一句公共配置里放什么。
- `patra-infra/cd/detect-changes.test.sh`：用例里如果引用了被删的 `.env.<svc>` 文件名，换成还存在的文件。规则本身不用改，`.env.<svc>` 原本就走「模块图外 → 全量」。

## 10. 交给 PAP-77 的约束

- ingest、catalog 迁移后会读到公共配置里的 Redis 坐标和数据库账号。要确认它们的库账号确实是 `postgres`。用到 Redis 的服务在自己的文件里写 `spring.data.redis.password: ${REDIS_PASSWORD}`（第 5 节）。
- `ApplicationConfigTest` 照抄到 ingest、catalog 的 boot 模块。
- 收尾时清理 `.env.common` 的 `SPRING_PROFILES_ACTIVE`、Redis 坐标、MinIO 坐标。
- 如果第 6 节决定改共享的 `service.Dockerfile`，ingest、catalog 直接沿用。
- `.claude/skills/patra-backend/skills/patra-hexagonal/resources/configuration.md` 仍按 dev / container 两个 profile 描述配置，ingest、catalog 迁完后改为 Nacos 的写法。

## 11. 不做的事

- 不把端口、路由、连接池、超时、限流参数搬进 Nacos（决定 1）。
- 不接动态刷新（PAP-78）。
- 不动 ingest、catalog 的配置（PAP-77），只受构建约定插件换依赖的影响。
