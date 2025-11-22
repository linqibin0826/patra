# patra-catalog-infra — 目录管理基础设施层

## 📋 概述

`patra-catalog-infra` 是 patra-catalog 服务的**基础设施层（Infrastructure Layer）**，负责实现 Domain 层定义的 Port 接口，提供数据持久化、外部服务调用等技术能力。

本模块在六边形架构中位于**外层**，遵循以下原则：
- **依赖倒置**：Infrastructure 层实现 Domain 层定义的 Port 接口，依赖方向从外向内
- **技术隔离**：所有技术细节封装在 Infrastructure 层，Domain 层和 Application 层不感知
- **DO 不泄露**：DO 对象只在 Infrastructure 层内部使用，不暴露到外层（通过 Converter 转换为领域对象）
- **最小依赖**：仅引入必要的技术依赖（如 `spring-web` 而非 `spring-boot-starter-web`）
- **流式处理**：使用 Stream 处理大数据量，避免一次性加载到内存

---

## 🏗️ 模块结构

```
patra-catalog-infra/
└─ src/main/java/.../infra/
   ├─ persistence/                 # 数据持久化
   │  ├─ repository/               # Repository 实现（实现 Domain 层 Port 接口）
   │  │  ├─ MeshImportRepositoryImpl.java         # MeSH 导入任务仓储实现
   │  │  ├─ MeshDescriptorRepositoryImpl.java     # MeSH 主题词仓储实现
   │  │  ├─ MeshBatchDetailRepositoryImpl.java    # MeSH 批次详情仓储实现
   │  │  ├─ PublicationRepositoryImpl.java        # 文献仓储实现
   │  │  ├─ AuthorRepositoryImpl.java             # 作者仓储实现
   │  │  ├─ AffiliationRepositoryImpl.java        # 机构仓储实现
   │  │  └─ VenueRepositoryImpl.java              # 期刊仓储实现
   │  ├─ mapper/                   # MyBatis Mapper 接口
   │  │  ├─ MeshImportTaskMapper.java             # 导入任务表 Mapper
   │  │  ├─ MeshTableProgressMapper.java          # 表进度表 Mapper
   │  │  ├─ MeshBatchDetailMapper.java            # 批次详情表 Mapper
   │  │  ├─ MeshDescriptorMapper.java             # 主题词表 Mapper
   │  │  ├─ MeshQualifierMapper.java              # 限定词表 Mapper
   │  │  ├─ MeshTreeNumberMapper.java             # 树形编号表 Mapper
   │  │  ├─ MeshEntryTermMapper.java              # 入口术语表 Mapper
   │  │  ├─ MeshConceptMapper.java                # 概念表 Mapper
   │  │  ├─ PublicationMapper.java                # 文献表 Mapper
   │  │  ├─ AuthorMapper.java                     # 作者表 Mapper
   │  │  ├─ AffiliationMapper.java                # 机构表 Mapper
   │  │  └─ VenueMapper.java                      # 期刊表 Mapper
   │  ├─ entity/                   # 数据库实体（DO）
   │  │  ├─ MeshImportTaskDO.java                 # 导入任务表实体
   │  │  ├─ MeshTableProgressDO.java              # 表进度表实体
   │  │  ├─ MeshBatchDetailDO.java                # 批次详情表实体
   │  │  ├─ MeshDescriptorDO.java                 # 主题词表实体
   │  │  ├─ MeshQualifierDO.java                  # 限定词表实体
   │  │  ├─ MeshTreeNumberDO.java                 # 树形编号表实体
   │  │  ├─ MeshEntryTermDO.java                  # 入口术语表实体
   │  │  ├─ MeshConceptDO.java                    # 概念表实体
   │  │  ├─ PublicationDO.java                    # 文献表实体
   │  │  ├─ AuthorDO.java                         # 作者表实体
   │  │  ├─ AffiliationDO.java                    # 机构表实体
   │  │  └─ VenueDO.java                          # 期刊表实体
   │  └─ converter/                # MapStruct 对象转换器
   │     ├─ MeshImportConverter.java              # 导入任务对象转换器
   │     ├─ MeshDescriptorConverter.java          # 主题词对象转换器
   │     ├─ PublicationConverter.java             # 文献对象转换器
   │     ├─ AuthorConverter.java                  # 作者对象转换器
   │     ├─ AffiliationConverter.java             # 机构对象转换器
   │     └─ VenueConverter.java                   # 期刊对象转换器
   ├─ parser/                      # XML 解析器实现
   │  └─ StaxXmlParserImpl.java                   # StAX 流式 XML 解析器
   ├─ download/                    # 文件下载实现
   │  └─ RestClientMeshFileDownloadImpl.java      # 基于 RestClient 的文件下载器
   ├─ metrics/                     # 指标收集
   │  └─ MeshImportMetrics.java                   # MeSH 导入指标
   └─ config/                      # 基础设施配置
      └─ RestClientConfig.java                    # RestClient 配置
```

