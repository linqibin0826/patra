# Nacos 配置接入 starter（PAP-74）

> **Issue**：[PAP-74](https://linear.app/papertrace/issue/PAP-74)
> **版本**：[v0.9 Config Center](../release-specs/v0.9-config-center.md)（决策 A、C、E、F；Done 判定 D2、D6、D7、D8）
> **日期**：2026-10-13
> **状态**：设计已确认，待实施

## 1. 要解决的问题

6 个服务各自复制了一段 Nacos 配置（地址、账号、服务发现），现在还要接入配置中心。本 Issue 提供一个 starter：

- 统一注入 Nacos 的连接信息、配置中心和服务发现的设置；
- 服务的启动类改用 starter 的启动器，就能从 Nacos 读到 `patra-common.yaml` 和 `{服务名}.yaml`，配置文件里不写任何 Nacos 配置；
- Nacos 的值覆盖 jar 里的默认值；
- 拿不到 Nacos 配置时启动失败，并说清原因；
- 测试不经过启动器，不连 Nacos 配置中心，现有测试不用改。

设计过程中推翻了 PAP-71 的「密钥放 `dev-secrets` namespace」，改为密钥不进 Nacos，见第 4 节。

**完成标准**：第 8.3 节的验收对照表。

## 2. 范围

做：

- 新建 `patra-starters/patra-spring-boot-starter-nacos`，含单元测试和集成测试。
- 撤销 `dev-secrets` namespace：改 `nacos-init.sh`、`nacos-verify.sh`，在 mini 上执行。
- 按新决定改写 release spec 的决策 A、E、F 和 Done 判定，同步相关文档和 Linear。

不做：

- 6 个服务接入 starter、删除 `dev` / `container` profile（PAP-76、PAP-77）。
- `patra-infra/nacos/dev/` 目录和推送脚本（PAP-75）。
- `RefreshableProperties<T>`（PAP-73）。PAP-74 只保证 Nacos 变更后应用的配置环境里能看到新值。
- 容器里的本地快照目录（第 4 节决定 4）。

## 3. 现状事实（2026-10-13 核对源码）

SCA 2025.1.0.0（`spring-alibaba-nacos-config`）、nacos-client 3.1.1、Spring Boot 4.0.8：

1. **导入只支持三个参数**：`nacos:` 导入只认 `group`、`refreshEnabled`、`preference`。`NacosConfigManager` 持有进程内唯一的 `ConfigService`，只连 `spring.cloud.nacos.config.namespace` 指定的一个 namespace。一个应用不能从两个 namespace 各导入一份配置。SCA 维护者在 issue #3148 中表示不推荐、也不打算支持。
2. **拿不到配置不报错**：`ConfigService.getConfig` 的顺序是 failover 文件 → 服务端 → 本地快照（服务端出错且不是 403 时）→ 返回 `null`；服务端答复 dataId 不存在时也返回 `null`，并删除本地快照。`NacosConfigDataLoader` 拿到 `null` 只打 warn「config is empty」，返回空配置，不抛异常。所以导入不标 `optional:` 也拦不住。只有 403、解析失败这类抛异常的情况会让启动失败。SCA 没有任何属性能改变这个行为。
3. **Nacos 优先于 jar**：`preference=remote` 让加载结果按 profile 专属配置处理，优先级高于 jar 里的 `application.yml`。可以对单条导入设置，也可以用 `spring.cloud.nacos.config.preference` 全局设置。
4. **刷新**：配置变更时，SCA 的 `NacosContextRefresher` 发出 `NacosConfigRefreshEvent`，再转成 Spring Cloud 的 `RefreshEvent`，监听用的是上面那个唯一的 `ConfigService`。
5. **初始导入里的占位符不会被解析**：`spring.config.import` 如果来自环境（启动注入、系统属性），Spring Boot 在读取 `application.yml` 之前就绑定它，未解析的 `${spring.application.name}` 会原样保留。写在服务 `application.yml` 里的导入，能解析同一文件里的服务名。
5a. **写在 `application.yml` 里的导入，测试关不掉**：profile 里再写 `spring.config.import` 只会叠加，不会替换；关掉 Nacos 配置中心（`spring.cloud.nacos.config.enabled=false`）后，没标 `optional:` 的 `nacos:` 导入会因没有解析器而报错。现有的 `test`、`integration-test`、`e2e-test` profile 都只关了服务发现。
6. **容器里没有主目录**：`appuser` 是系统用户，没有主目录，nacos-client 默认的快照目录写不进去。
7. **主流做法**：调研的 6 个开源脚手架（pig、芋道、JeecgBoot、SpringBlade、RuoYi-Cloud、mall-swarm）都只用一个 namespace，没有一个把密钥单独放 namespace 或单独放 dataId。它们的 Nacos 配置都在控制台或 SQL 里维护，不从 git 推送。都没有处理第 2 条的问题。
8. **导入写在哪**：5 个把导入写在服务的 `application.yml`（或 `bootstrap.yml`），且几乎都标 `optional:`，测试连不上 Nacos 时静默跳过；芋道把导入放在 `application-dev.yaml`，测试用单独 profile 绕开。SpringBlade 用启动器 `BladeApplication.run(appName, Source.class, args)`：服务名作为参数传入，启动器在配置加载前算好导入列表（不含占位符），写进 System properties。

## 4. 已定的决定（2026-10-13）

1. **只用一个 `dev` namespace，撤销 `dev-secrets`**（推翻 PAP-71 的密钥 namespace，改写 release spec 决策 A、E）。原因：
   - 要从两个 namespace 导入，就得自己实现配置加载，长期维护成本高；
   - 官方设计也是一个应用只连一个 namespace。
2. **密钥不进 Nacos**（改写决策 E）。git 推送是整份文件覆盖，「git 推送」「密钥写在普通配置文件里」「仓库公开」三者不能同时成立。所以按两类处理：
   - **公开的 dev 默认值**（PG、MinIO、xxl-job 的账号密码，已在仓库公开）：Nacos 配置里直接写，或写成 `${变量:默认值}`。
   - **真正的密钥**（Redis 密码、网关签身份断言的私钥、外部数据源 API key）：Nacos 配置里只写 `${变量}`，不给默认值。值留在两台机器的 `~/.patra/secrets/`：本机由 starter 读取（5.4），容器经 compose 的 env_file 注入。v0.8 决策 G 的理由仍然成立：Redis 密码一公开，任何能连上的人都能写入假会话。
3. **直接用 SCA 的 `nacos:` 导入，不自己实现配置加载**。SCA 的静默问题用签到检查兜底（5.5）。
4. **容器不处理本地快照**。容器里没有快照，Nacos 不可用时服务不能重启。单人 dev 环境里，Nacos 挂了时服务发现也会断，靠快照启动价值很小。本机 IDE 用 nacos-client 默认的 `~/nacos/config`，不做处理。release spec D7 和 PAP-74 验收里的快照条目删除。
5. **PAP-74 只交付 starter**。各服务接入时，Nacos 里必须已有对应配置，所以接入放在 PAP-76、PAP-77 迁移时做。
6. **导入由启动器加，不写在配置文件里**（SpringBlade 的做法，修正 release spec 决策 F）。服务启动类调 `PatraApplication.run("patra-registry", PatraRegistryApplication.class, args)`。理由：
   - 初始导入里的服务名占位符解析不了（第 3 节第 5 条），服务名得由代码传入；
   - 导入写在 `application.yml` 里，测试关不掉（第 3 节第 5a 条），每个带 `@SpringBootTest` 的测试都得连 Nacos；启动器只在 `main` 里生效，测试不经过它；
   - 服务的配置文件里一行 Nacos 配置都不用写。

   与 SpringBlade 的区别：启动器把值放进 Spring 的 default properties（优先级最低），不写 System properties。决策 F 否决 SpringBlade 的理由是「System properties 让环境变量覆盖不了」，用 default properties 后不成立。代价：这是少数派做法，配置文件里看不到导入，由 starter README 和启动器注释说明。

## 5. 设计

### 5.1 模块

`patra-starters/patra-spring-boot-starter-nacos`，插件 `linqibin.module-patra` + `linqibin.spring-boot-starter`，放在 Patra 专属 starter 下：两份配置的文件名和签到字段都是 Patra 的约定。依赖（`api`）：

- `spring-boot-starter`：`EnvironmentPostProcessor`、`SpringApplication` 等；SCA 的两个 starter 不把 Spring Boot 传到编译 classpath（实施时补）；
- `spring-cloud-starter-alibaba-nacos-config`（不引入 bootstrap）；
- `spring-cloud-starter-alibaba-nacos-discovery`。

包名 `dev.linqibin.patra.starter.nacos`。

### 5.2 启动时注入的默认配置（所有启动方式都生效）

一个 `EnvironmentPostProcessor`（`NacosDefaultsPostProcessor`，在 `META-INF/spring.factories` 里登记），在 Spring Boot 的配置加载之前，往环境里加一组属性。这组属性放在**最后**（优先级最低），服务的 `application.yml`、Nacos 配置、环境变量都能覆盖它。测试经过它，所以这里只放测试也需要的连接和服务发现设置。

| 内容 | 值 |
|---|---|
| Nacos 地址（服务发现和配置中心都用） | `${PATRA_NACOS_ADDR:${PATRA_INFRA_HOST:127.0.0.1}:8848}` |
| Nacos 账号 | `${NACOS_USERNAME}`、`${NACOS_PASSWORD}`，不给默认值 |
| 服务发现 | namespace 不设（public）；`service` 取 `${spring.application.name}`；通告地址取 `${PATRA_ADVERTISE_IP:}`；`fail-fast: false`（理由见现有 yml 的注释：nacos 3.x gRPC 握手未完成时，同步注册会让应用启动失败） |
| 配置中心开关 | 环境里没有 `nacos:` 导入时，设 `spring.cloud.nacos.config.enabled=false`。否则 SCA 的导入检查会因「`spring.config.import` 里没有 `nacos:`」让测试启动失败 |

`PATRA_NACOS_ADDR` 是新变量，平时不设。以后 Nacos 和其他基础设施不在同一台机器上时，只设它即可。具体属性键在实施计划里逐条列出，并由单元测试固定。

### 5.3 启动器 `PatraApplication`

服务启动类从 `SpringApplication.run(X.class, args)` 改为：

```java
PatraApplication.run("patra-registry", PatraRegistryApplication.class, args);
```

启动器在调用 Spring Boot 之前，把下面这些放进 Spring 的 default properties（优先级最低，环境变量、配置文件、Nacos 都能覆盖）：

| 内容 | 值 |
|---|---|
| 服务名 | `spring.application.name=patra-registry` |
| 导入列表 | `nacos:patra-common.yaml`、`nacos:patra-registry.yaml`，按此顺序，都不标 `optional:`。后导入的覆盖先导入的，服务自己的配置覆盖公共配置 |
| 配置中心 namespace | `spring.cloud.nacos.config.namespace=dev` |
| Nacos 优先于 jar 默认值 | `spring.cloud.nacos.config.preference=remote`（决策 C） |
| 签到检查开关 | `patra.config-source.required=true`（5.5） |

- 服务名只在启动类一处，`application.yml` 里删掉 `spring.application.name`。测试不经过启动器，需要服务名的测试在测试 profile 里自己设（ingest 的测试配置已经这样做）。
- 服务的配置文件里没有任何 Nacos 配置。
- 服务没用启动器、直接 `SpringApplication.run` 时，不导入 Nacos 配置，也不做签到检查，相当于测试的状态。这一点由 PAP-76、PAP-77 接入时保证，接入后在启动日志里确认读到了两份配置。

### 5.4 本机密钥文件

同一个 `EnvironmentPostProcessor` 把 `${user.home}/.patra/secrets/` 下的所有 `*.env` 文件读进来，作为一组属性：

- 文件格式是 `KEY=value`，按 properties 解析，与 compose env_file 共用同一批文件。
- 放在系统环境变量之后，所以环境变量能覆盖文件里的值。
- 目录不存在时跳过，不报错。容器里就是这种情况，容器的值来自 env_file 注入的环境变量。
- 按文件名排序后读取。各文件的变量名不重复（2026-10-13 核对 MacBook 上的 4 个文件），所以读取顺序不影响结果。

这样本机 IDE 启动的服务能读到 Nacos 账号、Redis 密码、网关私钥等，各服务删掉 `file:${user.home}/.patra/secrets/*.env[.properties]` 这类导入（PAP-76、PAP-77）。

这一步也在 5.2 的 `EnvironmentPostProcessor` 里做，测试同样会读到这些文件。测试用的值由测试 profile 设定，优先级更高，不受影响。

### 5.5 签到检查

每份 Nacos 配置里写一行签到：

```yaml
# patra-common.yaml
patra:
  config-source:
    common: patra-common.yaml
```

```yaml
# patra-ingest.yaml
patra:
  config-source:
    service: patra-ingest.yaml
```

starter 的第二个 `EnvironmentPostProcessor`（`ConfigSourceCheckPostProcessor`）在 Spring Boot 加载完配置（含 Nacos 导入）之后，立即检查这两个签到字段，缺了或为空白就抛异常、启动中止。报错写明原因，两个都缺时两条都报：

- 缺 `common`：「没拿到 Nacos 配置 patra-common.yaml：Nacos 不可达且本地没有快照，或该文件不存在」
- 缺 `service`：「没拿到本服务的 Nacos 配置 {服务名}.yaml：……」（原因同上）

签到字段只写在 Nacos 的文件里，jar 里永远不写，jar 默认值没法冒充。检查只在 `patra.config-source.required=true`（启动器设置）时进行，测试不经过启动器，不做这项检查。

放在配置加载阶段而不用 Bean 校验（写实施计划时改定）：
- 比任何 Bean 创建都早，Nacos 没到时不会先冒出数据源、Redis 等不相关的报错；
- 报错里能带上实际的服务名；
- 不依赖 Bean Validation 的实现。

配置刷新时，spring-cloud-context 会重跑所有 `EnvironmentPostProcessor`。少了签到行的那次变更会被拒绝，应用继续用旧配置。

边界：

- 只确认文件到了，不检查文件里每个配置项是否正确。
- 签到行被删时同样启动失败，但报错原因会写成「没拿到」，分不清是没取到还是少了签到行。
- 两个文件写成同一个签到值时发现不了。签到值写成文件名，主要是为了报错和排查时可读。

### 5.6 刷新

启动器加的两条导入都用 SCA 的默认值 `refreshEnabled=true`。Nacos 里的配置一变，SCA 发出 `RefreshEvent`，spring-cloud-context 重新加载配置并发出 `EnvironmentChangeEvent`。之后由谁、如何处理新值，归 PAP-73。

### 5.7 优先级

按 Spring Boot 的规则推断，从高到低：

1. 启动参数、系统属性、环境变量
2. 本机密钥文件（5.4）
3. Nacos 配置
4. jar 里的 `application.yml`
5. 启动器的 default properties、starter 注入的默认值

集成测试实测 1 > 3 > 4，结果写回本节（Issue 待办「实测并记录进程级覆盖与 Nacos 的优先级」）。

## 6. 故障时的表现

| 情况 | 结果 |
|---|---|
| Nacos 正常 | 读到两份配置，签到通过 |
| Nacos 不可达，本机 IDE 有快照 | 用快照启动，日志里有 warn |
| Nacos 不可达，没有快照（容器总是这样） | 签到检查失败，启动中止 |
| 某份配置在 Nacos 里不存在 | 签到检查失败，启动中止（客户端会删掉本地的旧快照） |
| 服务没用启动器启动 | 不导入 Nacos 配置、不做签到检查，按 jar 里的配置启动（5.3） |
| `NACOS_USERNAME` / `NACOS_PASSWORD` 没设 | 解析不了占位符，启动失败 |
| 账号或密码错（403） | SCA 抛异常，启动失败，不读快照 |
| 本地密钥变量没设（如 `REDIS_PASSWORD`） | 用到它的服务启动失败，报出缺的变量名 |

## 7. Nacos 脚本

- **`nacos-init.sh`**：
  - 删掉 `dev-secrets` namespace 的创建；
  - 删掉 `patra-app` 在 `dev-secrets` 上的权限；
  - 保留 `patra-app` 只读 `dev`、`patra-push` 可写 `dev`；
  - 脚本按清单删多余的权限，所以重跑后 mini 上的旧权限会被清掉。
  - 脚本只负责「确保存在」，不负责删除 namespace。mini 上已有的 `dev-secrets` 在切换时用管理员令牌调一次删除 namespace 的 v3 接口；删除前先确认里面没有配置。
- **`nacos-verify.sh`**：删掉 `dev-secrets` 相关的检查项（namespace 列表、`patra-app` 读 `dev-secrets`、`patra-push` 动不了 `dev-secrets` 等），检查项数相应减少。保留的检查项照旧 PASS。
- **在 mini 上执行**：先删 namespace、跑 `nacos-init.sh`，再跑 `nacos-verify.sh --expect-services`。动 mini 前先征得用户确认。

## 8. 测试

### 8.1 单元测试

- 注入的默认配置：每个属性键和值；`PATRA_NACOS_ADDR` 没设时回退到 `PATRA_INFRA_HOST:8848`；服务的配置能覆盖注入的默认值；环境里没有 `nacos:` 导入时关闭配置中心，有时不关。
- 启动器：放进 default properties 的服务名、导入列表（顺序）、namespace、`preference`、签到开关；服务名为空时报错。
- 本机密钥文件：目录不存在时跳过；读取多个 `*.env`；环境变量覆盖文件里的值；非 `.env` 文件被忽略。
- 签到校验：缺 `common`、缺 `service` 时报错信息正确；两个都有时通过。

### 8.2 集成测试

用 Testcontainers 起 `nacos/nacos-server:v3.2.4`，开启鉴权，管理员账号由测试创建并写入测试配置。测试应用只引入 starter，经 `PatraApplication` 启动，`application.yml` 里没有任何 Nacos 配置。

1. 读到两份配置；同一个键在 jar 的 `application.yml` 和 Nacos 里都有时，取 Nacos 的值；服务配置覆盖公共配置。
2. 环境变量（或系统属性）覆盖 Nacos 的值。实测结果写回 5.7。
3. 服务那份配置在 Nacos 里不存在：启动失败，异常链里有签到报错信息。
4. Nacos 不可达（地址指向一个没有服务的端口），且没有快照：启动失败，报签到错误。测试把快照目录指向临时目录，避免受本机已有快照影响。
5. 密码错误：启动失败。
6. 启动后修改 Nacos 里的值：运行中应用的 `Environment` 在超时内读到新值。
7. 同一个测试应用不经启动器、用 `@SpringBootTest` 启动，且 Nacos 不可达：正常启动，没有导入 Nacos 配置，也没有签到检查。
8. Nacos 配置里写 `${变量}`，值能从环境变量解析到（第 9 节第 4 条）。

### 8.3 验收对照

| PAP-74 验收（按本设计改写后） | 怎么验证 |
|---|---|
| 集成测试：服务按顺序读到两个 dataId，Nacos 的值覆盖 jar 默认值 | 8.2 第 1、2 条 |
| Nacos 不可达且无快照、或配置不存在时，启动失败并给出明确错误 | 8.2 第 3、4 条 |
| 引入 starter 的服务只改启动类，配置文件里不写 Nacos 配置；测试不连 Nacos 配置中心 | 8.2 测试应用的 `application.yml`；8.2 第 7 条 |
| `dev-secrets` 已撤销，mini 上 Nacos 检查全部通过 | `nacos-verify.sh --expect-services` |

## 9. 实施时要实测的点

1. **gRPC 端口偏移**：nacos-client 用「HTTP 端口 + 1000」连 gRPC。Testcontainers 随机映射端口时两者不满足这个关系，需要用 nacos-client 的端口偏移设置（如系统属性 `nacos.server.grpc.port.offset`）或固定端口解决，以实测为准。
2. **注入的属性能否被 SCA 的导入解析读到**：SCA 解析 `nacos:` 导入时绑定 `spring.cloud.nacos.*`。启动器的 default properties、starter 注入的属性和密钥文件的属性需要在那时已经在环境里，集成测试第 1 条会覆盖这一点。
2a. **`spring.cloud.nacos.config.enabled` 的判断时机**：5.2 的 `EnvironmentPostProcessor` 要在 SCA 的导入检查之前看到启动器放进 default properties 的导入列表。
3. **5.7 的优先级**：尤其是 Nacos 和 jar `application.yml` 都写了同一个键时，`preference=remote` 是否让 Nacos 胜出。
4. **Nacos 配置里的 `${变量}`**：Nacos 文件里写 `${REDIS_PASSWORD}`，能否解析到环境变量或本机密钥文件里的值。在集成测试里加一个这样的键。
5. **容器里写不了快照时的日志**：nacos-client 往不可写的目录存快照时，会不会每次都打错误日志。PAP-74 里不接入服务，这一条在 PAP-76 第一个服务上 mini 时观察；真会刷屏，再决定是否在 Dockerfile 里给快照一个可写目录。

## 10. 已知限制（接受）

- 容器在 Nacos 不可用时不能重启（第 4 节决定 4）。
- 签到检查的边界见 5.5。
- 改一个真正的密钥，仍要在两台机器的 `~/.patra/secrets/` 各改一次。这是 v0.8 的做法，只涉及 Redis 密码、网关私钥、几个外部 API key。
- 本机进程能读到 `~/.patra/secrets/` 下的所有密钥，包括它用不到的。这些值只在进程内存里；actuator 只暴露 health、info、metrics，不暴露 env。

## 11. 文档与 Linear

- **release spec `v0.9-config-center.md`**：
  - 决策 A：改为只有 `dev` 一个 namespace；
  - 决策 E：改写为「密钥不进 Nacos，配置用 `${变量}` 引用，值在本地」，写明推翻 PAP-71 做法的原因和代价；
  - 决策 F：改为「连接和服务发现由 starter 注入，服务名和导入由启动器 `PatraApplication` 放进 default properties」，删掉对 SpringBlade 做法的否决，写明与它的区别（第 4 节决定 6）；
  - PAP-74、PAP-76、PAP-77 的描述去掉 `patra-secrets.yaml` 和 `dev-secrets`；
  - Done 判定：
    - D2：删掉 `patra-secrets.yaml` 和 `dev-secrets`；「env 文件只剩本地变量和 Nacos 凭据」改为「env 文件只剩本地变量、Nacos 凭据和密钥」；
    - D6：删掉「`~/.patra/secrets` 除 Nacos 凭据外不再被读取」；
    - D7：删掉「靠本地快照启动」，保留「Nacos 恢复后配置变更照常推送和生效」；
    - D8：集成测试对象改为本 starter。
- **PAP-71 设计文档**：开头加一条说明，指出 `dev-secrets` 已在 PAP-74 撤销，见本设计第 4 节。
- **`patra-infra/CLAUDE.md`、`patra-infra/docker/README.md`**：去掉 `dev-secrets`，Nacos 一节写明只有 `dev` 一个 namespace。
- **Linear**：
  - PAP-71：评论说明撤销 `dev-secrets` 的原因，状态不变；
  - PAP-74：To-Do 和 Acceptance Criteria 改成本设计的内容；
  - PAP-75、PAP-76、PAP-77：去掉 `patra-secrets.yaml`，写明各自的约束（第 12 节）。

## 12. 交给其他 Issue 的约束

- **PAP-75（推送）**：
  - 每份推送的文件必须带签到行。`patra-common.yaml` 写 `patra.config-source.common`，服务文件写 `patra.config-source.service`，值为文件名。推送前脚本逐个检查，缺了就拒绝推送。
  - 仓库里的配置文件不能有真正的密钥，只能写 `${变量}`。
- **PAP-76、PAP-77（迁移）**：
  - 服务启动类改用 `PatraApplication.run("服务名", X.class, args)`（5.3）；`application.yml` 删掉 `spring.application.name`、原有的 Nacos 配置段和 `~/.patra/secrets` 导入；
  - 测试里需要服务名的，在测试 profile 里自己设 `spring.application.name`；
  - convention plugin `linqibin.hexagonal-boot` 把直接依赖的 nacos-discovery 换成本 starter；
  - 真正的密钥在 Nacos 配置里写成 `${变量}`，compose 的 env_file 继续加载 `~/.patra/secrets/` 下对应的文件；
  - 第一个服务上 mini 时观察第 9 节第 5 条。
- **PAP-73（刷新）**：配置变更通过 spring-cloud-context 的 `RefreshEvent` → `EnvironmentChangeEvent` 到达，签到字段不参与刷新。
