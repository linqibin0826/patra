# Infrastructure 层测试模板

基础设施层测试主要包括 Repository 集成测试和 Converter 单元测试。

## Repository 测试 {#repository}

### TestContainers 配置

```java
package com.patra.{service}.infra.persistence.repository;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest
@Testcontainers
@Transactional  // 自动回滚测试数据
@DisplayName("ProvenanceRepository 集成测试")
class ProvenanceRepositoryIntegrationTest {

    @Container
    static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0.36")
        .withDatabaseName("patra_test")
        .withUsername("root")
        .withPassword("123456")
        .withReuse(true);  // 容器重用以提高测试速度

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", mysql::getJdbcUrl);
        registry.add("spring.datasource.username", mysql::getUsername);
        registry.add("spring.datasource.password", mysql::getPassword);
        registry.add("spring.datasource.driver-class-name",
            () -> "com.mysql.cj.jdbc.Driver");
    }

    @Autowired
    private ProvenanceRepositoryImpl repository;

    @Autowired
    private ProvenanceMapper mapper;  // MyBatis-Plus Mapper

    private Provenance provenance;

    @BeforeEach
    void setUp() {
        provenance = Provenance.builder()
            .code("PUBMED")
            .name("PubMed Database")
            .config("{}")
            .active(true)
            .build();
    }

    @Test
    @DisplayName("应该保存并通过 ID 查找")
    void shouldSaveAndFindById() {
        // Act
        Provenance saved = repository.save(provenance);
        Provenance found = repository.findById(saved.getId()).orElseThrow();

        // Assert
        assertThat(saved.getId()).isNotNull();
        assertThat(found.getCode()).isEqualTo("PUBMED");
        assertThat(found.getName()).isEqualTo("PubMed Database");
    }

    @Test
    @DisplayName("应该通过 Code 查找唯一记录")
    void shouldFindByCode() {
        // Arrange
        repository.save(provenance);

        // Act
        Provenance found = repository.findByCode("PUBMED").orElseThrow();

        // Assert
        assertThat(found.getCode()).isEqualTo("PUBMED");
    }

    @Test
    @DisplayName("应该处理唯一约束违反")
    void shouldHandleUniqueConstraintViolation() {
        // Arrange
        repository.save(provenance);
        Provenance duplicate = Provenance.builder()
            .code("PUBMED")  // 相同的 code
            .name("Duplicate")
            .build();

        // Act & Assert
        assertThatThrownBy(() -> repository.save(duplicate))
            .isInstanceOf(DataIntegrityViolationException.class)
            .hasMessageContaining("Duplicate entry");
    }

    @Test
    @DisplayName("应该使用 LambdaQueryWrapper 进行复杂查询")
    void shouldQueryWithLambdaWrapper() {
        // Arrange
        repository.save(provenance);
        repository.save(Provenance.builder()
            .code("EPMC")
            .name("Europe PMC")
            .active(false)
            .build());

        // Act
        List<Provenance> activeProvenances = repository.findAllActive();

        // Assert
        assertThat(activeProvenances)
            .hasSize(1)
            .extracting(Provenance::getCode)
            .containsOnly("PUBMED");
    }

    @Test
    @DisplayName("应该分页查询")
    void shouldPaginate() {
        // Arrange
        for (int i = 0; i < 15; i++) {
            repository.save(Provenance.builder()
                .code("PROV_" + i)
                .name("Provenance " + i)
                .build());
        }

        // Act
        Page<Provenance> page = repository.findPage(
            PageRequest.of(0, 10, Sort.by("code"))
        );

        // Assert
        assertThat(page.getTotalElements()).isEqualTo(15);
        assertThat(page.getTotalPages()).isEqualTo(2);
        assertThat(page.getContent()).hasSize(10);
        assertThat(page.getContent().get(0).getCode()).isEqualTo("PROV_0");
    }

    @Test
    @DisplayName("应该批量插入")
    void shouldBatchInsert() {
        // Arrange
        List<Provenance> batch = IntStream.range(0, 100)
            .mapToObj(i -> Provenance.builder()
                .code("BATCH_" + i)
                .name("Batch " + i)
                .build())
            .toList();

        // Act
        repository.saveAll(batch);

        // Assert
        long count = repository.count();
        assertThat(count).isEqualTo(100);
    }

    @Test
    @DisplayName("应该处理乐观锁")
    void shouldHandleOptimisticLocking() {
        // Arrange
        Provenance saved = repository.save(provenance);
        Long originalVersion = saved.getVersion();

        // Act - 第一次更新
        saved.setName("Updated Name");
        Provenance updated = repository.save(saved);

        // Assert
        assertThat(updated.getVersion()).isEqualTo(originalVersion + 1);

        // Act & Assert - 使用旧版本更新应该失败
        saved.setVersion(originalVersion);  // 设置旧版本号
        saved.setName("Another Update");

        assertThatThrownBy(() -> repository.save(saved))
            .isInstanceOf(OptimisticLockingFailureException.class);
    }
}
```

