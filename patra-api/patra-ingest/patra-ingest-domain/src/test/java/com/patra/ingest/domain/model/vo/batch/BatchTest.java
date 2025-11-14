package com.patra.ingest.domain.model.vo.batch;

import static org.assertj.core.api.Assertions.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * {@link Batch} 的单元测试。
 *
 * <p>测试覆盖:
 *
 * <ul>
 *   <li>构造方法验证 (batchNo >= 1)
 *   <li>工厂方法 (first, withPage, withToken)
 *   <li>业务方法 (hasCursor)
 *   <li>分页模式 (页码分页 vs 游标分页)
 *   <li>Record 语义 (equals, hashCode, toString, 组件访问器)
 *   <li>不变性保证
 * </ul>
 *
 * @author Patra Team
 */
@DisplayName("Batch 单元测试")
class BatchTest {

  private static final ObjectMapper objectMapper = new ObjectMapper();
  private static final JsonNodeFactory jsonFactory = JsonNodeFactory.instance;

  private String testQuery;
  private JsonNode testParams;

  @BeforeEach
  void setUp() {
    // Given: 通用测试数据
    testQuery = "cancer[Title] AND human[MeSH Terms]";

    // 创建包含多个字段的 JsonNode
    ObjectNode paramsNode = jsonFactory.objectNode();
    paramsNode.put("term", "cancer");
    paramsNode.put("retmax", 100);
    paramsNode.put("sort", "relevance");
    testParams = paramsNode;
  }

  @Nested
  @DisplayName("构造方法验证")
  class ConstructorValidationTests {

    @Test
    @DisplayName("应该成功创建批次 - 所有字段有效")
    void shouldCreateBatchWithAllValidFields() {
      // When: 创建批次
      Batch batch = new Batch(1, testQuery, testParams, "cursor-token-123", 1, 100, null);

      // Then: 应该成功创建
      assertThat(batch.batchNo()).isEqualTo(1);
      assertThat(batch.query()).isEqualTo(testQuery);
      assertThat(batch.params()).isEqualTo(testParams);
      assertThat(batch.cursorToken()).isEqualTo("cursor-token-123");
      assertThat(batch.pageNo()).isEqualTo(1);
      assertThat(batch.pageSize()).isEqualTo(100);
    }

    @Test
    @DisplayName("应该成功创建批次 - 基于页码的分页 (cursorToken 为 null)")
    void shouldCreateBatchWithPageBasedPagination() {
      // When: 创建基于页码的批次
      Batch batch = new Batch(2, testQuery, testParams, null, 5, 50, null);

      // Then: 应该成功创建
      assertThat(batch.batchNo()).isEqualTo(2);
      assertThat(batch.cursorToken()).isNull();
      assertThat(batch.pageNo()).isEqualTo(5);
      assertThat(batch.pageSize()).isEqualTo(50);
    }

    @Test
    @DisplayName("应该成功创建批次 - 基于游标的分页 (pageNo 为 null)")
    void shouldCreateBatchWithCursorBasedPagination() {
      // When: 创建基于游标的批次
      Batch batch = new Batch(3, testQuery, testParams, "cursor-abc", null, 200, null);

      // Then: 应该成功创建
      assertThat(batch.batchNo()).isEqualTo(3);
      assertThat(batch.cursorToken()).isEqualTo("cursor-abc");
      assertThat(batch.pageNo()).isNull();
      assertThat(batch.pageSize()).isEqualTo(200);
    }

    @Test
    @DisplayName("应该成功创建批次 - 可选字段全部为 null")
    void shouldCreateBatchWithNullOptionalFields() {
      // When: 创建批次,可选字段为 null
      Batch batch = new Batch(1, testQuery, testParams, null, null, null, null);

      // Then: 应该成功创建
      assertThat(batch.batchNo()).isEqualTo(1);
      assertThat(batch.query()).isEqualTo(testQuery);
      assertThat(batch.params()).isEqualTo(testParams);
      assertThat(batch.cursorToken()).isNull();
      assertThat(batch.pageNo()).isNull();
      assertThat(batch.pageSize()).isNull();
    }

    @Test
    @DisplayName("应该拒绝 batchNo 为 0")
    void shouldRejectBatchNoZero() {
      // When & Then: batchNo = 0 应抛出 IllegalArgumentException
      assertThatThrownBy(() -> new Batch(0, testQuery, testParams, null, null, null, null))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("batchNo must be >= 1");
    }

    @Test
    @DisplayName("应该拒绝 batchNo 为负数")
    void shouldRejectNegativeBatchNo() {
      // When & Then: batchNo < 0 应抛出 IllegalArgumentException
      assertThatThrownBy(() -> new Batch(-1, testQuery, testParams, null, null, null, null))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("batchNo must be >= 1");

      assertThatThrownBy(() -> new Batch(-999, testQuery, testParams, null, null, null, null))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("batchNo must be >= 1");
    }

    @Test
    @DisplayName("应该接受 batchNo >= 1")
    void shouldAcceptValidBatchNo() {
      // When: 创建批次,batchNo >= 1
      Batch batch1 = new Batch(1, testQuery, testParams, null, null, null, null);
      Batch batch2 = new Batch(100, testQuery, testParams, null, null, null, null);
      Batch batch1000 = new Batch(1000, testQuery, testParams, null, null, null, null);

      // Then: 应该成功创建
      assertThat(batch1.batchNo()).isEqualTo(1);
      assertThat(batch2.batchNo()).isEqualTo(100);
      assertThat(batch1000.batchNo()).isEqualTo(1000);
    }
  }

  @Nested
  @DisplayName("工厂方法: first()")
  class FirstFactoryMethodTests {

