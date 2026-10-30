# Patra 基础设施 - Docker Compose 配置

Patra 全部基础设施容器部署在 **Mac mini**（hostname: `linqibins-mac-mini`），
通过 tailscale MagicDNS 对外提供服务。MacBook 上仅运行 patra-api 应用进程，
通过环境变量 `PATRA_INFRA_HOST=linqibins-mac-mini` 指向容器。

---

## 结构

```
docker/
├── docker-compose.core.yaml         # project patra-core：postgres + redis + nacos + rabbitmq
├── docker-compose.storage.yaml      # project patra-storage：minio + minio-init
├── docker-compose.search.yaml       # project patra-search：elasticsearch
├── docker-compose.observability.yaml # project patra-observability：otel/prom/loki/tempo/grafana
├── docker-compose.jobs.yaml         # project patra-jobs：xxl-job-admin + mysql-ops + 路由边车
├── docker-compose.tailnet.yaml      # project patra-tailnet：tailscale 共享网关
├── .env                              # mysql-ops 凭据等环境变量
├── .env.secret                       # TS_AUTHKEY 等真实密钥（gitignore，不入库）
├── rabbitmq/conf.d/                  # RabbitMQ 的内存与磁盘水位，只读挂进容器
├── secrets/                          # 密钥模板（*.example）；真文件在仓库外的 ~/.patra/secrets/
└── README.md                         # 本文件
```

**每个子栈是独立的 Docker project**（`patra-core` / `patra-storage` / …），共享外部网络 `patra-net`，
这样 IDEA / OrbStack 按子栈分组显示。多 project 无法用单条 `docker compose` 拉起，统一入口是
**`scripts/compose-all.sh`**（`up` / `down` / `ps`，取代旧的 `docker-compose.dev.yaml`）。

---

## 部署目标：Mac mini

| 项 | 值 |
|---|---|
| 主机 | `linqibins-mac-mini`（tailscale MagicDNS 短名） |
| 容器引擎 | OrbStack |
| 仓库副本位置 | `~/Projects/patra/`（monorepo，sparse-checkout 仅 patra-infra；部署 SSOT） |
| 数据卷根目录 | `~/.patra/docker/` |

**MacBook ↔ Mac mini 链路**：

- 在家（同一 LAN）→ DNS 解析为 `192.168.1.11` → LAN 直连，延迟 < 5ms
- 离家 → DNS 解析为 `100.103.73.27` → tailscale 加密隧道

两种网络环境无需任何切换配置。

---

## 首次部署

### 前置条件

1. Mac mini 已安装 OrbStack（`docker --version` 可用）
2. Mac mini 已加入 tailnet，hostname 为 `linqibins-mac-mini`
3. MacBook 可 `ssh linqibin@linqibins-mac-mini` 免密登录
4. patra-infra 仓库已配置 git remote（GitHub/GitLab/ssh 直连任意均可）

### 步骤

```bash
# 1. (MacBook) 推送当前分支
git push -u origin <branch-name>

# 2. (MacBook) ssh 到 Mac mini
ssh linqibin@linqibins-mac-mini

# 3. (Mac mini) sparse-checkout monorepo（仅 patra-infra，保持精简）到固定位置
git clone --filter=blob:none --sparse git@github.com:linqibin0826/patra.git ~/Projects/patra
cd ~/Projects/patra
git sparse-checkout set patra-infra
git checkout <branch-name>

# 4. (Mac mini) 一次性初始化数据目录与配置文件
bash patra-infra/scripts/init-volumes.sh

# 4b. (Mac mini) 创建 .env.secret 供 tailscale 网关使用（gitignore，不随 git 同步）
#     auth key 在 https://login.tailscale.com/admin/settings/keys 生成（reusable、非 ephemeral）
cp patra-infra/docker/.env.secret.example patra-infra/docker/.env.secret
# 编辑 .env.secret，把 TS_AUTHKEY 填成真实值（tskey-auth-...）

# 4c. (MacBook) 把密钥文件复制到 Mac mini：与 MacBook 上的是同一份，生成方法见下文「密钥」。
#     目录由第 4 步建好；缺了这几个文件，core 栈的 redis、nacos、rabbitmq 和整个 apps 栈都起不来。
#     mail.env 可选：没有它 identity 的邮件进 core 栈的 Mailpit；接真实服务商时按 secrets/mail.env.example 在 mini 上放一份
scp -p ~/.patra/secrets/redis.env ~/.patra/secrets/rabbitmq.env ~/.patra/secrets/gateway.env \
  ~/.patra/secrets/nacos.env ~/.patra/secrets/nacos-push.env linqibin@linqibins-mac-mini:.patra/secrets/
# 4d. (Mac mini) 生成只放 mini 的 nacos-server.env、nacos-admin.env，命令见 secrets/ 下对应的 *.example。
#     管理员密码另存一份到密码管理器

# 4e. (Mac mini) 下载 identity 查 IP 属地用的 ip2region 数据（两个 xdb，约 46 MB，到 ~/.patra/data/ip2region/）。
#     版本与校验和在 scripts/ip2region.properties；缺文件 identity 起不来。更新数据 = 改它、pull、重跑、重启 identity
bash patra-infra/scripts/fetch-ip2region.sh

# 5. (Mac mini) 启动全栈（含 tailscale 网关；镜像走 ghcr.io，docker.io 在国内常 502）
#    脚本会先幂等创建共享网络 patra-net，再按依赖顺序逐个子栈 up
bash patra-infra/scripts/compose-all.sh up

# 6. (Mac mini) 验证全部 healthy
bash patra-infra/scripts/compose-all.sh ps

# 7. (Mac mini) 初始化 Nacos（nacos 容器 healthy 后执行，幂等）：管理员、dev namespace、账号、角色和权限
bash patra-infra/scripts/nacos-init.sh
bash patra-infra/scripts/nacos-verify.sh    # 预期 通过 9 / 失败 0
```