### 复杂查询测试

```java
@Test
@DisplayName("应该执行复杂关联查询")
void shouldExecuteComplexJoinQuery() {
    // Arrange
    Plan plan = createPlanWithSlices();
    planRepository.save(plan);

    // Act - 使用自定义 XML Mapper 方法
    PlanDetailVO detail = planMapper.selectDetailWithSlices(plan.getId());

    // Assert
    assertThat(detail).isNotNull();
    assertThat(detail.getPlanId()).isEqualTo(plan.getId());
    assertThat(detail.getSlices()).hasSize(plan.getSlices().size());
    assertThat(detail.getTotalTasks()).isEqualTo(
        plan.getSlices().stream()
            .mapToLong(s -> s.getTasks().size())
            .sum()
    );
}

@Test
@DisplayName("应该使用动态 SQL 条件查询")
void shouldQueryWithDynamicConditions() {
    // Arrange
    PlanQueryDTO query = PlanQueryDTO.builder()
        .provenanceCode("PUBMED")
        .status(PlanStatus.RUNNING)
        .createdAfter(LocalDateTime.now().minusDays(7))
        .build();

    // Act
    List<Plan> plans = planRepository.findByConditions(query);

    // Assert
    assertThat(plans).allMatch(p ->
        p.getProvenanceCode().equals("PUBMED") &&
        p.getStatus() == PlanStatus.RUNNING &&
        p.getCreatedAt().isAfter(query.getCreatedAfter())
    );
}
```

## Converter 测试 {#converter}

### MapStruct Converter 测试