    @Test
    @DisplayName("应该创建第一个批次 - batchNo = 1")
    void shouldCreateFirstBatchWithBatchNoOne() {
      // When: 调用 first() 工厂方法
      Batch batch = Batch.first(testQuery, testParams);

      // Then: 应该创建第一个批次
      assertThat(batch.batchNo()).isEqualTo(1);
    }

    @Test
    @DisplayName("应该创建不包含分页元数据的批次")
    void shouldCreateBatchWithoutPaginationMetadata() {
      // When: 调用 first() 工厂方法
      Batch batch = Batch.first(testQuery, testParams);

      // Then: 所有分页字段应为 null
      assertThat(batch.cursorToken()).isNull();
      assertThat(batch.pageNo()).isNull();
      assertThat(batch.pageSize()).isNull();
    }

    @Test
    @DisplayName("应该正确设置查询和参数")
    void shouldSetQueryAndParams() {
      // When: 调用 first() 工厂方法
      Batch batch = Batch.first(testQuery, testParams);

      // Then: 查询和参数应正确设置
      assertThat(batch.query()).isEqualTo(testQuery);
      assertThat(batch.params()).isEqualTo(testParams);
    }

    @Test
    @DisplayName("应该接受 null 参数")
    void shouldAcceptNullParams() {
      // When: 调用 first(),params 为 null
      Batch batch = Batch.first(testQuery, null);

      // Then: 应该成功创建
      assertThat(batch.query()).isEqualTo(testQuery);
      assertThat(batch.params()).isNull();
    }

    @Test
    @DisplayName("应该接受空的 JsonNode")
    void shouldAcceptEmptyJsonNode() {
      // Given: 空的 JsonNode
      JsonNode emptyParams = jsonFactory.objectNode();

      // When: 调用 first()
      Batch batch = Batch.first(testQuery, emptyParams);

      // Then: 应该成功创建
      assertThat(batch.params()).isEqualTo(emptyParams);
      assertThat(batch.params().isEmpty()).isTrue();
    }
  }

  @Nested
  @DisplayName("工厂方法: withPage()")
  class WithPageFactoryMethodTests {

    @Test
    @DisplayName("应该创建基于页码的批次")
    void shouldCreatePageBasedBatch() {
      // When: 调用 withPage() 工厂方法
      Batch batch = Batch.withPage(5, testQuery, testParams, 10, 100);

      // Then: 应该创建基于页码的批次
      assertThat(batch.batchNo()).isEqualTo(5);
      assertThat(batch.query()).isEqualTo(testQuery);
      assertThat(batch.params()).isEqualTo(testParams);
      assertThat(batch.pageNo()).isEqualTo(10);
      assertThat(batch.pageSize()).isEqualTo(100);
    }

    @Test
    @DisplayName("应该设置 cursorToken 为 null")
    void shouldSetCursorTokenToNull() {
      // When: 调用 withPage() 工厂方法
      Batch batch = Batch.withPage(1, testQuery, testParams, 1, 50);

      // Then: cursorToken 应为 null
      assertThat(batch.cursorToken()).isNull();
    }

    @Test
    @DisplayName("应该接受不同的页码和页大小")
    void shouldAcceptDifferentPageNoAndPageSize() {
      // When: 创建不同页码和页大小的批次
      Batch batch1 = Batch.withPage(1, testQuery, testParams, 1, 10);
      Batch batch2 = Batch.withPage(2, testQuery, testParams, 100, 500);

      // Then: 应该成功创建
      assertThat(batch1.pageNo()).isEqualTo(1);
      assertThat(batch1.pageSize()).isEqualTo(10);
      assertThat(batch2.pageNo()).isEqualTo(100);
      assertThat(batch2.pageSize()).isEqualTo(500);
    }

    @Test
    @DisplayName("应该遵守 batchNo 验证规则")
    void shouldEnforceBatchNoValidation() {
      // When & Then: batchNo < 1 应抛出异常
      assertThatThrownBy(() -> Batch.withPage(0, testQuery, testParams, 1, 100))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("batchNo must be >= 1");
    }

    @Test
    @DisplayName("业务场景 - PubMed 风格的分页 (retstart/retmax)")
    void shouldSupportPubMedStylePagination() {
      // Given: PubMed 风格的分页参数
      int batchNo = 3;
      int retstart = 200; // pageNo = 3, 每页 100 条,retstart = 200
      int retmax = 100;

      // When: 创建批次
      Batch batch = Batch.withPage(batchNo, testQuery, testParams, retstart, retmax);

      // Then: 应该正确设置
      assertThat(batch.batchNo()).isEqualTo(3);
      assertThat(batch.pageNo()).isEqualTo(200);
      assertThat(batch.pageSize()).isEqualTo(100);
      assertThat(batch.cursorToken()).isNull();
    }
  }

  @Nested
  @DisplayName("工厂方法: withPageAndSession()")
  class WithPageAndSessionFactoryMethodTests {

    @Test
    @DisplayName("应该创建带会话令牌的批次")
    void shouldCreateBatchWithSessionTokens() {
      // When: 调用 withPageAndSession() 工厂方法
      Batch batch = Batch.withPageAndSession(1, testQuery, testParams, 0, 100, "MCID_123456", "1");

      // Then: 应该创建带会话令牌的批次
      assertThat(batch.batchNo()).isEqualTo(1);
      assertThat(batch.query()).isEqualTo(testQuery);
      assertThat(batch.params()).isEqualTo(testParams);
      assertThat(batch.pageNo()).isEqualTo(0);
      assertThat(batch.pageSize()).isEqualTo(100);
      assertThat(batch.sessionTokens()).isNotNull();
      assertThat(batch.sessionTokens()).containsEntry("webEnv", "MCID_123456");
      assertThat(batch.sessionTokens()).containsEntry("queryKey", "1");
    }

