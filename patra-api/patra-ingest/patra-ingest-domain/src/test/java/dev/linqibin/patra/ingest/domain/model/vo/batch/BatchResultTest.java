package dev.linqibin.patra.ingest.domain.model.vo.batch;

import static org.assertj.core.api.Assertions.*;

import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/// {@link BatchResult} 的单元测试。
///
/// 测试覆盖:
///
/// - 构造方法验证 (batchNo >= 1, fetchedCount >= 0, 失败批次必须提供错误信息)
///   - 工厂方法 (success, failure)
///   - 业务方法 (hasNextCursor)
///   - Record 语义 (equals, hashCode, toString, 组件访问器)
///   - 不变性保证
///   - 边界条件测试
///
/// @author linqibin
@DisplayName("BatchResult 单元测试")
class BatchResultTest {

  @Nested
  @DisplayName("构造方法验证")
  class ConstructorValidationTests {

    @Test
    @DisplayName("应该成功创建成功批次 - 所有字段有效")
    void shouldCreateSuccessBatchResultWithAllValidFields() {
      // When: 创建成功批次结果
      BatchResult result =
          new BatchResult(1, true, 100, "cursor-next", null, "oss://bucket/key/batch1.json");

      // Then: 应该成功创建
      assertThat(result.batchNo()).isEqualTo(1);
      assertThat(result.success()).isTrue();
      assertThat(result.fetchedCount()).isEqualTo(100);
      assertThat(result.nextCursorToken()).isEqualTo("cursor-next");
      assertThat(result.errorMessage()).isNull();
      assertThat(result.storageKey()).isEqualTo("oss://bucket/key/batch1.json");
    }

    @Test
    @DisplayName("应该成功创建失败批次 - 包含错误信息")
    void shouldCreateFailureBatchResultWithErrorMessage() {
      // When: 创建失败批次结果
      BatchResult result = new BatchResult(2, false, 0, null, "API rate limit exceeded", null);

      // Then: 应该成功创建
      assertThat(result.batchNo()).isEqualTo(2);
      assertThat(result.success()).isFalse();
      assertThat(result.fetchedCount()).isEqualTo(0);
      assertThat(result.nextCursorToken()).isNull();
      assertThat(result.errorMessage()).isEqualTo("API rate limit exceeded");
      assertThat(result.storageKey()).isNull();
    }

    @Test
    @DisplayName("应该成功创建成功批次 - 可选字段为 null")
    void shouldCreateSuccessBatchResultWithNullOptionalFields() {
      // When: 创建批次结果,nextCursorToken 为 null (最后一批)
      BatchResult result = new BatchResult(3, true, 50, null, null, "oss://bucket/key/batch3.json");

      // Then: 应该成功创建
      assertThat(result.batchNo()).isEqualTo(3);
      assertThat(result.success()).isTrue();
      assertThat(result.fetchedCount()).isEqualTo(50);
      assertThat(result.nextCursorToken()).isNull();
      assertThat(result.errorMessage()).isNull();
      assertThat(result.storageKey()).isEqualTo("oss://bucket/key/batch3.json");
    }

    @Test
    @DisplayName("应该成功创建成功批次 - fetchedCount = 0 (空批次)")
    void shouldCreateSuccessBatchResultWithZeroFetchedCount() {
      // When: 创建成功批次,但获取记录数为 0
      BatchResult result = new BatchResult(1, true, 0, null, null, "oss://bucket/key/empty.json");

      // Then: 应该成功创建
      assertThat(result.success()).isTrue();
      assertThat(result.fetchedCount()).isZero();
    }

    @Test
    @DisplayName("应该拒绝 batchNo 为 0")
    void shouldRejectBatchNoZero() {
      // When & Then: batchNo = 0 应抛出 IllegalArgumentException
      assertThatThrownBy(
              () -> new BatchResult(0, true, 100, "cursor", null, "oss://bucket/key/batch.json"))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("批次序号必须 >= 1");
    }

    @Test
    @DisplayName("应该拒绝 batchNo 为负数")
    void shouldRejectNegativeBatchNo() {
      // When & Then: batchNo < 0 应抛出 IllegalArgumentException
      assertThatThrownBy(
              () -> new BatchResult(-1, true, 100, "cursor", null, "oss://bucket/key/batch.json"))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("批次序号必须 >= 1");

      assertThatThrownBy(
              () -> new BatchResult(-999, true, 100, "cursor", null, "oss://bucket/key/batch.json"))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("批次序号必须 >= 1");
    }

    @Test
    @DisplayName("应该接受 batchNo >= 1")
    void shouldAcceptValidBatchNo() {
      // When: 创建批次结果,batchNo >= 1
      BatchResult result1 = new BatchResult(1, true, 100, "cursor", null, "oss://key1.json");
      BatchResult result100 = new BatchResult(100, true, 100, "cursor", null, "oss://key100.json");
      BatchResult result1000 =
          new BatchResult(1000, true, 100, "cursor", null, "oss://key1000.json");

      // Then: 应该成功创建
      assertThat(result1.batchNo()).isEqualTo(1);
      assertThat(result100.batchNo()).isEqualTo(100);
      assertThat(result1000.batchNo()).isEqualTo(1000);
    }

    @Test
    @DisplayName("应该拒绝 fetchedCount 为负数")
    void shouldRejectNegativeFetchedCount() {
      // When & Then: fetchedCount < 0 应抛出 IllegalArgumentException
      assertThatThrownBy(
              () -> new BatchResult(1, true, -1, "cursor", null, "oss://bucket/key/batch.json"))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("获取记录数不能为负数");

      assertThatThrownBy(
              () -> new BatchResult(1, true, -100, "cursor", null, "oss://bucket/key/batch.json"))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("获取记录数不能为负数");
    }

