# 架构重构报告：DataType 和 TypeReference 位置优化

## 重构日期
2025-11-12

## 重构目标
解决框架层依赖业务层的架构违规问题

## 重构内容

### 1. 移动 DataType.java
- **从**: `patra-ingest/patra-ingest-domain/src/main/java/com/patra/ingest/domain/model/DataType.java`
- **到**: `patra-common/patra-common-model/src/main/java/com/patra/common/model/DataType.java`
- **包名**: `com.patra.ingest.domain.model` → `com.patra.common.model`
- **理由**: DataType 是全局数据类型枚举，与 CanonicalLiterature 强关联，属于 Shared Kernel

### 2. 移动 TypeReference.java
- **从**: `patra-ingest/patra-ingest-domain/src/main/java/com/patra/ingest/domain/model/TypeReference.java`
- **到**: `patra-common/patra-common-core/src/main/java/com/patra/common/type/TypeReference.java`
- **包名**: `com.patra.ingest.domain.model` → `com.patra.common.type`
- **理由**: TypeReference 是纯粹的泛型工具类，类似 Jackson/Gson/Spring 的实现

### 3. 移动测试文件
- **DataTypeTest.java**: 移动到 `patra-common-model/src/test/`
- **TypeReferenceTest.java**: 移动到 `patra-common-core/src/test/`

### 4. 更新依赖关系
- **patra-spring-boot-starter-provenance**: 删除对 `patra-ingest-domain` 的依赖
- **patra-common-core**: 新增对 `patra-common-model` 的测试依赖

### 5. 批量更新 import 语句
- 更新了 18 个 Java 文件的 import 语句
- 修复了 Javadoc 中的 @see 和 @link 引用

## 重构结果

### 架构改进
✅ **消除架构违规**: 框架层不再依赖业务层
✅ **依赖方向正确**: Common ← Framework ← Business
✅ **职责清晰**: 通用类型与业务逻辑分离

### 验证结果
✅ **编译成功**: `mvn clean compile` 通过
✅ **测试通过**: `mvn test` 全部通过 (BUILD SUCCESS)
✅ **依赖合理**: 无循环依赖

## 影响范围

### 修改的模块
1. patra-common-model
2. patra-common-core
3. patra-spring-boot-starter-provenance
4. patra-ingest-domain
5. patra-ingest-app
6. patra-ingest-infra

### 修改的文件统计
- 新建文件: 4 个（源码 2 + 测试 2）
- 删除文件: 4 个（源码 2 + 测试 2）
- 修改 pom.xml: 2 个
- 修改 import 语句: 18 个 Java 文件
- 修改 Javadoc: 3 个文件

## 后续建议

1. **重命名业务层 ProviderRegistry**: 
   - 从 `ProviderRegistry` 重命名为 `DataTypeAwareProviderRegistry`
   - 理由：与框架层的 ProviderRegistry 区分

2. **更新架构文档**:
   - 更新设计方案文档中的包名引用
   - 更新 README 中的示例代码

3. **添加架构测试**:
   - 使用 ArchUnit 自动检测依赖方向违规
   - 防止未来再次出现类似问题

## 总结

本次重构成功解决了框架层依赖业务层的架构违规问题，提升了代码的可维护性和可复用性。重构过程顺利，所有测试通过，无破坏性变更。