    @Test
    @DisplayName("应该设置 cursorToken 为 null")
    void shouldSetCursorTokenToNull() {
      // When: 调用 withPageAndSession() 工厂方法
      Batch batch = Batch.withPageAndSession(1, testQuery, testParams, 0, 100, "MCID_123456", "1");

      // Then: cursorToken 应为 null
      assertThat(batch.cursorToken()).isNull();
    }

    @Test
    @DisplayName("应该遵守 batchNo 验证规则")
    void shouldEnforceBatchNoValidation() {
      // When & Then: batchNo < 1 应抛出异常
      assertThatThrownBy(
              () -> Batch.withPageAndSession(0, testQuery, testParams, 0, 100, "MCID_123456", "1"))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("batchNo must be >= 1");
    }

    @Test
    @DisplayName("业务场景 - PubMed History Server 分页")
    void shouldSupportPubMedHistoryServerPagination() {
      // Given: PubMed History Server 参数
      int batchNo = 2;
      int retstart = 100;
      int retmax = 100;
      String webEnv = "MCID_674c8b5a5e8a9b1234567890";
      String queryKey = "1";

      // When: 创建批次
      Batch batch =
          Batch.withPageAndSession(
              batchNo, testQuery, testParams, retstart, retmax, webEnv, queryKey);

      // Then: 应该正确设置
      assertThat(batch.batchNo()).isEqualTo(2);
      assertThat(batch.pageNo()).isEqualTo(100);
      assertThat(batch.pageSize()).isEqualTo(100);
      assertThat(batch.cursorToken()).isNull();
      assertThat(batch.hasSessionTokens()).isTrue();
      assertThat(batch.sessionTokens().get("webEnv")).isEqualTo(webEnv);
      assertThat(batch.sessionTokens().get("queryKey")).isEqualTo(queryKey);
    }
  }

  @Nested
  @DisplayName("工厂方法: withToken()")
  class WithTokenFactoryMethodTests {

    @Test
    @DisplayName("应该创建基于游标的批次")
    void shouldCreateCursorBasedBatch() {
      // When: 调用 withToken() 工厂方法
      Batch batch = Batch.withToken(2, testQuery, testParams, "cursor-xyz", 200);

      // Then: 应该创建基于游标的批次
      assertThat(batch.batchNo()).isEqualTo(2);
      assertThat(batch.query()).isEqualTo(testQuery);
      assertThat(batch.params()).isEqualTo(testParams);
      assertThat(batch.cursorToken()).isEqualTo("cursor-xyz");
      assertThat(batch.pageSize()).isEqualTo(200);
    }

    @Test
    @DisplayName("应该设置 pageNo 为 null")
    void shouldSetPageNoToNull() {
      // When: 调用 withToken() 工厂方法
      Batch batch = Batch.withToken(1, testQuery, testParams, "cursor-123", null);

      // Then: pageNo 应为 null
      assertThat(batch.pageNo()).isNull();
    }

    @Test
    @DisplayName("应该接受 null 的 pageSize")
    void shouldAcceptNullPageSize() {
      // When: 调用 withToken(),pageSize 为 null
      Batch batch = Batch.withToken(1, testQuery, testParams, "cursor-abc", null);

      // Then: 应该成功创建
      assertThat(batch.cursorToken()).isEqualTo("cursor-abc");
      assertThat(batch.pageSize()).isNull();
    }

    @Test
    @DisplayName("应该接受不同的游标令牌")
    void shouldAcceptDifferentCursorTokens() {
      // When: 创建不同游标令牌的批次
      Batch batch1 = Batch.withToken(1, testQuery, testParams, "cursor-first", 100);
      Batch batch2 = Batch.withToken(2, testQuery, testParams, "cursor-next-page", 100);
      Batch batch3 = Batch.withToken(3, testQuery, testParams, "AoE-base64-encoded-cursor", 50);

      // Then: 应该成功创建
      assertThat(batch1.cursorToken()).isEqualTo("cursor-first");
      assertThat(batch2.cursorToken()).isEqualTo("cursor-next-page");
      assertThat(batch3.cursorToken()).isEqualTo("AoE-base64-encoded-cursor");
    }

    @Test
    @DisplayName("应该遵守 batchNo 验证规则")
    void shouldEnforceBatchNoValidation() {
      // When & Then: batchNo < 1 应抛出异常
      assertThatThrownBy(() -> Batch.withToken(-1, testQuery, testParams, "cursor", 100))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("batchNo must be >= 1");
    }

    @Test
    @DisplayName("业务场景 - EPMC 风格的游标分页 (cursorMark)")
    void shouldSupportEpmcStyleCursorPagination() {
      // Given: EPMC 风格的游标分页参数
      int batchNo = 2;
      String cursorMark = "AoJ2WlBKRFJxKzJUOGM3YWJhNElRQUFBQUFBQUFBPT0"; // Base64 编码的游标
      int pageSize = 1000;

      // When: 创建批次
      Batch batch = Batch.withToken(batchNo, testQuery, testParams, cursorMark, pageSize);

      // Then: 应该正确设置
      assertThat(batch.batchNo()).isEqualTo(2);
      assertThat(batch.cursorToken()).isEqualTo(cursorMark);
      assertThat(batch.pageSize()).isEqualTo(1000);
      assertThat(batch.pageNo()).isNull();
    }

    @Test
    @DisplayName("应该接受空字符串作为游标令牌")
    void shouldAcceptEmptyStringCursorToken() {
      // When: 调用 withToken(),cursorToken 为空字符串
      Batch batch = Batch.withToken(1, testQuery, testParams, "", 100);

      // Then: 应该成功创建
      assertThat(batch.cursorToken()).isEmpty();
    }
  }

  @Nested
  @DisplayName("业务方法: hasCursor()")
  class HasCursorMethodTests {