    @Test
    @DisplayName("应该接受 fetchedCount >= 0")
    void shouldAcceptValidFetchedCount() {
      // When: 创建批次结果,fetchedCount >= 0
      BatchResult result0 = new BatchResult(1, true, 0, "cursor", null, "oss://key.json");
      BatchResult result1 = new BatchResult(1, true, 1, "cursor", null, "oss://key.json");
      BatchResult result1000 = new BatchResult(1, true, 1000, "cursor", null, "oss://key.json");

      // Then: 应该成功创建
      assertThat(result0.fetchedCount()).isZero();
      assertThat(result1.fetchedCount()).isEqualTo(1);
      assertThat(result1000.fetchedCount()).isEqualTo(1000);
    }

    @Test
    @DisplayName("应该拒绝失败批次但 errorMessage 为 null")
    void shouldRejectFailureBatchResultWithNullErrorMessage() {
      // When & Then: success = false 但 errorMessage = null 应抛出 IllegalArgumentException
      assertThatThrownBy(() -> new BatchResult(1, false, 0, null, null, null))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("失败批次必须提供错误信息");
    }

    @Test
    @DisplayName("应该拒绝失败批次但 errorMessage 为空字符串")
    void shouldRejectFailureBatchResultWithEmptyErrorMessage() {
      // When & Then: success = false 但 errorMessage 为空字符串应抛出 IllegalArgumentException
      assertThatThrownBy(() -> new BatchResult(1, false, 0, null, "", null))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("失败批次必须提供错误信息");
    }

    @Test
    @DisplayName("应该拒绝失败批次但 errorMessage 为空白字符串")
    void shouldRejectFailureBatchResultWithBlankErrorMessage() {
      // When & Then: success = false 但 errorMessage 为空白字符串应抛出 IllegalArgumentException
      assertThatThrownBy(() -> new BatchResult(1, false, 0, null, "   ", null))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("失败批次必须提供错误信息");

      assertThatThrownBy(() -> new BatchResult(1, false, 0, null, "\t\n", null))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("失败批次必须提供错误信息");
    }

    @Test
    @DisplayName("应该接受失败批次且 errorMessage 有内容")
    void shouldAcceptFailureBatchResultWithValidErrorMessage() {
      // When: 创建失败批次,errorMessage 有内容
      BatchResult result = new BatchResult(1, false, 0, null, "Connection timeout", null);

      // Then: 应该成功创建
      assertThat(result.success()).isFalse();
      assertThat(result.errorMessage()).isEqualTo("Connection timeout");
    }

    @Test
    @DisplayName("应该接受成功批次但 errorMessage 为 null")
    void shouldAcceptSuccessBatchResultWithNullErrorMessage() {
      // When: 创建成功批次,errorMessage 为 null
      BatchResult result = new BatchResult(1, true, 100, "cursor", null, "oss://key.json");

      // Then: 应该成功创建
      assertThat(result.success()).isTrue();
      assertThat(result.errorMessage()).isNull();
    }
  }

  @Nested
  @DisplayName("工厂方法: success()")
  class SuccessFactoryMethodTests {

    @Test
    @DisplayName("应该创建成功的批次结果 - 所有参数有效")
    void shouldCreateSuccessBatchResultWithAllParameters() {
      // When: 调用 success() 工厂方法
      BatchResult result =
          BatchResult.success(1, 200, "cursor-next-page", "oss://bucket/key/batch1.json");

      // Then: 应该创建成功批次
      assertThat(result.batchNo()).isEqualTo(1);
      assertThat(result.success()).isTrue();
      assertThat(result.fetchedCount()).isEqualTo(200);
      assertThat(result.nextCursorToken()).isEqualTo("cursor-next-page");
      assertThat(result.errorMessage()).isNull();
      assertThat(result.storageKey()).isEqualTo("oss://bucket/key/batch1.json");
    }

    @Test
    @DisplayName("应该设置 errorMessage 为 null")
    void shouldSetErrorMessageToNull() {
      // When: 调用 success() 工厂方法
      BatchResult result = BatchResult.success(1, 100, "cursor", "oss://key.json");

      // Then: errorMessage 应为 null
      assertThat(result.errorMessage()).isNull();
    }

    @Test
    @DisplayName("应该设置 success 为 true")
    void shouldSetSuccessToTrue() {
      // When: 调用 success() 工厂方法
      BatchResult result = BatchResult.success(1, 100, "cursor", "oss://key.json");

      // Then: success 应为 true
      assertThat(result.success()).isTrue();
    }

    @Test
    @DisplayName("应该接受 nextCursorToken 为 null (最后一批)")
    void shouldAcceptNullNextCursorToken() {
      // When: 调用 success(),nextCursorToken 为 null
      BatchResult result = BatchResult.success(1, 50, null, "oss://key.json");

      // Then: 应该成功创建
      assertThat(result.nextCursorToken()).isNull();
      assertThat(result.success()).isTrue();
    }

    @Test
    @DisplayName("应该接受 storageKey 为 null")
    void shouldAcceptNullStorageKey() {
      // When: 调用 success(),storageKey 为 null
      BatchResult result = BatchResult.success(1, 100, "cursor", null);

      // Then: 应该成功创建
      assertThat(result.storageKey()).isNull();
      assertThat(result.success()).isTrue();
    }

    @Test
    @DisplayName("应该接受 fetchedCount = 0 (空批次)")
    void shouldAcceptZeroFetchedCount() {
      // When: 调用 success(),fetchedCount = 0
      BatchResult result = BatchResult.success(1, 0, null, "oss://key.json");

      // Then: 应该成功创建
      assertThat(result.fetchedCount()).isZero();
      assertThat(result.success()).isTrue();
    }

