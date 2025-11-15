package com.patra.ingest.domain.model.vo.batch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.patra.common.enums.ProvenanceCode;
import com.patra.common.model.DataType;
import com.patra.ingest.domain.model.snapshot.ProvenanceConfigSnapshot;
import com.patra.ingest.domain.model.vo.execution.ExecutionContext;
import com.patra.ingest.domain.model.vo.fetch.FetchMetadata;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * {@link BatchSchedule} 的单元测试。
 *
 * <p>测试覆盖：
 *
 * <ul>
 *   <li>成功构造场景（有效字段、空列表、多批次）
 *   <li>验证失败场景（null batches、null context、null fetchMetadata）
 *   <li>工厂方法（empty、single）
 *   <li>业务方法（hasBatches、totalBatches、exceedsLimit）
 *   <li>Record 语义（equals、hashCode、toString、访问器）
 *   <li>不可变性验证
 * </ul>
 *
 * @author linqibin
 * @since 0.2.0
 */
@DisplayName("BatchSchedule 批次调度结果值对象测试")
class BatchScheduleTest {

  // ==================== 测试工具方法 ====================

  /** 创建一个测试用 Batch 实例 */
  private Batch createTestBatch(int batchNo, int offset, int limit) {
    return new Batch(batchNo, "test query " + batchNo, offset, limit);
  }

  /** 创建一个测试用 ProvenanceInfo 实例 */
  private ProvenanceConfigSnapshot.ProvenanceInfo createTestProvenanceInfo() {
    return new ProvenanceConfigSnapshot.ProvenanceInfo(
        1L, // id
        "PUBMED", // code
        "PubMed", // name
        "https://eutils.ncbi.nlm.nih.gov/entrez/eutils", // baseUrlDefault
        "UTC", // timezoneDefault
        "https://www.ncbi.nlm.nih.gov/books/NBK25501/", // docsUrl
        true, // active
        "ACTIVE" // lifecycleStatusCode
        );
  }

  /** 创建一个测试用 PaginationConfig 实例 */
  private ProvenanceConfigSnapshot.PaginationConfig createTestPaginationConfig() {
    return new ProvenanceConfigSnapshot.PaginationConfig(
        1L, // id
        1L, // provenanceId
        "FETCH", // operationType
        Instant.now().minusSeconds(86400), // effectiveFrom
        null, // effectiveTo (长期有效)
        "PAGE_NUMBER", // paginationModeCode
        500, // pageSizeValue
        10000, // maxPagesPerExecution
        "sort", // sortFieldParamName
        1 // sortingDirection (ASC)
        );
  }

  /** 创建一个测试用 ProvenanceConfigSnapshot 实例 */
  private ProvenanceConfigSnapshot createTestConfigSnapshot() {
    return new ProvenanceConfigSnapshot(
        createTestProvenanceInfo(), // provenance
        null, // windowOffset
        createTestPaginationConfig(), // pagination
        null, // http
        null, // batching
        null, // retry
        null // rateLimit
        );
  }

  /** 创建一个测试用 ExecutionContext 实例 */
  private ExecutionContext createTestContext() {
    return new ExecutionContext(
        1L, // taskId
        1L, // runId
        1L, // planId
        1L, // sliceId
        1L, // scheduleInstanceId
        ProvenanceCode.PUBMED, // provenanceCode
        "FETCH", // operationCode
        DataType.LITERATURE, // dataType
        createTestConfigSnapshot(), // configSnapshot (使用真实的 ProvenanceConfigSnapshot)
        "test-hash", // exprHash
        "test query", // compiledQuery
        JsonNodeFactory.instance.objectNode(), // compiledParams
        "test normalized", // normalizedExpression
        null // windowSpec
        );
  }

