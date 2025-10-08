# 运维文档索引

> Papertrace 运维指南、部署文档、监控配置

---

## 📄 文档列表

### 本地开发环境
- **Docker Compose 配置**：[docker/docker-compose.yml](../../docker/docker-compose.yml)
  - 包含 MySQL、Redis、Elasticsearch、Nacos、SkyWalking、XXL-Job
  - 启动命令：`docker-compose -f docker/docker-compose.yml up -d`
  - 停止命令：`docker-compose -f docker/docker-compose.yml down`

- **环境依赖**：
  - Java 21
  - Maven 3.8+
  - Docker 20.10+
  - Docker Compose 2.0+

---

## 🔗 相关文档

### 配置管理
- **Nacos 配置中心**：
  - 控制台：http://localhost:8848/nacos
  - 用户名/密码：`nacos/nacos`
  - 配置格式：YAML
  - 配置层级：
    - 系统级：`application.yaml`（所有服务共享）
    - 服务级：`{service-name}.yaml`（单个服务）
    - 环境级：通过 Namespace 区分（dev/test/prod）

- **Spring Profile 使用**：
  - `local`：本地开发环境（使用 Docker Compose）
  - `dev`：开发环境（远程 Nacos）
  - `test`：测试环境
  - `prod`：生产环境

### 监控与追踪
- **SkyWalking APM**：
  - UI 控制台：http://localhost:8080
  - Trace 查询：通过 TraceId 关联日志
  - 性能分析：慢请求、错误率、拓扑图

- **Micrometer + Prometheus**（规划中）：
  - 指标导出端点：`/actuator/prometheus`
  - Grafana 仪表板：系统级、服务级、业务级指标

- **日志聚合**（规划中）：
  - ELK Stack（Elasticsearch + Logstash + Kibana）
  - 日志格式：JSON（便于解析和查询）
  - 日志级别：ERROR（报警）、WARN（关注）、INFO（记录）、DEBUG（调试）

### 调度任务
- **XXL-Job 调度中心**：
  - 控制台：http://localhost:8082/xxl-job-admin
  - 用户名/密码：`admin/123456`
  - 执行器配置：每个服务注册为独立执行器
  - 任务类型：
    - BEAN 模式：直接调用 Spring Bean 方法
    - GLUE 模式：动态脚本（Java/Groovy/Shell）

---

## 📝 贡献指南

### 添加新环境
1. 在 Nacos 创建新 Namespace（如：`uat`）
2. 复制配置文件到新 Namespace
3. 修改环境相关配置（数据库连接、Redis 地址等）
4. 更新部署脚本

### 添加新监控指标
```java
@Component
public class CustomMetrics {
    
    private final MeterRegistry meterRegistry;
    
    @PostConstruct
    public void registerMetrics() {
        // 计数器
        Counter.builder("custom.operation.count")
               .tag("type", "import")
               .register(meterRegistry);
        
        // 计时器
        Timer.builder("custom.operation.duration")
             .tag("type", "export")
             .publishPercentiles(0.5, 0.95, 0.99)
             .register(meterRegistry);
        
        // 仪表
        Gauge.builder("custom.queue.size", queue::size)
             .register(meterRegistry);
    }
}
```

---

## 🗂️ 运维规范

### 部署流程
1. **构建**：`mvn clean package -DskipTests`
2. **镜像打包**：`docker build -t papertrace/{service}:${version} .`
3. **镜像推送**：`docker push papertrace/{service}:${version}`
4. **部署**：`kubectl apply -f k8s/{service}-deployment.yaml`
5. **验证**：`curl http://{service}/actuator/health`

### 配置变更流程
1. **在测试环境验证配置**
2. **提交配置变更 PR**
3. **Code Review**
4. **在 Nacos 控制台发布配置**
5. **观察服务日志和指标**
6. **如有问题，立即回滚**

### 回滚策略
```bash
# 1. Nacos 配置回滚
# 在 Nacos 控制台选择历史版本并回滚

# 2. 服务版本回滚（Kubernetes）
kubectl rollout undo deployment/{service-name}

# 3. 验证回滚结果
kubectl rollout status deployment/{service-name}
curl http://{service}/actuator/health
```

---

## 🔧 常用命令

### Docker Compose
```bash
# 启动所有服务
docker-compose -f docker/docker-compose.yml up -d

# 查看服务状态
docker-compose -f docker/docker-compose.yml ps

# 查看服务日志
docker-compose -f docker/docker-compose.yml logs -f {service}

# 停止所有服务
docker-compose -f docker/docker-compose.yml down

# 清理数据卷（慎用）
docker-compose -f docker/docker-compose.yml down -v
```

### Maven
```bash
# 编译所有模块
mvn clean compile

# 打包（跳过测试）
mvn clean package -DskipTests

# 运行单个服务
mvn -pl {module-path} spring-boot:run

# 运行测试
mvn test

# 查看依赖树
mvn dependency:tree
```

### Kubernetes
```bash
# 查看 Pod 状态
kubectl get pods -l app={service-name}

# 查看 Pod 日志
kubectl logs -f {pod-name}

# 进入 Pod
kubectl exec -it {pod-name} -- /bin/bash

# 查看服务详情
kubectl describe service {service-name}

# 扩缩容
kubectl scale deployment/{service-name} --replicas=3
```

---

## 📊 监控指标

### 系统级指标
- **CPU 使用率**：`system_cpu_usage`
- **内存使用率**：`jvm_memory_used / jvm_memory_max`
- **GC 时间**：`jvm_gc_pause_seconds`
- **线程数**：`jvm_threads_live`

### 服务级指标
- **QPS**：`http_server_requests_total / time_window`
- **响应时间**：`http_server_requests_seconds{quantile="0.95"}`
- **错误率**：`http_server_requests_total{status="5xx"} / http_server_requests_total`
- **活跃连接数**：`tomcat_sessions_active_current`

### 业务级指标
- **采集任务数**：`ingest_task_total{status="COMPLETED"}`
- **采集成功率**：`ingest_task_success_rate`
- **数据量**：`ingest_batch_size_total`
- **外部调用成功率**：`egress_call_success_rate`

---

## 🚨 故障排查

### 常见问题

#### 问题1：服务启动失败
```bash
# 排查步骤
1. 检查日志：docker-compose logs -f {service}
2. 检查依赖服务：docker-compose ps
3. 检查 Nacos 连接：curl http://localhost:8848/nacos
4. 检查数据库连接：mysql -h127.0.0.1 -uroot -p
```

#### 问题2：Nacos 配置不生效
```bash
# 排查步骤
1. 确认 Namespace 和 Group 正确
2. 确认配置文件格式正确（YAML）
3. 重启服务：docker-compose restart {service}
4. 查看配置加载日志：grep "Nacos Config" logs/{service}.log
```

#### 问题3：调度任务不执行
```bash
# 排查步骤
1. 检查 XXL-Job 控制台：http://localhost:8082/xxl-job-admin
2. 检查执行器是否在线
3. 检查任务配置（Cron 表达式、执行器、JobHandler）
4. 手动触发测试
5. 查看执行日志
```

---

**更新记录**

| 版本 | 日期 | 变更说明 | 作者 |
|-----|------|---------|------|
| 1.0 | 2025-10-08 | 初始版本：运维文档索引 | docs-engineer |

---

**许可证**

Copyright © 2025 Papertrace