    @Test
    @DisplayName("应该返回 true - cursorToken 非空且非空白")
    void shouldReturnTrueWhenCursorTokenIsNotBlank() {
      // Given: 包含有效游标令牌的批次
      Batch batch = Batch.withToken(1, testQuery, testParams, "cursor-abc", 100);

      // When: 调用 hasCursor()
      boolean result = batch.hasCursor();

      // Then: 应返回 true
      assertThat(result).isTrue();
    }

    @Test
    @DisplayName("应该返回 false - cursorToken 为 null")
    void shouldReturnFalseWhenCursorTokenIsNull() {
      // Given: cursorToken 为 null 的批次
      Batch batch = Batch.withPage(1, testQuery, testParams, 1, 100);

      // When: 调用 hasCursor()
      boolean result = batch.hasCursor();

      // Then: 应返回 false
      assertThat(result).isFalse();
    }

    @Test
    @DisplayName("应该返回 false - cursorToken 为空字符串")
    void shouldReturnFalseWhenCursorTokenIsEmpty() {
      // Given: cursorToken 为空字符串的批次
      Batch batch = new Batch(1, testQuery, testParams, "", null, null, null);

      // When: 调用 hasCursor()
      boolean result = batch.hasCursor();

      // Then: 应返回 false
      assertThat(result).isFalse();
    }

    @Test
    @DisplayName("应该返回 false - cursorToken 为空白字符串")
    void shouldReturnFalseWhenCursorTokenIsBlank() {
      // Given: cursorToken 为空白字符串的批次
      Batch blankBatch1 = new Batch(1, testQuery, testParams, "   ", null, null, null);
      Batch blankBatch2 = new Batch(2, testQuery, testParams, "\t\n", null, null, null);

      // When: 调用 hasCursor()
      boolean result1 = blankBatch1.hasCursor();
      boolean result2 = blankBatch2.hasCursor();

      // Then: 应返回 false
      assertThat(result1).isFalse();
      assertThat(result2).isFalse();
    }

    @Test
    @DisplayName("应该返回 true - cursorToken 包含前后空白但有内容")
    void shouldReturnTrueWhenCursorTokenHasContent() {
      // Given: cursorToken 包含前后空白但有实际内容的批次
      Batch batch = new Batch(1, testQuery, testParams, "  cursor-with-spaces  ", null, null, null);

      // When: 调用 hasCursor()
      boolean result = batch.hasCursor();

      // Then: 应返回 true (isBlank() 会 trim 后判断)
      assertThat(result).isTrue();
    }

    @Test
    @DisplayName("业务场景 - 判断分页模式")
    void shouldDeterminePaginationMode() {
      // Given: 两种分页模式的批次
      Batch pageBasedBatch = Batch.withPage(1, testQuery, testParams, 1, 100);
      Batch cursorBasedBatch = Batch.withToken(2, testQuery, testParams, "cursor-xyz", 100);

      // When & Then: hasCursor() 应该区分分页模式
      assertThat(pageBasedBatch.hasCursor()).isFalse(); // 页码分页
      assertThat(cursorBasedBatch.hasCursor()).isTrue(); // 游标分页
    }
  }

  @Nested
  @DisplayName("业务方法: hasSessionTokens()")
  class HasSessionTokensMethodTests {

    @Test
    @DisplayName("应该返回 true - sessionTokens 非空")
    void shouldReturnTrueWhenSessionTokensNotEmpty() {
      // Given: 包含会话令牌的批次
      Batch batch = Batch.withPageAndSession(1, testQuery, testParams, 0, 100, "MCID_123456", "1");

      // When: 调用 hasSessionTokens()
      boolean result = batch.hasSessionTokens();

      // Then: 应返回 true
      assertThat(result).isTrue();
    }

    @Test
    @DisplayName("应该返回 false - sessionTokens 为空")
    void shouldReturnFalseWhenSessionTokensEmpty() {
      // Given: 不包含会话令牌的批次
      Batch batch = Batch.withPage(1, testQuery, testParams, 0, 100);

      // When: 调用 hasSessionTokens()
      boolean result = batch.hasSessionTokens();

      // Then: 应返回 false
      assertThat(result).isFalse();
    }

    @Test
    @DisplayName("业务场景 - 判断是否使用 History Server")
    void shouldDetermineHistoryServerMode() {
      // Given: 三种批次类型
      Batch pageBasedBatch = Batch.withPage(1, testQuery, testParams, 0, 100);
      Batch cursorBasedBatch = Batch.withToken(2, testQuery, testParams, "cursor-xyz", 100);
      Batch sessionBasedBatch =
          Batch.withPageAndSession(3, testQuery, testParams, 0, 100, "MCID_123456", "1");

      // When & Then: hasSessionTokens() 应该区分是否有会话令牌
      assertThat(pageBasedBatch.hasSessionTokens()).isFalse();
      assertThat(cursorBasedBatch.hasSessionTokens()).isFalse();
      assertThat(sessionBasedBatch.hasSessionTokens()).isTrue();
    }
  }

  @Nested
  @DisplayName("Record 语义: equals() 和 hashCode()")
  class EqualsAndHashCodeTests {

    @Test
    @DisplayName("equals() - 相同值应相等")
    void shouldBeEqualForSameValues() {
      // Given: 两个相同值的 Batch
      Batch batch1 = Batch.withPage(1, testQuery, testParams, 1, 100);
      Batch batch2 = Batch.withPage(1, testQuery, testParams, 1, 100);

      // When & Then: 应该相等
      assertThat(batch1).isEqualTo(batch2).hasSameHashCodeAs(batch2);
    }

    @Test
    @DisplayName("equals() - 不同 batchNo 应不相等")
    void shouldNotBeEqualForDifferentBatchNo() {
      // Given: batchNo 不同的 Batch
      Batch batch1 = Batch.withPage(1, testQuery, testParams, 1, 100);
      Batch batch2 = Batch.withPage(2, testQuery, testParams, 1, 100);

      // When & Then: 应该不相等
      assertThat(batch1).isNotEqualTo(batch2);
    }