### MacBook 应用访问 Nacos

MacBook 上的应用直连 Mac mini 的 Nacos（HTTP 8848 + gRPC 9848），**无需 ssh tunnel**：Nacos 地址缺省跟随 `PATRA_INFRA_HOST`，由 Nacos starter 注入。账号 `patra-app` 来自 `~/.patra/secrets/nacos.env`，由 starter 读取，缺了启动失败。Nacos 控制台同样直连：`http://$PATRA_INFRA_HOST:8080`，用管理员登录。

> 曾因 tailscale MTU 丢 gRPC HTTP/2 帧而必须经 ssh tunnel 转发，现已拆除。直连卡 STARTING 时的排查见 `../docs/mac-mini-connectivity.md` §2。

### Mac mini 一次性 PATH 配置

非交互式 ssh 会话不会加载完整的 shell profile，需要额外把 OrbStack 的 docker 路径写入 `~/.zshenv`：

```bash
ssh linqibin@linqibins-mac-mini "echo 'export PATH=/usr/local/bin:\$PATH' >> ~/.zshenv"
```

之后远程 `ssh ... docker ...` 才能找到 docker 命令。

---

## 日常迭代

每次 MacBook 改完 compose / .env 后：

```bash
ssh linqibin@linqibins-mac-mini '
  cd ~/Projects/patra &&
  git pull &&
  bash patra-infra/scripts/compose-all.sh up
'
```

如果只改了某个栈，可只 up 该栈，或直接重启单个容器（容器名稳定，与 project 无关）：

```bash
ssh linqibin@linqibins-mac-mini '
  cd ~/Projects/patra &&
  bash patra-infra/scripts/compose-all.sh up jobs   # 只重建 jobs 栈
'
ssh linqibin@linqibins-mac-mini 'docker restart patra-xxl-job-admin'  # 只重启单容器
```

> **一次性迁移（旧单 project `patra` → 多 project）**：旧容器 `container_name` 与新栈相同，
> 不先拆旧 project 会因重名启动失败（安全失败，非数据丢失）。**git pull 前**先用旧文件拆掉旧栈：
> `docker compose -p patra down`，再 pull、再 `compose-all.sh up`。数据是 bind mount，不受影响。

## 密钥

密钥放在仓库外的 `~/.patra/secrets/`（目录 0700，文件 0600）。除了只放 mini 的 `nacos-server.env`、`nacos-admin.env`，MacBook 和 Mac mini 各放一份，内容相同。

- 两边放同一份，是因为本机开发和 mini 是同一个环境，共用 mini 的 Redis、RabbitMQ 和同一对签名密钥。
- 不放在 compose 目录，是因为 CD 每次运行前的 `git clean` 会删掉 runner 工作区里被忽略的文件。