  /** 创建一个测试用 FetchMetadata 实例 */
  private FetchMetadata createTestMetadata(int totalRecords) {
    return new FetchMetadata() {
      @Override
      public int totalRecords() {
        return totalRecords;
      }

      @Override
      public ProvenanceCode provenanceCode() {
        return ProvenanceCode.PUBMED;
      }

      @Override
      public boolean hasStateToken() {
        return false;
      }

      @Override
      public Optional<Map<String, String>> stateToken() {
        return Optional.empty();
      }
    };
  }

  /** 创建一个带状态令牌的 FetchMetadata */
  private FetchMetadata createMetadataWithToken(int totalRecords, Map<String, String> token) {
    return new FetchMetadata() {
      @Override
      public int totalRecords() {
        return totalRecords;
      }

      @Override
      public ProvenanceCode provenanceCode() {
        return ProvenanceCode.PUBMED;
      }

      @Override
      public boolean hasStateToken() {
        return true;
      }

      @Override
      public Optional<Map<String, String>> stateToken() {
        return Optional.of(token);
      }
    };
  }

  // ==================== 成功构造测试 ====================

  @Nested
  @DisplayName("成功构造场景")
  class SuccessfulConstruction {

    @Test
    @DisplayName("应该成功创建包含单个批次的计划")
    void shouldCreatePlanWithSingleBatch() {
      // Given: 准备单个批次、上下文和元数据
      Batch batch = createTestBatch(1, 0, 500);
      List<Batch> batches = List.of(batch);
      ExecutionContext ctx = createTestContext();
      FetchMetadata metadata = createTestMetadata(500);

      // When: 创建批次调度
      BatchSchedule plan = new BatchSchedule(batches, ctx, metadata);

      // Then: 验证所有字段
      assertThat(plan.batches()).hasSize(1).containsExactly(batch);
      assertThat(plan.context()).isEqualTo(ctx);
      assertThat(plan.fetchMetadata()).isEqualTo(metadata);
      assertThat(plan.totalBatches()).isEqualTo(1);
      assertThat(plan.hasBatches()).isTrue();
      assertThat(plan.exceedsLimit()).isFalse();
    }

    @Test
    @DisplayName("应该成功创建空批次列表的计划")
    void shouldCreatePlanWithEmptyBatchList() {
      // Given: 空批次列表、上下文和元数据
      List<Batch> emptyBatches = List.of();
      ExecutionContext ctx = createTestContext();
      FetchMetadata metadata = createTestMetadata(0);

      // When: 创建批次调度
      BatchSchedule plan = new BatchSchedule(emptyBatches, ctx, metadata);

      // Then: 验证空列表
      assertThat(plan.batches()).isEmpty();
      assertThat(plan.context()).isEqualTo(ctx);
      assertThat(plan.fetchMetadata()).isEqualTo(metadata);
      assertThat(plan.totalBatches()).isZero();
      assertThat(plan.hasBatches()).isFalse();
      assertThat(plan.exceedsLimit()).isFalse();
    }

    @Test
    @DisplayName("应该成功创建包含多个批次的计划")
    void shouldCreatePlanWithMultipleBatches() {
      // Given: 多个批次、上下文和元数据
      List<Batch> batches =
          List.of(
              createTestBatch(1, 0, 500),
              createTestBatch(2, 500, 500),
              createTestBatch(3, 1000, 500));
      ExecutionContext ctx = createTestContext();
      FetchMetadata metadata = createTestMetadata(1500);

      // When: 创建批次调度
      BatchSchedule plan = new BatchSchedule(batches, ctx, metadata);

      // Then: 验证批次列表
      assertThat(plan.batches()).hasSize(3);
      assertThat(plan.context()).isEqualTo(ctx);
      assertThat(plan.fetchMetadata()).isEqualTo(metadata);
      assertThat(plan.totalBatches()).isEqualTo(3);
      assertThat(plan.hasBatches()).isTrue();
    }
  }

  // ==================== 验证失败测试 ====================

  @Nested
  @DisplayName("验证失败场景")
  class ValidationFailures {

