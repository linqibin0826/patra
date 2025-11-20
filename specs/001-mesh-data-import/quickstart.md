# MeSH 数据导入快速开始指南

## 前提条件

1. **环境要求**
   - Java 25+
   - Maven 3.8+
   - MySQL 8.0+
   - Nacos 2.x（配置中心）
   - XXL-Job 2.x（任务调度中心）

2. **资源要求**
   - 内存：最小 4GB（建议 8GB）
   - 磁盘空间：最小 10GB（用于下载和处理 XML 文件）
   - 网络：能够访问 NLM 官网（https://nlm.nih.gov）

## 快速启动步骤

### Step 1: 数据库初始化

执行数据库迁移脚本创建必要的表：

```bash
# 进入项目目录
cd patra-catalog

# 执行 Flyway 迁移
mvn flyway:migrate
```

### Step 2: 配置 Nacos

在 Nacos 配置中心添加以下配置：

```yaml
# 配置 ID: patra-catalog-dev.yml
mesh:
  import:
    # NLM 数据源配置
    source:
      url: https://nlm.nih.gov/mesh/MESH_FILES/xmlmesh/desc2025.xml
      connect-timeout: 30000
      read-timeout: 60000

    # 批次处理配置
    batch:
      descriptor-size: 1000
      qualifier-size: 100
      tree-number-size: 2000
      entry-term-size: 2000
      concept-size: 1500

    # 重试策略
    retry:
      max-attempts: 3
      delay-seconds: 10

    # 性能调优
    performance:
      thread-pool-size: 4
      memory-limit-mb: 2048
```

### Step 3: 启动服务

```bash
# 启动 patra-catalog 服务
cd patra-catalog/patra-catalog-boot
mvn spring-boot:run
```

服务启动后将在以下端口提供服务：
- HTTP API: `http://localhost:8080`
- Actuator: `http://localhost:8080/actuator`

### Step 4: 发起导入任务

使用 curl 或 Postman 调用 API：

```bash
# 开始导入
curl -X POST http://localhost:8080/api/v1/mesh/import/start \
  -H "Content-Type: application/json" \
  -d '{
    "taskName": "2025年MeSH数据首次导入"
  }'
```

响应示例：
```json
{
  "taskId": "550e8400-e29b-41d4-a716-446655440000",
  "taskName": "2025年MeSH数据首次导入",
  "status": "PROCESSING",
  "startTime": "2025-11-20T10:00:00",
  "message": "导入任务已启动"
}
```

### Step 5: 监控进度

```bash
# 查询导入进度
curl http://localhost:8080/api/v1/mesh/import/progress/550e8400-e29b-41d4-a716-446655440000
```

响应示例：
```json
{
  "taskId": "550e8400-e29b-41d4-a716-446655440000",
  "status": "PROCESSING",
  "overallProgress": {
    "totalRecords": 350000,
    "processedRecords": 175000,
    "progressPercentage": 50.0,
    "processSpeed": 500,
    "estimatedTimeRemaining": 350
  },
  "tableProgress": [
    {
      "tableName": "cat_mesh_descriptor",
      "displayName": "主题词",
      "totalCount": 35000,
      "processedCount": 35000,
      "progressPercentage": 100.0,
      "status": "COMPLETED"
    },
    {
      "tableName": "cat_mesh_tree_number",
      "displayName": "树形编号",
      "totalCount": 80000,
      "processedCount": 40000,
      "progressPercentage": 50.0,
      "status": "IN_PROGRESS"
    }
  ]
}
```

## 运维操作

### 处理失败任务

如果导入过程中断或失败：

```bash
# 重试失败任务（从断点继续）
curl -X POST http://localhost:8080/api/v1/mesh/import/retry/550e8400-e29b-41d4-a716-446655440000
```

### 清除数据重新导入

如果需要从头开始：

```bash
# 清除所有数据和进度
curl -X POST http://localhost:8080/api/v1/mesh/import/clear \
  -H "Content-Type: application/json" \
  -d '{"confirmClear": true}'
```

### 监控指标

通过 Spring Boot Actuator 查看监控指标：

```bash
# 查看自定义指标
curl http://localhost:8080/actuator/metrics/mesh.import.progress

# 查看健康状态
curl http://localhost:8080/actuator/health
```

## 日志查看

### 应用日志
```bash
tail -f logs/patra-catalog/app.log
```

### XXL-Job 任务日志
```bash
tail -f /data/applogs/xxl-job/jobhandler/mesh-import.log
```

## 常见问题

### Q1: 导入任务启动失败
**原因**：已有任务正在运行
**解决**：等待当前任务完成或查询任务状态

### Q2: 批次处理失败
**原因**：网络超时或数据格式错误
**解决**：系统会自动重试 3 次，也可手动调用重试接口

### Q3: 内存不足错误
**原因**：JVM 堆内存设置过小
**解决**：调整启动参数 `-Xmx4G -Xms2G`

### Q4: 下载速度慢
**原因**：NLM 服务器响应慢
**解决**：系统支持断点续传，可以中断后重试

## 性能优化建议

1. **批次大小调优**
   - 根据服务器性能调整批次大小
   - 较大的批次提高效率但增加内存占用

2. **数据库优化**
   - 确保 MySQL 缓冲池足够大（innodb_buffer_pool_size）
   - 临时关闭 binlog 可提升导入速度

3. **网络优化**
   - 使用代理服务器加速 NLM 访问
   - 预先下载 XML 文件到本地

## 验证导入结果

导入完成后，验证数据完整性：

```sql
-- 检查各表记录数
SELECT 'descriptor' as table_name, COUNT(*) as count FROM cat_mesh_descriptor
UNION ALL
SELECT 'qualifier', COUNT(*) FROM cat_mesh_qualifier
UNION ALL
SELECT 'tree_number', COUNT(*) FROM cat_mesh_tree_number
UNION ALL
SELECT 'entry_term', COUNT(*) FROM cat_mesh_entry_term
UNION ALL
SELECT 'concept', COUNT(*) FROM cat_mesh_concept;
```

预期结果：
- 主题词：~35,000
- 限定词：~100
- 树形编号：~80,000
- 入口术语：~250,000
- 概念：~180,000

## 技术支持

如遇到问题，请查看：
1. 应用日志：`logs/patra-catalog/`
2. XXL-Job 控制台：`http://xxl-job-admin:8080`
3. Nacos 控制台：`http://nacos:8848`
4. SkyWalking 追踪：`http://skywalking:8080`