    @Test
    @DisplayName("应该遵守 batchNo 验证规则")
    void shouldEnforceBatchNoValidation() {
      // When & Then: batchNo < 1 应抛出异常
      assertThatThrownBy(() -> BatchResult.success(0, 100, "cursor", "oss://key.json"))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("批次序号必须 >= 1");
    }

    @Test
    @DisplayName("应该遵守 fetchedCount 验证规则")
    void shouldEnforceFetchedCountValidation() {
      // When & Then: fetchedCount < 0 应抛出异常
      assertThatThrownBy(() -> BatchResult.success(1, -1, "cursor", "oss://key.json"))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("获取记录数不能为负数");
    }

    @Test
    @DisplayName("业务场景 - 有下一批次的成功结果")
    void shouldCreateSuccessResultWithNextBatch() {
      // Given: 成功获取 1000 条记录,有下一批次
      int batchNo = 5;
      int fetchedCount = 1000;
      String nextCursorToken = "AoE-base64-encoded-cursor";
      String storageKey = "oss://patra-ingest/pubmed/batch5.json";

      // When: 创建成功结果
      BatchResult result = BatchResult.success(batchNo, fetchedCount, nextCursorToken, storageKey);

      // Then: 应该正确设置
      assertThat(result.batchNo()).isEqualTo(5);
      assertThat(result.success()).isTrue();
      assertThat(result.fetchedCount()).isEqualTo(1000);
      assertThat(result.nextCursorToken()).isEqualTo(nextCursorToken);
      assertThat(result.storageKey()).isEqualTo(storageKey);
    }

    @Test
    @DisplayName("业务场景 - 最后一批的成功结果")
    void shouldCreateSuccessResultForLastBatch() {
      // Given: 最后一批,没有下一批次游标
      int batchNo = 10;
      int fetchedCount = 250;
      String storageKey = "oss://patra-ingest/pubmed/batch10-final.json";

      // When: 创建成功结果,nextCursorToken = null
      BatchResult result = BatchResult.success(batchNo, fetchedCount, null, storageKey);

      // Then: 应该正确设置
      assertThat(result.batchNo()).isEqualTo(10);
      assertThat(result.success()).isTrue();
      assertThat(result.fetchedCount()).isEqualTo(250);
      assertThat(result.nextCursorToken()).isNull();
      assertThat(result.storageKey()).isEqualTo(storageKey);
    }
  }

  @Nested
  @DisplayName("工厂方法: failure()")
  class FailureFactoryMethodTests {

    @Test
    @DisplayName("应该创建失败的批次结果")
    void shouldCreateFailureBatchResult() {
      // When: 调用 failure() 工厂方法
      BatchResult result = BatchResult.failure(3, "API connection timeout after 30s");

      // Then: 应该创建失败批次
      assertThat(result.batchNo()).isEqualTo(3);
      assertThat(result.success()).isFalse();
      assertThat(result.fetchedCount()).isZero();
      assertThat(result.nextCursorToken()).isNull();
      assertThat(result.errorMessage()).isEqualTo("API connection timeout after 30s");
      assertThat(result.storageKey()).isNull();
    }

    @Test
    @DisplayName("应该设置 success 为 false")
    void shouldSetSuccessToFalse() {
      // When: 调用 failure() 工厂方法
      BatchResult result = BatchResult.failure(1, "Error occurred");

      // Then: success 应为 false
      assertThat(result.success()).isFalse();
    }

    @Test
    @DisplayName("应该设置 fetchedCount 为 0")
    void shouldSetFetchedCountToZero() {
      // When: 调用 failure() 工厂方法
      BatchResult result = BatchResult.failure(1, "Error occurred");

      // Then: fetchedCount 应为 0
      assertThat(result.fetchedCount()).isZero();
    }

    @Test
    @DisplayName("应该设置 nextCursorToken 为 null")
    void shouldSetNextCursorTokenToNull() {
      // When: 调用 failure() 工厂方法
      BatchResult result = BatchResult.failure(1, "Error occurred");

      // Then: nextCursorToken 应为 null
      assertThat(result.nextCursorToken()).isNull();
    }

    @Test
    @DisplayName("应该设置 storageKey 为 null")
    void shouldSetStorageKeyToNull() {
      // When: 调用 failure() 工厂方法
      BatchResult result = BatchResult.failure(1, "Error occurred");

      // Then: storageKey 应为 null
      assertThat(result.storageKey()).isNull();
    }

    @Test
    @DisplayName("应该接受不同的错误信息")
    void shouldAcceptDifferentErrorMessages() {
      // When: 创建不同错误信息的失败结果
      BatchResult result1 = BatchResult.failure(1, "HTTP 500 Internal Server Error");
      BatchResult result2 = BatchResult.failure(2, "Rate limit exceeded: 1000 requests per hour");
      BatchResult result3 = BatchResult.failure(3, "Invalid API key");

      // Then: 应该成功创建
      assertThat(result1.errorMessage()).isEqualTo("HTTP 500 Internal Server Error");
      assertThat(result2.errorMessage()).isEqualTo("Rate limit exceeded: 1000 requests per hour");
      assertThat(result3.errorMessage()).isEqualTo("Invalid API key");
    }

    @Test
    @DisplayName("应该遵守 batchNo 验证规则")
    void shouldEnforceBatchNoValidation() {
      // When & Then: batchNo < 1 应抛出异常
      assertThatThrownBy(() -> BatchResult.failure(0, "Error occurred"))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("批次序号必须 >= 1");
    }

    @Test
    @DisplayName("应该遵守 errorMessage 验证规则")
    void shouldEnforceErrorMessageValidation() {
      // When & Then: errorMessage 为 null 应抛出异常
      assertThatThrownBy(() -> BatchResult.failure(1, null))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("失败批次必须提供错误信息");

      // When & Then: errorMessage 为空字符串应抛出异常
      assertThatThrownBy(() -> BatchResult.failure(1, ""))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("失败批次必须提供错误信息");

      // When & Then: errorMessage 为空白字符串应抛出异常
      assertThatThrownBy(() -> BatchResult.failure(1, "   "))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("失败批次必须提供错误信息");
    }