    @Test
    @DisplayName("当 batches 为 null 时应该抛出 IllegalArgumentException")
    void shouldThrowExceptionWhenBatchesIsNull() {
      // Given: null 批次列表、有效上下文和元数据
      List<Batch> nullBatches = null;
      ExecutionContext ctx = createTestContext();
      FetchMetadata metadata = createTestMetadata(0);

      // When & Then: 验证异常
      assertThatThrownBy(() -> new BatchSchedule(nullBatches, ctx, metadata))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("batches must not be null");
    }

    @Test
    @DisplayName("当 context 为 null 时应该抛出 IllegalArgumentException")
    void shouldThrowExceptionWhenContextIsNull() {
      // Given: 有效批次列表、null 上下文和有效元数据
      List<Batch> batches = List.of(createTestBatch(1, 0, 500));
      ExecutionContext nullCtx = null;
      FetchMetadata metadata = createTestMetadata(500);

      // When & Then: 验证异常
      assertThatThrownBy(() -> new BatchSchedule(batches, nullCtx, metadata))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("context must not be null");
    }

    @Test
    @DisplayName("当 fetchMetadata 为 null 时应该抛出 IllegalArgumentException")
    void shouldThrowExceptionWhenFetchMetadataIsNull() {
      // Given: 有效批次列表、有效上下文和 null 元数据
      List<Batch> batches = List.of(createTestBatch(1, 0, 500));
      ExecutionContext ctx = createTestContext();
      FetchMetadata nullMetadata = null;

      // When & Then: 验证异常
      assertThatThrownBy(() -> new BatchSchedule(batches, ctx, nullMetadata))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("fetchMetadata must not be null");
    }
  }

  // ==================== 工厂方法测试 ====================

  @Nested
  @DisplayName("工厂方法")
  class FactoryMethods {

    @Test
    @DisplayName("empty() 应该创建空批次调度")
    void emptyShouldCreateEmptyPlan() {
      // Given: 准备上下文和元数据
      ExecutionContext ctx = createTestContext();
      FetchMetadata metadata = createTestMetadata(0);

      // When: 调用 empty 工厂方法
      BatchSchedule plan = BatchSchedule.empty(ctx, metadata);

      // Then: 验证空计划
      assertThat(plan.batches()).isEmpty();
      assertThat(plan.context()).isEqualTo(ctx);
      assertThat(plan.fetchMetadata()).isEqualTo(metadata);
      assertThat(plan.totalBatches()).isZero();
      assertThat(plan.hasBatches()).isFalse();
      assertThat(plan.exceedsLimit()).isFalse();
    }

    @Test
    @DisplayName("single() 应该创建包含单个批次的计划")
    void singleShouldCreatePlanWithOneBatch() {
      // Given: 准备单个批次、上下文和元数据
      Batch batch = createTestBatch(1, 0, 500);
      ExecutionContext ctx = createTestContext();
      FetchMetadata metadata = createTestMetadata(500);

      // When: 调用 single 工厂方法
      BatchSchedule plan = BatchSchedule.single(batch, ctx, metadata);

      // Then: 验证单批次调度
      assertThat(plan.batches()).hasSize(1).containsExactly(batch);
      assertThat(plan.context()).isEqualTo(ctx);
      assertThat(plan.fetchMetadata()).isEqualTo(metadata);
      assertThat(plan.totalBatches()).isEqualTo(1);
      assertThat(plan.hasBatches()).isTrue();
      assertThat(plan.exceedsLimit()).isFalse();
    }

    @Test
    @DisplayName("empty() 和 single() 应该创建不同的实例")
    void factoryMethodsShouldCreateDistinctInstances() {
      // Given: 准备上下文和元数据（共享实例以便 equals 比较）
      ExecutionContext ctx = createTestContext();
      FetchMetadata emptyMetadata = createTestMetadata(0);
      FetchMetadata metadata = createTestMetadata(500);

      // When: 多次调用工厂方法
      BatchSchedule empty1 = BatchSchedule.empty(ctx, emptyMetadata);
      BatchSchedule empty2 = BatchSchedule.empty(ctx, emptyMetadata);
      BatchSchedule single1 = BatchSchedule.single(createTestBatch(1, 0, 500), ctx, metadata);
      BatchSchedule single2 = BatchSchedule.single(createTestBatch(1, 0, 500), ctx, metadata);

      // Then: 验证不同实例（但值相等）
      assertThat(empty1).isNotSameAs(empty2).isEqualTo(empty2);
      assertThat(single1).isNotSameAs(single2).isEqualTo(single2);
    }
  }