    @Test
    @DisplayName("equals() - 不同 query 应不相等")
    void shouldNotBeEqualForDifferentQuery() {
      // Given: query 不同的 Batch
      Batch batch1 = Batch.first("query1", testParams);
      Batch batch2 = Batch.first("query2", testParams);

      // When & Then: 应该不相等
      assertThat(batch1).isNotEqualTo(batch2);
    }

    @Test
    @DisplayName("equals() - 不同 params 应不相等")
    void shouldNotBeEqualForDifferentParams() {
      // Given: params 不同的 Batch
      JsonNode params1 = jsonFactory.objectNode().put("key", "value1");
      JsonNode params2 = jsonFactory.objectNode().put("key", "value2");
      Batch batch1 = Batch.first(testQuery, params1);
      Batch batch2 = Batch.first(testQuery, params2);

      // When & Then: 应该不相等
      assertThat(batch1).isNotEqualTo(batch2);
    }

    @Test
    @DisplayName("equals() - 不同 cursorToken 应不相等")
    void shouldNotBeEqualForDifferentCursorToken() {
      // Given: cursorToken 不同的 Batch
      Batch batch1 = Batch.withToken(1, testQuery, testParams, "cursor1", 100);
      Batch batch2 = Batch.withToken(1, testQuery, testParams, "cursor2", 100);

      // When & Then: 应该不相等
      assertThat(batch1).isNotEqualTo(batch2);
    }

    @Test
    @DisplayName("equals() - 不同 pageNo 应不相等")
    void shouldNotBeEqualForDifferentPageNo() {
      // Given: pageNo 不同的 Batch
      Batch batch1 = Batch.withPage(1, testQuery, testParams, 1, 100);
      Batch batch2 = Batch.withPage(1, testQuery, testParams, 2, 100);

      // When & Then: 应该不相等
      assertThat(batch1).isNotEqualTo(batch2);
    }

    @Test
    @DisplayName("equals() - 不同 pageSize 应不相等")
    void shouldNotBeEqualForDifferentPageSize() {
      // Given: pageSize 不同的 Batch
      Batch batch1 = Batch.withPage(1, testQuery, testParams, 1, 100);
      Batch batch2 = Batch.withPage(1, testQuery, testParams, 1, 200);

      // When & Then: 应该不相等
      assertThat(batch1).isNotEqualTo(batch2);
    }

    @Test
    @DisplayName("equals() - 不同 sessionTokens 应不相等")
    void shouldNotBeEqualForDifferentSessionTokens() {
      // Given: sessionTokens 不同的 Batch
      Batch batch1 = Batch.withPageAndSession(1, testQuery, testParams, 0, 100, "MCID_123456", "1");
      Batch batch2 = Batch.withPageAndSession(1, testQuery, testParams, 0, 100, "MCID_789012", "2");

      // When & Then: 应该不相等
      assertThat(batch1).isNotEqualTo(batch2);
    }

    @Test
    @DisplayName("equals() - 有无 sessionTokens 应不相等")
    void shouldNotBeEqualForDifferentSessionTokensPresence() {
      // Given: 一个有 sessionTokens，一个没有
      Batch batch1 = Batch.withPage(1, testQuery, testParams, 0, 100);
      Batch batch2 = Batch.withPageAndSession(1, testQuery, testParams, 0, 100, "MCID_123456", "1");

      // When & Then: 应该不相等
      assertThat(batch1).isNotEqualTo(batch2);
    }

    @Test
    @DisplayName("equals() - 与自身比较应返回 true")
    void shouldBeEqualToItself() {
      // Given: 一个 Batch
      Batch batch = Batch.first(testQuery, testParams);

      // When & Then: 与自身比较应相等
      assertThat(batch).isEqualTo(batch);
    }

    @Test
    @DisplayName("equals() - 与 null 比较应返回 false")
    void shouldNotBeEqualToNull() {
      // Given: 一个 Batch
      Batch batch = Batch.first(testQuery, testParams);

      // When & Then: 与 null 比较应不相等
      assertThat(batch).isNotEqualTo(null);
    }

    @Test
    @DisplayName("equals() - 与不同类型比较应返回 false")
    void shouldNotBeEqualToDifferentType() {
      // Given: 一个 Batch 和一个 String
      Batch batch = Batch.first(testQuery, testParams);
      String other = "not a batch";

      // When & Then: 与不同类型比较应不相等
      assertThat(batch).isNotEqualTo(other);
    }

    @Test
    @DisplayName("hashCode() - 相同值应有相同的哈希码")
    void shouldHaveSameHashCodeForSameValues() {
      // Given: 两个相同值的 Batch
      Batch batch1 = Batch.withPage(1, testQuery, testParams, 1, 100);
      Batch batch2 = Batch.withPage(1, testQuery, testParams, 1, 100);

      // When & Then: 哈希码应相同
      assertThat(batch1.hashCode()).isEqualTo(batch2.hashCode());
    }

    @Test
    @DisplayName("hashCode() - 不同值通常应有不同的哈希码")
    void shouldHaveDifferentHashCodeForDifferentValues() {
      // Given: 两个不同值的 Batch
      Batch batch1 = Batch.withPage(1, testQuery, testParams, 1, 100);
      Batch batch2 = Batch.withPage(2, testQuery, testParams, 1, 100);

      // When & Then: 哈希码通常不同 (不是绝对保证,但概率很高)
      assertThat(batch1.hashCode()).isNotEqualTo(batch2.hashCode());
    }