---

## 🔑 核心职责

### 1. 数据持久化

**职责**：实现 Repository Port 接口，使用 MyBatis-Plus 操作数据库。

**技术选型**：
- **ORM**：MyBatis-Plus 3.5.12
- **对象转换**：MapStruct 1.5.x
- **连接池**：HikariCP（Spring Boot 默认）
- **数据库**：MySQL 8.0+

### 2. 对象转换

**职责**：使用 MapStruct 在领域对象和 DO 对象之间转换。

**转换方向**：
- **DO → Domain**：查询场景（`toDomain`）
- **Domain → DO**：持久化场景（`toEntity`、`toDO`）

### 3. XML 解析

**职责**：使用 StAX 流式解析 MeSH XML 文件。

**技术选型**：
- **解析器**：JDK 内置 StAX（`javax.xml.stream.XMLStreamReader`）
- **流式处理**：返回 `Stream<T>`，支持大文件处理

### 4. HTTP 下载

**职责**：使用 Spring RestClient 下载 MeSH 数据文件。

**技术选型**：
- **HTTP 客户端**：Spring RestClient（底层 JDK 21 HttpClient）
- **依赖**：仅引入 `spring-web`，不引入 `spring-boot-starter-web`

### 5. 技术实现

**职责**：封装所有技术细节，对 Domain 层和 Application 层透明。

---

## 🎯 核心组件

### 1. Repository 实现

#### MeshImportRepositoryImpl (MeSH 导入任务仓储实现)

**实现接口**：`MeshImportPort`

**核心方法**：

##### save(MeshImportAggregate aggregate)

保存导入任务聚合根。

**逻辑**：
```java
@Override
public MeshImportAggregate save(MeshImportAggregate aggregate) {
    // 1. 转换为 DO 对象
    MeshImportTaskDO taskDO = meshImportConverter.toTaskDO(aggregate);
    List<MeshTableProgressDO> progressDOList = meshImportConverter.toProgressDOList(aggregate);

    // 2. 保存到数据库
    if (taskDO.getId() == null) {
        meshImportTaskMapper.insert(taskDO);
    } else {
        meshImportTaskMapper.updateById(taskDO);
    }

    // 3. 保存表进度（使用 insertOrUpdate 实现幂等）
    progressDOList.forEach(progress -> {
        progress.setImportId(taskDO.getId());
        meshTableProgressMapper.insertOrUpdate(progress);
    });

    // 4. 转换回领域对象
    return meshImportConverter.toDomain(taskDO, progressDOList);
}
```

**关键设计**：
- 使用 `insertOrUpdate` 实现幂等性（支持断点续传）
- 表进度保存在单独的表中，支持独立更新

**文件**：`persistence/repository/MeshImportRepositoryImpl.java`

##### findById(MeshImportId id)

根据 ID 查询导入任务。