| 文件 | 内容 | 谁加载 | 缺失时 |
|---|---|---|---|
| `redis.env` | `REDIS_PASSWORD`，64 位十六进制 | core 栈的 redis；catalog、identity 的容器和本机进程（gateway 经 identity 查会话，不连 Redis） | compose 报出路径并拒绝执行；本机进程启动失败，报出缺的文件路径或变量名 |
| `rabbitmq.env` | `RABBITMQ_PASSWORD`，64 位十六进制，RabbitMQ 账号 `patra` 的密码 | core 栈的 rabbitmq（只在首次启动时用来建账号，见「RabbitMQ」）；interaction、identity、notification 的容器和本机进程 | 同 `redis.env`；rabbitmq 的值为空时拒绝启动 |
| `gateway.env` | `PATRA_GATEWAY_IDENTITY_ASSERTION_PRIVATE_KEY`，含私钥的 EC P-256 JWK JSON，一行 | gateway 的容器和本机进程 | 同上 |
| `nacos-server.env` | Nacos 的 `NACOS_AUTH_TOKEN`、`NACOS_AUTH_IDENTITY_KEY`、`NACOS_AUTH_IDENTITY_VALUE`。**只放 mini** | core 栈的 nacos | compose 报出路径并拒绝执行 |
| `nacos-admin.env` | Nacos 管理员密码 `NACOS_ADMIN_PASSWORD`。**只放 mini**，另存密码管理器 | `nacos-init.sh`、`nacos-verify.sh` | 脚本说明缺哪个变量并退出 |
| `nacos.env` | 应用账号 `NACOS_USERNAME=patra-app`、`NACOS_PASSWORD` | 6 个后端服务的容器和本机进程；两个 Nacos 脚本 | 同 `redis.env` |
| `nacos-push.env` | 推送账号 `NACOS_PUSH_USERNAME=patra-push`、`NACOS_PUSH_PASSWORD` | 两个 Nacos 脚本；PAP-75 的推送 | 脚本说明缺哪个变量并退出 |
| `mail.env` | 真实邮件服务商（Resend）的 SMTP 凭据：`PATRA_MAIL_HOST` / `PORT` / `USERNAME` / `PASSWORD` / `AUTH` / `SSL` / `STARTTLS` / `FROM`。**只放 mini**（本机要走真实服务商时再放一份） | identity 的容器和本机进程 | 跳过：邮件发往 core 栈的 Mailpit |
| `<服务>.env` | 该服务的外部数据源密钥，例如 catalog 的 `SCOPUS_API_KEY`、`PROXY_AUTH_KEY`、`PROXY_AUTH_PWD` | 对应服务的容器和本机进程 | 跳过 |

两种进程怎么读到这些文件：

- 模板在 `secrets/*.example`。
- 容器：由 `docker-compose.apps.yaml`、`docker-compose.core.yaml` 的 `env_file` 按 `${HOME}/.patra/secrets/...` 加载。
- 本机进程：Nacos starter 把 `~/.patra/secrets/` 下的全部 `*.env` 读进来，IDEA 和 shell 都不用设这些变量。

排查时注意：`docker compose config` 会展开环境文件，`docker inspect <容器>` 会列出容器的全部环境变量，两者都会把 Redis 密码和网关私钥原样打到终端。只想校验 compose 配置，用 `docker compose config --quiet`；想知道容器里有哪些变量，只打变量名：

```bash
docker inspect -f '{{range .Config.Env}}{{println .}}{{end}}' patra-gateway | cut -d= -f1
```

### 生成

在 MacBook 的仓库根目录执行。全程只写文件，不要把密钥打印到终端或粘进任何地方。

```bash
umask 077 && mkdir -p ~/.patra/secrets
printf 'REDIS_PASSWORD=%s\n' "$(openssl rand -hex 32)" > ~/.patra/secrets/redis.env
printf 'RABBITMQ_PASSWORD=%s\n' "$(openssl rand -hex 32)" > ~/.patra/secrets/rabbitmq.env

K="$(mktemp -u ~/.patra/secrets/key.XXXXXX)"   # 任务要求私钥文件事先不存在
./gradlew -q :patra-tool:patra-tool-security:generateIdentityAssertionKey -PkeyOut="$K" \
  | tail -1 > /tmp/patra-identity-assertion-public.jwks   # 前两行是提示，最后一行是公钥 JWK Set
{ printf 'PATRA_GATEWAY_IDENTITY_ASSERTION_PRIVATE_KEY='; cat "$K"; } > ~/.patra/secrets/gateway.env
rm -f "$K"
printf 'NACOS_USERNAME=patra-app\nNACOS_PASSWORD=%s\n' "$(openssl rand -hex 24)" > ~/.patra/secrets/nacos.env
printf 'NACOS_PUSH_USERNAME=patra-push\nNACOS_PUSH_PASSWORD=%s\n' "$(openssl rand -hex 24)" > ~/.patra/secrets/nacos-push.env
```