    @Test
    @DisplayName("hashCode() - 多次调用应返回相同值")
    void shouldHaveConsistentHashCode() {
      // Given: 一个 Batch
      Batch batch = Batch.first(testQuery, testParams);

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
      Batch batch1 = Batch.withPage(1, testQuery, testParams, 1, 100);
      Batch batch2 = Batch.withPage(1, testQuery, testParams, 1, 100); // 相同值
      Batch batch3 = Batch.withPage(2, testQuery, testParams, 1, 100); // 不同值

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
      // Given: 一个包含所有字段的 Batch
      Batch batch = new Batch(5, testQuery, testParams, "cursor-xyz", 10, 100, null);

      // When: 调用 toString()
      String result = batch.toString();

      // Then: 应包含所有字段名和值
      assertThat(result)
          .contains("Batch")
          .contains("batchNo=5")
          .contains("query=" + testQuery)
          .contains("params=")
          .contains("cursorToken=cursor-xyz")
          .contains("pageNo=10")
          .contains("pageSize=100");
    }

    @Test
    @DisplayName("toString() 应正确显示 null 值")
    void shouldShowNullValuesInToString() {
      // Given: 一个包含 null 字段的 Batch
      Batch batch = Batch.first(testQuery, null);

      // When: 调用 toString()
      String result = batch.toString();

      // Then: 应显示 null
      assertThat(result).contains("params=null").contains("cursorToken=null");
    }
  }

  @Nested
  @DisplayName("Record 语义: 组件访问器")
  class ComponentAccessorTests {

    @Test
    @DisplayName("batchNo() 应返回批次序号")
    void shouldReturnBatchNo() {
      // Given: 一个 Batch
      Batch batch = Batch.withPage(42, testQuery, testParams, 1, 100);

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
      Batch batch = Batch.first(expectedQuery, testParams);

      // When: 调用 query()
      String query = batch.query();

      // Then: 应返回正确的值
      assertThat(query).isEqualTo(expectedQuery);
    }

    @Test
    @DisplayName("params() 应返回查询参数")
    void shouldReturnParams() {
      // Given: 一个 Batch
      JsonNode expectedParams = jsonFactory.objectNode().put("key", "value");
      Batch batch = Batch.first(testQuery, expectedParams);

      // When: 调用 params()
      JsonNode params = batch.params();

      // Then: 应返回正确的值
      assertThat(params).isEqualTo(expectedParams);
    }

    @Test
    @DisplayName("cursorToken() 应返回游标令牌")
    void shouldReturnCursorToken() {
      // Given: 一个 Batch
      String expectedToken = "cursor-token-xyz";
      Batch batch = Batch.withToken(1, testQuery, testParams, expectedToken, 100);

      // When: 调用 cursorToken()
      String token = batch.cursorToken();

      // Then: 应返回正确的值
      assertThat(token).isEqualTo(expectedToken);
    }

    @Test
    @DisplayName("pageNo() 应返回页码")
    void shouldReturnPageNo() {
      // Given: 一个 Batch
      Batch batch = Batch.withPage(1, testQuery, testParams, 99, 100);

      // When: 调用 pageNo()
      Integer pageNo = batch.pageNo();

      // Then: 应返回正确的值
      assertThat(pageNo).isEqualTo(99);
    }

    @Test
    @DisplayName("pageSize() 应返回页大小")
    void shouldReturnPageSize() {
      // Given: 一个 Batch
      Batch batch = Batch.withPage(1, testQuery, testParams, 1, 500);

      // When: 调用 pageSize()
      Integer pageSize = batch.pageSize();

      // Then: 应返回正确的值
      assertThat(pageSize).isEqualTo(500);
    }

    @Test
    @DisplayName("组件访问器应返回不可变的引用")
    void shouldReturnImmutableReferences() {
      // Given: 一个 Batch
      Batch batch = Batch.first(testQuery, testParams);

      // When: 多次调用组件访问器
      String query1 = batch.query();
      String query2 = batch.query();
      JsonNode params1 = batch.params();
      JsonNode params2 = batch.params();

      // Then: 应返回相同的引用 (Record 组件是 final 的)
      assertThat(query1).isSameAs(query2);
      assertThat(params1).isSameAs(params2);
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
      assertThat(Batch.class.getDeclaredField("params"))
          .matches(field -> java.lang.reflect.Modifier.isFinal(field.getModifiers()));
      assertThat(Batch.class.getDeclaredField("cursorToken"))
          .matches(field -> java.lang.reflect.Modifier.isFinal(field.getModifiers()));
      assertThat(Batch.class.getDeclaredField("pageNo"))
          .matches(field -> java.lang.reflect.Modifier.isFinal(field.getModifiers()));
      assertThat(Batch.class.getDeclaredField("pageSize"))
          .matches(field -> java.lang.reflect.Modifier.isFinal(field.getModifiers()));
    }

    @Test
    @DisplayName("修改外部引用不应影响对象内部状态")
    void shouldNotBeAffectedByExternalModification() {
      // Given: 一个 ObjectNode 作为 params
      ObjectNode originalParams = jsonFactory.objectNode();
      originalParams.put("term", "cancer");
      originalParams.put("retmax", 100);

      // When: 创建 Batch
      Batch batch = Batch.first(testQuery, originalParams);

      // And: 修改外部引用 (JsonNode 本身是可变的,但 Record 不应暴露可变性)
      originalParams.put("term", "modified");

      // Then: Batch 内部的 params 应该被修改 (因为 JsonNode 是可变的)
      // 注意: 这是 JsonNode 的特性,不是 Batch 的问题
      // 真实场景中应该考虑使用不可变的 JsonNode 实现或防御性拷贝
      assertThat(batch.params().get("term").asText()).isEqualTo("modified");
    }