**逻辑**：
```java
@Override
public Optional<MeshImportAggregate> findById(MeshImportId id) {
    MeshImportTaskDO taskDO = meshImportTaskMapper.selectById(id.value());
    if (taskDO == null) {
        return Optional.empty();
    }

    List<MeshTableProgressDO> progressDOList =
        meshTableProgressMapper.findByImportId(taskDO.getId());

    return Optional.of(meshImportConverter.toDomain(taskDO, progressDOList));
}
```

**文件**：`persistence/repository/MeshImportRepositoryImpl.java`

##### existsRunningTask()

检查是否有正在运行的导入任务。

**逻辑**：
```java
@Override
public boolean existsRunningTask() {
    LambdaQueryWrapper<MeshImportTaskDO> wrapper = new LambdaQueryWrapper<>();
    wrapper.in(MeshImportTaskDO::getStatus,
        MeshImportTaskStatus.PENDING,
        MeshImportTaskStatus.PROCESSING
    );

    return meshImportTaskMapper.selectCount(wrapper) > 0;
}
```

**文件**：`persistence/repository/MeshImportRepositoryImpl.java`

#### MeshDescriptorRepositoryImpl (MeSH 主题词仓储实现)

**实现接口**：`MeshDescriptorPort`

**核心方法**：

##### save(MeshDescriptorAggregate aggregate)

保存 MeSH 主题词聚合根（包括树形编号、入口术语、概念）。

**逻辑**：
```java
@Override
public MeshDescriptorAggregate save(MeshDescriptorAggregate aggregate) {
    // 1. 转换为 DO 对象
    MeshDescriptorDO descriptorDO = meshDescriptorConverter.toDescriptorDO(aggregate);

    // 2. 保存主题词
    meshDescriptorMapper.insert(descriptorDO);

    // 3. 保存关联数据
    saveMeshTreeNumbers(descriptorDO.getId(), aggregate.getTreeNumbers());
    saveMeshEntryTerms(descriptorDO.getId(), aggregate.getEntryTerms());
    saveMeshConcepts(descriptorDO.getId(), aggregate.getConcepts());

    return aggregate;
}
```

**优化**：使用批量插入提升性能。

**文件**：`persistence/repository/MeshDescriptorRepositoryImpl.java`

### 2. Mapper 接口

#### MeshTableProgressMapper

**核心方法**：

##### insertOrUpdate(MeshTableProgressDO progress)

插入或更新表进度（幂等操作）。

**实现**（XML）：
```xml
<insert id="insertOrUpdate" parameterType="MeshTableProgressDO">
    INSERT INTO cat_mesh_table_progress (
        import_id, table_name, status, total_count, processed_count, current_batch_index
    ) VALUES (
        #{importId}, #{tableName}, #{status}, #{totalCount}, #{processedCount}, #{currentBatchIndex}
    )
    ON DUPLICATE KEY UPDATE
        status = VALUES(status),
        processed_count = VALUES(processed_count),
        current_batch_index = VALUES(current_batch_index),
        updated_at = CURRENT_TIMESTAMP(6)
</insert>
```

**关键设计**：
- 使用 `ON DUPLICATE KEY UPDATE` 实现幂等性
- 仅更新变化的字段（status、processed_count、current_batch_index）

**文件**：`persistence/mapper/MeshTableProgressMapper.java` + `MeshTableProgressMapper.xml`

### 3. 对象转换器

#### MeshImportConverter (MapStruct 转换器)

**核心方法**：

##### toDomain(MeshImportTaskDO taskDO, List<MeshTableProgressDO> progressDOList)

将 DO 对象转换为领域对象。

**实现**：
```java
@Mapper(componentModel = "spring")
public interface MeshImportConverter {

    // DO → 领域对象
    @Mapping(target = "id", source = "taskDO.id", qualifiedByName = "toMeshImportId")
    @Mapping(target = "tableProgressList", source = "progressDOList")
    MeshImportAggregate toDomain(MeshImportTaskDO taskDO, List<MeshTableProgressDO> progressDOList);

    // 领域对象 → DO
    @Mapping(target = "id", source = "id.value")
    MeshImportTaskDO toTaskDO(MeshImportAggregate aggregate);

    List<MeshTableProgressDO> toProgressDOList(MeshImportAggregate aggregate);

    @Named("toMeshImportId")
    default MeshImportId toMeshImportId(Long id) {
        return MeshImportId.of(id);
    }
}
```