`nacos-server.env`、`nacos-admin.env` 在 mini 上生成，命令见 `secrets/` 下对应的模板。

公钥不是机密，只有一处：Nacos 公共配置 `patra-infra/nacos/dev/patra-common.yaml` 的 `patra.security.identity-assertion.public-keys`，值用单引号包住。identity 和 gateway 都从这里读。

### 复制到 Mac mini

```bash
ssh linqibin@linqibins-mac-mini 'umask 077 && mkdir -p ~/.patra/secrets && chmod 700 ~/.patra/secrets'
scp -p ~/.patra/secrets/redis.env ~/.patra/secrets/rabbitmq.env ~/.patra/secrets/gateway.env \
  ~/.patra/secrets/nacos.env ~/.patra/secrets/nacos-push.env linqibin@linqibins-mac-mini:.patra/secrets/
```

### 改了密钥文件之后

容器只在创建时读环境文件，`docker restart` 不会重新读，要用 `--force-recreate` 重建对应容器。

apps 栈的服务要在 runner 工作区的 compose 目录里重建，不要在 CD 运行期间执行。以 gateway 为例：

```bash
cd ~/actions-runner/_work/patra/patra/patra-infra/docker
GATEWAY_IMAGE_TAG="$(cat ~/.patra/cd/last-good-gateway)" docker compose -f docker-compose.apps.yaml up -d --force-recreate gateway
```

本机进程重启一次即可。

`rabbitmq.env` 另有一步：RabbitMQ 里的账号密码不会跟着文件变，见「RabbitMQ」的「改密码」。

### 换签名密钥

1. 按上面的方法生成一对新密钥。新私钥先存成别的文件名，不要覆盖 `gateway.env`。
2. 把新公钥加进 `patra-common.yaml` 的 `keys` 数组，旧公钥保留。提交、推送：CD 把配置推到 Nacos 后重新部署全部 6 个服务，容器读到新公钥；本机进程各重启一次。

   此时新旧两把公钥都被信任，网关仍用旧私钥签。
3. 两台机器上都把 `gateway.env` 换成新私钥。
   - mini：按「改了密钥文件之后」重建 gateway。
   - 本机：网关重启一次。
4. 等至少 60 秒。断言有效期是 60 秒，等完旧私钥签出的断言就全部过期了。
5. 从 `patra-common.yaml` 删掉旧公钥，提交、推送，再同样重建 identity 和 gateway 容器、重启本机进程。

## Nacos

- 3.2.4，开着鉴权。存储在业务 PG 的 `patra_nacos` 库（官方 PG 插件，镜像自带）；`data/` 卷只剩服务发现的持久化数据和 jraft 元数据。
- namespace：配置只有 `dev` 一个 namespace，服务发现留在 `public`。密钥不进 Nacos：配置里写 `${变量}`，值在 `~/.patra/secrets/`（PAP-74 撤销了 `dev-secrets`，见 `docs/patra/specs/2026-10-13-nacos-config-starter-design.md` 第 4 节）。
- 账号：管理员 `nacos`；应用账号 `patra-app`（public 服务注册读写、`dev` 的配置只读）；推送账号 `patra-push`（只能写 `dev` 的 `DEFAULT_GROUP`）。
- 账号、角色、权限由 `scripts/nacos-init.sh` 按脚本里的清单同步，不要在控制台手改：下次执行会被改回。验收用 `scripts/nacos-verify.sh`（加 `--expect-services` 时另查 6 个服务都已注册）。
- 改应用或推送账号的密码：两台机器都改密钥文件，mini 上跑 `nacos-init.sh`，再按「改了密钥文件之后」重建 6 个应用容器、重启本机进程。
- 换 token 签名密钥：改 mini 上的 `nacos-server.env`，执行 `docker compose -f docker-compose.core.yaml up -d --force-recreate nacos`。已签出的令牌全部失效，客户端会自动重新登录。改密码或删用户**不会**作废已签出的令牌（默认 18000 秒有效），要立刻撤销只能换签名密钥。
- 镜像启动脚本开着 `set -x`，token 签名密钥和 identity 会出现在 `docker logs patra-nacos` 里（3.3 起改掉）。排查时不要把这段日志贴到任何地方。

### 建表

全新部署时，PG 的 init 脚本会自动执行 `postgres/init-scripts/03-nacos-schema.sh`。已有数据的 PG（如 mini）手动执行：