```java
package com.patra.{service}.infra.converter;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import java.time.LocalDateTime;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest
@DisplayName("ProvenanceConverter 转换器测试")
class ProvenanceConverterTest {

    @Autowired
    private ProvenanceConverter converter;

    private Provenance domain;
    private ProvenanceDO dataObject;

    @BeforeEach
    void setUp() {
        domain = Provenance.builder()
            .id(new ProvenanceId(1L))
            .code("PUBMED")
            .name("PubMed Database")
            .config("{\"key\":\"value\"}")
            .active(true)
            .createdAt(LocalDateTime.now())
            .build();

        dataObject = new ProvenanceDO();
        dataObject.setId(1L);
        dataObject.setCode("PUBMED");
        dataObject.setName("PubMed Database");
        dataObject.setConfig("{\"key\":\"value\"}");
        dataObject.setActive(1);  // tinyint(1)
        dataObject.setCreatedAt(LocalDateTime.now());
    }

    @Test
    @DisplayName("应该转换 Domain → DO")
    void shouldConvertDomainToDataObject() {
        // Act
        ProvenanceDO result = converter.toDataObject(domain);

        // Assert
        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo(domain.getId().getValue());
        assertThat(result.getCode()).isEqualTo(domain.getCode());
        assertThat(result.getName()).isEqualTo(domain.getName());
        assertThat(result.getConfig()).isEqualTo(domain.getConfig());
        assertThat(result.getActive()).isEqualTo(1);  // true → 1
    }

    @Test
    @DisplayName("应该转换 DO → Domain")
    void shouldConvertDataObjectToDomain() {
        // Act
        Provenance result = converter.toDomain(dataObject);

        // Assert
        assertThat(result).isNotNull();
        assertThat(result.getId().getValue()).isEqualTo(dataObject.getId());
        assertThat(result.getCode()).isEqualTo(dataObject.getCode());
        assertThat(result.isActive()).isTrue();  // 1 → true
    }

    @Test
    @DisplayName("应该保持双向转换一致性")
    void shouldMaintainRoundTripConsistency() {
        // Act
        ProvenanceDO convertedDO = converter.toDataObject(domain);
        Provenance convertedBack = converter.toDomain(convertedDO);

        // Assert
        assertThat(convertedBack).usingRecursiveComparison()
            .ignoringFields("createdAt", "updatedAt")  // 时间戳可能有微小差异
            .isEqualTo(domain);
    }

    @Test
    @DisplayName("应该处理 null 值")
    void shouldHandleNullValues() {
        // Act & Assert - null input
        assertThat(converter.toDataObject(null)).isNull();
        assertThat(converter.toDomain(null)).isNull();

        // Arrange - null fields
        domain = Provenance.builder()
            .id(new ProvenanceId(1L))
            .code("TEST")
            .name(null)  // null name
            .config(null)  // null config
            .build();

        // Act
        ProvenanceDO result = converter.toDataObject(domain);

        // Assert
        assertThat(result.getName()).isNull();
        assertThat(result.getConfig()).isNull();
    }

    @Test
    @DisplayName("应该转换嵌套对象")
    void shouldConvertNestedObjects() {
        // Arrange
        Plan plan = Plan.builder()
            .id(new PlanId(1L))
            .provenance(domain)  // 嵌套的 Provenance
            .slices(List.of(
                createSlice(1L),
                createSlice(2L)
            ))
            .build();

        // Act
        PlanDO planDO = planConverter.toDataObject(plan);

        // Assert
        assertThat(planDO.getProvenanceId()).isEqualTo(1L);
        // MapStruct 应该处理嵌套转换
        assertThat(planDO.getSlices()).hasSize(2);
    }

    @Test
    @DisplayName("应该转换集合")
    void shouldConvertCollections() {
        // Arrange
        List<Provenance> domains = List.of(
            createProvenance("PUBMED"),
            createProvenance("EPMC"),
            createProvenance("ARXIV")
        );

        // Act
        List<ProvenanceDO> dataObjects = converter.toDataObjectList(domains);

        // Assert
        assertThat(dataObjects).hasSize(3);
        assertThat(dataObjects).extracting(ProvenanceDO::getCode)
            .containsExactly("PUBMED", "EPMC", "ARXIV");
    }

    @Test
    @DisplayName("应该使用自定义映射方法")
    void shouldUseCustomMappingMethods() {
        // 当 MapStruct 需要自定义转换逻辑时
        // 例如：JSON 字符串 ↔ 对象

        // Arrange
        domain.setConfig("{\"batchSize\":100,\"timeout\":30}");

        // Act
        ProvenanceDO result = converter.toDataObject(domain);

        // Assert
        // 假设有自定义方法处理 JSON
        assertThat(result.getConfig()).isEqualTo(domain.getConfig());

        // 如果有自定义解析
        Map<String, Object> configMap = JsonUtils.parseJson(result.getConfig());
        assertThat(configMap).containsEntry("batchSize", 100);
    }

    @Test
    @DisplayName("应该映射枚举类型")
    void shouldMapEnumTypes() {
        // Arrange
        Task task = Task.builder()
            .status(TaskStatus.RUNNING)  // Domain 枚举
            .build();

        // Act
        TaskDO taskDO = taskConverter.toDataObject(task);

        // Assert
        assertThat(taskDO.getStatus()).isEqualTo("RUNNING");  // String

        // 反向转换
        Task converted = taskConverter.toDomain(taskDO);
        assertThat(converted.getStatus()).isEqualTo(TaskStatus.RUNNING);
    }
}
```

## 测试数据工厂

```java
public class TestDataFactory {

    public static ProvenanceDO createProvenanceDO(String code) {
        ProvenanceDO provenance = new ProvenanceDO();
        provenance.setCode(code);
        provenance.setName(code + " Database");
        provenance.setConfig("{}");
        provenance.setActive(1);
        provenance.setCreatedAt(LocalDateTime.now());
        provenance.setUpdatedAt(LocalDateTime.now());
        return provenance;
    }

    public static PlanDO createPlanDO() {
        PlanDO plan = new PlanDO();
        plan.setPlanNo(generatePlanNo());
        plan.setProvenanceId(1L);
        plan.setStatus("DRAFT");
        plan.setConfig("{}");
        plan.setCreatedAt(LocalDateTime.now());
        return plan;
    }

    private static String generatePlanNo() {
        return "PLAN:" + System.currentTimeMillis();
    }
}
```

## 性能测试

```java
@Test
@DisplayName("应该高效处理大批量数据")
void shouldHandleLargeBatchEfficiently() {
    // Arrange
    List<Provenance> largeBatch = IntStream.range(0, 10000)
        .mapToObj(i -> createProvenance("BATCH_" + i))
        .toList();

    // Act
    long startTime = System.currentTimeMillis();
    repository.saveAllBatch(largeBatch, 500);  // 批量大小 500
    long duration = System.currentTimeMillis() - startTime;

    // Assert
    assertThat(duration).isLessThan(5000);  // 应该在 5 秒内完成
    assertThat(repository.count()).isEqualTo(10000);
}
```