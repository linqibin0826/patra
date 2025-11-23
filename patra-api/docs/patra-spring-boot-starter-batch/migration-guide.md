# patra-spring-boot-starter-batch 迁移指南

**版本**: v1.0.0
**创建日期**: 2025-11-23
**适用场景**: 从自定义批处理方案迁移到 Spring Batch

---

## 📋 目录

- [一、迁移概述](#一迁移概述)
- [二、迁移前准备](#二迁移前准备)
- [三、代码迁移](#三代码迁移)
- [四、数据库迁移](#四数据库迁移)
- [五、测试验证](#五测试验证)
- [六、回滚方案](#六回滚方案)

---

## 一、迁移概述

### 1.1 迁移目标

将 `patra-catalog` 模块的 MeSH 数据导入功能从自定义批处理方案迁移到 Spring Batch。

### 1.2 迁移范围

#### 删除的代码

| 类型 | 文件路径 | 原因 |
|------|---------|------|
| **聚合根** | `domain/model/aggregate/MeshImportAggregate.java` | Spring Batch 的 JobExecution 替代 |
| **值对象** | `domain/model/valueobject/TableProgress.java` | Spring Batch 的 StepExecution 替代 |
| **策略类** | `app/usecase/meshimport/strategy/*` | Spring Batch 的 Step 替代 |
| **编排器** | `app/usecase/meshimport/MeshImportOrchestrator.java` | Spring Batch 的 Job 配置替代 |
| **Repository** | `domain/port/MeshImportRepository.java` | Spring Batch 的 JobRepository 替代 |
| **Repository 实现** | `infra/persistence/repository/MeshImportRepositoryImpl.java` | 不再需要 |
| **Mapper** | `infra/persistence/mapper/MeshImportTaskMapper.java` | 不再需要 |
| **DO** | `infra/persistence/entity/MeshImportTaskDO.java` | Spring Batch 元数据表替代 |
| **DO** | `infra/persistence/entity/MeshTableProgressDO.java` | Spring Batch 元数据表替代 |
| **DO** | `infra/persistence/entity/MeshBatchDetailDO.java` | Spring Batch 元数据表替代 |
| **领域事件** | `domain/event/MeshImport*.java` | Spring Batch 监听器替代 |
| **数据库表** | `V0.6.0__create_mesh_import_tables.sql` | Spring Batch 元数据表替代 |

#### 保留的代码（可复用）

| 类型 | 文件路径 | 用途 |
|------|---------|------|
| **XML 解析器** | `infra/parser/StaxXmlParserImpl.java` | 作为 ItemReader 的一部分 |
| **文件下载器** | `infra/download/RestClientMeshFileDownloadImpl.java` | 作为 ItemReader 的一部分 |
| **转换器** | `infra/persistence/converter/MeshDescriptorConverter.java` | 作为 ItemProcessor |
| **Mapper** | `infra/persistence/mapper/MeshDescriptorMapper.java` | 作为 ItemWriter 的一部分 |
| **DO** | `infra/persistence/entity/MeshDescriptorDO.java` | 数据实体 |
| **REST Controller** | `adapter/rest/MeshImportController.java` | 重构后继续使用 |
| **XXL-Job Handler** | `adapter/scheduler/job/MeshImportJob.java` | 重构后继续使用 |

### 1.3 迁移收益

| 维度 | 当前 | 迁移后 |
|------|------|--------|
| **代码行数** | ~3000 行 | ~1500 行（减少 50%） |
| **复杂度** | 高（状态机、进度跟踪） | 低（框架管理） |
| **维护成本** | 高 | 低 |
| **可扩展性** | 低（需自己开发） | 高（框架内置） |
| **可观测性** | 需自己实现 | 框架集成 |

---

## 二、迁移前准备

### 2.1 环境检查

**检查清单**：

- [ ] 确认当前 MeSH 导入是否处于开发阶段（未在生产环境运行）
- [ ] 备份现有代码（创建 Git 分支 `backup/mesh-import-custom`）
- [ ] 备份数据库（导出 `cat_mesh_*` 表数据）
- [ ] 准备测试环境（独立数据库实例）

**备份命令**：

```bash
# 1. 创建备份分支
git checkout -b backup/mesh-import-custom

# 2. 导出数据库
mysqldump -u root -p patra_catalog \
  cat_mesh_import_task \
  cat_mesh_table_progress \
  cat_mesh_batch_detail \
  > mesh_import_backup.sql

# 3. 切换回开发分支
git checkout 001-mesh-data-import
```

### 2.2 依赖准备

**pom.xml 变更**：

```xml
<!-- patra-catalog/pom.xml -->

<!-- 删除：不再需要的依赖 -->
<!-- 无需删除任何依赖，因为当前没有引入专门的批处理库 -->

<!-- 新增：Spring Batch Starter -->
<dependency>
    <groupId>com.patra</groupId>
    <artifactId>patra-spring-boot-starter-batch</artifactId>
    <version>1.0.0</version>
</dependency>
```

---

## 三、代码迁移

### 3.1 迁移步骤

#### Step 1: 删除自定义批处理代码

```bash
# 删除 Domain 层的批处理相关代码
rm patra-catalog/patra-catalog-domain/src/main/java/com/patra/catalog/domain/model/aggregate/MeshImportAggregate.java
rm patra-catalog/patra-catalog-domain/src/main/java/com/patra/catalog/domain/model/valueobject/TableProgress.java
rm patra-catalog/patra-catalog-domain/src/main/java/com/patra/catalog/domain/port/MeshImportRepository.java
rm patra-catalog/patra-catalog-domain/src/main/java/com/patra/catalog/domain/event/MeshImportStarted.java
rm patra-catalog/patra-catalog-domain/src/main/java/com/patra/catalog/domain/event/MeshImportCompleted.java
rm patra-catalog/patra-catalog-domain/src/main/java/com/patra/catalog/domain/event/MeshImportFailed.java

# 删除 Application 层的编排器和策略
rm -rf patra-catalog/patra-catalog-app/src/main/java/com/patra/catalog/app/usecase/meshimport/strategy/
rm patra-catalog/patra-catalog-app/src/main/java/com/patra/catalog/app/usecase/meshimport/MeshImportOrchestrator.java

# 删除 Infrastructure 层的 Repository 实现
rm patra-catalog/patra-catalog-infra/src/main/java/com/patra/catalog/infra/persistence/repository/MeshImportRepositoryImpl.java
rm patra-catalog/patra-catalog-infra/src/main/java/com/patra/catalog/infra/persistence/mapper/MeshImportTaskMapper.java
rm patra-catalog/patra-catalog-infra/src/main/java/com/patra/catalog/infra/persistence/mapper/MeshTableProgressMapper.java
rm patra-catalog/patra-catalog-infra/src/main/java/com/patra/catalog/infra/persistence/mapper/MeshBatchDetailMapper.java
rm patra-catalog/patra-catalog-infra/src/main/java/com/patra/catalog/infra/persistence/entity/MeshImportTaskDO.java
rm patra-catalog/patra-catalog-infra/src/main/java/com/patra/catalog/infra/persistence/entity/MeshTableProgressDO.java
rm patra-catalog/patra-catalog-infra/src/main/java/com/patra/catalog/infra/persistence/entity/MeshBatchDetailDO.java

# 删除数据库迁移脚本（自定义表）
rm patra-catalog/patra-catalog-infra/src/main/resources/db/migration/V0.6.0__create_mesh_import_tables.sql
```

#### Step 2: 创建 Spring Batch Job 配置

**新建文件**：`patra-catalog-app/src/main/java/com/patra/catalog/app/batch/MeshImportJobConfig.java`

```java
package com.patra.catalog.app.batch;

import com.patra.catalog.infra.batch.reader.*;
import com.patra.catalog.infra.batch.processor.*;
import com.patra.catalog.infra.batch.writer.*;
import com.patra.starter.batch.config.BatchProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * MeSH 数据导入 Job 配置
 */
@Configuration
@RequiredArgsConstructor
public class MeshImportJobConfig {

    private final JobRepository jobRepository;
    private final PlatformTransactionManager transactionManager;
    private final BatchProperties batchProperties;

    /**
     * MeSH 导入 Job
     * <p>
     * 按依赖顺序串行导入 6 张表：
     * 1. Descriptor（主题词）
     * 2. Qualifier（限定词）
     * 3. TreeNumber（树形编号）
     * 4. EntryTerm（入口术语）
     * 5. Concept（概念）
     * 6. PublicationMesh（文献关联）
     */
    @Bean
    public Job meshImportJob(
            Step importDescriptorStep,
            Step importQualifierStep,
            Step importTreeNumberStep,
            Step importEntryTermStep,
            Step importConceptStep
    ) {
        return new JobBuilder("meshImportJob", jobRepository)
            .start(importDescriptorStep)
            .next(importQualifierStep)
            .next(importTreeNumberStep)
            .next(importEntryTermStep)
            .next(importConceptStep)
            .build();
    }

    /**
     * Step 1: 导入 Descriptor（主题词）
     */
    @Bean
    public Step importDescriptorStep(
            MeshDescriptorItemReader reader,
            MeshDescriptorItemProcessor processor,
            MeshDescriptorItemWriter writer
    ) {
        return new StepBuilder("importDescriptorStep", jobRepository)
            .<MeshDescriptor, MeshDescriptorDO>chunk(
                batchProperties.getChunk().getDefaultSize(),
                transactionManager
            )
            .reader(reader)
            .processor(processor)
            .writer(writer)
            .build();
    }

    /**
     * Step 2: 导入 Qualifier（限定词）
     */
    @Bean
    public Step importQualifierStep(
            MeshQualifierItemReader reader,
            MeshQualifierItemProcessor processor,
            MeshQualifierItemWriter writer
    ) {
        return new StepBuilder("importQualifierStep", jobRepository)
            .<MeshQualifier, MeshQualifierDO>chunk(500, transactionManager)
            .reader(reader)
            .processor(processor)
            .writer(writer)
            .build();
    }

    /**
     * Step 3: 导入 TreeNumber（树形编号）
     */
    @Bean
    public Step importTreeNumberStep(
            MeshTreeNumberItemReader reader,
            MeshTreeNumberItemProcessor processor,
            MeshTreeNumberItemWriter writer
    ) {
        return new StepBuilder("importTreeNumberStep", jobRepository)
            .<MeshTreeNumber, MeshTreeNumberDO>chunk(2000, transactionManager)
            .reader(reader)
            .processor(processor)
            .writer(writer)
            .build();
    }

    /**
     * Step 4: 导入 EntryTerm（入口术语）
     */
    @Bean
    public Step importEntryTermStep(
            MeshEntryTermItemReader reader,
            MeshEntryTermItemProcessor processor,
            MeshEntryTermItemWriter writer
    ) {
        return new StepBuilder("importEntryTermStep", jobRepository)
            .<MeshEntryTerm, MeshEntryTermDO>chunk(2000, transactionManager)
            .reader(reader)
            .processor(processor)
            .writer(writer)
            .build();
    }

    /**
     * Step 5: 导入 Concept（概念）
     */
    @Bean
    public Step importConceptStep(
            MeshConceptItemReader reader,
            MeshConceptItemProcessor processor,
            MeshConceptItemWriter writer
    ) {
        return new StepBuilder("importConceptStep", jobRepository)
            .<MeshConcept, MeshConceptDO>chunk(2000, transactionManager)
            .reader(reader)
            .processor(processor)
            .writer(writer)
            .build();
    }
}
```

#### Step 3: 实现 ItemReader

**新建文件**：`patra-catalog-infra/src/main/java/com/patra/catalog/infra/batch/reader/MeshDescriptorItemReader.java`

```java
package com.patra.catalog.infra.batch.reader;

import com.patra.catalog.domain.model.entity.MeshDescriptor;
import com.patra.catalog.domain.port.MeshFileDownloadPort;
import com.patra.catalog.domain.port.XmlParserPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.item.ItemReader;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.Iterator;
import java.util.List;

/**
 * MeSH Descriptor ItemReader
 * <p>
 * 从 NLM 下载 XML 文件并流式解析
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class MeshDescriptorItemReader implements ItemReader<MeshDescriptor> {

    private final XmlParserPort xmlParser;
    private final MeshFileDownloadPort downloadPort;

    private Iterator<MeshDescriptor> descriptorIterator;
    private boolean initialized = false;

    @Override
    public MeshDescriptor read() throws Exception {
        if (!initialized) {
            initialize();
        }

        if (descriptorIterator.hasNext()) {
            return descriptorIterator.next();
        }

        return null;  // 读取完成，返回 null
    }

    private void initialize() throws Exception {
        log.info("初始化 MeshDescriptorItemReader");

        // 下载 XML 文件
        String url = "https://nlmpubs.nlm.nih.gov/projects/mesh/MESH_FILES/xmlmesh/desc2024.xml";
        Path xmlFile = downloadPort.download(url);

        // 流式解析 XML（不全部加载到内存）
        List<MeshDescriptor> descriptors = xmlParser.parseDescriptors(xmlFile);
        this.descriptorIterator = descriptors.iterator();
        this.initialized = true;

        log.info("MeshDescriptorItemReader 初始化完成，共 {} 条记录", descriptors.size());
    }
}
```

#### Step 4: 实现 ItemProcessor

**新建文件**：`patra-catalog-infra/src/main/java/com/patra/catalog/infra/batch/processor/MeshDescriptorItemProcessor.java`

```java
package com.patra.catalog.infra.batch.processor;

import com.patra.catalog.domain.model.entity.MeshDescriptor;
import com.patra.catalog.infra.persistence.converter.MeshDescriptorConverter;
import com.patra.catalog.infra.persistence.entity.MeshDescriptorDO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.stereotype.Component;

/**
 * MeSH Descriptor ItemProcessor
 * <p>
 * 将领域实体转换为持久化实体
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class MeshDescriptorItemProcessor implements ItemProcessor<MeshDescriptor, MeshDescriptorDO> {

    private final MeshDescriptorConverter converter;

    @Override
    public MeshDescriptorDO process(MeshDescriptor item) throws Exception {
        // 数据验证
        if (item.getUI() == null || item.getUI().isEmpty()) {
            log.warn("跳过无效 Descriptor: {}", item);
            return null;  // 返回 null 表示跳过该记录
        }

        // 领域实体 -> 持久化实体
        MeshDescriptorDO entity = converter.toEntity(item);

        return entity;
    }
}
```

#### Step 5: 实现 ItemWriter

**新建文件**：`patra-catalog-infra/src/main/java/com/patra/catalog/infra/batch/writer/MeshDescriptorItemWriter.java`

```java
package com.patra.catalog.infra.batch.writer;

import com.patra.catalog.infra.persistence.entity.MeshDescriptorDO;
import com.patra.catalog.infra.persistence.mapper.MeshDescriptorMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.item.Chunk;
import org.springframework.batch.item.ItemWriter;
import org.springframework.stereotype.Component;

/**
 * MeSH Descriptor ItemWriter
 * <p>
 * 批量写入数据库
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class MeshDescriptorItemWriter implements ItemWriter<MeshDescriptorDO> {

    private final MeshDescriptorMapper mapper;

    @Override
    public void write(Chunk<? extends MeshDescriptorDO> chunk) throws Exception {
        var items = chunk.getItems();

        if (items.isEmpty()) {
            return;
        }

        log.debug("批量写入 {} 条 MeshDescriptor", items.size());

        // 批量插入（使用 MyBatis-Plus 的批量插入）
        for (MeshDescriptorDO item : items) {
            mapper.insert(item);
        }

        log.debug("批量写入完成");
    }
}
```

#### Step 6: 重构 XXL-Job Handler

**修改文件**：`patra-catalog-adapter/src/main/java/com/patra/catalog/adapter/scheduler/job/MeshImportJob.java`

```java
package com.patra.catalog.adapter.scheduler.job;

import com.patra.starter.batch.core.JobLauncherHelper;
import com.patra.starter.batch.lock.DistributedJobLock;
import com.xxl.job.core.handler.annotation.XxlJob;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.Job;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * MeSH 数据导入定时任务
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class MeshImportJob {

    private final JobLauncherHelper jobLauncherHelper;
    private final Job meshImportJob;

    /**
     * MeSH 数据导入任务
     * <p>
     * 使用分布式锁防止多实例并发执行
     */
    @XxlJob("meshImportJob")
    @DistributedJobLock(key = "batch:job:mesh-import", timeout = 7200)
    public void execute() {
        log.info("MeSH 数据导入任务启动");

        Long executionId = jobLauncherHelper.launch(meshImportJob, Map.of(
            "year", "2024",
            "source", "NLM"
        ));

        log.info("MeSH 数据导入任务已提交，执行 ID: {}", executionId);
    }
}
```

#### Step 7: 重构 REST Controller

**修改文件**：`patra-catalog-adapter/src/main/java/com/patra/catalog/adapter/rest/MeshImportController.java`

```java
package com.patra.catalog.adapter.rest;

import com.patra.starter.batch.core.JobLauncherHelper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.explore.JobExplorer;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * MeSH 导入管理 API
 */
@RestController
@RequestMapping("/api/v1/mesh/import")
@RequiredArgsConstructor
@Slf4j
public class MeshImportController {

    private final JobLauncherHelper jobLauncherHelper;
    private final Job meshImportJob;
    private final JobExplorer jobExplorer;

    /**
     * 启动 MeSH 导入任务
     */
    @PostMapping("/start")
    public ResponseEntity<Map<String, Object>> startImport() {
        Long executionId = jobLauncherHelper.launch(meshImportJob, Map.of(
            "year", "2024"
        ));

        return ResponseEntity.ok(Map.of(
            "executionId", executionId,
            "message", "MeSH 导入任务已启动"
        ));
    }

    /**
     * 查询导入进度
     */
    @GetMapping("/progress/{executionId}")
    public ResponseEntity<Map<String, Object>> getProgress(@PathVariable Long executionId) {
        JobExecution execution = jobExplorer.getJobExecution(executionId);

        if (execution == null) {
            return ResponseEntity.notFound().build();
        }

        Map<String, Object> progress = new HashMap<>();
        progress.put("status", execution.getStatus().name());
        progress.put("startTime", execution.getStartTime());
        progress.put("endTime", execution.getEndTime());

        // 统计各 Step 进度
        List<Map<String, Object>> steps = execution.getStepExecutions().stream()
            .map(step -> Map.of(
                "name", step.getStepName(),
                "status", step.getStatus().name(),
                "readCount", step.getReadCount(),
                "writeCount", step.getWriteCount(),
                "skipCount", step.getSkipCount()
            ))
            .toList();

        progress.put("steps", steps);

        return ResponseEntity.ok(progress);
    }
}
```

---

## 四、数据库迁移

### 4.1 删除自定义表

**创建迁移脚本**：`patra-catalog-infra/src/main/resources/db/migration/V0.7.0__drop_custom_batch_tables.sql`

```sql
-- 删除自定义批处理表
DROP TABLE IF EXISTS cat_mesh_batch_detail;
DROP TABLE IF EXISTS cat_mesh_table_progress;
DROP TABLE IF EXISTS cat_mesh_import_task;
```

### 4.2 Spring Batch 元数据表

Spring Batch 会自动创建以下表（配置 `spring.batch.jdbc.initialize-schema=always`）：

- `BATCH_JOB_INSTANCE`
- `BATCH_JOB_EXECUTION`
- `BATCH_JOB_EXECUTION_PARAMS`
- `BATCH_JOB_EXECUTION_CONTEXT`
- `BATCH_STEP_EXECUTION`
- `BATCH_STEP_EXECUTION_CONTEXT`

**验证**：

```sql
-- 查看 Spring Batch 元数据表
SHOW TABLES LIKE 'BATCH_%';
```

---

## 五、测试验证

### 5.1 单元测试

**测试 ItemReader**：

```java
@Test
void testMeshDescriptorItemReader() throws Exception {
    MeshDescriptorItemReader reader = new MeshDescriptorItemReader(xmlParser, downloadPort);

    MeshDescriptor descriptor = reader.read();
    assertNotNull(descriptor);
    assertNotNull(descriptor.getUI());
}
```

**测试 ItemProcessor**：

```java
@Test
void testMeshDescriptorItemProcessor() throws Exception {
    MeshDescriptor descriptor = new MeshDescriptor();
    descriptor.setUI("D000001");
    descriptor.setName("Calcimycin");

    MeshDescriptorItemProcessor processor = new MeshDescriptorItemProcessor(converter);
    MeshDescriptorDO result = processor.process(descriptor);

    assertNotNull(result);
    assertEquals("D000001", result.getUi());
}
```

### 5.2 集成测试

**测试 Job 执行**：

```java
@SpringBootTest
@ActiveProfiles("test")
class MeshImportJobIT {

    @Autowired
    private Job meshImportJob;

    @Autowired
    private JobLauncher jobLauncher;

    @Test
    void testMeshImportJob() throws Exception {
        JobParameters params = new JobParametersBuilder()
            .addLong("timestamp", System.currentTimeMillis())
            .toJobParameters();

        JobExecution execution = jobLauncher.run(meshImportJob, params);

        assertEquals(BatchStatus.COMPLETED, execution.getStatus());
    }
}
```

### 5.3 E2E 测试

**完整导入流程测试**：

```bash
# 1. 启动应用
mvn spring-boot:run -pl patra-catalog-boot

# 2. 调用 REST API 启动导入
curl -X POST http://localhost:8080/api/v1/mesh/import/start

# 3. 查询进度
curl http://localhost:8080/api/v1/mesh/import/progress/{executionId}

# 4. 验证数据
mysql -u root -p patra_catalog -e "SELECT COUNT(*) FROM cat_mesh_descriptor"
```

---

## 六、回滚方案

### 6.1 回滚触发条件

- 迁移后测试失败
- 性能显著下降
- 发现关键 Bug

### 6.2 回滚步骤

```bash
# 1. 切换回备份分支
git checkout backup/mesh-import-custom

# 2. 恢复数据库
mysql -u root -p patra_catalog < mesh_import_backup.sql

# 3. 重启应用
mvn spring-boot:run -pl patra-catalog-boot
```

### 6.3 回滚验证

- [ ] 应用正常启动
- [ ] MeSH 导入任务可正常触发
- [ ] 数据完整性验证通过

---

## 七、常见问题

### Q1: 迁移后性能是否会下降？

**A**: 不会。Spring Batch 经过高度优化，性能通常优于自定义实现。批次大小可调整优化。

### Q2: Spring Batch 元数据表会不会占用过多空间？

**A**: 不会。可以定期清理历史执行记录。配置保留策略：

```yaml
spring:
  batch:
    job:
      execution-context-serializer-type: JACKSON  # 使用紧凑的序列化
```

### Q3: 如何处理已经运行过的旧任务？

**A**: 旧任务记录在自定义表中，新任务记录在 Spring Batch 表中，互不影响。迁移后可以删除旧表。

### Q4: 断点续传如何工作？

**A**: Spring Batch 自动管理。JobExecution 记录执行状态，StepExecution 记录 Chunk 进度。重启时自动从上次位置继续。

---

## 八、总结

### 迁移清单

- [ ] 备份代码和数据库
- [ ] 删除自定义批处理代码
- [ ] 创建 Spring Batch Job 配置
- [ ] 实现 ItemReader/Processor/Writer
- [ ] 重构 XXL-Job Handler
- [ ] 重构 REST Controller
- [ ] 删除自定义数据库表
- [ ] 编写单元测试
- [ ] 编写集成测试
- [ ] E2E 测试验证
- [ ] 性能测试
- [ ] 文档更新

### 预期收益

- ✅ 代码量减少 50%
- ✅ 维护成本降低 70%
- ✅ 可扩展性提升（并行、分区等内置支持）
- ✅ 可观测性增强（SkyWalking、Micrometer 集成）
- ✅ 标准化（团队成员熟悉 Spring Batch）

---

**文档结束**