    @Test
    @DisplayName("业务场景 - API 连接超时")
    void shouldCreateFailureResultForConnectionTimeout() {
      // Given: API 连接超时
      int batchNo = 2;
      String errorMessage = "Connection timeout: PubMed API unreachable after 30s";

      // When: 创建失败结果
      BatchResult result = BatchResult.failure(batchNo, errorMessage);

      // Then: 应该正确设置
      assertThat(result.batchNo()).isEqualTo(2);
      assertThat(result.success()).isFalse();
      assertThat(result.errorMessage()).isEqualTo(errorMessage);
    }

    @Test
    @DisplayName("业务场景 - API 限流")
    void shouldCreateFailureResultForRateLimit() {
      // Given: API 限流
      int batchNo = 5;
      String errorMessage = "Rate limit exceeded: 3 requests per second, retry after 60s";

      // When: 创建失败结果
      BatchResult result = BatchResult.failure(batchNo, errorMessage);

      // Then: 应该正确设置
      assertThat(result.batchNo()).isEqualTo(5);
      assertThat(result.success()).isFalse();
      assertThat(result.errorMessage()).isEqualTo(errorMessage);
    }

    @Test
    @DisplayName("业务场景 - 认证失败")
    void shouldCreateFailureResultForAuthenticationError() {
      // Given: 认证失败
      int batchNo = 1;
      String errorMessage = "Authentication failed: invalid API key";

      // When: 创建失败结果
      BatchResult result = BatchResult.failure(batchNo, errorMessage);

      // Then: 应该正确设置
      assertThat(result.batchNo()).isEqualTo(1);
      assertThat(result.success()).isFalse();
      assertThat(result.errorMessage()).isEqualTo(errorMessage);
    }
  }

  @Nested
  @DisplayName("业务方法: hasNextCursor()")
  class HasNextCursorMethodTests {

    @Test
    @DisplayName("应该返回 true - nextCursorToken 非空且非空白")
    void shouldReturnTrueWhenNextCursorTokenIsNotBlank() {
      // Given: 包含有效下一批次游标的批次结果
      BatchResult result =
          BatchResult.success(1, 100, "cursor-next-batch", "oss://bucket/key/batch1.json");

      // When: 调用 hasNextCursor()
      boolean hasNext = result.hasNextCursor();

      // Then: 应返回 true
      assertThat(hasNext).isTrue();
    }

    @Test
    @DisplayName("应该返回 false - nextCursorToken 为 null")
    void shouldReturnFalseWhenNextCursorTokenIsNull() {
      // Given: nextCursorToken 为 null 的批次结果 (最后一批)
      BatchResult result = BatchResult.success(10, 50, null, "oss://bucket/key/batch10.json");

      // When: 调用 hasNextCursor()
      boolean hasNext = result.hasNextCursor();

      // Then: 应返回 false
      assertThat(hasNext).isFalse();
    }

    @Test
    @DisplayName("应该返回 false - nextCursorToken 为空字符串")
    void shouldReturnFalseWhenNextCursorTokenIsEmpty() {
      // Given: nextCursorToken 为空字符串的批次结果
      BatchResult result = new BatchResult(1, true, 100, "", null, "oss://key.json");

      // When: 调用 hasNextCursor()
      boolean hasNext = result.hasNextCursor();

      // Then: 应返回 false
      assertThat(hasNext).isFalse();
    }

    @Test
    @DisplayName("应该返回 false - nextCursorToken 为空白字符串")
    void shouldReturnFalseWhenNextCursorTokenIsBlank() {
      // Given: nextCursorToken 为空白字符串的批次结果
      BatchResult result1 = new BatchResult(1, true, 100, "   ", null, "oss://key.json");
      BatchResult result2 = new BatchResult(2, true, 100, "\t\n", null, "oss://key.json");

      // When: 调用 hasNextCursor()
      boolean hasNext1 = result1.hasNextCursor();
      boolean hasNext2 = result2.hasNextCursor();

      // Then: 应返回 false
      assertThat(hasNext1).isFalse();
      assertThat(hasNext2).isFalse();
    }

    @Test
    @DisplayName("应该返回 true - nextCursorToken 包含前后空白但有内容")
    void shouldReturnTrueWhenNextCursorTokenHasContent() {
      // Given: nextCursorToken 包含前后空白但有实际内容的批次结果
      BatchResult result =
          new BatchResult(1, true, 100, "  cursor-with-spaces  ", null, "oss://key.json");

      // When: 调用 hasNextCursor()
      boolean hasNext = result.hasNextCursor();

      // Then: 应返回 true (isBlank() 会 trim 后判断)
      assertThat(hasNext).isTrue();
    }

    @Test
    @DisplayName("业务场景 - 判断是否继续下一批次")
    void shouldDetermineIfContinueNextBatch() {
      // Given: 两种批次结果
      BatchResult resultWithNext =
          BatchResult.success(1, 1000, "cursor-next", "oss://bucket/key/batch1.json");
      BatchResult resultLastBatch =
          BatchResult.success(2, 500, null, "oss://bucket/key/batch2.json");

      // When & Then: hasNextCursor() 应该区分是否有下一批次
      assertThat(resultWithNext.hasNextCursor()).isTrue(); // 有下一批次
      assertThat(resultLastBatch.hasNextCursor()).isFalse(); // 最后一批
    }

    @Test
    @DisplayName("业务场景 - 失败批次没有下一批次游标")
    void shouldReturnFalseForFailureBatchResult() {
      // Given: 失败批次结果
      BatchResult failureResult = BatchResult.failure(3, "API connection timeout");

      // When: 调用 hasNextCursor()
      boolean hasNext = failureResult.hasNextCursor();

      // Then: 应返回 false
      assertThat(hasNext).isFalse();
    }
  }

