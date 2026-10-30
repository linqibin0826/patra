# 配置管理指南

## 环境配置

5 个后端服务的配置分两处，不用 Spring profile：

```
patra-{service}-boot/src/main/resources/
└── application.yml            # 随代码变的默认值：端口、连接池、超时、路由等

patra-infra/nacos/dev/          # 随环境变的值，推送到 Nacos（git 为准）
├── patra-common.yaml           # 各服务共用：数据库账号、Redis、MinIO、xxl-job、身份断言公钥
└── {服务名}.yaml               # 本服务：库地址、密钥引用等
```

启动类用 `PatraApplication.run("patra-xxx", XxxApplication.class, args)`，由启动器导入两份 Nacos 配置并检查签到。密钥在 Nacos 里只写 `${变量}`，值放在 `~/.patra/secrets/*.env`。设计见 `docs/patra/specs/2026-10-13-config-migration-batch1-design.md`。

### 本地变量约定（PAP-72）

IDE 与容器的差异只放在 Nacos 之外的本地变量里，变量按用途命名，不按来源命名：

| 变量 | 含义 | IDE | 容器 |
|---|---|---|---|
| `PATRA_INFRA_HOST` | dev 环境里基础设施所在的主机，配合宿主机映射端口使用 | mini 的 tailscale IP | `linqibins-mac-mini`（`extra_hosts` 指向宿主机网关） |
| `PATRA_ADVERTISE_IP` | 通告给 Nacos 和 xxl 的地址，留空自动探测 | 本机 tailscale IP | 不设 |
| `PATRA_LOG_DIR` | 日志目录 | 不设 | `/app/logs` |

详见 `docs/patra/specs/2026-10-13-infra-access-unification-design.md`。

## 配置优先级（由高到低）

1. 命令行参数 `--spring.datasource.url=...`
2. JVM 系统属性 `-Dspring.datasource.url=...`
3. 环境变量 `SPRING_DATASOURCE_URL`
4. `~/.patra/secrets/*.env`（仅本机进程，由 patra-tool-launch 读入，排在系统环境变量之后；容器经 compose 的 `env_file` 注入，属于上一层）
5. Nacos 配置（`{服务名}.yaml` 高于 `patra-common.yaml`）
6. `application.yml`
7. default properties：启动器 `PatraApplication` 放的服务名、Nacos 导入、签到开关，以及 patra-tool-launch 补的 Nacos 连接与服务发现默认值

同一个键不在 jar 和 Nacos 两处写：patra-tool-launch 的 `JarConfigOverlapCheckerTest` 拿 `patra-infra/nacos/dev/` 与 7 个 boot 模块 `src/main/resources` 下的全部 yml 比对。

## Nacos 接入

Nacos 的地址、账号、服务发现设置由 `patra-tool-launch` 注入，服务的配置里不写 `spring.cloud.nacos`。Nacos 同时用于服务发现和配置管理。

## 配置类模式

```java
@Component
@ConfigurationProperties(prefix = "app")
@Validated
@Data
public class AppConfig {
    @NotBlank
    private String name;

    @Min(1) @Max(60)
    private int timeout;
}
```

## 配置命名规范

```yaml
# 层级结构 + 语义化命名
patra:
  catalog:
    pubmed:
      batch-size: 1000
      api-timeout: 30s
```

## 规范

- 密钥在 Nacos 配置里写成 `${变量}` 引用，值放在 `~/.patra/secrets/`
- 所有配置属性必须提供默认值
- 禁止硬编码敏感信息
- 禁止使用 `@Profile("test")`
- 禁止在日志中输出敏感配置