**关键设计**：
- 使用 `@Mapping` 自定义字段映射
- 使用 `@Named` 定义自定义转换方法
- 强类型 ID 自动转换

**文件**：`persistence/converter/MeshImportConverter.java`

### 4. XML 解析器

#### StaxXmlParserImpl (StAX 流式 XML 解析器)

**实现接口**：`XmlParserPort`

**核心方法**：

##### parseDescriptors(InputStream inputStream)

解析 MeSH XML 文件中的 Descriptor（主题词）。

**实现**：
```java
@Component
@RequiredArgsConstructor
public class StaxXmlParserImpl implements XmlParserPort {

    @Override
    public Stream<MeshDescriptorAggregate> parseDescriptors(InputStream inputStream) {
        try {
            XMLInputFactory factory = XMLInputFactory.newInstance();
            XMLStreamReader reader = factory.createXMLStreamReader(inputStream);

            return StreamSupport.stream(
                Spliterators.spliteratorUnknownSize(
                    new DescriptorIterator(reader),
                    Spliterator.ORDERED
                ),
                false
            ).onClose(() -> {
                try {
                    reader.close();
                } catch (XMLStreamException e) {
                    throw new RuntimeException(e);
                }
            });

        } catch (XMLStreamException e) {
            throw new RuntimeException("XML 解析失败", e);
        }
    }

    // 内部迭代器，逐个解析 Descriptor
    private static class DescriptorIterator implements Iterator<MeshDescriptorAggregate> {
        private final XMLStreamReader reader;
        private MeshDescriptorAggregate next;

        @Override
        public boolean hasNext() {
            if (next == null) {
                next = readNextDescriptor();
            }
            return next != null;
        }

        @Override
        public MeshDescriptorAggregate next() {
            if (next == null && !hasNext()) {
                throw new NoSuchElementException();
            }
            MeshDescriptorAggregate result = next;
            next = null;
            return result;
        }

        private MeshDescriptorAggregate readNextDescriptor() {
            try {
                while (reader.hasNext()) {
                    int event = reader.next();
                    if (event == XMLStreamConstants.START_ELEMENT
                        && "DescriptorRecord".equals(reader.getLocalName())) {
                        return parseDescriptorRecord();
                    }
                }
                return null;
            } catch (XMLStreamException e) {
                throw new RuntimeException(e);
            }
        }

        private MeshDescriptorAggregate parseDescriptorRecord() {
            // 解析 Descriptor 的详细逻辑
            // ...
        }
    }
}
```

**关键设计**：
- 使用 StAX（Streaming API for XML）实现流式解析
- 返回 `Stream<T>`，支持延迟加载，避免内存溢出
- 使用 `Iterator` 模式封装 XML 读取逻辑

**文件**：`parser/StaxXmlParserImpl.java`

### 5. 文件下载器

#### RestClientMeshFileDownloadImpl (基于 RestClient 的文件下载器)

**实现接口**：`MeshFileDownloadPort`

**核心方法**：

##### download(String sourceUrl)

从 NLM 官网下载 MeSH XML 文件。

**实现**：
```java
@Component
@RequiredArgsConstructor
public class RestClientMeshFileDownloadImpl implements MeshFileDownloadPort {

    private final RestClient restClient;

    @Override
    public File download(String sourceUrl) {
        try {
            File tempFile = Files.createTempFile("mesh-", ".xml").toFile();

            restClient.get()
                .uri(sourceUrl)
                .retrieve()
                .body((inputStream, httpHeaders) -> {
                    Files.copy(inputStream, tempFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                    return tempFile;
                });

            return tempFile;

        } catch (IOException e) {
            throw new RuntimeException("文件下载失败", e);
        }
    }

    @Override
    public boolean validateChecksum(File xmlFile, String expectedHash) {
        try (InputStream is = new FileInputStream(xmlFile)) {
            String actualHash = DigestUtils.md5Hex(is);
            return actualHash.equalsIgnoreCase(expectedHash);
        } catch (IOException e) {
            throw new RuntimeException("文件校验失败", e);
        }
    }
}
```