```bash
docker exec patra-postgres psql -U postgres -c 'CREATE DATABASE patra_nacos;'
docker exec patra-postgres bash /docker-entrypoint-initdb.d/03-nacos-schema.sh
```

脚本先判断库的状态：空库才建表，表齐了就跳过，只有一部分时报错、不动库。建表在一个事务里，失败会回滚到空库。官方 schema 每张表都先 DROP 再建，不要绕过 03 直接执行 `pg-schema-*.sql`。

### 升级

- 对比新旧两个 tag 的 `pg-schema.sql`，手动执行差异部分；把新文件放进 `postgres/init-scripts/nacos/`，并改 03 里的默认路径。
- 升到 3.3 时，compose 里数据库相关的环境变量要改名为 `NACOS_PLUGIN_DATASOURCE_DB_*`：3.3 镜像的配置文件改用新键，新键优先于旧的 `db.*`。

---

## RabbitMQ

v0.16 起的消息中间件：interaction、identity 把集成事件发到这里，notification 从这里收（v0.16 release spec 决策 E）。交换机、队列、死信队列都由应用启动时自己声明，这里不预建任何东西。

- **镜像**：`rabbitmq:4.4.0-management`（带管理界面）。版本以 `.github/workflows/ci.yml` 的 `RABBITMQ_IMAGE` 为准，CI 检查 core 栈的 compose 和测试容器 `RabbitMQContainerInitializer` 都和它一致，升级时三处一起改。
- **端口**：`5672` 是 AMQP，应用连这个；`15672` 是管理界面。集群端口 25672、指标端口 15692 不映射到宿主机。
- **账号**：只有 `patra` 一个（管理员），应用和管理界面共用；vhost（RabbitMQ 里互相隔离的命名空间）只用默认的 `/`。密码在 `~/.patra/secrets/rabbitmq.env`。
- **只在 tailnet 与局域网内可达**：安全邮件的一次性令牌链接会经过这里的消息（决策 F）。不要把 5672、15672 加进 Cloudflare Tunnel 或路由器的端口转发。
- **固定 hostname `patra-rabbitmq`**：数据目录按节点名 `rabbit@patra-rabbitmq` 分。hostname 一变，RabbitMQ 会当成新节点另起一个空目录，账号和没消费的消息看上去全没了（其实还在旧目录里）。
- **内存与磁盘水位**：容器内存上限 768 MiB。RabbitMQ 不读容器上限，`rabbitmq/conf.d/20-patra.conf` 里写死了可用内存 768 MiB，两处一起改。用量超过其中 0.6（约 460 MiB）或磁盘剩余不足 2 GB 时，它暂停接收消息、阻塞发送方，等降下来再放行。本机实测空闲约 210 MiB。
- **健康检查**：端口连得上，且没有内存或磁盘告警。命令以 `rabbitmq` 用户执行，自己 `docker exec` 进去跑命令行工具也要加 `gosu rabbitmq`：用 root 跑，首次启动时可能抢先建出 root 属主的 `.erlang.cookie`，节点读不了就退出。

### 管理界面

http://linqibins-mac-mini:15672 ，用 `patra` 登录。

- **Overview**：节点状态，内存、磁盘离水位还有多远。
- **Queues**：每个队列的 Ready 是等着被取走的消息数，Unacked 是消费方取走了还没确认的。某个队列的 Ready 一直涨，说明消费方没在取（服务挂了，或一直处理失败）。
- 死信队列里的消息不会自己回来。在该队列页面的 **Get messages** 里查看内容，Ack mode 选 Nack message requeue true，查看后消息仍留在队列里。

### 改密码

账号只在数据目录为空的首次启动时按 `rabbitmq.env` 创建，之后改文件不会改到 RabbitMQ 里的密码。

1. 两台机器都改 `rabbitmq.env`（生成命令见 `secrets/rabbitmq.env.example`）。
2. mini 上重建容器读入新文件，再在容器里改密码。密码从容器自己的环境变量里取，不经过 shell 历史：

   ```bash
   cd ~/Projects/patra/patra-infra/docker
   docker compose -f docker-compose.core.yaml up -d --force-recreate rabbitmq
   docker exec patra-rabbitmq sh -c 'gosu rabbitmq rabbitmqctl change_password patra "$RABBITMQ_PASSWORD"'
   ```

3. 按「改了密钥文件之后」重建 interaction、identity、notification 的容器，本机进程各重启一次。

### 排查