  @Nested
  @DisplayName("Record 语义: equals() 和 hashCode()")
  class EqualsAndHashCodeTests {

    @Test
    @DisplayName("equals() - 相同值应相等")
    void shouldBeEqualForSameValues() {
      // Given: 两个相同值的 BatchResult
      BatchResult result1 =
          BatchResult.success(1, 100, "cursor-next", "oss://bucket/key/batch1.json");
      BatchResult result2 =
          BatchResult.success(1, 100, "cursor-next", "oss://bucket/key/batch1.json");

      // When & Then: 应该相等
      assertThat(result1).isEqualTo(result2).hasSameHashCodeAs(result2);
    }

    @Test
    @DisplayName("equals() - 不同 batchNo 应不相等")
    void shouldNotBeEqualForDifferentBatchNo() {
      // Given: batchNo 不同的 BatchResult
      BatchResult result1 = BatchResult.success(1, 100, "cursor", "oss://key.json");
      BatchResult result2 = BatchResult.success(2, 100, "cursor", "oss://key.json");

      // When & Then: 应该不相等
      assertThat(result1).isNotEqualTo(result2);
    }

    @Test
    @DisplayName("equals() - 不同 success 应不相等")
    void shouldNotBeEqualForDifferentSuccess() {
      // Given: success 不同的 BatchResult
      BatchResult successResult = BatchResult.success(1, 100, "cursor", "oss://key.json");
      BatchResult failureResult = BatchResult.failure(1, "Error occurred");

      // When & Then: 应该不相等
      assertThat(successResult).isNotEqualTo(failureResult);
    }

    @Test
    @DisplayName("equals() - 不同 fetchedCount 应不相等")
    void shouldNotBeEqualForDifferentFetchedCount() {
      // Given: fetchedCount 不同的 BatchResult
      BatchResult result1 = BatchResult.success(1, 100, "cursor", "oss://key.json");
      BatchResult result2 = BatchResult.success(1, 200, "cursor", "oss://key.json");

      // When & Then: 应该不相等
      assertThat(result1).isNotEqualTo(result2);
    }

    @Test
    @DisplayName("equals() - 不同 nextCursorToken 应不相等")
    void shouldNotBeEqualForDifferentNextCursorToken() {
      // Given: nextCursorToken 不同的 BatchResult
      BatchResult result1 = BatchResult.success(1, 100, "cursor1", "oss://key.json");
      BatchResult result2 = BatchResult.success(1, 100, "cursor2", "oss://key.json");

      // When & Then: 应该不相等
      assertThat(result1).isNotEqualTo(result2);
    }

    @Test
    @DisplayName("equals() - 不同 errorMessage 应不相等")
    void shouldNotBeEqualForDifferentErrorMessage() {
      // Given: errorMessage 不同的 BatchResult
      BatchResult result1 = BatchResult.failure(1, "Error 1");
      BatchResult result2 = BatchResult.failure(1, "Error 2");

      // When & Then: 应该不相等
      assertThat(result1).isNotEqualTo(result2);
    }

    @Test
    @DisplayName("equals() - 不同 storageKey 应不相等")
    void shouldNotBeEqualForDifferentStorageKey() {
      // Given: storageKey 不同的 BatchResult
      BatchResult result1 = BatchResult.success(1, 100, "cursor", "oss://key1.json");
      BatchResult result2 = BatchResult.success(1, 100, "cursor", "oss://key2.json");

      // When & Then: 应该不相等
      assertThat(result1).isNotEqualTo(result2);
    }

    @Test
    @DisplayName("equals() - 与自身比较应返回 true")
    void shouldBeEqualToItself() {
      // Given: 一个 BatchResult
      BatchResult result = BatchResult.success(1, 100, "cursor", "oss://key.json");

      // When & Then: 与自身比较应相等
      assertThat(result).isEqualTo(result);
    }

    @Test
    @DisplayName("equals() - 与 null 比较应返回 false")
    void shouldNotBeEqualToNull() {
      // Given: 一个 BatchResult
      BatchResult result = BatchResult.success(1, 100, "cursor", "oss://key.json");

      // When & Then: 与 null 比较应不相等
      assertThat(result).isNotEqualTo(null);
    }

    @Test
    @DisplayName("equals() - 与不同类型比较应返回 false")
    void shouldNotBeEqualToDifferentType() {
      // Given: 一个 BatchResult 和一个 String
      BatchResult result = BatchResult.success(1, 100, "cursor", "oss://key.json");
      String other = "not a batch result";

      // When & Then: 与不同类型比较应不相等
      assertThat(result).isNotEqualTo(other);
    }

    @Test
    @DisplayName("hashCode() - 相同值应有相同的哈希码")
    void shouldHaveSameHashCodeForSameValues() {
      // Given: 两个相同值的 BatchResult
      BatchResult result1 = BatchResult.success(1, 100, "cursor", "oss://key.json");
      BatchResult result2 = BatchResult.success(1, 100, "cursor", "oss://key.json");

      // When & Then: 哈希码应相同
      assertThat(result1.hashCode()).isEqualTo(result2.hashCode());
    }

    @Test
    @DisplayName("hashCode() - 不同值通常应有不同的哈希码")
    void shouldHaveDifferentHashCodeForDifferentValues() {
      // Given: 两个不同值的 BatchResult
      BatchResult result1 = BatchResult.success(1, 100, "cursor", "oss://key.json");
      BatchResult result2 = BatchResult.success(2, 100, "cursor", "oss://key.json");

      // When & Then: 哈希码通常不同 (不是绝对保证,但概率很高)
      assertThat(result1.hashCode()).isNotEqualTo(result2.hashCode());
    }

