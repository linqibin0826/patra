# Nacos 配置（dev namespace）

这个目录就是 Nacos `dev` namespace、`DEFAULT_GROUP` 分组的全部配置，以 git 为准。

- **文件名即 dataId**：`patra-common.yaml` 是公共配置；`{spring.application.name}.yaml` 是各服务的配置。
- **合入 main 后自动推送**：CD 的 `nacos-push` job 在部署前把整个目录同步到 Nacos。内容不同的发布，相同的跳过，Nacos 里有、目录里没有的删除。推送失败时本次不部署。
- **控制台只在应急时用**：在控制台改的内容，下次推送会被覆盖。回滚配置就 git revert 再合入。

## 每份文件的规则

CI 里的 `NacosConfigFilesTest` 会检查这些规则，不通过的合不进 main：

1. 第一行写签到，必须是一行扁平键，值为文件名：
   - `patra-common.yaml` 写 `patra.config-source.common: patra-common.yaml`
   - 服务文件写 `patra.config-source.service: <文件名>`，例如 `patra.config-source.service: patra-ingest.yaml`

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