```bash
# 节点状态：内存与磁盘用量、水位、告警
docker exec patra-rabbitmq gosu rabbitmq rabbitmq-diagnostics status
# 只看有没有告警（有告警时发送方被阻塞）
docker exec patra-rabbitmq gosu rabbitmq rabbitmq-diagnostics check_local_alarms
# 队列：待取消息数、未确认数、消费者数
docker exec patra-rabbitmq gosu rabbitmq rabbitmqctl list_queues name messages_ready messages_unacknowledged consumers
# 连接：哪些服务连着
docker exec patra-rabbitmq gosu rabbitmq rabbitmqctl list_connections user peer_host state
# 内存花在哪
docker exec patra-rabbitmq gosu rabbitmq rabbitmq-diagnostics memory_breakdown --unit mb
# 日志
docker logs --tail 200 patra-rabbitmq
```

| 现象 | 原因与处理 |
|---|---|
| 应用连接被拒，报 `ACCESS_REFUSED` | 应用读到的密码和 RabbitMQ 里的不一样，多半是改了 `rabbitmq.env` 没做「改密码」第 2 步 |
| 容器反复重启，日志是 `RABBITMQ_PASSWORD 为空` | `rabbitmq.env` 缺值 |
| 容器退出，日志里有 `.erlang.cookie: eacces` | 有人用 root 跑了命令行工具。`docker restart patra-rabbitmq`：入口脚本启动时会把目录属主改回 `rabbitmq` |
| 声明队列时连接被关，报 `transient_nonexcl_queues` | RabbitMQ 4 默认不允许非持久的共享队列，把队列声明成持久（durable）的 |
| 发送卡住、健康检查 unhealthy | 触发了内存或磁盘告警，先看 `check_local_alarms` 和 Overview |
| 从 MacBook 连不上 | `nc -zv linqibins-mac-mini 5672`，不通再按「故障排查」查 tailscale |

## 门户的固定地址与受信转发（PAP-99）

`portal` 容器在 `patra-net` 上固定为 `192.168.97.250`（`docker-compose.apps.yaml`）：网关只信任这个地址发来的 `X-Forwarded-For`（同一个文件里 gateway 的 `PATRA_GATEWAY_TRUSTED_PROXIES`，即 `server.tomcat.remoteip.internal-proxies`），据此记录浏览器的真实 IP。两边要一起改。不能信任整个网段：tailnet 经 `tailscale-gw` 子网路由进来的连接会被 SNAT 成网段内地址，OrbStack 端口转发进来的连接都显示成 `192.168.97.1`。所以 mini 上从 tailnet 访问门户时，门户看到的浏览器地址本身就是内网地址，登录设备列表里属地显示「内网」。

identity 的定时任务除 `emailTokenCleanupJob`（04:30）外还有 `loginRecordCleanupJob`（Cron `0 40 4 * * ?`），在 xxl-job-admin 里手工新建，执行器 `patra-identity`。

interaction 的执行器 `patra-interaction`（端口 6501，不映射到宿主机）在 xxl-job-admin 的「执行器管理」里手工新建、自动注册；它的两个每日任务 `subjectReconciliationJob`（`0 30 6 * * ?`）、`deletedUserCleanupJob`（`0 0 6 * * ?`）见 `docs/patra/runbooks/v0.13-library-go-live-runbook.md`。

notification 的执行器 `patra-notification`（端口 6601，不映射到宿主机）同样手工新建、自动注册；它的任务（提醒清理、注销清理、离站邮件、每日汇总）随 v0.16 各 Issue 登记，见 `docs/patra/runbooks/v0.16-notification-go-live-runbook.md`。

## 门户的公网入口（Cloudflare Tunnel）

给少数人体验和测试用：https://patra.linqibin.dev 经 Cloudflare Tunnel 转到 mini 上门户的 `localhost:4000`。**只有门户对外**，PG、Redis、Nacos、MinIO 等基础设施端口仍然只在 tailnet 和局域网里。门口没有加 Cloudflare Access，访问控制只靠 Patra 自己的登录（2026-10-28 决定）。2026-10-28 实测：首页首次打开 5～6 秒，连接复用后每个请求约 1 秒（免费版落在洛杉矶节点）。