    @Test
    @DisplayName("hashCode() - 多次调用应返回相同值")
    void shouldHaveConsistentHashCode() {
      // Given: 一个 BatchResult
      BatchResult result = BatchResult.success(1, 100, "cursor", "oss://key.json");

      // When: 多次调用 hashCode()
      int hashCode1 = result.hashCode();
      int hashCode2 = result.hashCode();
      int hashCode3 = result.hashCode();

      // Then: 应返回相同值
      assertThat(hashCode1).isEqualTo(hashCode2).isEqualTo(hashCode3);
    }

    @Test
    @DisplayName("应该在 HashSet 中正确工作 - 验证 equals 和 hashCode 契约")
    void shouldWorkCorrectlyInHashSet() {
      // Given: 多个 BatchResult
      BatchResult result1 = BatchResult.success(1, 100, "cursor", "oss://key.json");
      BatchResult result2 = BatchResult.success(1, 100, "cursor", "oss://key.json"); // 相同值
      BatchResult result3 = BatchResult.success(2, 100, "cursor", "oss://key.json"); // 不同值

      // When: 添加到 HashSet
      Set<BatchResult> set = new HashSet<>();
      set.add(result1);
      set.add(result2); // 应该被去重
      set.add(result3);

      // Then: Set 应该只包含 2 个不同的值
      assertThat(set).hasSize(2).contains(result1, result3);
    }
  }

  @Nested
  @DisplayName("Record 语义: toString()")
  class ToStringTests {

    @Test
    @DisplayName("toString() 应包含所有字段信息 - 成功批次")
    void shouldIncludeAllFieldsInToStringForSuccessResult() {
      // Given: 一个包含所有字段的成功批次结果
      BatchResult result =
          BatchResult.success(5, 1000, "cursor-next-batch", "oss://bucket/key/batch5.json");

      // When: 调用 toString()
      String resultString = result.toString();

      // Then: 应包含所有字段名和值
      assertThat(resultString)
          .contains("BatchResult")
          .contains("batchNo=5")
          .contains("success=true")
          .contains("fetchedCount=1000")
          .contains("nextCursorToken=cursor-next-batch")
          .contains("storageKey=oss://bucket/key/batch5.json");
    }

    @Test
    @DisplayName("toString() 应包含所有字段信息 - 失败批次")
    void shouldIncludeAllFieldsInToStringForFailureResult() {
      // Given: 一个失败批次结果
      BatchResult result = BatchResult.failure(3, "Connection timeout");

      // When: 调用 toString()
      String resultString = result.toString();

      // Then: 应包含所有字段名和值
      assertThat(resultString)
          .contains("BatchResult")
          .contains("batchNo=3")
          .contains("success=false")
          .contains("fetchedCount=0")
          .contains("errorMessage=Connection timeout");
    }

    @Test
    @DisplayName("toString() 应正确显示 null 值")
    void shouldShowNullValuesInToString() {
      // Given: 一个包含 null 字段的 BatchResult
      BatchResult result = BatchResult.success(1, 100, null, null);

      // When: 调用 toString()
      String resultString = result.toString();

      // Then: 应显示 null
      assertThat(resultString).contains("nextCursorToken=null").contains("storageKey=null");
    }
  }

  @Nested
  @DisplayName("Record 语义: 组件访问器")
  class ComponentAccessorTests {

    @Test
    @DisplayName("batchNo() 应返回批次序号")
    void shouldReturnBatchNo() {
      // Given: 一个 BatchResult
      BatchResult result = BatchResult.success(42, 100, "cursor", "oss://key.json");

      // When: 调用 batchNo()
      int batchNo = result.batchNo();

      // Then: 应返回正确的值
      assertThat(batchNo).isEqualTo(42);
    }

    @Test
    @DisplayName("success() 应返回成功标识")
    void shouldReturnSuccess() {
      // Given: 一个成功的 BatchResult
      BatchResult successResult = BatchResult.success(1, 100, "cursor", "oss://key.json");

      // When: 调用 success()
      boolean success = successResult.success();

      // Then: 应返回 true
      assertThat(success).isTrue();
    }

    @Test
    @DisplayName("fetchedCount() 应返回获取记录数")
    void shouldReturnFetchedCount() {
      // Given: 一个 BatchResult
      BatchResult result = BatchResult.success(1, 500, "cursor", "oss://key.json");

      // When: 调用 fetchedCount()
      int fetchedCount = result.fetchedCount();

      // Then: 应返回正确的值
      assertThat(fetchedCount).isEqualTo(500);
    }

    @Test
    @DisplayName("nextCursorToken() 应返回下一批次游标令牌")
    void shouldReturnNextCursorToken() {
      // Given: 一个 BatchResult
      String expectedToken = "cursor-next-batch";
      BatchResult result = BatchResult.success(1, 100, expectedToken, "oss://key.json");

      // When: 调用 nextCursorToken()
      String token = result.nextCursorToken();

      // Then: 应返回正确的值
      assertThat(token).isEqualTo(expectedToken);
    }

    @Test
    @DisplayName("errorMessage() 应返回错误详情")
    void shouldReturnErrorMessage() {
      // Given: 一个失败的 BatchResult
      String expectedError = "Connection timeout";
      BatchResult result = BatchResult.failure(1, expectedError);

      // When: 调用 errorMessage()
      String errorMessage = result.errorMessage();

      // Then: 应返回正确的值
      assertThat(errorMessage).isEqualTo(expectedError);
    }

    @Test
    @DisplayName("storageKey() 应返回存储位置")
    void shouldReturnStorageKey() {
      // Given: 一个 BatchResult
      String expectedStorageKey = "oss://bucket/key/batch1.json";
      BatchResult result = BatchResult.success(1, 100, "cursor", expectedStorageKey);

      // When: 调用 storageKey()
      String storageKey = result.storageKey();

      // Then: 应返回正确的值
      assertThat(storageKey).isEqualTo(expectedStorageKey);
    }

