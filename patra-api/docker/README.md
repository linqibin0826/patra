# Docker Compose 配置

Papertrace 开发环境的模块化 Docker Compose 设置,按服务生命周期和优先级组织。

## 结构

```
docker/
├── docker-compose.dev.yaml          # 主入口点 (所有服务)
├── docker-compose.core.yaml         # 必需的运行时服务
├── docker-compose.storage.yaml      # 对象存储服务 (MinIO)
├── docker-compose.observability.yaml # APM 和监控栈
├── docker-compose.jobs.yaml         # 任务调度和消息队列
└── README.md                        # 本文件
```

---

## 卷存储位置

所有服务数据存储在您主目录的 `~/.papertrace/docker/` 目录中:

```
~/.papertrace/docker/
├── mysql/
│   ├── data/           # MySQL 数据库文件
│   ├── conf.d/         # MySQL 配置文件
│   └── init/           # 数据库初始化脚本
├── redis/
│   ├── data/           # Redis 持久化数据
│   └── redis.conf      # Redis 配置
├── nacos/
│   ├── data/           # Nacos 配置数据
│   └── logs/           # Nacos 日志
├── minio/
│   └── data/           # MinIO 对象存储数据
├── es/
│   └── data/           # Elasticsearch 索引和数据
├── xxl-job-admin/
│   └── logs/           # XXL-Job 日志
└── rocketmq/
    ├── namesrv/
    │   ├── logs/       # NameServer 日志
    │   └── store/      # NameServer 数据
    └── broker/
        ├── logs/       # Broker 日志
        ├── store/      # 消息存储
        └── conf/       # Broker 配置
```

**注意**: 首次启动前需要创建所需的目录和配置文件。参见下面的"首次设置"部分。

---

## 服务组织

### 核心服务 (`docker-compose.core.yaml`)
应用运行时所需的基础设施:

- **MySQL** (端口 13306): 主数据库
- **Redis** (端口 16379): 缓存和会话存储
- **Nacos** (端口 4000, 8848, 9848, 9849): 服务发现和配置中心

**何时使用**: 始终首先启动这些服务。应用程序没有它们无法运行。

### 存储服务 (`docker-compose.storage.yaml`)
文件上传和文档管理的对象存储基础设施:

- **MinIO** (端口 19000, 19001): S3 兼容的对象存储,带 Web 控制台

**何时使用**: 使用文件上传功能时需要。包含自动创建存储桶 (`dev-ingest`),具有私有访问策略。

### 可观测性服务 (`docker-compose.observability.yaml`)
用于分布式追踪的可选监控和 APM 栈:

- **Elasticsearch** (端口 9200): 追踪数据的存储后端
- **SkyWalking OAP** (端口 11800, 12800): APM 服务器
- **SkyWalking UI** (端口 8088): Web 仪表板

**何时使用**: 在调试分布式追踪、性能问题或集成测试时启用。可以禁用以节省资源 (~2GB RAM)。

### 任务服务 (`docker-compose.jobs.yaml`)
用于批处理和事件驱动功能的异步工作负载服务:

- **XXL-Job Admin** (端口 7070): 分布式任务调度平台
- **RocketMQ NameServer** (端口 9876): 消息队列注册中心
- **RocketMQ Broker** (端口 10909-10912, 7071, 8081): 带代理的消息代理
- **RocketMQ Dashboard** (端口 4002): Web 控制台

**何时使用**: 处理定时任务、批处理作业或事件驱动功能时启动。同步 API 开发不需要。

---

## 首次设置

首次启动服务前,创建所需的目录结构和配置文件:

```bash
# 创建目录结构
mkdir -p ~/.papertrace/docker/{mysql/{data,conf.d,init},redis/data,nacos/{data,logs},minio/data,es/data,xxl-job-admin/logs,rocketmq/{namesrv/{logs,store},broker/{logs,store,conf}}}

# 创建 Redis 配置 (最小示例)
cat > ~/.papertrace/docker/redis/redis.conf << 'EOF'
bind 0.0.0.0
protected-mode no
port 6379
appendonly yes
appendfilename "appendonly.aof"
dir /data
EOF

# 创建 RocketMQ broker 配置 (最小示例)
cat > ~/.papertrace/docker/rocketmq/broker/conf/broker.conf << 'EOF'
brokerClusterName = PapertraceCluster
brokerName = broker-a
brokerId = 0
deleteWhen = 04
fileReservedTime = 48
brokerRole = ASYNC_MASTER
flushDiskType = ASYNC_FLUSH
EOF

# 设置适当的权限 (对 Elasticsearch 很重要)
chmod -R 777 ~/.papertrace/docker/es/data
```