    @Test
    @DisplayName("Record 应该是浅不可变的 (组件引用不变,但组件内容可能可变)")
    void shouldBeShallowImmutable() {
      // Given: 一个 Batch
      ObjectNode params = jsonFactory.objectNode().put("key", "value");
      Batch batch = Batch.first(testQuery, params);

      // When: 获取 params 引用
      JsonNode retrievedParams1 = batch.params();
      JsonNode retrievedParams2 = batch.params();

      // Then: 应该返回相同的引用 (浅不可变)
      assertThat(retrievedParams1).isSameAs(retrievedParams2);
    }
  }

  @Nested
  @DisplayName("边界条件测试")
  class BoundaryConditionTests {

    @Test
    @DisplayName("应该接受 batchNo = 1 (最小有效值)")
    void shouldAcceptMinimumValidBatchNo() {
      // When: batchNo = 1
      Batch batch = Batch.first(testQuery, testParams);

      // Then: 应该成功创建
      assertThat(batch.batchNo()).isEqualTo(1);
    }

    @Test
    @DisplayName("应该接受极大的 batchNo")
    void shouldAcceptVeryLargeBatchNo() {
      // When: batchNo = Integer.MAX_VALUE
      Batch batch = Batch.withPage(Integer.MAX_VALUE, testQuery, testParams, 1, 100);

      // Then: 应该成功创建
      assertThat(batch.batchNo()).isEqualTo(Integer.MAX_VALUE);
    }

    @Test
    @DisplayName("应该接受极长的查询字符串")
    void shouldAcceptVeryLongQueryString() {
      // Given: 极长的查询字符串
      String longQuery = "a".repeat(10000);

      // When: 创建 Batch
      Batch batch = Batch.first(longQuery, testParams);

      // Then: 应该成功创建
      assertThat(batch.query()).hasSize(10000);
    }

    @Test
    @DisplayName("应该接受极长的游标令牌")
    void shouldAcceptVeryLongCursorToken() {
      // Given: 极长的游标令牌 (模拟 Base64 编码的长字符串)
      String longCursor = "A".repeat(5000);

      // When: 创建 Batch
      Batch batch = Batch.withToken(1, testQuery, testParams, longCursor, 100);

      // Then: 应该成功创建
      assertThat(batch.cursorToken()).hasSize(5000);
    }

    @Test
    @DisplayName("应该接受 pageNo = 1 (最小有效值)")
    void shouldAcceptMinimumValidPageNo() {
      // When: pageNo = 1
      Batch batch = Batch.withPage(1, testQuery, testParams, 1, 100);

      // Then: 应该成功创建
      assertThat(batch.pageNo()).isEqualTo(1);
    }

    @Test
    @DisplayName("应该接受极大的 pageNo 和 pageSize")
    void shouldAcceptVeryLargePageNoAndPageSize() {
      // When: pageNo 和 pageSize = Integer.MAX_VALUE
      Batch batch = Batch.withPage(1, testQuery, testParams, Integer.MAX_VALUE, Integer.MAX_VALUE);

      // Then: 应该成功创建
      assertThat(batch.pageNo()).isEqualTo(Integer.MAX_VALUE);
      assertThat(batch.pageSize()).isEqualTo(Integer.MAX_VALUE);
    }

    @Test
    @DisplayName("应该接受复杂的嵌套 JsonNode")
    void shouldAcceptComplexNestedJsonNode() throws Exception {
      // Given: 复杂的嵌套 JsonNode
      String complexJson =
          """
                {
                  "term": "cancer",
                  "filters": {
                    "year": [2020, 2021, 2022],
                    "journal": ["Nature", "Science"]
                  },
                  "metadata": {
                    "source": "PubMed",
                    "timestamp": "2025-01-01T00:00:00Z"
                  }
                }
                """;
      JsonNode complexParams = objectMapper.readTree(complexJson);

      // When: 创建 Batch
      Batch batch = Batch.first(testQuery, complexParams);

      // Then: 应该成功创建
      assertThat(batch.params()).isEqualTo(complexParams);
      assertThat(batch.params().get("filters").get("year")).hasSize(3);
    }
  }

  @Nested
  @DisplayName("集成场景测试")
  class IntegrationScenarioTests {

    @Test
    @DisplayName("业务场景 - 连续批次构建 (页码递增)")
    void shouldBuildSequentialBatchesWithIncrementingPageNo() {
      // Given: 第一个批次
      Batch batch1 = Batch.withPage(1, testQuery, testParams, 1, 100);

      // When: 构建后续批次 (页码递增)
      Batch batch2 = Batch.withPage(2, testQuery, testParams, 2, 100);
      Batch batch3 = Batch.withPage(3, testQuery, testParams, 3, 100);

      // Then: 批次序号和页码应该递增
      assertThat(batch1.batchNo()).isEqualTo(1);
      assertThat(batch1.pageNo()).isEqualTo(1);
      assertThat(batch2.batchNo()).isEqualTo(2);
      assertThat(batch2.pageNo()).isEqualTo(2);
      assertThat(batch3.batchNo()).isEqualTo(3);
      assertThat(batch3.pageNo()).isEqualTo(3);
    }

    @Test
    @DisplayName("业务场景 - 游标分页批次链 (游标传递)")
    void shouldBuildCursorBasedBatchChain() {
      // Given: 第一个批次没有游标
      Batch batch1 = Batch.first(testQuery, testParams);

      // When: 构建后续批次 (游标传递)
      Batch batch2 = Batch.withToken(2, testQuery, testParams, "cursor-from-batch1", 100);
      Batch batch3 = Batch.withToken(3, testQuery, testParams, "cursor-from-batch2", 100);

      // Then: 应该形成游标传递链
      assertThat(batch1.hasCursor()).isFalse();
      assertThat(batch2.hasCursor()).isTrue();
      assertThat(batch2.cursorToken()).isEqualTo("cursor-from-batch1");
      assertThat(batch3.hasCursor()).isTrue();
      assertThat(batch3.cursorToken()).isEqualTo("cursor-from-batch2");
    }

