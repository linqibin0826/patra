package com.patra.ingest.domain.model.vo.batch;

import static org.assertj.core.api.Assertions.*;

import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * {@link Batch} 的单元测试。
 *
 * <p>测试覆盖:
 *
 * <ul>
 *   <li>构造方法验证 (batchNo >= 1, query not blank, offset >= 0, limit > 0)
 *   <li>业务方法 (endOffset, contains)
 *   <li>Record 语义 (equals, hashCode, toString, 组件访问器)
 *   <li>不变性保证
 * </ul>
 *
 * @author Patra Team
 */
@DisplayName("Batch 单元测试")
class BatchTest {

  private static final String TEST_QUERY = "cancer[Title] AND human[MeSH Terms]";

  @Nested
  @DisplayName("构造方法验证")
  class ConstructorValidationTests {

    @Test
    @DisplayName("应该成功创建批次 - 所有字段有效")
    void shouldCreateBatchWithAllValidFields() {
      // When: 创建批次
      Batch batch = new Batch(1, TEST_QUERY, 0, 500);

      // Then: 应该成功创建
      assertThat(batch.batchNo()).isEqualTo(1);
      assertThat(batch.query()).isEqualTo(TEST_QUERY);
      assertThat(batch.offset()).isEqualTo(0);
      assertThat(batch.limit()).isEqualTo(500);
    }

    @Test
    @DisplayName("应该拒绝 batchNo 为 0")
    void shouldRejectBatchNoZero() {
      // When & Then: batchNo = 0 应抛出 IllegalArgumentException
      assertThatThrownBy(() -> new Batch(0, TEST_QUERY, 0, 500))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("batchNo must be >= 1");
    }

    @Test
    @DisplayName("应该拒绝 batchNo 为负数")
    void shouldRejectNegativeBatchNo() {
      // When & Then: batchNo < 0 应抛出 IllegalArgumentException
      assertThatThrownBy(() -> new Batch(-1, TEST_QUERY, 0, 500))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("batchNo must be >= 1");

      assertThatThrownBy(() -> new Batch(-999, TEST_QUERY, 0, 500))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("batchNo must be >= 1");
    }

    @Test
    @DisplayName("应该接受 batchNo >= 1")
    void shouldAcceptValidBatchNo() {
      // When: 创建批次,batchNo >= 1
      Batch batch1 = new Batch(1, TEST_QUERY, 0, 500);
      Batch batch2 = new Batch(100, TEST_QUERY, 0, 500);
      Batch batch1000 = new Batch(1000, TEST_QUERY, 0, 500);

      // Then: 应该成功创建
      assertThat(batch1.batchNo()).isEqualTo(1);
      assertThat(batch2.batchNo()).isEqualTo(100);
      assertThat(batch1000.batchNo()).isEqualTo(1000);
    }

    @Test
    @DisplayName("应该允许 null 或空白的 query（PubMed 等数据源允许仅使用日期过滤器）")
    void shouldAllowNullOrBlankQuery() {
      // When: query = null
      Batch batchWithNull = new Batch(1, null, 0, 500);

      // Then: 应该成功创建
      assertThat(batchWithNull).isNotNull();
      assertThat(batchWithNull.query()).isNull();

      // When: query = ""
      Batch batchWithEmpty = new Batch(1, "", 0, 500);

      // Then: 应该成功创建
      assertThat(batchWithEmpty).isNotNull();
      assertThat(batchWithEmpty.query()).isEmpty();

      // When: query = "   "
      Batch batchWithBlank = new Batch(1, "   ", 0, 500);

      // Then: 应该成功创建
      assertThat(batchWithBlank).isNotNull();
      assertThat(batchWithBlank.query()).isEqualTo("   ");
    }

    @Test
    @DisplayName("应该拒绝 offset 为负数")
    void shouldRejectNegativeOffset() {
      // When & Then: offset < 0 应抛出 IllegalArgumentException
      assertThatThrownBy(() -> new Batch(1, TEST_QUERY, -1, 500))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("offset must be >= 0");

      assertThatThrownBy(() -> new Batch(1, TEST_QUERY, -100, 500))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("offset must be >= 0");
    }

