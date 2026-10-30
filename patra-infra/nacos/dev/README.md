# Nacos 配置（dev namespace）

这个目录就是 Nacos `dev` namespace、`DEFAULT_GROUP` 分组的全部配置，以 git 为准。

- **文件名即 dataId**：`patra-common.yaml` 是公共配置；`{spring.application.name}.yaml` 是各服务的配置。
- **只放随环境变化的值**：基础设施坐标、账号、公钥、密钥引用。端口、路由、连接池、超时等和代码一起变的默认值，留在各服务 jar 里的 `application.yml`。`patra-common.yaml` 放各服务共用的数据库账号、Redis 坐标、身份断言公钥，以及 catalog、identity 共用的 MinIO 坐标和 xxl-job 管理端地址；服务文件放本服务的库地址等。
- **公共配置里不写没有默认值的变量引用**：服务启动时会检查 Nacos 配置引用的每个变量，没有值、也没写默认值的直接启动失败。公共配置每个服务都读，引用 `${REDIS_PASSWORD}` 这类密钥会让没加载对应 `.env` 的服务也启动不了，所以密钥引用写在用到它的服务文件里。可以不配的外部凭据（Scopus key、青果代理）写成带空默认值的引用，例如 `${SCOPUS_API_KEY:}`：没配时服务照常启动，用到时才失败。
- **合入 main 后自动推送**：每次 push main 触发的 CD 都会先跑 `nacos-push` job，在部署前把整个目录同步到 Nacos（不只是改了配置的那次）。内容不同的发布，相同的跳过，Nacos 里有、目录里没有的删除。推送失败时本次不部署。
- **控制台只在应急时用**：在控制台改的内容，下次推送会被覆盖。回滚配置就 git revert 再合入。

## 每份文件的规则

`NacosConfigFilesTest` 会检查这些规则：CI 里不通过的合不进 main；CD 推送前在 mini 上再跑一次，不通过就不推送（直推 main 时也挡得住）：

1. 第一行写签到，必须是一行扁平键，值为文件名：
   - `patra-common.yaml` 写 `patra.config-source.common: patra-common.yaml`
   - 服务文件写 `patra.config-source.service: <文件名>`，例如 `patra.config-source.service: patra-catalog.yaml`

   服务启动时，靠这一行确认确实读到了 Nacos 的配置。
2. **不写密钥**：仓库是公开的。键名以 password、passwd、secret、token、credential(s)、private-key、api-key、access-key、secret-key 结尾的，值只能写 `${变量}` 或 `${变量:公开的 dev 默认值}`，也可以留空。变量的值本机放在 `~/.patra/secrets/`，容器经 env_file 注入。
3. 不能有重复键；换行符用 LF。

## 本机手动推送

推送用 `patra-push` 账号，凭据从 `~/.patra/secrets/nacos-push.env` 读。先看会改什么，再推：

```bash
NACOS_ADDR=http://100.103.73.27:8848 bash patra-infra/scripts/nacos-push.sh --check
NACOS_ADDR=http://100.103.73.27:8848 bash patra-infra/scripts/nacos-push.sh
```

`--check` 的退出码：0 表示一致，2 表示有差异，1 表示出错。在 mini 上直接运行时不用设 `NACOS_ADDR`。

设计：`docs/patra/specs/2026-10-13-nacos-config-push-design.md`