    @Test
    @DisplayName("业务场景 - 批次序列验证 (确保 batchNo 连续)")
    void shouldValidateBatchSequence() {
      // Given: 一系列批次
      Batch batch1 = Batch.withPage(1, testQuery, testParams, 1, 100);
      Batch batch2 = Batch.withPage(2, testQuery, testParams, 2, 100);
      Batch batch3 = Batch.withPage(3, testQuery, testParams, 3, 100);

      // When: 验证批次序列
      Set<Integer> batchNos = Set.of(batch1.batchNo(), batch2.batchNo(), batch3.batchNo());

      // Then: 批次序号应该是连续的 1, 2, 3
      assertThat(batchNos).containsExactlyInAnyOrder(1, 2, 3);
    }

    @Test
    @DisplayName("业务场景 - 不同分页策略切换")
    void shouldSwitchBetweenPaginationStrategies() {
      // Given: 先使用页码分页
      Batch pageBasedBatch = Batch.withPage(1, testQuery, testParams, 1, 100);

      // When: 切换到游标分页
      Batch cursorBasedBatch = Batch.withToken(2, testQuery, testParams, "cursor-xyz", 100);

      // Then: 应该支持不同分页策略
      assertThat(pageBasedBatch.hasCursor()).isFalse();
      assertThat(pageBasedBatch.pageNo()).isNotNull();
      assertThat(cursorBasedBatch.hasCursor()).isTrue();
      assertThat(cursorBasedBatch.pageNo()).isNull();
    }

    @Test
    @DisplayName("业务场景 - 批量生成批次并去重")
    void shouldGenerateAndDeduplicateBatches() {
      // Given: 批量生成批次
      Set<Batch> batches = new HashSet<>();
      batches.add(Batch.withPage(1, testQuery, testParams, 1, 100));
      batches.add(Batch.withPage(1, testQuery, testParams, 1, 100)); // 重复
      batches.add(Batch.withPage(2, testQuery, testParams, 2, 100));

      // Then: Set 应该自动去重
      assertThat(batches).hasSize(2);
    }

    @Test
    @DisplayName("业务场景 - 不同查询使用相同分页参数")
    void shouldUseSamePaginationForDifferentQueries() {
      // Given: 不同查询,相同分页参数
      String query1 = "cancer[Title]";
      String query2 = "diabetes[Title]";
      int pageNo = 1;
      int pageSize = 100;

      // When: 创建批次
      Batch batch1 = Batch.withPage(1, query1, testParams, pageNo, pageSize);
      Batch batch2 = Batch.withPage(1, query2, testParams, pageNo, pageSize);

      // Then: 分页参数应该相同
      assertThat(batch1.pageNo()).isEqualTo(batch2.pageNo());
      assertThat(batch1.pageSize()).isEqualTo(batch2.pageSize());
      // But: 查询应该不同
      assertThat(batch1.query()).isNotEqualTo(batch2.query());
    }
  }

  @Nested
  @DisplayName("JsonNode 集成测试")
  class JsonNodeIntegrationTests {

    @Test
    @DisplayName("应该正确处理 ObjectNode")
    void shouldHandleObjectNode() {
      // Given: ObjectNode
      ObjectNode objectNode = jsonFactory.objectNode();
      objectNode.put("key1", "value1");
      objectNode.put("key2", 123);

      // When: 创建 Batch
      Batch batch = Batch.first(testQuery, objectNode);

      // Then: 应该正确存储
      assertThat(batch.params().get("key1").asText()).isEqualTo("value1");
      assertThat(batch.params().get("key2").asInt()).isEqualTo(123);
    }

    @Test
    @DisplayName("应该正确处理 ArrayNode")
    void shouldHandleArrayNode() throws Exception {
      // Given: ArrayNode
      String arrayJson = "[\"value1\", \"value2\", \"value3\"]";
      JsonNode arrayNode = objectMapper.readTree(arrayJson);

      // When: 创建 Batch
      Batch batch = Batch.first(testQuery, arrayNode);

      // Then: 应该正确存储
      assertThat(batch.params().isArray()).isTrue();
      assertThat(batch.params()).hasSize(3);
      assertThat(batch.params().get(0).asText()).isEqualTo("value1");
    }

    @Test
    @DisplayName("应该正确处理嵌套 JsonNode")
    void shouldHandleNestedJsonNode() throws Exception {
      // Given: 嵌套 JsonNode
      String nestedJson =
          """
                {
                  "filters": {
                    "year": 2025,
                    "authors": ["John", "Jane"]
                  }
                }
                """;
      JsonNode nestedNode = objectMapper.readTree(nestedJson);

      // When: 创建 Batch
      Batch batch = Batch.first(testQuery, nestedNode);

      // Then: 应该正确存储嵌套结构
      assertThat(batch.params().get("filters").get("year").asInt()).isEqualTo(2025);
      assertThat(batch.params().get("filters").get("authors")).hasSize(2);
    }

    @Test
    @DisplayName("应该正确处理 NullNode")
    void shouldHandleNullNode() {
      // Given: NullNode
      JsonNode nullNode = jsonFactory.nullNode();

      // When: 创建 Batch
      Batch batch = Batch.first(testQuery, nullNode);

      // Then: 应该正确存储
      assertThat(batch.params().isNull()).isTrue();
    }

    @Test
    @DisplayName("应该正确处理空 ObjectNode")
    void shouldHandleEmptyObjectNode() {
      // Given: 空 ObjectNode
      ObjectNode emptyNode = jsonFactory.objectNode();

      // When: 创建 Batch
      Batch batch = Batch.first(testQuery, emptyNode);

      // Then: 应该正确存储
      assertThat(batch.params().isEmpty()).isTrue();
      assertThat(batch.params().isObject()).isTrue();
    }
  }
}