    @Test
    @DisplayName("应该接受 offset >= 0")
    void shouldAcceptValidOffset() {
      // When: 创建批次,offset >= 0
      Batch batch1 = new Batch(1, TEST_QUERY, 0, 500);
      Batch batch2 = new Batch(2, TEST_QUERY, 500, 500);
      Batch batch3 = new Batch(3, TEST_QUERY, 1000, 500);

      // Then: 应该成功创建
      assertThat(batch1.offset()).isEqualTo(0);
      assertThat(batch2.offset()).isEqualTo(500);
      assertThat(batch3.offset()).isEqualTo(1000);
    }

    @Test
    @DisplayName("应该拒绝 limit <= 0")
    void shouldRejectNonPositiveLimit() {
      // When & Then: limit = 0 应抛出 IllegalArgumentException
      assertThatThrownBy(() -> new Batch(1, TEST_QUERY, 0, 0))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("limit must be > 0");

      // When & Then: limit < 0 应抛出 IllegalArgumentException
      assertThatThrownBy(() -> new Batch(1, TEST_QUERY, 0, -1))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("limit must be > 0");
    }

    @Test
    @DisplayName("应该接受 limit > 0")
    void shouldAcceptValidLimit() {
      // When: 创建批次,limit > 0
      Batch batch1 = new Batch(1, TEST_QUERY, 0, 1);
      Batch batch2 = new Batch(2, TEST_QUERY, 0, 500);
      Batch batch3 = new Batch(3, TEST_QUERY, 0, 10000);

      // Then: 应该成功创建
      assertThat(batch1.limit()).isEqualTo(1);
      assertThat(batch2.limit()).isEqualTo(500);
      assertThat(batch3.limit()).isEqualTo(10000);
    }
  }

  @Nested
  @DisplayName("业务方法: endOffset()")
  class EndOffsetMethodTests {

    @Test
    @DisplayName("应该正确计算结束位置 - offset=0, limit=500")
    void shouldCalculateEndOffsetCorrectly() {
      // Given: offset=0, limit=500
      Batch batch = new Batch(1, TEST_QUERY, 0, 500);

      // When: 调用 endOffset()
      int endOffset = batch.endOffset();

      // Then: 应返回 500
      assertThat(endOffset).isEqualTo(500);
    }

    @Test
    @DisplayName("应该正确计算结束位置 - offset=500, limit=500")
    void shouldCalculateEndOffsetForSecondBatch() {
      // Given: offset=500, limit=500
      Batch batch = new Batch(2, TEST_QUERY, 500, 500);

      // When: 调用 endOffset()
      int endOffset = batch.endOffset();

      // Then: 应返回 1000
      assertThat(endOffset).isEqualTo(1000);
    }

    @Test
    @DisplayName("应该正确计算结束位置 - 不同的 limit")
    void shouldCalculateEndOffsetForDifferentLimits() {
      // Given: 不同的 limit
      Batch batch1 = new Batch(1, TEST_QUERY, 0, 100);
      Batch batch2 = new Batch(2, TEST_QUERY, 100, 200);
      Batch batch3 = new Batch(3, TEST_QUERY, 300, 50);

      // When: 调用 endOffset()
      // Then: 应正确计算
      assertThat(batch1.endOffset()).isEqualTo(100);
      assertThat(batch2.endOffset()).isEqualTo(300);
      assertThat(batch3.endOffset()).isEqualTo(350);
    }
  }

  @Nested
  @DisplayName("业务方法: contains()")
  class ContainsMethodTests {

    @Test
    @DisplayName("应该返回 true - 记录位置在范围内")
    void shouldReturnTrueWhenRecordPositionIsInRange() {
      // Given: offset=0, limit=500 (范围: 0-499)
      Batch batch = new Batch(1, TEST_QUERY, 0, 500);

      // When & Then: 范围内的位置应返回 true
      assertThat(batch.contains(0)).isTrue();
      assertThat(batch.contains(250)).isTrue();
      assertThat(batch.contains(499)).isTrue();
    }

    @Test
    @DisplayName("应该返回 false - 记录位置在范围外")
    void shouldReturnFalseWhenRecordPositionIsOutOfRange() {
      // Given: offset=0, limit=500 (范围: 0-499)
      Batch batch = new Batch(1, TEST_QUERY, 0, 500);

      // When & Then: 范围外的位置应返回 false
      assertThat(batch.contains(-1)).isFalse();
      assertThat(batch.contains(500)).isFalse();
      assertThat(batch.contains(1000)).isFalse();
    }

