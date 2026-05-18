# Patra 基础设施 - Docker Compose 配置

Patra 全部基础设施容器部署在 **Mac mini**（hostname: `linqibins-mac-mini`），
通过 tailscale MagicDNS 对外提供服务。MacBook 上仅运行 patra-api 应用进程，
通过环境变量 `PATRA_INFRA_HOST=linqibins-mac-mini` 指向容器。

---

## 结构

```
docker/
├── docker-compose.dev.yaml          # 主入口（include 全部栈）
├── docker-compose.core.yaml         # postgres + redis + consul
├── docker-compose.storage.yaml      # minio + minio-init
├── docker-compose.search.yaml       # elasticsearch
├── docker-compose.observability.yaml # otel/prom/loki/tempo/grafana
├── docker-compose.jobs.yaml         # rocketmq + xxl-job-admin
├── .env                              # BROKER_IP1 等环境变量
└── README.md                         # 本文件
```

---

## 部署目标：Mac mini

| 项 | 值 |
|---|---|
| 主机 | `linqibins-mac-mini`（tailscale MagicDNS 短名） |
| 容器引擎 | OrbStack |
| 仓库副本位置 | `~/Projects/patra-api/` |
| 数据卷根目录 | `~/.patra/docker/` |

**MacBook ↔ Mac mini 链路**：

- 在家（同一 LAN）→ DNS 解析为 `192.168.1.11` → LAN 直连，延迟 < 5ms
- 离家 → DNS 解析为 `100.73.7.112` → tailscale 加密隧道

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

# 3. (Mac mini) clone 仓库到固定位置
git clone <git-remote-url> ~/Projects/patra-api
cd ~/Projects/patra-api
git checkout <branch-name>

# 4. (Mac mini) 一次性初始化数据目录与配置文件
bash infra/scripts/init-volumes.sh

# 5. (Mac mini) 启动全栈
docker compose -f infra/docker/docker-compose.dev.yaml up -d

# 6. (Mac mini) 验证全部 healthy
docker compose -f infra/docker/docker-compose.dev.yaml ps
```

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
  cd ~/Projects/patra-api &&
  git pull &&
  docker compose -f infra/docker/docker-compose.dev.yaml up -d --remove-orphans
'
```

如果只改了某个栈，可针对性重启：

```bash
ssh linqibin@linqibins-mac-mini '
  cd ~/Projects/patra-api &&
  docker compose -f infra/docker/docker-compose.jobs.yaml restart rocketmq-broker
'
```

---

## 应用侧配置（patra-api，本仓库范围外）

各微服务通过单一环境变量切换基础设施地址：

```bash
export PATRA_INFRA_HOST=linqibins-mac-mini
```

`application.yml` 引用方式示例：

> 注意：以下示例使用 Spring 的 `${VAR:default}` 语法（与 docker-compose 的 `${VAR:-default}` 略有不同，Spring 没有连字符）。

```yaml
spring:
  datasource:
    url: jdbc:postgresql://${PATRA_INFRA_HOST:localhost}:15432/patra_ingest
  data:
    redis:
      host: ${PATRA_INFRA_HOST:localhost}
  cloud:
    consul:
      host: ${PATRA_INFRA_HOST:localhost}
patra:
  object-storage:
    providers:
      minio:
        endpoint: http://${PATRA_INFRA_HOST:localhost}:19000
rocketmq:
  name-server: ${PATRA_INFRA_HOST:localhost}:9876
```

默认值 `localhost` 仅用于应急 fallback（如临时在 MacBook 上本地起容器调试）。

---

## 服务访问 URL

### 核心服务
- **PostgreSQL**: `linqibins-mac-mini:15432` (postgres/123456)
- **Redis**: `linqibins-mac-mini:16379`
- **Consul UI**: http://linqibins-mac-mini:8500

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
- **mysql-ops**: 不暴露宿主机端口；容器内 `mysql-ops:3306`，仅供 xxl-job-admin 使用，凭据见 `.env.dev` 的 `MYSQL_OPS_*`
- **XXL-Job Admin**: http://linqibins-mac-mini:7070/xxl-job-admin (admin/123456)
- **RocketMQ NameServer**: `linqibins-mac-mini:9876`
- **RocketMQ Dashboard**: http://linqibins-mac-mini:4002

---

## 选择性启动

不需要全栈时按需启动子栈：

```bash
# 只起核心（最快）
docker compose -f infra/docker/docker-compose.core.yaml up -d

# 核心 + 存储（开发文件上传）
docker compose -f infra/docker/docker-compose.core.yaml \
               -f infra/docker/docker-compose.storage.yaml up -d

# 核心 + 任务（开发消息/定时任务）
docker compose -f infra/docker/docker-compose.core.yaml \
               -f infra/docker/docker-compose.jobs.yaml up -d

# 核心 + 监控
docker compose -f infra/docker/docker-compose.core.yaml \
               -f infra/docker/docker-compose.observability.yaml up -d
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
# 健康状态
docker compose -f infra/docker/docker-compose.dev.yaml ps

# 单服务日志
docker compose -f infra/docker/docker-compose.dev.yaml logs -f postgres

# 重启单服务
docker compose -f infra/docker/docker-compose.dev.yaml restart rocketmq-broker

# 停止全栈
docker compose -f infra/docker/docker-compose.dev.yaml down

# 停止 + 清理 volume（⚠️ 会删数据）
docker compose -f infra/docker/docker-compose.dev.yaml down -v
```

---

## 故障排查

### 服务无法启动

```bash
# 查看具体服务日志
docker compose -f infra/docker/docker-compose.dev.yaml logs <service-name>

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

### RocketMQ 客户端连不上 broker

最常见原因：`BROKER_IP1` 注册的字符串客户端解析失败。

```bash
# 查看 broker 启动日志，确认 brokerIP1 写入正确
ssh linqibin@linqibins-mac-mini 'docker logs patra-rocketmq-broker 2>&1 | grep "Final broker.conf" -A 10'

# 回退方案：改为 FQDN（在 docker/.env 中）
# BROKER_IP1=linqibins-mac-mini.taild06182.ts.net
```

### MinIO 登录后跳转 404

`MINIO_BROWSER_REDIRECT_URL` 必须指向用户浏览器能访问的地址。默认 `linqibins-mac-mini:19001`，如改用 IP 部署可：

```bash
PATRA_INFRA_HOST=100.73.7.112 docker compose -f infra/docker/docker-compose.storage.yaml up -d minio
```

---

## 栈之间的依赖关系

```
┌─────────────────────────────────────┐
│ 核心服务                             │  (无依赖)
│ - postgres, redis, consul           │
└─────────────────────────────────────┘
         ▲                    ▲             ▲
         │                    │             │
┌────────┴─────────────┐  ┌──┴────────┐  ┌─┴───────────────────┐
│ 任务服务              │  │ 可观测性   │  │ 存储 + 搜索         │
│ - xxl-job → mysql-ops│  │ - otel    │  │ - minio              │
│ - rocketmq           │  │ - grafana │  │ - elasticsearch      │
└──────────────────────┘  └───────────┘  └─────────────────────┘
```

- **xxl-job-admin** 依赖 `mysql-ops`（独立的 MySQL 8.0 容器，仅服务于不支持 PG 的运维组件；业务库走 PG）
- **可观测性栈** 自包含
- **rocketmq** 自包含；broker 注册地址由 `.env` BROKER_IP1 控制
