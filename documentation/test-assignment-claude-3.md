# Claude Code #3 - 测试任务分配清单

> **负责范围**: patra-storage 微服务全栈测试 + 公共模块测试
> **并行策略**: 同时调度 3 个 test-architect subagents
> **预计工作量**: 6-10 小时
> **优先级**: P2（高）

---

## 📋 任务概览

你负责完成两部分工作：
1. **patra-storage 微服务** 的 Infrastructure、Application、Adapter 层测试
2. **公共模块** (patra-common、patra-expr-kernel) 的核心测试

### 当前状态

**patra-storage**:
| 层级 | 当前覆盖率 | 目标覆盖率 | 差距 | 状态 |
|------|-----------|-----------|------|------|
| **Domain** | 99% | 80% | - | ✅ 已达标 |
| **Infrastructure** | 0% | 60% | +60% | 🔴 未开始 |
| **Application** | 0% | 70% | +70% | 🔴 未开始 |
| **Adapter** | 0% | 50% | +50% | 🔴 未开始 |

**公共模块**:
- patra-common: 0%
- patra-expr-kernel: 0%

---

## 🎯 阶段 1：patra-storage Infrastructure 层（1-2 小时）

### 目标
patra-storage-infra: **0% → 65%**

### 待测试包（按优先级排序）

#### Batch 1A - Repository 层（并行生成）
1. **persistence.repository 包**
   - 路径: `patra-storage/patra-storage-infra/src/main/java/com/patra/storage/infra/persistence/repository`
   - 预计文件: StorageFileRepository, BusinessContextRepository 等
   - 预计测试: ~60 个
   - 预期覆盖: 75%+
   - **说明**: MyBatis 仓储测试，使用 @MybatisTest

2. **persistence.mapper 包**
   - 路径: `patra-storage/patra-storage-infra/src/main/java/com/patra/storage/infra/persistence/mapper`
   - 预计测试: ~50 个
   - 预期覆盖: 80%+
   - **说明**: MyBatis Mapper 接口测试

3. **persistence.converter 包**
   - 路径: `patra-storage/patra-storage-infra/src/main/java/com/patra/storage/infra/persistence/converter`
   - 预计测试: ~40 个
   - 预期覆盖: 80%+
   - **说明**: TypeHandler 测试

#### Batch 1B - Object Storage 集成（并行生成）
4. **integration.s3 包**（如果存在）
   - 路径: `patra-storage/patra-storage-infra/src/main/java/com/patra/storage/infra/integration/s3`
   - 预计测试: ~50 个
   - 预期覆盖: 60%+
   - **说明**: S3/MinIO 对象存储集成测试，使用 TestContainers

5. **integration.oss 包**（阿里云 OSS，如果存在）
   - 路径: `patra-storage/patra-storage-infra/src/main/java/com/patra/storage/infra/integration/oss`
   - 预计测试: ~40 个
   - 预期覆盖: 60%+

6. **config 包**
   - 路径: `patra-storage/patra-storage-infra/src/main/java/com/patra/storage/infra/config`
   - 预计测试: ~20 个
   - 预期覆盖: 50%+

### 执行指令

```bash
# 探索 infra 层结构
Task(subagent_type=Explore,
     description="探索 patra-storage-infra 结构",
     prompt="分析 patra-storage-infra 包结构，识别 Repository、ObjectStorage 集成...")

# 阶段 1：并行生成 Repository 测试
Task(subagent_type=test-architect,
     description="生成 StorageFileRepository 测试",
     prompt="为 StorageFileRepository 生成 MyBatis 仓储测试...")

Task(subagent_type=test-architect,
     description="生成 Mapper 测试",
     prompt="为 MyBatis Mapper 生成 SQL 映射测试...")

Task(subagent_type=test-architect,
     description="生成 Converter 测试",
     prompt="为 TypeHandler 生成类型转换测试...")

# 阶段 2：并行生成 Object Storage 集成测试
Task(subagent_type=test-architect,
     description="生成 S3 集成测试",
     prompt="为 S3/MinIO 集成生成测试，使用 TestContainers MinIO...")

Task(subagent_type=test-architect,
     description="生成 OSS 集成测试",
     prompt="为阿里云 OSS 集成生成测试...")

# 验证
cd patra-storage/patra-storage-infra
mvn clean test jacoco:report -q
```

---

## 🎯 阶段 2：patra-storage Application 层（1-2 小时）

### 目标
patra-storage-app: **0% → 70%**

### 待测试包