    @Test
    @DisplayName("应该正确处理第二批次的范围")
    void shouldHandleSecondBatchRange() {
      // Given: offset=500, limit=500 (范围: 500-999)
      Batch batch = new Batch(2, TEST_QUERY, 500, 500);

      // When & Then: 应正确判断范围
      assertThat(batch.contains(499)).isFalse();
      assertThat(batch.contains(500)).isTrue();
      assertThat(batch.contains(750)).isTrue();
      assertThat(batch.contains(999)).isTrue();
      assertThat(batch.contains(1000)).isFalse();
    }

    @Test
    @DisplayName("应该正确处理边界条件")
    void shouldHandleBoundaryConditions() {
      // Given: offset=100, limit=50 (范围: 100-149)
      Batch batch = new Batch(1, TEST_QUERY, 100, 50);

      // When & Then: 边界值应正确处理
      assertThat(batch.contains(99)).isFalse();
      assertThat(batch.contains(100)).isTrue();
      assertThat(batch.contains(149)).isTrue();
      assertThat(batch.contains(150)).isFalse();
    }
  }

  @Nested
  @DisplayName("Record 语义: equals() 和 hashCode()")
  class EqualsAndHashCodeTests {

    @Test
    @DisplayName("equals() - 相同值应相等")
    void shouldBeEqualForSameValues() {
      // Given: 两个相同值的 Batch
      Batch batch1 = new Batch(1, TEST_QUERY, 0, 500);
      Batch batch2 = new Batch(1, TEST_QUERY, 0, 500);

      // When & Then: 应该相等
      assertThat(batch1).isEqualTo(batch2).hasSameHashCodeAs(batch2);
    }

    @Test
    @DisplayName("equals() - 不同 batchNo 应不相等")
    void shouldNotBeEqualForDifferentBatchNo() {
      // Given: batchNo 不同的 Batch
      Batch batch1 = new Batch(1, TEST_QUERY, 0, 500);
      Batch batch2 = new Batch(2, TEST_QUERY, 0, 500);

      // When & Then: 应该不相等
      assertThat(batch1).isNotEqualTo(batch2);
    }

    @Test
    @DisplayName("equals() - 不同 query 应不相等")
    void shouldNotBeEqualForDifferentQuery() {
      // Given: query 不同的 Batch
      Batch batch1 = new Batch(1, "query1", 0, 500);
      Batch batch2 = new Batch(1, "query2", 0, 500);

      // When & Then: 应该不相等
      assertThat(batch1).isNotEqualTo(batch2);
    }

    @Test
    @DisplayName("equals() - 不同 offset 应不相等")
    void shouldNotBeEqualForDifferentOffset() {
      // Given: offset 不同的 Batch
      Batch batch1 = new Batch(1, TEST_QUERY, 0, 500);
      Batch batch2 = new Batch(1, TEST_QUERY, 500, 500);

      // When & Then: 应该不相等
      assertThat(batch1).isNotEqualTo(batch2);
    }

    @Test
    @DisplayName("equals() - 不同 limit 应不相等")
    void shouldNotBeEqualForDifferentLimit() {
      // Given: limit 不同的 Batch
      Batch batch1 = new Batch(1, TEST_QUERY, 0, 500);
      Batch batch2 = new Batch(1, TEST_QUERY, 0, 1000);

      // When & Then: 应该不相等
      assertThat(batch1).isNotEqualTo(batch2);
    }

    @Test
    @DisplayName("equals() - 与自身比较应返回 true")
    void shouldBeEqualToItself() {
      // Given: 一个 Batch
      Batch batch = new Batch(1, TEST_QUERY, 0, 500);

      // When & Then: 与自身比较应相等
      assertThat(batch).isEqualTo(batch);
    }

    @Test
    @DisplayName("equals() - 与 null 比较应返回 false")
    void shouldNotBeEqualToNull() {
      // Given: 一个 Batch
      Batch batch = new Batch(1, TEST_QUERY, 0, 500);

      // When & Then: 与 null 比较应不相等
      assertThat(batch).isNotEqualTo(null);
    }

    @Test
    @DisplayName("equals() - 与不同类型比较应返回 false")
    void shouldNotBeEqualToDifferentType() {
      // Given: 一个 Batch 和一个 String
      Batch batch = new Batch(1, TEST_QUERY, 0, 500);
      String other = "not a batch";

      // When & Then: 与不同类型比较应不相等
      assertThat(batch).isNotEqualTo(other);
    }