**关键设计**：
- 使用 Spring RestClient（JDK 21 HttpClient）
- 下载到临时文件（避免内存溢出）
- 支持 MD5 校验

**文件**：`download/RestClientMeshFileDownloadImpl.java`

---

## 📦 依赖关系

### 上游依赖

- `patra-catalog-domain`：领域模型和 Port 接口
- `patra-spring-boot-starter-mybatis`：MyBatis-Plus 自动配置
- `spring-web`：RestClient（仅此依赖，不引入 Web 容器）
- `MapStruct`：对象转换
- `Hutool`：工具类（DigestUtils）

### 下游消费者

- 无（Infrastructure 层位于最外层，不被其他模块依赖）

**依赖方向**：Domain ← App ← Infra（符合六边形架构）

---

## 💡 使用示例

### 示例 1：Repository 实现（实现 Port 接口）

```java
@Repository
@RequiredArgsConstructor
public class MeshImportRepositoryImpl implements MeshImportPort {

    private final MeshImportTaskMapper meshImportTaskMapper;
    private final MeshTableProgressMapper meshTableProgressMapper;
    private final MeshImportConverter meshImportConverter;

    @Override
    public MeshImportAggregate save(MeshImportAggregate aggregate) {
        // 1. 转换为 DO 对象
        MeshImportTaskDO taskDO = meshImportConverter.toTaskDO(aggregate);
        List<MeshTableProgressDO> progressDOList = meshImportConverter.toProgressDOList(aggregate);

        // 2. 保存到数据库
        if (taskDO.getId() == null) {
            meshImportTaskMapper.insert(taskDO);
        } else {
            meshImportTaskMapper.updateById(taskDO);
        }

        // 3. 保存表进度
        progressDOList.forEach(progress -> {
            progress.setImportId(taskDO.getId());
            meshTableProgressMapper.insertOrUpdate(progress);
        });

        // 4. 转换回领域对象
        return meshImportConverter.toDomain(taskDO, progressDOList);
    }

    @Override
    public Optional<MeshImportAggregate> findById(MeshImportId id) {
        MeshImportTaskDO taskDO = meshImportTaskMapper.selectById(id.value());
        if (taskDO == null) {
            return Optional.empty();
        }

        List<MeshTableProgressDO> progressDOList =
            meshTableProgressMapper.findByImportId(taskDO.getId());

        return Optional.of(meshImportConverter.toDomain(taskDO, progressDOList));
    }
}
```

### 示例 2：MapStruct 对象转换器

```java
@Mapper(componentModel = "spring")
public interface MeshImportConverter {

    // DO → 领域对象
    @Mapping(target = "id", source = "taskDO.id", qualifiedByName = "toMeshImportId")
    @Mapping(target = "tableProgressList", source = "progressDOList")
    MeshImportAggregate toDomain(MeshImportTaskDO taskDO, List<MeshTableProgressDO> progressDOList);

    // 领域对象 → DO
    @Mapping(target = "id", source = "id.value")
    MeshImportTaskDO toTaskDO(MeshImportAggregate aggregate);

    List<MeshTableProgressDO> toProgressDOList(MeshImportAggregate aggregate);

    @Named("toMeshImportId")
    default MeshImportId toMeshImportId(Long id) {
        return MeshImportId.of(id);
    }
}
```

### 示例 3：StAX 流式 XML 解析器

