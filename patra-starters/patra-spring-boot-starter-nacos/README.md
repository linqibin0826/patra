# patra-spring-boot-starter-nacos

Patra 服务接入 Nacos 的 starter。它统一注入 Nacos 的连接和服务发现设置，经启动器从配置中心读取 `patra-common.yaml` 和 `{服务名}.yaml`，拿不到配置时启动失败。

工程设计：`docs/patra/specs/2026-10-13-nacos-config-starter-design.md`。

## 1. 怎么引入

boot 模块依赖本 starter，它带上 SCA 的 nacos-config 和 nacos-discovery：

```kotlin
implementation(project(":patra-starters:patra-spring-boot-starter-nacos"))
```

启动类改用启动器，服务名只写在这里：

```java
public static void main(String[] args) {
  PatraApplication.run("patra-registry", PatraRegistryApplication.class, args);
}
```

服务的 `application.yml` 里不写 `spring.application.name`，也不写任何 Nacos 配置。

## 2. 启动器做了什么

`PatraApplication.run` 在启动 Spring Boot 之前，把下面 5 项放进 default properties。它们优先级最低，环境变量、配置文件、Nacos 都能覆盖：

| 键 | 值 |
|---|---|
| `spring.application.name` | 传入的服务名 |
| `spring.config.import` | `nacos:patra-common.yaml,nacos:{服务名}.yaml`：后者覆盖前者，都不标 `optional:` |
| `spring.cloud.nacos.config.namespace` | `dev` |
| `spring.cloud.nacos.config.preference` | `remote`：Nacos 的值覆盖 jar 里的默认值 |
| `patra.config-source.required` | `true`：开启签到检查 |

导入不写在配置文件里，原因有两个：
- 初始导入里的服务名占位符解析不了；
- 写在 `application.yml` 里的导入，测试关不掉。

## 3. 所有启动方式都会注入的设置

`NacosDefaultsPostProcessor` 在 Spring Boot 加载配置之前运行，测试也会经过它。默认值并入 default properties，服务的配置能覆盖：

| 键 | 值 |
|---|---|
| `spring.cloud.nacos.server-addr` | `${PATRA_NACOS_ADDR:${PATRA_INFRA_HOST:127.0.0.1}:8848}` |
| `spring.cloud.nacos.username` / `password` | `${NACOS_USERNAME}` / `${NACOS_PASSWORD}`，不给默认值 |
| `spring.cloud.nacos.discovery.service` | `${spring.application.name}` |
| `spring.cloud.nacos.discovery.ip` | `${PATRA_ADVERTISE_IP:}`，留空时自动探测 |
| `spring.cloud.nacos.discovery.fail-fast` | `false`：nacos 3.x gRPC 握手未完成时，同步注册会让应用启动失败 |

它还做两件事：
- 环境里没有 `nacos:` 导入时（测试、没用启动器的应用），设 `spring.cloud.nacos.config.enabled=false`，不连配置中心。
- 有 `nacos:` 导入时，读完本机密钥后检查 Nacos 账号密码能否解析，解析不了就启动失败并报出缺的变量名。SCA 自己不严格解析，会拿字面量 `${NACOS_PASSWORD}` 去登录，报错和密码错误分不开。
- 把 `~/.patra/secrets/*.env` 读成一组属性，放在系统环境变量之后。
  - 文件按文件名排序后读，同名变量以后读的为准。
  - 目录不存在时跳过。容器里就是这种情况，值来自 compose env_file 注入的环境变量。

## 4. 签到检查

每份 Nacos 配置写一行签到，值为文件名，只写在 Nacos 里：

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

SCA 拿不到配置时只打 warn、返回空配置，不会让启动失败。所以 `ConfigSourceCheckPostProcessor` 在配置加载完之后检查这两个字段，缺了就让启动失败，例如：

```text
没拿到本服务的 Nacos 配置 patra-ingest.yaml：Nacos 不可达且本地没有快照，或该文件不存在
```

配置刷新时也会检查：少了签到行的那次变更被拒绝，应用继续用旧配置。

## 5. 优先级（从高到低）

1. 启动参数、系统属性、环境变量
2. 本机密钥文件 `~/.patra/secrets/*.env`
3. Nacos 配置（本服务的覆盖公共的）
4. jar 里的 `application.yml`
5. 启动器的 default properties、starter 注入的默认值

## 6. 密钥怎么写

- 公开的 dev 默认值（PG、MinIO、xxl-job 的账号密码）：Nacos 配置里直接写，或写成 `${变量:默认值}`。
- 真正的密钥（Redis 密码、网关私钥、外部数据源 API key）：Nacos 配置里只写 `${变量}`，不给默认值。值留在两台机器的 `~/.patra/secrets/`：本机由本 starter 读取，容器经 compose 的 env_file 注入。

## 7. 测试

- 测试不经过启动器，不导入 Nacos 配置，也不做签到检查。
- 需要服务名的测试，在测试 profile 里设 `spring.application.name`。
- 服务发现照旧在测试 profile 里关掉。