    @Test
    @DisplayName("hashCode() - 相同值应有相同的哈希码")
    void shouldHaveSameHashCodeForSameValues() {
      // Given: 两个相同值的 Batch
      Batch batch1 = new Batch(1, TEST_QUERY, 0, 500);
      Batch batch2 = new Batch(1, TEST_QUERY, 0, 500);

      // When & Then: 哈希码应相同
      assertThat(batch1.hashCode()).isEqualTo(batch2.hashCode());
    }

    @Test
    @DisplayName("hashCode() - 不同值通常应有不同的哈希码")
    void shouldHaveDifferentHashCodeForDifferentValues() {
      // Given: 两个不同值的 Batch
      Batch batch1 = new Batch(1, TEST_QUERY, 0, 500);
      Batch batch2 = new Batch(2, TEST_QUERY, 0, 500);

      // When & Then: 哈希码通常不同 (不是绝对保证,但概率很高)
      assertThat(batch1.hashCode()).isNotEqualTo(batch2.hashCode());
    }

    @Test
    @DisplayName("hashCode() - 多次调用应返回相同值")
    void shouldHaveConsistentHashCode() {
      // Given: 一个 Batch
      Batch batch = new Batch(1, TEST_QUERY, 0, 500);

      // When: 多次调用 hashCode()
      int hashCode1 = batch.hashCode();
      int hashCode2 = batch.hashCode();
      int hashCode3 = batch.hashCode();

      // Then: 应返回相同值
      assertThat(hashCode1).isEqualTo(hashCode2).isEqualTo(hashCode3);
    }

    @Test
    @DisplayName("应该在 HashSet 中正确工作 - 验证 equals 和 hashCode 契约")
    void shouldWorkCorrectlyInHashSet() {
      // Given: 多个 Batch
      Batch batch1 = new Batch(1, TEST_QUERY, 0, 500);
      Batch batch2 = new Batch(1, TEST_QUERY, 0, 500); // 相同值
      Batch batch3 = new Batch(2, TEST_QUERY, 0, 500); // 不同值

      // When: 添加到 HashSet
      Set<Batch> set = new HashSet<>();
      set.add(batch1);
      set.add(batch2); // 应该被去重
      set.add(batch3);

      // Then: Set 应该只包含 2 个不同的值
      assertThat(set).hasSize(2).contains(batch1, batch3);
    }
  }

  @Nested
  @DisplayName("Record 语义: toString()")
  class ToStringTests {

    @Test
    @DisplayName("toString() 应包含所有字段信息")
    void shouldIncludeAllFieldsInToString() {
      // Given: 一个 Batch
      Batch batch = new Batch(5, TEST_QUERY, 100, 500);

      // When: 调用 toString()
      String result = batch.toString();

      // Then: 应包含所有字段名和值
      assertThat(result)
          .contains("Batch")
          .contains("batchNo=5")
          .contains("query=" + TEST_QUERY)
          .contains("offset=100")
          .contains("limit=500");
    }
  }

  @Nested
  @DisplayName("Record 语义: 组件访问器")
  class ComponentAccessorTests {

    @Test
    @DisplayName("batchNo() 应返回批次序号")
    void shouldReturnBatchNo() {
      // Given: 一个 Batch
      Batch batch = new Batch(42, TEST_QUERY, 0, 500);

      // When: 调用 batchNo()
      int batchNo = batch.batchNo();

      // Then: 应返回正确的值
      assertThat(batchNo).isEqualTo(42);
    }

    @Test
    @DisplayName("query() 应返回查询字符串")
    void shouldReturnQuery() {
      // Given: 一个 Batch
      String expectedQuery = "custom query string";
      Batch batch = new Batch(1, expectedQuery, 0, 500);

      // When: 调用 query()
      String query = batch.query();

      // Then: 应返回正确的值
      assertThat(query).isEqualTo(expectedQuery);
    }

    @Test
    @DisplayName("offset() 应返回偏移量")
    void shouldReturnOffset() {
      // Given: 一个 Batch
      Batch batch = new Batch(1, TEST_QUERY, 100, 500);

      // When: 调用 offset()
      int offset = batch.offset();

      // Then: 应返回正确的值
      assertThat(offset).isEqualTo(100);
    }