  // ==================== 业务方法测试 ====================

  @Nested
  @DisplayName("业务方法")
  class BusinessMethods {

    @Test
    @DisplayName("hasBatches() 应该在包含批次时返回 true")
    void hasBatchesShouldReturnTrueWhenBatchesExist() {
      // Given: 包含批次的计划
      List<Batch> batches = List.of(createTestBatch(1, 0, 500));
      ExecutionContext ctx = createTestContext();
      FetchMetadata metadata = createTestMetadata(500);
      BatchSchedule plan = new BatchSchedule(batches, ctx, metadata);

      // When & Then: 验证有批次
      assertThat(plan.hasBatches()).isTrue();
    }

    @Test
    @DisplayName("hasBatches() 应该在空列表时返回 false")
    void hasBatchesShouldReturnFalseWhenEmpty() {
      // Given: 空批次调度
      ExecutionContext ctx = createTestContext();
      FetchMetadata metadata = createTestMetadata(0);
      BatchSchedule plan = BatchSchedule.empty(ctx, metadata);

      // When & Then: 验证无批次
      assertThat(plan.hasBatches()).isFalse();
    }

    @Test
    @DisplayName("hasBatches() 应该在包含多个批次时返回 true")
    void hasBatchesShouldReturnTrueWithMultipleBatches() {
      // Given: 多批次调度
      List<Batch> batches = List.of(createTestBatch(1, 0, 500), createTestBatch(2, 500, 500));
      ExecutionContext ctx = createTestContext();
      FetchMetadata metadata = createTestMetadata(1000);
      BatchSchedule plan = new BatchSchedule(batches, ctx, metadata);

      // When & Then: 验证有批次
      assertThat(plan.hasBatches()).isTrue();
    }

    @Test
    @DisplayName("totalBatches() 应该返回批次列表的大小")
    void totalBatchesShouldReturnBatchesSize() {
      // Given: 包含 3 个批次的计划
      List<Batch> batches =
          List.of(
              createTestBatch(1, 0, 500),
              createTestBatch(2, 500, 500),
              createTestBatch(3, 1000, 500));
      ExecutionContext ctx = createTestContext();
      FetchMetadata metadata = createTestMetadata(1500);
      BatchSchedule plan = new BatchSchedule(batches, ctx, metadata);

      // When & Then: 验证总数
      assertThat(plan.totalBatches()).isEqualTo(3);
      assertThat(plan.totalBatches()).isEqualTo(plan.batches().size());
    }

    @Test
    @DisplayName("exceedsLimit() 应该返回 false（当前默认实现）")
    void exceedsLimitShouldReturnFalseByDefault() {
      // Given: 任意批次调度
      List<Batch> batches = List.of(createTestBatch(1, 0, 500));
      ExecutionContext ctx = createTestContext();
      FetchMetadata metadata = createTestMetadata(500);
      BatchSchedule plan = new BatchSchedule(batches, ctx, metadata);

      // When & Then: 验证默认不超限
      assertThat(plan.exceedsLimit()).isFalse();
    }
  }

  // ==================== Record 语义测试 ====================

  @Nested
  @DisplayName("Record 语义")
  class RecordSemantics {