#### Batch 2A - Service 层（并行生成）
1. **应用服务**
   - 路径: `patra-storage/patra-storage-app/src/main/java/com/patra/storage/app/service`
   - 预计文件: StorageFileService, FileUploadService, FileDownloadService 等
   - 预计测试: ~70 个
   - 预期覆盖: 70%+
   - **说明**: 应用服务测试，Mock 仓储和对象存储

2. **命令处理器**
   - 路径: `patra-storage/patra-storage-app/src/main/java/com/patra/storage/app/command`
   - 预计测试: ~40 个
   - 预期覆盖: 70%+

3. **查询处理器**
   - 路径: `patra-storage/patra-storage-app/src/main/java/com/patra/storage/app/query`
   - 预计测试: ~50 个
   - 预期覆盖: 75%+

#### Batch 2B - Assembler（并行生成）
4. **DTO 组装器**
   - 路径: `patra-storage/patra-storage-app/src/main/java/com/patra/storage/app/assembler`
   - 预计测试: ~30 个
   - 预期覆盖: 80%+

5. **校验器**
   - 路径: `patra-storage/patra-storage-app/src/main/java/com/patra/storage/app/validator`
   - 预计测试: ~25 个
   - 预期覆盖: 80%+

### 执行指令

```bash
# 探索 app 层结构
Task(subagent_type=Explore,
     description="探索 patra-storage-app 结构",
     prompt="分析 patra-storage-app 包结构...")

# 并行生成 Service 层测试
Task(subagent_type=test-architect,
     description="生成 StorageFileService 测试",
     prompt="为 StorageFileService 生成应用服务测试，Mock 仓储和对象存储...")

Task(subagent_type=test-architect,
     description="生成 FileUploadService 测试",
     prompt="为文件上传服务生成测试...")

Task(subagent_type=test-architect,
     description="生成 Command/Query 处理器测试",
     prompt="为 CQRS 处理器生成测试...")

# 验证
cd patra-storage/patra-storage-app
mvn clean test jacoco:report -q
```

---

## 🎯 阶段 3：patra-storage Adapter 层（1-2 小时）

### 目标
patra-storage-adapter: **0% → 55%**

### 待测试包

#### Batch 3A - REST Controller（并行生成）
1. **REST 控制器**
   - 路径: `patra-storage/patra-storage-adapter/src/main/java/com/patra/storage/adapter/controller`
   - 预计文件: FileUploadController, FileDownloadController 等
   - 预计测试: ~60 个
   - 预期覆盖: 60%+
   - **说明**: @WebMvcTest + MockMultipartFile

2. **Feign 客户端**（如果存在）
   - 路径: `patra-storage/patra-storage-adapter/src/main/java/com/patra/storage/adapter/feign`
   - 预计测试: ~20 个
   - 预期覆盖: 50%+

3. **DTO 和转换器**
   - 路径: `patra-storage/patra-storage-adapter/src/main/java/com/patra/storage/adapter/dto`
   - 预计测试: ~30 个
   - 预期覆盖: 60%+

### 执行指令

```bash
# 并行生成 Controller 测试
Task(subagent_type=test-architect,
     description="生成 FileUploadController 测试",
     prompt="为文件上传 Controller 生成 @WebMvcTest，使用 MockMultipartFile...")

Task(subagent_type=test-architect,
     description="生成 FileDownloadController 测试",
     prompt="为文件下载 Controller 生成测试...")

Task(subagent_type=test-architect,
     description="生成 DTO 转换器测试",
     prompt="为 API DTO 转换器生成测试...")

# 验证
cd patra-storage/patra-storage-adapter
mvn clean test jacoco:report -q
```

---

## 🎯 阶段 4：公共模块测试（2-4 小时）

### 目标
- patra-common-core: **0% → 70%**
- patra-expr-kernel: **0% → 65%**

### 4.1 patra-common-core 测试

#### Batch 4A - 工具类（并行生成）
1. **utils 包**
   - 路径: `patra-common/patra-common-core/src/main/java/com/patra/common/core/utils`
   - 预计测试: ~80 个
   - 预期覆盖: 75%+
   - **说明**: 字符串、日期、JSON 等工具类测试

2. **constants 包**
   - 路径: `patra-common/patra-common-core/src/main/java/com/patra/common/core/constants`
   - 预计测试: ~20 个
   - 预期覆盖: 60%+

3. **exception 包**
   - 路径: `patra-common/patra-common-core/src/main/java/com/patra/common/core/exception`
   - 预计测试: ~30 个
   - 预期覆盖: 80%+