    @Test
    @DisplayName("limit() 应返回限制数量")
    void shouldReturnLimit() {
      // Given: 一个 Batch
      Batch batch = new Batch(1, TEST_QUERY, 0, 500);

      // When: 调用 limit()
      int limit = batch.limit();

      // Then: 应返回正确的值
      assertThat(limit).isEqualTo(500);
    }

    @Test
    @DisplayName("组件访问器应返回不可变的引用")
    void shouldReturnImmutableReferences() {
      // Given: 一个 Batch
      Batch batch = new Batch(1, TEST_QUERY, 0, 500);

      // When: 多次调用组件访问器
      String query1 = batch.query();
      String query2 = batch.query();

      // Then: 应返回相同的引用 (Record 组件是 final 的)
      assertThat(query1).isSameAs(query2);
    }
  }

  @Nested
  @DisplayName("不变性保证")
  class ImmutabilityTests {

    @Test
    @DisplayName("类应该是 final - 防止子类破坏不变性")
    void shouldBeFinalClass() {
      // When & Then: Batch 应该是 final 类 (Record 自动 final)
      assertThat(Batch.class).isFinal();
    }

    @Test
    @DisplayName("字段应该是 final - 确保不可变")
    void shouldHaveFinalFields() throws NoSuchFieldException {
      // When & Then: 所有字段应该是 final (Record 自动 final)
      assertThat(Batch.class.getDeclaredField("batchNo"))
          .matches(field -> java.lang.reflect.Modifier.isFinal(field.getModifiers()));
      assertThat(Batch.class.getDeclaredField("query"))
          .matches(field -> java.lang.reflect.Modifier.isFinal(field.getModifiers()));
      assertThat(Batch.class.getDeclaredField("offset"))
          .matches(field -> java.lang.reflect.Modifier.isFinal(field.getModifiers()));
      assertThat(Batch.class.getDeclaredField("limit"))
          .matches(field -> java.lang.reflect.Modifier.isFinal(field.getModifiers()));
    }
  }

  @Nested
  @DisplayName("边界条件测试")
  class BoundaryConditionTests {

    @Test
    @DisplayName("应该接受 batchNo = 1 (最小有效值)")
    void shouldAcceptMinimumValidBatchNo() {
      // When: batchNo = 1
      Batch batch = new Batch(1, TEST_QUERY, 0, 500);

      // Then: 应该成功创建
      assertThat(batch.batchNo()).isEqualTo(1);
    }

    @Test
    @DisplayName("应该接受极大的 batchNo")
    void shouldAcceptVeryLargeBatchNo() {
      // When: batchNo = Integer.MAX_VALUE
      Batch batch = new Batch(Integer.MAX_VALUE, TEST_QUERY, 0, 500);

      // Then: 应该成功创建
      assertThat(batch.batchNo()).isEqualTo(Integer.MAX_VALUE);
    }

    @Test
    @DisplayName("应该接受极长的查询字符串")
    void shouldAcceptVeryLongQueryString() {
      // Given: 极长的查询字符串
      String longQuery = "a".repeat(10000);

      // When: 创建 Batch
      Batch batch = new Batch(1, longQuery, 0, 500);

      // Then: 应该成功创建
      assertThat(batch.query()).hasSize(10000);
    }

    @Test
    @DisplayName("应该接受 offset = 0 (最小有效值)")
    void shouldAcceptMinimumValidOffset() {
      // When: offset = 0
      Batch batch = new Batch(1, TEST_QUERY, 0, 500);

      // Then: 应该成功创建
      assertThat(batch.offset()).isEqualTo(0);
    }

    @Test
    @DisplayName("应该接受极大的 offset")
    void shouldAcceptVeryLargeOffset() {
      // When: offset = Integer.MAX_VALUE - 1 (避免 endOffset 溢出)
      Batch batch = new Batch(1, TEST_QUERY, Integer.MAX_VALUE - 1000, 500);

      // Then: 应该成功创建
      assertThat(batch.offset()).isEqualTo(Integer.MAX_VALUE - 1000);
    }

    @Test
    @DisplayName("应该接受 limit = 1 (最小有效值)")
    void shouldAcceptMinimumValidLimit() {
      // When: limit = 1
      Batch batch = new Batch(1, TEST_QUERY, 0, 1);

      // Then: 应该成功创建
      assertThat(batch.limit()).isEqualTo(1);
    }