```java
@Component
@RequiredArgsConstructor
public class StaxXmlParserImpl implements XmlParserPort {

    @Override
    public Stream<MeshDescriptorAggregate> parseDescriptors(InputStream inputStream) {
        XMLInputFactory factory = XMLInputFactory.newInstance();
        XMLStreamReader reader = factory.createXMLStreamReader(inputStream);

        return StreamSupport.stream(
            Spliterators.spliteratorUnknownSize(
                new DescriptorIterator(reader),
                Spliterator.ORDERED
            ),
            false
        ).onClose(() -> {
            try {
                reader.close();
            } catch (XMLStreamException e) {
                throw new RuntimeException(e);
            }
        });
    }

    // 内部迭代器，逐个解析 Descriptor
    private static class DescriptorIterator implements Iterator<MeshDescriptorAggregate> {
        // ... 实现细节
    }
}
```

### 示例 4：RestClient 文件下载器

```java
@Component
@RequiredArgsConstructor
public class RestClientMeshFileDownloadImpl implements MeshFileDownloadPort {

    private final RestClient restClient;

    @Override
    public File download(String sourceUrl) {
        File tempFile = Files.createTempFile("mesh-", ".xml").toFile();

        restClient.get()
            .uri(sourceUrl)
            .retrieve()
            .body((inputStream, httpHeaders) -> {
                Files.copy(inputStream, tempFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                return tempFile;
            });

        return tempFile;
    }

    @Override
    public boolean validateChecksum(File xmlFile, String expectedHash) {
        String actualHash = DigestUtils.md5Hex(new FileInputStream(xmlFile));
        return actualHash.equalsIgnoreCase(expectedHash);
    }
}
```

---

## 🎨 设计模式

### 1. 适配器模式 (Adapter Pattern)

**核心思想**：Infrastructure 层实现 Domain 层定义的 Port 接口，适配外部技术实现。

**示例**：
- `MeshImportRepositoryImpl` 实现 `MeshImportPort`
- `StaxXmlParserImpl` 实现 `XmlParserPort`
- `RestClientMeshFileDownloadImpl` 实现 `MeshFileDownloadPort`

### 2. 转换器模式 (Converter Pattern)

**核心思想**：使用 MapStruct 自动生成对象转换代码，避免手写转换逻辑。

**特点**：
- 编译时生成转换代码（零运行时开销）
- 支持自定义转换方法
- 类型安全

### 3. 迭代器模式 (Iterator Pattern)

**核心思想**：使用迭代器封装 XML 读取逻辑，支持流式处理。

**特点**：
- 延迟加载（只在需要时解析下一个元素）
- 内存友好（不需要一次性加载所有数据）

---

## 🧪 测试覆盖

| 测试类型 | 覆盖率目标 | 当前覆盖率 |
|---------|-----------|-----------|
| 单元测试 | ≥70% | [待测试运行后更新] |
| 集成测试 | 核心仓储 100% | [待测试运行后更新] |

**关键测试类**：
- `MeshImportRepositoryImplIT` - 导入任务仓储集成测试
- `MeshDescriptorRepositoryImplIT` - 主题词仓储集成测试
- `MeshBatchDetailRepositoryImplIT` - 批次详情仓储集成测试
- `StaxXmlParserImplTest` - XML 解析器单元测试
- `RestClientMeshFileDownloadImplTest` - 文件下载器单元测试
- `MeshImportConverterTest` - 对象转换器单元测试

---

## 🛠️ 技术栈

- **Spring Boot**：3.5.7
- **MyBatis-Plus**：3.5.12
- **MapStruct**：1.5.x
- **Spring Web**：6.2.x（仅用于 RestClient）
- **HikariCP**：连接池
- **Hutool**：工具类

---

## 📚 相关文档

- [patra-catalog 模块总览](../README.md)
- [patra-catalog-domain 领域层文档](../patra-catalog-domain/README.md)
- [patra-catalog-app 应用层文档](../patra-catalog-app/README.md)
- [MeSH 导入功能规格](../../specs/001-mesh-data-import/spec.md)
- [MyBatis-Plus 官方文档](https://baomidou.com/)
- [MapStruct 官方文档](https://mapstruct.org/)

---

**最后更新**：2025-11-22
**Maven 坐标**：`com.patra:patra-catalog-infra:0.2.0-SNAPSHOT`
**作者**：Patra Team