    @Test
    @DisplayName("equals() 应该对相同值返回 true")
    void equalsShouldReturnTrueForSameValues() {
      // Given: 两个相同值的计划
      Batch batch = createTestBatch(1, 0, 500);
      List<Batch> batches = List.of(batch);
      ExecutionContext ctx = createTestContext();
      FetchMetadata metadata = createTestMetadata(500);
      BatchSchedule plan1 = new BatchSchedule(batches, ctx, metadata);
      BatchSchedule plan2 = new BatchSchedule(batches, ctx, metadata);

      // When & Then: 验证相等性
      assertThat(plan1).isEqualTo(plan2);
      assertThat(plan1.equals(plan2)).isTrue();
    }

    @Test
    @DisplayName("equals() 应该对不同批次列表返回 false")
    void equalsShouldReturnFalseForDifferentBatches() {
      // Given: 不同批次列表的计划
      ExecutionContext ctx = createTestContext();
      FetchMetadata metadata = createTestMetadata(500);
      BatchSchedule plan1 = new BatchSchedule(List.of(createTestBatch(1, 0, 500)), ctx, metadata);
      BatchSchedule plan2 = new BatchSchedule(List.of(createTestBatch(2, 0, 500)), ctx, metadata);

      // When & Then: 验证不相等
      assertThat(plan1).isNotEqualTo(plan2);
    }

    @Test
    @DisplayName("equals() 应该对不同上下文返回 false")
    void equalsShouldReturnFalseForDifferentContext() {
      // Given: 不同上下文的计划
      List<Batch> batches = List.of(createTestBatch(1, 0, 500));
      ExecutionContext ctx1 = createTestContext();
      ExecutionContext ctx2 =
          new ExecutionContext(
              2L, // 不同的 taskId
              1L,
              1L,
              1L,
              1L,
              ProvenanceCode.PUBMED,
              "FETCH",
              DataType.LITERATURE,
              null,
              "test-hash",
              "test query",
              JsonNodeFactory.instance.objectNode(),
              "test normalized",
              null);
      FetchMetadata metadata = createTestMetadata(500);
      BatchSchedule plan1 = new BatchSchedule(batches, ctx1, metadata);
      BatchSchedule plan2 = new BatchSchedule(batches, ctx2, metadata);

      // When & Then: 验证不相等
      assertThat(plan1).isNotEqualTo(plan2);
    }

    @Test
    @DisplayName("equals() 应该对 null 返回 false")
    void equalsShouldReturnFalseForNull() {
      // Given: 一个有效计划
      ExecutionContext ctx = createTestContext();
      FetchMetadata metadata = createTestMetadata(0);
      BatchSchedule plan = BatchSchedule.empty(ctx, metadata);

      // When & Then: 验证与 null 不相等
      assertThat(plan.equals(null)).isFalse();
    }

    @Test
    @DisplayName("equals() 应该对不同类型返回 false")
    void equalsShouldReturnFalseForDifferentType() {
      // Given: 一个有效计划和不同类型对象
      ExecutionContext ctx = createTestContext();
      FetchMetadata metadata = createTestMetadata(0);
      BatchSchedule plan = BatchSchedule.empty(ctx, metadata);
      Object other = new Object();

      // When & Then: 验证与不同类型不相等
      assertThat(plan.equals(other)).isFalse();
    }

    @Test
    @DisplayName("equals() 应该对自身返回 true")
    void equalsShouldReturnTrueForSelf() {
      // Given: 一个有效计划
      ExecutionContext ctx = createTestContext();
      FetchMetadata metadata = createTestMetadata(0);
      BatchSchedule plan = BatchSchedule.empty(ctx, metadata);

      // When & Then: 验证自反性
      assertThat(plan).isEqualTo(plan);
    }

    @Test
    @DisplayName("hashCode() 应该对相同值返回相同哈希码")
    void hashCodeShouldReturnSameValueForEqualObjects() {
      // Given: 两个相同值的计划
      Batch batch = createTestBatch(1, 0, 500);
      List<Batch> batches = List.of(batch);
      ExecutionContext ctx = createTestContext();
      FetchMetadata metadata = createTestMetadata(500);
      BatchSchedule plan1 = new BatchSchedule(batches, ctx, metadata);
      BatchSchedule plan2 = new BatchSchedule(batches, ctx, metadata);

      // When & Then: 验证哈希码一致性
      assertThat(plan1.hashCode()).isEqualTo(plan2.hashCode());
    }