**注意**: 如果有自定义配置,您可能需要将现有配置文件从旧设置 (`docker/mysql/conf.d/`, `docker/mysql/init/` 等) 复制到新位置。

---

## 使用模式

### 快速开始 (最小环境)

仅启动核心服务以实现最快启动和最低资源使用:

```bash
cd docker/compose
docker compose -f docker-compose.core.yaml up -d
```

**用例**: API 开发、前端开发、快速测试

---

### 文件上传开发 (核心 + 存储)

添加存储服务以支持文件上传功能:

```bash
docker compose -f docker-compose.core.yaml \
               -f docker-compose.storage.yaml up -d
```

**用例**: 处理文件上传、文档管理、图像处理功能

---

### 标准开发 (核心 + 存储 + 任务)

同时添加存储和任务服务:

```bash
docker compose -f docker-compose.core.yaml \
               -f docker-compose.storage.yaml \
               -f docker-compose.jobs.yaml up -d
```

**用例**: 处理批量导入作业、定时任务、消息消费者及文件上传

---

### 完整可观测性 (核心 + 监控)

启用 APM 进行分布式追踪:

```bash
docker compose -f docker-compose.core.yaml \
               -f docker-compose.observability.yaml up -d
```

**用例**: 调试微服务交互、性能分析、延迟追踪

---

### 完整环境 (所有服务)

使用主文件启动所有服务:

```bash
docker compose -f docker-compose.dev.yaml up -d
```

**用例**: 集成测试、全栈开发、类生产环境

---

## 常用命令

### 启动服务
```bash
# 所有服务
docker compose -f docker-compose.dev.yaml up -d

# 特定栈
docker compose -f docker-compose.core.yaml up -d

# 多个栈
docker compose -f docker-compose.core.yaml \
               -f docker-compose.jobs.yaml up -d
```

### 检查状态
```bash
docker compose -f docker-compose.dev.yaml ps
docker compose -f docker-compose.core.yaml ps
```

### 查看日志
```bash
# 所有服务
docker compose -f docker-compose.dev.yaml logs -f

# 特定服务
docker compose -f docker-compose.dev.yaml logs -f mysql

# 特定栈
docker compose -f docker-compose.core.yaml logs -f
```

### 停止服务
```bash
# 所有服务
docker compose -f docker-compose.dev.yaml down

# 特定栈
docker compose -f docker-compose.core.yaml down

# 带卷清理
docker compose -f docker-compose.dev.yaml down -v
```

### 重启单个服务
```bash
docker compose -f docker-compose.dev.yaml restart mysql
docker compose -f docker-compose.observability.yaml restart skywalking-oap
```

---

## 服务访问 URL

### 核心服务
- **MySQL**: `localhost:13306` (root/123456)
- **Redis**: `localhost:16379`
- **Nacos 控制台**: http://localhost:8848/nacos (patra/patra)

### 存储服务
- **MinIO API**: `localhost:19000` (minioadmin/minioadmin123)
- **MinIO 控制台**: http://localhost:19001 (minioadmin/minioadmin123)

### 可观测性服务
- **Elasticsearch**: http://localhost:9200
- **SkyWalking UI**: http://localhost:8088

### 任务服务
- **XXL-Job Admin**: http://localhost:7070/xxl-job-admin (admin/123456)
- **RocketMQ Dashboard**: http://localhost:4002

---

## 资源需求

### 最小环境 (仅核心)
- **CPU**: 2 核
- **内存**: ~1GB
- **服务数**: 3

### 标准环境 (核心 + 存储)
- **CPU**: 2 核
- **内存**: ~1.5GB
- **服务数**: 4

### 扩展环境 (核心 + 存储 + 任务)
- **CPU**: 4 核
- **内存**: ~3.5GB
- **服务数**: 8

### 完整环境 (所有服务)
- **CPU**: 6+ 核
- **内存**: ~5.5GB
- **服务数**: 11

---

## 健康检查

所有服务都包含健康检查。等待所有服务健康:

```bash
# 检查健康状态
docker compose -f docker-compose.dev.yaml ps

# 等待服务健康 (核心服务示例)
until docker compose -f docker-compose.core.yaml ps | grep -q '(healthy)'; do
  echo "等待服务健康..."
  sleep 5
done
```

---

## 故障排除

### 服务无法启动

1. 检查日志:
   ```bash
   docker compose -f docker-compose.dev.yaml logs <service-name>
   ```

2. 验证端口可用性:
   ```bash
   lsof -i :13306  # MySQL 示例
   ```

3. 检查服务健康:
   ```bash
   docker compose -f docker-compose.dev.yaml ps
   ```

### 内存不足

通过仅启动所需服务来减少资源使用:
```bash
# 禁用可观测性栈
docker compose -f docker-compose.observability.yaml down

# 或仅启动核心服务
docker compose -f docker-compose.core.yaml up -d
```

### 权限问题 (macOS/Linux)

修复卷权限:
```bash
sudo chown -R $(id -u):$(id -g) ~/.papertrace/docker/
# 或针对特定服务:
sudo chown -R $(id -u):$(id -g) ~/.papertrace/docker/mysql
sudo chown -R $(id -u):$(id -g) ~/.papertrace/docker/es
```

### 重置所有

删除所有数据并重启:
```bash
# 停止所有服务
docker compose -f docker-compose.dev.yaml down -v

# 删除所有数据 (警告: 这会删除所有持久化数据!)
rm -rf ~/.papertrace/docker/mysql/data
rm -rf ~/.papertrace/docker/redis/data
rm -rf ~/.papertrace/docker/es/data
rm -rf ~/.papertrace/docker/nacos/data
rm -rf ~/.papertrace/docker/rocketmq/*/store

# 或一次性删除所有
rm -rf ~/.papertrace/docker

# 重新创建目录结构和配置 (参见首次设置部分)
# 然后重启服务
docker compose -f docker-compose.dev.yaml up -d
```

---

## 从单体设置迁移

原始的 `docker-compose.dev.yaml` 已拆分为三个模块化文件,卷挂载现在使用 `~/.papertrace/docker/` 而不是相对路径。

### 变更内容
- ✅ 服务定义保持不变
- ✅ 所有端口和配置保持不变
- ✅ 网络配置不变 (`patra-net`)
- ✅ 环境变量和健康检查完整
- ⚠️ **卷路径已更新**: 从 `../service/` 到 `~/.papertrace/docker/service/`

### 迁移现有数据

如果您在旧位置 (`docker/mysql/`, `docker/redis/` 等) 有现有数据,将其迁移到新位置:

```bash
# 首先停止所有服务
docker compose -f docker-compose.dev.yaml down

# 创建新目录结构
mkdir -p ~/.papertrace/docker

# 复制现有数据 (调整路径到您的项目根目录)
cd /path/to/Papertrace-api
cp -r docker/mysql ~/.papertrace/docker/
cp -r docker/redis ~/.papertrace/docker/
cp -r docker/nacos ~/.papertrace/docker/
cp -r docker/minio ~/.papertrace/docker/  # 如果有现有 MinIO 数据
cp -r docker/es ~/.papertrace/docker/
cp -r docker/xxl-job-admin ~/.papertrace/docker/
cp -r docker/rocketmq ~/.papertrace/docker/

# 验证迁移
ls -la ~/.papertrace/docker/

# 使用新配置启动服务
cd docker/compose
docker compose -f docker-compose.dev.yaml up -d
```

**注意**: 成功迁移后,您可以选择删除旧的数据目录以释放空间:
```bash
# 可选: 验证新设置工作正常后删除旧数据
rm -rf /path/to/Papertrace-api/docker/{mysql,redis,nacos,minio,es,xxl-job-admin,rocketmq}
```

### 向后兼容性
主文件仍像以前一样工作:
```bash
docker compose -f docker-compose.dev.yaml up -d
```

### 优势
- **更快的启动**: 仅启动所需服务
- **资源效率**: 通过禁用未使用的栈节省 RAM
- **更好的组织**: 按生命周期明确分离
- **灵活的工作流**: 根据需要混合搭配栈

---

## 最佳实践

1. **首先启动核心服务**: 始终从 `docker-compose.core.yaml` 开始
2. **使用选择性启动**: 仅运行您正在处理的服务
3. **监控资源**: 使用 `docker stats` 跟踪内存使用
4. **定期清理**: 运行 `docker compose down -v` 删除未使用的卷
5. **检查健康状态**: 在启动应用程序服务前等待健康检查

