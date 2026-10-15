# patra-tool-launch

## 能力

服务的启动器模块。`PatraApplication` 是统一的 `main` 入口：启动前把服务名、Nacos 的 `patra-common.yaml` 与本服务 `{服务名}.yaml` 的导入放进 default properties，服务自己的配置文件里因此不写任何 Nacos 配置。`NacosDefaultsPostProcessor` 在 Spring Boot 加载配置之前注入 Nacos 连接与服务发现的默认值，并读取 `~/.patra/secrets/*.env` 作为本机密钥；`ConfigSourceCheckPostProcessor` 在启动期检查两份 Nacos 配置真的拿到了（靠配置里的签到行）、且其中引用的变量都能解析，不满足就让启动失败。

自动配置 `LaunchAutoConfiguration` 注册系统 UTC `Clock` bean，服务自定义了 `Clock` 时让位。测试不经过启动器，不导入 Nacos 配置，也不做签到检查。

## 配置（前缀 `patra.launch`）

| 键 | 类型 | 默认值 | 说明 |
|---|---|---|---|
| `enabled` | boolean | `true` | 总开关 |

## 怎么覆盖

```yaml
patra:
  launch:
    enabled: false
```

启动器放的默认值都在 default properties 里，优先级最低，环境变量、服务配置文件、Nacos 都能覆盖，例如用环境变量 `PATRA_NACOS_ADDR` 指定 Nacos 地址。

## 出哪些默认值

`NacosDefaultsPostProcessor` 出（同名键已存在时不覆盖）：

- `spring.cloud.nacos.server-addr`：`${PATRA_NACOS_ADDR:${PATRA_INFRA_HOST:127.0.0.1}:8848}`
- `spring.cloud.nacos.username`：`${NACOS_USERNAME}`
- `spring.cloud.nacos.password`：`${NACOS_PASSWORD}`
- `spring.cloud.nacos.discovery.service`：`${spring.application.name}`
- `spring.cloud.nacos.discovery.ip`：`${PATRA_ADVERTISE_IP:}`
- `spring.cloud.nacos.discovery.fail-fast`：`false`
- `spring.cloud.nacos.config.enabled`：`false`，仅当环境里没有 `nacos:` 导入时

`PatraApplication` 以 default properties 出：

- `spring.application.name`：启动时传入的服务名
- `spring.config.import`：`nacos:patra-common.yaml,nacos:{服务名}.yaml`
- `spring.cloud.nacos.config.namespace`：`dev`
- `spring.cloud.nacos.config.preference`：`remote`
- `patra.config-source.required`：`true`