    @Test
    @DisplayName("应该接受极大的 limit")
    void shouldAcceptVeryLargeLimit() {
      // When: limit = Integer.MAX_VALUE
      Batch batch = new Batch(1, TEST_QUERY, 0, Integer.MAX_VALUE);

      // Then: 应该成功创建
      assertThat(batch.limit()).isEqualTo(Integer.MAX_VALUE);
    }
  }

  @Nested
  @DisplayName("集成场景测试")
  class IntegrationScenarioTests {

    @Test
    @DisplayName("业务场景 - 连续批次构建")
    void shouldBuildSequentialBatches() {
      // Given: 第一个批次
      Batch batch1 = new Batch(1, TEST_QUERY, 0, 500);

      // When: 构建后续批次
      Batch batch2 = new Batch(2, TEST_QUERY, 500, 500);
      Batch batch3 = new Batch(3, TEST_QUERY, 1000, 500);

      // Then: 批次序号和 offset 应该递增
      assertThat(batch1.batchNo()).isEqualTo(1);
      assertThat(batch1.offset()).isEqualTo(0);
      assertThat(batch2.batchNo()).isEqualTo(2);
      assertThat(batch2.offset()).isEqualTo(500);
      assertThat(batch3.batchNo()).isEqualTo(3);
      assertThat(batch3.offset()).isEqualTo(1000);
    }

    @Test
    @DisplayName("业务场景 - 批次序列验证 (确保 batchNo 连续)")
    void shouldValidateBatchSequence() {
      // Given: 一系列批次
      Batch batch1 = new Batch(1, TEST_QUERY, 0, 500);
      Batch batch2 = new Batch(2, TEST_QUERY, 500, 500);
      Batch batch3 = new Batch(3, TEST_QUERY, 1000, 500);

      // When: 验证批次序列
      Set<Integer> batchNos = Set.of(batch1.batchNo(), batch2.batchNo(), batch3.batchNo());

      // Then: 批次序号应该是连续的 1, 2, 3
      assertThat(batchNos).containsExactlyInAnyOrder(1, 2, 3);
    }

    @Test
    @DisplayName("业务场景 - 批量生成批次并去重")
    void shouldGenerateAndDeduplicateBatches() {
      // Given: 批量生成批次
      Set<Batch> batches = new HashSet<>();
      batches.add(new Batch(1, TEST_QUERY, 0, 500));
      batches.add(new Batch(1, TEST_QUERY, 0, 500)); // 重复
      batches.add(new Batch(2, TEST_QUERY, 500, 500));

      // Then: Set 应该自动去重
      assertThat(batches).hasSize(2);
    }

    @Test
    @DisplayName("业务场景 - 不同查询使用相同分页参数")
    void shouldUseSamePaginationForDifferentQueries() {
      // Given: 不同查询,相同分页参数
      String query1 = "cancer[Title]";
      String query2 = "diabetes[Title]";
      int offset = 0;
      int limit = 500;

      // When: 创建批次
      Batch batch1 = new Batch(1, query1, offset, limit);
      Batch batch2 = new Batch(1, query2, offset, limit);

      // Then: 分页参数应该相同
      assertThat(batch1.offset()).isEqualTo(batch2.offset());
      assertThat(batch1.limit()).isEqualTo(batch2.limit());
      // But: 查询应该不同
      assertThat(batch1.query()).isNotEqualTo(batch2.query());
    }

    @Test
    @DisplayName("业务场景 - 判断记录是否在批次范围内")
    void shouldDetermineIfRecordIsInBatchRange() {
      // Given: 三个连续批次
      Batch batch1 = new Batch(1, TEST_QUERY, 0, 500); // 0-499
      Batch batch2 = new Batch(2, TEST_QUERY, 500, 500); // 500-999
      Batch batch3 = new Batch(3, TEST_QUERY, 1000, 500); // 1000-1499

      // When & Then: 应该正确判断记录所属批次
      assertThat(batch1.contains(0)).isTrue();
      assertThat(batch1.contains(499)).isTrue();
      assertThat(batch1.contains(500)).isFalse();

      assertThat(batch2.contains(499)).isFalse();
      assertThat(batch2.contains(500)).isTrue();
      assertThat(batch2.contains(999)).isTrue();
      assertThat(batch2.contains(1000)).isFalse();

      assertThat(batch3.contains(999)).isFalse();
      assertThat(batch3.contains(1000)).isTrue();
      assertThat(batch3.contains(1499)).isTrue();
      assertThat(batch3.contains(1500)).isFalse();
    }
  }
}