- **隧道**：Cloudflare 账号里的 `patra-portal`（远程管理，ingress 在 Cloudflare 后台：`patra.linqibin.dev` → `http://localhost:4000`，其余返回 404）；DNS 是 `patra` 指向 `<隧道 ID>.cfargotunnel.com` 的代理 CNAME。`linqibin.dev` 开着 Always Use HTTPS，http 访问会 301 到 https。同一账号里的 `office-mac-ssh` 隧道与 Patra 无关。
- **mini 上的进程**：brew 装的 `cloudflared`，由 LaunchAgent `~/Library/LaunchAgents/dev.patra.cloudflared-portal.plist` 常驻（`RunAtLoad` + `KeepAlive`，执行 `cloudflared tunnel --no-autoupdate run --token-file ~/.patra/secrets/cloudflared-patra-portal.token`），日志在 `~/Library/Logs/cloudflared-patra-portal.log`。cloudflared 走家宽直连 Cloudflare（QUIC），不经 Clash。LaunchAgent 要有用户登录会话才会运行。
- **令牌**：`~/.patra/secrets/cloudflared-patra-portal.token`（权限 600），只在 mini 上。泄露后到 Cloudflare 后台轮换该隧道的令牌，再覆盖这个文件并 `launchctl kickstart -k gui/$(id -u)/dev.patra.cloudflared-portal`。
- **查状态**：`launchctl print gui/$(id -u)/dev.patra.cloudflared-portal | grep state`；正常时日志里有 4 条 `Registered tunnel connection`。
- **暂停对外**：`launchctl bootout gui/$(id -u)/dev.patra.cloudflared-portal`；恢复用 `launchctl bootstrap gui/$(id -u) ~/Library/LaunchAgents/dev.patra.cloudflared-portal.plist`。

已知限制：

- 门户没配 `PATRA_PORTAL_PUBLIC_URL`，「复制链接」按请求的 `Host` 拼成 `http://patra.linqibin.dev/...`，点开后由 Always Use HTTPS 跳到 https。
- 外网访问也经 OrbStack 端口转发进入，门户看到的来源都是 `192.168.97.1`，登录设备列表里外网用户同样显示「内网」。
- 会话 Cookie 仍是 `PATRA_SESSION_COOKIE_SECURE=false`（不带 `Secure`、不带 `__Host-` 前缀），经 https 访问照常能用。
- 没有人机验证，注册邮件经 Resend 以 `linqibin.dev` 发出：被刷注册会影响域名的发信信誉。

## 应用侧配置（patra-api，本仓库范围外）

各微服务通过单一环境变量切换基础设施地址：

```bash
export PATRA_INFRA_HOST=100.103.73.27   # IDE；容器里由 .env.common 设为 linqibins-mac-mini
```

配置（Nacos 里的文件或 `application.yml`）的引用方式示例：

> 注意：以下示例使用 Spring 的 `${VAR:default}` 语法（与 docker-compose 的 `${VAR:-default}` 略有不同，Spring 没有连字符）。

```yaml
spring:
  datasource:
    url: jdbc:postgresql://${PATRA_INFRA_HOST:127.0.0.1}:15432/patra_catalog
  data:
    redis:
      host: ${PATRA_INFRA_HOST:127.0.0.1}
patra:
  object-storage:
    providers:
      minio:
        endpoint: http://${PATRA_INFRA_HOST:127.0.0.1}:19000
```

Nacos 的地址、账号和服务发现设置由 `patra-spring-boot-starter-nacos` 注入，服务的配置里不写。

默认值 `127.0.0.1` 仅用于应急 fallback（如临时在 MacBook 上本地起容器调试）。统一用 `127.0.0.1` 而非 `localhost`，避免 hosts/IPv6 解析歧义。

容器与 IDE 用同一种写法：容器里 `linqibins-mac-mini` 由 compose 的 `extra_hosts` 解析到宿主机网关，端口同样是映射端口（原理与实测见 `../docs/mac-mini-connectivity.md` §8）。服务对外通告的地址读 `PATRA_ADVERTISE_IP`，IDE 设为本机 tailscale IP，容器不设。

---

## 服务访问 URL

### 核心服务
- **PostgreSQL**: `linqibins-mac-mini:15432` (postgres/123456)
- **Redis**: `linqibins-mac-mini:16379`（要密码：用户 `default`，密码在 `~/.patra/secrets/redis.env`）
- **Nacos 控制台**: http://linqibins-mac-mini:8080（用管理员 `nacos` 登录，密码在 mini 的 `~/.patra/secrets/nacos-admin.env`）
- **RabbitMQ**: AMQP `linqibins-mac-mini:5672`；管理界面 http://linqibins-mac-mini:15672（账号 `patra`，密码在 `~/.patra/secrets/rabbitmq.env`）
- **Mailpit**（开发环境的邮件收集器）: SMTP `linqibins-mac-mini:1025`（无鉴权、无 TLS），Web UI http://linqibins-mac-mini:8025。identity 没放 `mail.env` 时的邮件都在这里