    @Test
    @DisplayName("hashCode() 应该对不同值返回不同哈希码（通常情况）")
    void hashCodeShouldReturnDifferentValueForDifferentObjects() {
      // Given: 不同值的计划
      ExecutionContext ctx1 = createTestContext();
      ExecutionContext ctx2 =
          new ExecutionContext(
              2L,
              1L,
              1L,
              1L,
              1L,
              ProvenanceCode.EPMC,
              "FETCH",
              DataType.LITERATURE,
              null,
              "test-hash",
              "test query",
              JsonNodeFactory.instance.objectNode(),
              "test normalized",
              null);
      FetchMetadata metadata = createTestMetadata(500);
      BatchSchedule plan1 = new BatchSchedule(List.of(createTestBatch(1, 0, 500)), ctx1, metadata);
      BatchSchedule plan2 = new BatchSchedule(List.of(createTestBatch(2, 0, 500)), ctx2, metadata);

      // When & Then: 验证哈希码不同（通常情况）
      assertThat(plan1.hashCode()).isNotEqualTo(plan2.hashCode());
    }

    @Test
    @DisplayName("toString() 应该包含所有字段信息")
    void toStringShouldContainAllFields() {
      // Given: 包含批次的计划
      Batch batch = createTestBatch(1, 0, 500);
      List<Batch> batches = List.of(batch);
      ExecutionContext ctx = createTestContext();
      FetchMetadata metadata = createTestMetadata(500);
      BatchSchedule plan = new BatchSchedule(batches, ctx, metadata);

      // When: 调用 toString
      String result = plan.toString();

      // Then: 验证包含字段名称
      assertThat(result)
          .contains("BatchSchedule")
          .contains("batches")
          .contains("context")
          .contains("fetchMetadata");
    }

    @Test
    @DisplayName("组件访问器应该返回正确的字段值")
    void componentAccessorsShouldReturnCorrectValues() {
      // Given: 创建计划
      Batch batch = createTestBatch(1, 0, 500);
      List<Batch> batches = List.of(batch);
      ExecutionContext ctx = createTestContext();
      FetchMetadata metadata = createTestMetadata(500);
      BatchSchedule plan = new BatchSchedule(batches, ctx, metadata);

      // When & Then: 验证访问器
      assertThat(plan.batches()).isEqualTo(batches);
      assertThat(plan.context()).isEqualTo(ctx);
      assertThat(plan.fetchMetadata()).isEqualTo(metadata);
      assertThat(plan.totalBatches()).isEqualTo(1);
      assertThat(plan.hasBatches()).isTrue();
    }
  }

  // ==================== 不可变性测试 ====================

  @Nested
  @DisplayName("不可变性验证")
  class Immutability {