    @Test
    @DisplayName("组件访问器应返回不可变的引用")
    void shouldReturnImmutableReferences() {
      // Given: 一个 BatchResult
      BatchResult result = BatchResult.success(1, 100, "cursor", "oss://key.json");

      // When: 多次调用组件访问器
      String cursor1 = result.nextCursorToken();
      String cursor2 = result.nextCursorToken();
      String storageKey1 = result.storageKey();
      String storageKey2 = result.storageKey();

      // Then: 应返回相同的引用 (Record 组件是 final 的)
      assertThat(cursor1).isSameAs(cursor2);
      assertThat(storageKey1).isSameAs(storageKey2);
    }
  }

  @Nested
  @DisplayName("不变性保证")
  class ImmutabilityTests {

    @Test
    @DisplayName("类应该是 final - 防止子类破坏不变性")
    void shouldBeFinalClass() {
      // When & Then: BatchResult 应该是 final 类 (Record 自动 final)
      assertThat(BatchResult.class).isFinal();
    }

    @Test
    @DisplayName("字段应该是 final - 确保不可变")
    void shouldHaveFinalFields() throws NoSuchFieldException {
      // When & Then: 所有字段应该是 final (Record 自动 final)
      assertThat(BatchResult.class.getDeclaredField("batchNo"))
          .matches(field -> java.lang.reflect.Modifier.isFinal(field.getModifiers()));
      assertThat(BatchResult.class.getDeclaredField("success"))
          .matches(field -> java.lang.reflect.Modifier.isFinal(field.getModifiers()));
      assertThat(BatchResult.class.getDeclaredField("fetchedCount"))
          .matches(field -> java.lang.reflect.Modifier.isFinal(field.getModifiers()));
      assertThat(BatchResult.class.getDeclaredField("nextCursorToken"))
          .matches(field -> java.lang.reflect.Modifier.isFinal(field.getModifiers()));
      assertThat(BatchResult.class.getDeclaredField("errorMessage"))
          .matches(field -> java.lang.reflect.Modifier.isFinal(field.getModifiers()));
      assertThat(BatchResult.class.getDeclaredField("storageKey"))
          .matches(field -> java.lang.reflect.Modifier.isFinal(field.getModifiers()));
    }

    @Test
    @DisplayName("Record 应该是深度不可变的 (所有字段都是基本类型或不可变类型)")
    void shouldBeDeeplyImmutable() {
      // Given: 一个 BatchResult
      BatchResult result = BatchResult.success(1, 100, "cursor", "oss://key.json");

      // When: 获取字段值
      int batchNo = result.batchNo();
      boolean success = result.success();
      int fetchedCount = result.fetchedCount();

      // Then: 基本类型字段是不可变的 (int, boolean 是值类型)
      assertThat(batchNo).isEqualTo(1);
      assertThat(success).isTrue();
      assertThat(fetchedCount).isEqualTo(100);

      // And: String 字段是不可变的 (String 是 immutable)
      assertThat(result.nextCursorToken()).isEqualTo("cursor");
      assertThat(result.storageKey()).isEqualTo("oss://key.json");
    }
  }

  @Nested
  @DisplayName("边界条件测试")
  class BoundaryConditionTests {

    @Test
    @DisplayName("应该接受 batchNo = 1 (最小有效值)")
    void shouldAcceptMinimumValidBatchNo() {
      // When: batchNo = 1
      BatchResult result = BatchResult.success(1, 100, "cursor", "oss://key.json");

      // Then: 应该成功创建
      assertThat(result.batchNo()).isEqualTo(1);
    }

    @Test
    @DisplayName("应该接受极大的 batchNo")
    void shouldAcceptVeryLargeBatchNo() {
      // When: batchNo = Integer.MAX_VALUE
      BatchResult result = BatchResult.success(Integer.MAX_VALUE, 100, "cursor", "oss://key.json");

      // Then: 应该成功创建
      assertThat(result.batchNo()).isEqualTo(Integer.MAX_VALUE);
    }

    @Test
    @DisplayName("应该接受 fetchedCount = 0 (空批次)")
    void shouldAcceptZeroFetchedCount() {
      // When: fetchedCount = 0
      BatchResult result = BatchResult.success(1, 0, null, "oss://key.json");

      // Then: 应该成功创建
      assertThat(result.fetchedCount()).isZero();
    }

    @Test
    @DisplayName("应该接受极大的 fetchedCount")
    void shouldAcceptVeryLargeFetchedCount() {
      // When: fetchedCount = Integer.MAX_VALUE
      BatchResult result = BatchResult.success(1, Integer.MAX_VALUE, "cursor", "oss://key.json");

      // Then: 应该成功创建
      assertThat(result.fetchedCount()).isEqualTo(Integer.MAX_VALUE);
    }

    @Test
    @DisplayName("应该接受极长的 nextCursorToken")
    void shouldAcceptVeryLongNextCursorToken() {
      // Given: 极长的游标令牌 (模拟 Base64 编码的长字符串)
      String longCursor = "A".repeat(5000);

      // When: 创建 BatchResult
      BatchResult result = BatchResult.success(1, 100, longCursor, "oss://key.json");

      // Then: 应该成功创建
      assertThat(result.nextCursorToken()).hasSize(5000);
    }

    @Test
    @DisplayName("应该接受极长的 errorMessage")
    void shouldAcceptVeryLongErrorMessage() {
      // Given: 极长的错误信息
      String longError = "Error: " + "x".repeat(10000);

      // When: 创建 BatchResult
      BatchResult result = BatchResult.failure(1, longError);

      // Then: 应该成功创建
      assertThat(result.errorMessage()).hasSize(10007);
    }