---

## 栈之间的依赖关系

```
┌─────────────────────────────────────┐
│ 核心服务                             │  (无依赖)
│ - MySQL, Redis, Nacos               │
└─────────────────────────────────────┘
         ▲                    ▲             ▲
         │                    │             │
         │                    │             │
┌────────┴─────────┐  ┌──────┴────────┐  ┌┴────────────────────┐
│ 任务服务          │  │ 可观测性       │  │ 存储服务            │
│ - XXL-Job (MySQL)│  │ - ES          │  │ - MinIO             │
│ - RocketMQ       │  │ - SkyWalking  │  │ (无依赖)            │
└──────────────────┘  └───────────────┘  └─────────────────────┘
```

- **任务栈** 依赖 MySQL (用于 XXL-Job)
- **可观测性栈** 有内部依赖 (SkyWalking → Elasticsearch)
- **存储栈** 是自包含的 (无外部依赖)
- **RocketMQ** 服务是自包含的 (无外部依赖)

---

## 环境变量

通过 `.env` 文件或 shell 环境配置:

```bash
# docker/ 目录中的 .env 文件示例
MYSQL_ROOT_PASSWORD=your_secure_password
MINIO_ROOT_USER=your_minio_user
MINIO_ROOT_PASSWORD=your_minio_password
```

当前可配置变量:
- `MYSQL_ROOT_PASSWORD` (默认: 123456)
- `MINIO_ROOT_USER` (默认: minioadmin)
- `MINIO_ROOT_PASSWORD` (默认: minioadmin123)

---

## MinIO 使用指南

### 访问 MinIO 控制台

启动存储栈后,在 http://localhost:19001 访问 MinIO Web 控制台:

1. 使用凭证登录: `minioadmin` / `minioadmin123` (或来自 `.env` 的自定义凭证)
2. 以下存储桶会自动创建:
   - `dev-ingest` - patra-ingest 服务的开发环境存储

### 从应用程序连接

在 Spring Boot 应用程序配置 (Nacos) 中使用这些设置:

```yaml
patra:
  object-storage:
    active-provider: minio
    max-file-size: 104857600  # 100MB
    providers:
      minio:
        endpoint: http://localhost:19000
        access-key: minioadmin
        secret-key: minioadmin123

patra:
  ingest:
    storage:
      bucket: dev-ingest  # patra-ingest 的默认存储桶
```

### 使用 MinIO 客户端 (mc)

`minio-init` 容器包含 `mc` 命令行工具。使用它:

```bash
# 访问 minio 容器
docker exec -it patra-minio sh

# 在容器内,mc 已经配置好
mc ls /data  # 列出所有存储桶
mc ls /data/dev-ingest  # 列出存储桶中的文件
```

或从主机使用 `mc`:

```bash
# 安装 mc (macOS)
brew install minio/stable/mc

# 配置连接
mc alias set papertrace http://localhost:19000 minioadmin minioadmin123

# 列出存储桶
mc ls papertrace

# 上传文件
mc cp /path/to/file.pdf papertrace/dev-ingest/

# 下载文件
mc cp papertrace/dev-ingest/file.pdf ./
```

### 创建额外的存储桶

要创建额外的存储桶:

```bash
# 使用 mc 命令
docker exec -it patra-minio sh -c \
  "mc mb /data/my-new-bucket && mc anonymous set none /data/my-new-bucket"

# 或通过 MinIO 控制台
# 导航到 http://localhost:19001 → Buckets → Create Bucket
```

### 从命令行测试上传

```bash
# 测试文件上传
curl -X PUT \
  -H "Host: dev-ingest.localhost" \
  --user minioadmin:minioadmin123 \
  --upload-file /path/to/test.txt \
  http://localhost:19000/dev-ingest/test.txt

# 验证上传
mc ls papertrace/dev-ingest/test.txt
```

---

## 下一步

1. 从核心服务开始: `docker compose -f docker-compose.core.yaml up -d`
2. 添加存储栈以支持文件上传: `docker compose -f docker-compose.storage.yaml up -d`
3. 根据开发任务需要添加其他栈
4. 运行应用程序服务前验证健康状态
5. 参考服务 URL 部分访问 Web 控制台

如有问题或疑问,请参阅主项目文档。