    @Test
    @DisplayName("batches 列表应该是不可变的")
    void batchesListShouldBeImmutable() {
      // Given: 创建计划
      Batch batch = createTestBatch(1, 0, 500);
      List<Batch> batches = List.of(batch);
      ExecutionContext ctx = createTestContext();
      FetchMetadata metadata = createTestMetadata(500);
      BatchSchedule plan = new BatchSchedule(batches, ctx, metadata);

      // When & Then: 验证列表不可修改
      assertThatThrownBy(() -> plan.batches().add(createTestBatch(2, 500, 500)))
          .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    @DisplayName("外部修改可变列表不应影响 BatchSchedule")
    void externalMutableListModificationShouldNotAffectPlan() {
      // Given: 使用 ArrayList 创建计划
      List<Batch> mutableList = new ArrayList<>();
      mutableList.add(createTestBatch(1, 0, 500));
      ExecutionContext ctx = createTestContext();
      FetchMetadata metadata = createTestMetadata(500);
      BatchSchedule plan = new BatchSchedule(List.copyOf(mutableList), ctx, metadata);

      int originalSize = plan.batches().size();

      // When: 修改外部列表
      mutableList.add(createTestBatch(2, 500, 500));

      // Then: 计划内部列表不受影响
      assertThat(plan.batches()).hasSize(originalSize);
    }

    @Test
    @DisplayName("Record 实例应该是不可变的")
    void recordInstanceShouldBeImmutable() {
      // Given: 创建计划
      Batch batch = createTestBatch(1, 0, 500);
      List<Batch> batches = List.of(batch);
      ExecutionContext ctx = createTestContext();
      FetchMetadata metadata = createTestMetadata(500);
      BatchSchedule plan = new BatchSchedule(batches, ctx, metadata);

      // When: 保存原始值
      List<Batch> originalBatches = plan.batches();
      ExecutionContext originalCtx = plan.context();
      FetchMetadata originalMetadata = plan.fetchMetadata();

      // Then: 多次访问应返回相同值（验证不可变）
      assertThat(plan.batches()).isSameAs(originalBatches);
      assertThat(plan.context()).isSameAs(originalCtx);
      assertThat(plan.fetchMetadata()).isSameAs(originalMetadata);
    }
  }

  // ==================== 边界条件测试 ====================

  @Nested
  @DisplayName("边界条件")
  class BoundaryConditions {

    @Test
    @DisplayName("应该处理大量批次")
    void shouldHandleLargeNumberOfBatches() {
      // Given: 创建大量批次
      List<Batch> batches =
          List.of(
              createTestBatch(1, 0, 500),
              createTestBatch(2, 500, 500),
              createTestBatch(3, 1000, 500),
              createTestBatch(4, 1500, 500),
              createTestBatch(5, 2000, 500),
              createTestBatch(6, 2500, 500),
              createTestBatch(7, 3000, 500),
              createTestBatch(8, 3500, 500),
              createTestBatch(9, 4000, 500),
              createTestBatch(10, 4500, 500));
      ExecutionContext ctx = createTestContext();
      FetchMetadata metadata = createTestMetadata(5000);

      // When: 创建计划
      BatchSchedule plan = new BatchSchedule(batches, ctx, metadata);

      // Then: 验证正确处理
      assertThat(plan.batches()).hasSize(10);
      assertThat(plan.totalBatches()).isEqualTo(10);
      assertThat(plan.hasBatches()).isTrue();
    }
  }

  // ==================== 工厂方法与构造器等价性测试 ====================

  @Nested
  @DisplayName("工厂方法与构造器等价性")
  class FactoryConstructorEquivalence {

    @Test
    @DisplayName("empty() 应该等价于构造器创建的空计划")
    void emptyShouldBeEquivalentToConstructor() {
      // Given: 准备上下文和元数据
      ExecutionContext ctx = createTestContext();
      FetchMetadata metadata = createTestMetadata(0);

      // When: 使用工厂方法和构造器
      BatchSchedule factoryPlan = BatchSchedule.empty(ctx, metadata);
      BatchSchedule constructorPlan = new BatchSchedule(List.of(), ctx, metadata);

      // Then: 验证等价性
      assertThat(factoryPlan).isEqualTo(constructorPlan);
    }

    @Test
    @DisplayName("single() 应该等价于构造器创建的单批次调度")
    void singleShouldBeEquivalentToConstructor() {
      // Given: 准备批次、上下文和元数据
      Batch batch = createTestBatch(1, 0, 500);
      ExecutionContext ctx = createTestContext();
      FetchMetadata metadata = createTestMetadata(500);

      // When: 使用工厂方法和构造器
      BatchSchedule factoryPlan = BatchSchedule.single(batch, ctx, metadata);
      BatchSchedule constructorPlan = new BatchSchedule(List.of(batch), ctx, metadata);

      // Then: 验证等价性
      assertThat(factoryPlan).isEqualTo(constructorPlan);
    }
  }
}