    @Test
    @DisplayName("应该接受极长的 storageKey")
    void shouldAcceptVeryLongStorageKey() {
      // Given: 极长的存储键 (模拟深层路径)
      String longStorageKey = "oss://bucket/" + "path/".repeat(500) + "file.json";

      // When: 创建 BatchResult
      BatchResult result = BatchResult.success(1, 100, "cursor", longStorageKey);

      // Then: 应该成功创建
      assertThat(result.storageKey()).startsWith("oss://bucket/");
    }
  }

  @Nested
  @DisplayName("集成场景测试")
  class IntegrationScenarioTests {

    @Test
    @DisplayName("业务场景 - 连续成功批次构建 (batchNo 递增)")
    void shouldBuildSequentialSuccessBatchResults() {
      // Given: 第一批次
      BatchResult batch1 =
          BatchResult.success(1, 1000, "cursor-batch2", "oss://bucket/pubmed/batch1.json");

      // When: 构建后续批次 (batchNo 递增)
      BatchResult batch2 =
          BatchResult.success(2, 1000, "cursor-batch3", "oss://bucket/pubmed/batch2.json");
      BatchResult batch3 = BatchResult.success(3, 500, null, "oss://bucket/pubmed/batch3.json");

      // Then: 批次序号应该递增
      assertThat(batch1.batchNo()).isEqualTo(1);
      assertThat(batch2.batchNo()).isEqualTo(2);
      assertThat(batch3.batchNo()).isEqualTo(3);

      // And: 最后一批没有下一批次游标
      assertThat(batch1.hasNextCursor()).isTrue();
      assertThat(batch2.hasNextCursor()).isTrue();
      assertThat(batch3.hasNextCursor()).isFalse();
    }

    @Test
    @DisplayName("业务场景 - 批次执行中途失败")
    void shouldHandleMidExecutionFailure() {
      // Given: 前两批次成功
      BatchResult batch1 =
          BatchResult.success(1, 1000, "cursor-batch2", "oss://bucket/pubmed/batch1.json");
      BatchResult batch2 =
          BatchResult.success(2, 1000, "cursor-batch3", "oss://bucket/pubmed/batch2.json");

      // When: 第三批次失败
      BatchResult batch3 = BatchResult.failure(3, "Rate limit exceeded: retry after 60s");

      // Then: 失败批次应该正确设置
      assertThat(batch3.success()).isFalse();
      assertThat(batch3.fetchedCount()).isZero();
      assertThat(batch3.hasNextCursor()).isFalse();
      assertThat(batch3.errorMessage()).isEqualTo("Rate limit exceeded: retry after 60s");
    }

    @Test
    @DisplayName("业务场景 - 批次结果去重")
    void shouldDeduplicateBatchResults() {
      // Given: 批量生成批次结果
      Set<BatchResult> results = new HashSet<>();
      results.add(BatchResult.success(1, 100, "cursor", "oss://key1.json"));
      results.add(BatchResult.success(1, 100, "cursor", "oss://key1.json")); // 重复
      results.add(BatchResult.success(2, 100, "cursor", "oss://key2.json"));

      // Then: Set 应该自动去重
      assertThat(results).hasSize(2);
    }

    @Test
    @DisplayName("业务场景 - 统计批次执行结果")
    void shouldCalculateExecutionStatistics() {
      // Given: 一批批次执行结果
      BatchResult batch1 =
          BatchResult.success(1, 1000, "cursor-batch2", "oss://bucket/pubmed/batch1.json");
      BatchResult batch2 =
          BatchResult.success(2, 1000, "cursor-batch3", "oss://bucket/pubmed/batch2.json");
      BatchResult batch3 = BatchResult.failure(3, "Connection timeout");
      BatchResult batch4 = BatchResult.success(4, 500, null, "oss://bucket/pubmed/batch4.json");

      // When: 统计结果
      Set<BatchResult> results = Set.of(batch1, batch2, batch3, batch4);
      long successCount = results.stream().filter(BatchResult::success).count();
      long failureCount = results.stream().filter(r -> !r.success()).count();
      int totalFetched =
          results.stream().filter(BatchResult::success).mapToInt(BatchResult::fetchedCount).sum();

      // Then: 应该正确统计
      assertThat(successCount).isEqualTo(3);
      assertThat(failureCount).isEqualTo(1);
      assertThat(totalFetched).isEqualTo(2500); // 1000 + 1000 + 500
    }

    @Test
    @DisplayName("业务场景 - 判断批次是否完成 (没有下一批次)")
    void shouldDetermineIfExecutionCompleted() {
      // Given: 一系列批次结果
      BatchResult batch1 =
          BatchResult.success(1, 1000, "cursor-batch2", "oss://bucket/pubmed/batch1.json");
      BatchResult batch2 =
          BatchResult.success(2, 1000, "cursor-batch3", "oss://bucket/pubmed/batch2.json");
      BatchResult batch3 = BatchResult.success(3, 500, null, "oss://bucket/pubmed/batch3.json");

      // When: 检查是否完成
      boolean isCompleted = !batch3.hasNextCursor();

      // Then: 应该判断为完成
      assertThat(isCompleted).isTrue();
    }

    @Test
    @DisplayName("业务场景 - 空批次结果 (fetchedCount = 0)")
    void shouldHandleEmptyBatchResult() {
      // Given: 查询结果为空的批次
      BatchResult emptyBatch = BatchResult.success(1, 0, null, "oss://bucket/empty.json");

      // When & Then: 应该正确处理
      assertThat(emptyBatch.success()).isTrue();
      assertThat(emptyBatch.fetchedCount()).isZero();
      assertThat(emptyBatch.hasNextCursor()).isFalse();
    }
  }
}