### 存储服务
- **MinIO API**: `linqibins-mac-mini:19000` (minioadmin/minioadmin123)
- **MinIO 控制台**: http://linqibins-mac-mini:19001

### 搜索服务
- **Elasticsearch**: http://linqibins-mac-mini:19200

### 可观测性
- **Grafana**: http://linqibins-mac-mini:3000
- **Prometheus**: http://linqibins-mac-mini:9090
- **Loki**: http://linqibins-mac-mini:3100
- **Tempo**: http://linqibins-mac-mini:3200
- **AlertManager**: http://linqibins-mac-mini:9093

### 任务服务
- **mysql-ops**: 不暴露宿主机端口；容器内 `mysql-ops:3306`，仅供 xxl-job-admin 使用，凭据见 `.env` 的 `MYSQL_OPS_*`
- **XXL-Job Admin**: http://linqibins-mac-mini:7070/xxl-job-admin (admin/123456)

---

## 选择性启动

不需要全栈时按需启动子栈（`compose-all.sh up` 接子栈名，可传多个；自动确保 patra-net 存在）：

```bash
# 只起核心（最快）
bash patra-infra/scripts/compose-all.sh up core

# 核心 + 存储（开发文件上传）
bash patra-infra/scripts/compose-all.sh up core storage

# 核心 + 任务（开发定时任务）
bash patra-infra/scripts/compose-all.sh up core jobs

# 核心 + 监控
bash patra-infra/scripts/compose-all.sh up core observability
```

---

## 资源需求（参考）

| 配置 | CPU | 内存 |
|---|---|---|
| 仅 core | 2 核 | ~1 GB |
| core + storage | 2 核 | ~1.5 GB |
| core + storage + jobs | 4 核 | ~3.5 GB |
| 全栈 | 6+ 核 | ~5.5 GB |

---

## 常用命令

```bash
# 健康状态（所有 patra-* 容器）
bash patra-infra/scripts/compose-all.sh ps

# 单服务日志（容器名稳定，与 project 无关）
docker logs -f patra-postgres

# 重启单服务
docker restart patra-redis

# 停止全栈（逆序；patra-net 是 external，保留不删）
bash patra-infra/scripts/compose-all.sh down

# 清数据：容器数据是 bind mount，停栈后删宿主目录即可（⚠️ 会删数据）
# rm -rf ~/.patra/docker/<service>/data
```

---

## 故障排查

### 服务无法启动

```bash
# 查看具体服务日志（容器名形如 patra-postgres / patra-nacos）
docker logs <container-name>

# 在 Mac mini 上检查端口占用
ssh linqibin@linqibins-mac-mini 'lsof -i :15432'
```

### 从 MacBook 连不上服务

```bash
# 1. 验证 MagicDNS 解析
ping -c 1 linqibins-mac-mini

# 2. 验证端口可达
nc -zv linqibins-mac-mini 15432
nc -zv linqibins-mac-mini 16379

# 3. 验证 tailscale 状态
tailscale status | grep linqibins-mac-mini
```

### MinIO 登录后跳转 404

`MINIO_BROWSER_REDIRECT_URL` 必须指向用户浏览器能访问的地址。默认 `linqibins-mac-mini:19001`，如改用 IP 部署可：

```bash
PATRA_INFRA_HOST=100.103.73.27 docker compose -f patra-infra/docker/docker-compose.storage.yaml up -d minio
```

---

## 栈之间的依赖关系

```
┌─────────────────────────────────────┐
│ 核心服务                             │  (无依赖)
│ - postgres, redis, nacos, rabbitmq  │
└─────────────────────────────────────┘
         ▲                    ▲             ▲
         │                    │             │
┌────────┴─────────────┐  ┌──┴────────┐  ┌─┴───────────────────┐
│ 任务服务              │  │ 可观测性   │  │ 存储 + 搜索         │
│ - xxl-job → mysql-ops│  │ - otel    │  │ - minio              │
│                      │  │ - grafana │  │ - elasticsearch      │
└──────────────────────┘  └───────────┘  └─────────────────────┘
```

- **xxl-job-admin** 依赖 `mysql-ops`（独立的 MySQL 8.0 容器，仅服务于不支持 PG 的运维组件；业务库走 PG）
- **可观测性栈** 自包含