### 4.2 patra-expr-kernel 测试（表达式引擎）

#### Batch 4B - 表达式引擎核心（并行生成）
1. **parser 包**
   - 路径: `patra-expr-kernel/src/main/java/com/patra/expr/parser`
   - 预计测试: ~100 个
   - 预期覆盖: 70%+
   - **说明**: 表达式解析器测试

2. **compiler 包**
   - 路径: `patra-expr-kernel/src/main/java/com/patra/expr/compiler`
   - 预计测试: ~80 个
   - 预期覆盖: 65%+
   - **说明**: 表达式编译器测试

3. **evaluator 包**
   - 路径: `patra-expr-kernel/src/main/java/com/patra/expr/evaluator`
   - 预计测试: ~90 个
   - 预期覆盖: 70%+
   - **说明**: 表达式求值器测试

### 执行指令

```bash
# 探索公共模块结构
Task(subagent_type=Explore,
     description="探索 patra-common 结构",
     prompt="分析 patra-common-core 包结构，识别工具类、常量、异常...")

Task(subagent_type=Explore,
     description="探索 patra-expr-kernel 结构",
     prompt="分析 patra-expr-kernel 包结构，识别 parser、compiler、evaluator...")

# 并行生成 common-core 测试
Task(subagent_type=test-architect,
     description="生成 Utils 工具类测试",
     prompt="为 DateUtils、StringUtils、JsonUtils 等生成测试...")

Task(subagent_type=test-architect,
     description="生成 Exception 测试",
     prompt="为公共异常类生成测试...")

# 并行生成 expr-kernel 测试
Task(subagent_type=test-architect,
     description="生成 Parser 测试",
     prompt="为表达式解析器生成测试，覆盖各种语法...")

Task(subagent_type=test-architect,
     description="生成 Compiler 测试",
     prompt="为表达式编译器生成测试...")

Task(subagent_type=test-architect,
     description="生成 Evaluator 测试",
     prompt="为表达式求值器生成测试...")

# 验证
cd patra-common/patra-common-core
mvn clean test jacoco:report -q

cd ../../patra-expr-kernel
mvn clean test jacoco:report -q
```

---

## ✅ 测试规范要求

### patra-storage 模块
1. **Infrastructure**:
   - Repository/Mapper: @MybatisTest
   - Object Storage: TestContainers (MinIO)
   - Converter: 纯 Java 单元测试

2. **Application**:
   - Service: @ExtendWith(MockitoExtension.class)
   - Mock 仓储和对象存储客户端

3. **Adapter**:
   - Controller: @WebMvcTest + MockMultipartFile
   - 文件上传/下载测试

### 公共模块
1. **patra-common-core**: 纯 Java 单元测试
2. **patra-expr-kernel**:
   - 纯 Java 单元测试
   - 大量表达式用例测试
   - 边界条件和错误处理

---

## 📊 验证与报告

### 覆盖率验证

```bash
# patra-storage 模块
cd patra-storage/patra-storage-[infra|app|adapter]
mvn clean test jacoco:report -q

# 公共模块
cd patra-common/patra-common-core
mvn clean test jacoco:report -q

cd ../../patra-expr-kernel
mvn clean test jacoco:report -q
```

---

## 🎯 预期成果

### 覆盖率目标
**patra-storage**:
- Infra: 0% → **65%** ✅
- App: 0% → **70%** ✅
- Adapter: 0% → **55%** ✅

**公共模块**:
- patra-common-core: 0% → **70%** ✅
- patra-expr-kernel: 0% → **65%** ✅

### 测试数量预估
- patra-storage (全栈): ~450 个测试
- patra-common-core: ~130 个测试
- patra-expr-kernel: ~270 个测试
- **总计**: ~850 个测试

### 时间规划
- 阶段 1 (storage-infra): 1-2 小时
- 阶段 2 (storage-app): 1-2 小时
- 阶段 3 (storage-adapter): 1-2 小时
- 阶段 4 (公共模块): 2-4 小时
- **总计**: 6-10 小时

---

## 📝 注意事项

1. **storage 模块轻量**: Domain 已完美（99%），其他层代码量较小
2. **表达式引擎重要**: expr-kernel 是核心基础设施，需重点测试
3. **并行执行**: 充分利用 3 个 test-architect 并行能力
4. **增量验证**: 每个模块完成后立即验证

祝顺利完成任务！🚀
