# Adapter 层测试模板

适配器层测试主要包括 REST Controller 和 XXL-Job 测试。

## REST Controller 测试 {#controller}

### 基础 Controller 测试模板

```java
package com.patra.{service}.adapter.rest;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import com.fasterxml.jackson.databind.ObjectMapper;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.*;

@WebMvcTest(ProvenanceController.class)
@DisplayName("ProvenanceController REST 控制器测试")
class ProvenanceControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private ProvenanceOrchestrator orchestrator;

    private CreateProvenanceCommand validCommand;

    @BeforeEach
    void setUp() {
        validCommand = CreateProvenanceCommand.builder()
            .code("PUBMED")
            .name("PubMed Database")
            .config("{}")
            .build();
    }

    @Test
    @DisplayName("应该成功创建 Provenance")
    void shouldCreateProvenance() throws Exception {
        // Arrange
        var result = ProvenanceResult.builder()
            .id(1L)
            .code("PUBMED")
            .build();

        when(orchestrator.create(any())).thenReturn(result);

        // Act & Assert
        mockMvc.perform(post("/api/v1/provenances")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(validCommand)))
            .andDo(print())  // 打印请求和响应详情
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(1))
            .andExpect(jsonPath("$.code").value("PUBMED"));

        verify(orchestrator).create(argThat(cmd ->
            cmd.getCode().equals("PUBMED")
        ));
    }

    @Test
    @DisplayName("应该通过 ID 获取 Provenance")
    void shouldGetProvenanceById() throws Exception {
        // Arrange
        var provenance = ProvenanceDTO.builder()
            .id(1L)
            .code("PUBMED")
            .name("PubMed Database")
            .build();

        when(orchestrator.findById(1L)).thenReturn(provenance);

        // Act & Assert
        mockMvc.perform(get("/api/v1/provenances/1")
                .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(1))
            .andExpect(jsonPath("$.code").value("PUBMED"))
            .andExpect(jsonPath("$.name").value("PubMed Database"));
    }

    @Test
    @DisplayName("应该返回 404 当资源不存在")
    void shouldReturn404WhenNotFound() throws Exception {
        // Arrange
        when(orchestrator.findById(999L))
            .thenThrow(new ResourceNotFoundException("Provenance", "999"));

        // Act & Assert
        mockMvc.perform(get("/api/v1/provenances/999"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.status").value(404))
            .andExpect(jsonPath("$.title").value("Resource Not Found"))
            .andExpect(jsonPath("$.detail").value("Provenance with id '999' not found"));
    }

    @Test
    @DisplayName("应该验证必填字段")
    void shouldValidateRequiredFields() throws Exception {
        // Arrange - 缺少必填字段 code
        var invalidCommand = CreateProvenanceCommand.builder()
            .name("PubMed Database")
            .build();

        // Act & Assert
        mockMvc.perform(post("/api/v1/provenances")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(invalidCommand)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.status").value(400))
            .andExpect(jsonPath("$.violations[0].field").value("code"))
            .andExpect(jsonPath("$.violations[0].message").value("must not be null"));
    }

    @Test
    @DisplayName("应该验证字段长度")
    void shouldValidateFieldLength() throws Exception {
        // Arrange - code 太长
        validCommand.setCode("A".repeat(51));  // 超过最大长度 50

        // Act & Assert
        mockMvc.perform(post("/api/v1/provenances")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(validCommand)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.violations[0].field").value("code"))
            .andExpect(jsonPath("$.violations[0].message").value("size must be between 1 and 50"));
    }

    @Test
    @DisplayName("应该处理业务异常")
    void shouldHandleBusinessException() throws Exception {
        // Arrange
        when(orchestrator.create(any()))
            .thenThrow(new BusinessException("DUPLICATE_CODE", "Code already exists"));

        // Act & Assert
        mockMvc.perform(post("/api/v1/provenances")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(validCommand)))
            .andExpect(status().isConflict())  // 409
            .andExpect(jsonPath("$.status").value(409))
            .andExpect(jsonPath("$.code").value("DUPLICATE_CODE"))
            .andExpect(jsonPath("$.detail").value("Code already exists"));
    }

    @Test
    @DisplayName("应该分页查询")
    void shouldPaginateResults() throws Exception {
        // Arrange
        var page = PageResponse.<ProvenanceDTO>builder()
            .content(List.of(/* ... */))
            .page(0)
            .size(10)
            .totalElements(100)
            .totalPages(10)
            .build();

        when(orchestrator.findPage(any())).thenReturn(page);

        // Act & Assert
        mockMvc.perform(get("/api/v1/provenances")
                .param("page", "0")
                .param("size", "10")
                .param("sort", "code,asc"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.page").value(0))
            .andExpect(jsonPath("$.size").value(10))
            .andExpect(jsonPath("$.totalElements").value(100));
    }

    @Test
    @DisplayName("应该更新 Provenance")
    void shouldUpdateProvenance() throws Exception {
        // Arrange
        var updateCommand = UpdateProvenanceCommand.builder()
            .name("Updated Name")
            .config("{\"new\":\"config\"}")
            .build();

        // Act & Assert
        mockMvc.perform(put("/api/v1/provenances/1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(updateCommand)))
            .andExpect(status().isOk());

        verify(orchestrator).update(eq(1L), any(UpdateProvenanceCommand.class));
    }

    @Test
    @DisplayName("应该删除 Provenance")
    void shouldDeleteProvenance() throws Exception {
        // Act & Assert
        mockMvc.perform(delete("/api/v1/provenances/1"))
            .andExpect(status().isNoContent());

        verify(orchestrator).delete(1L);
    }
}
```

### 文件上传测试

```java
@Test
@DisplayName("应该处理文件上传")
void shouldHandleFileUpload() throws Exception {
    // Arrange
    MockMultipartFile file = new MockMultipartFile(
        "file",
        "test.csv",
        MediaType.TEXT_PLAIN_VALUE,
        "test,data\n1,2".getBytes()
    );

    // Act & Assert
    mockMvc.perform(multipart("/api/v1/import")
            .file(file)
            .param("type", "CSV"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.message").value("File uploaded successfully"));

    verify(importService).importFile(any(MultipartFile.class), eq("CSV"));
}
```

## XXL-Job 测试 {#xxl-job}

### XXL-Job 任务测试模板

```java
package com.patra.{service}.adapter.scheduler.job;

import com.xxl.job.core.context.XxlJobHelper;
import com.xxl.job.core.handler.annotation.XxlJob;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("PubmedHarvestJob 定时任务测试")
class PubmedHarvestJobTest {

    @Mock
    private HarvestOrchestrator harvestOrchestrator;

    @Mock
    private ProvenancePort provenancePort;

    @InjectMocks
    private PubmedHarvestJob job;

    @Test
    @DisplayName("应该执行采集任务")
    void shouldExecuteHarvestJob() {
        // Arrange
        try (MockedStatic<XxlJobHelper> helper = mockStatic(XxlJobHelper.class)) {
            helper.when(XxlJobHelper::getJobParam).thenReturn("days=7");
            helper.when(XxlJobHelper::getJobId).thenReturn(1L);
            helper.when(XxlJobHelper::getShardIndex).thenReturn(0);
            helper.when(XxlJobHelper::getShardTotal).thenReturn(1);

            var provenance = createProvenance();
            when(provenancePort.findByCode("PUBMED")).thenReturn(provenance);

            // Act
            job.execute();

            // Assert
            verify(harvestOrchestrator).harvest(argThat(cmd ->
                cmd.getProvenanceCode().equals("PUBMED") &&
                cmd.getDays() == 7
            ));

            // 验证日志记录
            helper.verify(() -> XxlJobHelper.log("开始执行 PubMed 采集任务"));
            helper.verify(() -> XxlJobHelper.log(contains("成功采集")));
        }
    }

    @Test
    @DisplayName("应该处理任务参数")
    void shouldParseJobParameters() {
        try (MockedStatic<XxlJobHelper> helper = mockStatic(XxlJobHelper.class)) {
            // 测试不同的参数格式
            helper.when(XxlJobHelper::getJobParam)
                .thenReturn("days=30,batchSize=100,retry=3");

            // Act
            JobParams params = job.parseParameters();

            // Assert
            assertThat(params.getDays()).isEqualTo(30);
            assertThat(params.getBatchSize()).isEqualTo(100);
            assertThat(params.getRetryCount()).isEqualTo(3);
        }
    }

    @Test
    @DisplayName("应该处理分片执行")
    void shouldHandleSharding() {
        try (MockedStatic<XxlJobHelper> helper = mockStatic(XxlJobHelper.class)) {
            helper.when(XxlJobHelper::getShardIndex).thenReturn(1);
            helper.when(XxlJobHelper::getShardTotal).thenReturn(3);

            // Arrange - 模拟有 9 个数据源
            List<String> dataSources = List.of(
                "DS1", "DS2", "DS3", "DS4", "DS5", "DS6", "DS7", "DS8", "DS9"
            );

            // Act
            List<String> assigned = job.getShardedDataSources(dataSources);

            // Assert - 分片 1 应该处理 DS2, DS5, DS8
            assertThat(assigned).containsExactly("DS2", "DS5", "DS8");
        }
    }

    @Test
    @DisplayName("应该记录任务失败")
    void shouldHandleJobFailure() {
        try (MockedStatic<XxlJobHelper> helper = mockStatic(XxlJobHelper.class)) {
            helper.when(XxlJobHelper::getJobParam).thenReturn("invalid");

            // 模拟异常
            doThrow(new RuntimeException("Database connection failed"))
                .when(harvestOrchestrator).harvest(any());

            // Act & Assert
            assertThatThrownBy(() -> job.execute())
                .isInstanceOf(RuntimeException.class);

            // 验证错误日志
            helper.verify(() -> XxlJobHelper.log(contains("任务执行失败")));
            helper.verify(() -> XxlJobHelper.handleFail(contains("Database connection failed")));
        }
    }

    @Test
    @DisplayName("应该设置任务执行结果")
    void shouldSetJobResult() {
        try (MockedStatic<XxlJobHelper> helper = mockStatic(XxlJobHelper.class)) {
            // Arrange
            var result = HarvestResult.builder()
                .totalRecords(1000)
                .successCount(950)
                .failureCount(50)
                .build();

            when(harvestOrchestrator.harvest(any())).thenReturn(result);

            // Act
            job.execute();

            // Assert
            helper.verify(() -> XxlJobHelper.handleSuccess(
                "采集完成: 总数=1000, 成功=950, 失败=50"
            ));
        }
    }

    @Test
    @DisplayName("应该处理超时")
    void shouldHandleTimeout() {
        try (MockedStatic<XxlJobHelper> helper = mockStatic(XxlJobHelper.class)) {
            // 模拟长时间运行的任务
            doAnswer(invocation -> {
                Thread.sleep(5000);  // 模拟长时间操作
                return null;
            }).when(harvestOrchestrator).harvest(any());

            // Act & Assert
            assertThatThrownBy(() -> job.executeWithTimeout(1000))  // 1秒超时
                .isInstanceOf(TimeoutException.class);
        }
    }
}
```

### 任务链测试

```java
@Test
@DisplayName("应该触发下游任务")
void shouldTriggerDownstreamJob() {
    try (MockedStatic<XxlJobHelper> helper = mockStatic(XxlJobHelper.class)) {
        // Arrange
        when(harvestOrchestrator.harvest(any())).thenReturn(
            HarvestResult.success()
        );

        // Act
        job.execute();

        // Assert - 验证触发下游任务
        helper.verify(() -> XxlJobHelper.triggerChildJob(
            eq(2L),  // 下游任务 ID
            contains("parentJobId=1")  // 传递参数
        ));
    }
}
```

## Message Consumer 测试

```java
package com.patra.{service}.adapter.stream;

import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@SpringBootTest
@DisplayName("TaskReadyConsumer 消息消费者测试")
class TaskReadyConsumerTest {

    @Autowired
    private TaskReadyConsumer consumer;

    @MockBean
    private TaskExecutionOrchestrator orchestrator;

    @Test
    @DisplayName("应该消费任务就绪消息")
    void shouldConsumeTaskReadyMessage() {
        // Arrange
        TaskReadyMessage message = TaskReadyMessage.builder()
            .taskId(100L)
            .planId(10L)
            .provenanceCode("PUBMED")
            .build();

        // Act
        consumer.onMessage(message);

        // Assert
        verify(orchestrator).executeTask(argThat(cmd ->
            cmd.getTaskId().equals(100L) &&
            cmd.getPlanId().equals(10L)
        ));
    }

    @Test
    @DisplayName("应该处理消息处理失败")
    void shouldHandleMessageProcessingFailure() {
        // Arrange
        TaskReadyMessage message = createMessage();
        doThrow(new RuntimeException("Processing failed"))
            .when(orchestrator).executeTask(any());

        // Act & Assert
        assertThatThrownBy(() -> consumer.onMessage(message))
            .isInstanceOf(RuntimeException.class);

        // 验证消息会被重试（由 RocketMQ 框架处理）
    }

    @Test
    @DisplayName("应该过滤重复消息")
    void shouldFilterDuplicateMessages() {
        // Arrange
        String messageId = "MSG_12345";
        TaskReadyMessage message = createMessage(messageId);

        // 第一次消费
        consumer.onMessage(message);

        // 第二次消费（重复）
        consumer.onMessage(message);

        // Assert - 只处理一次
        verify(orchestrator, times(1)).executeTask(any());
    }
}
```

## 测试配置

### MockMvc 配置

```java
@TestConfiguration
public class TestMvcConfig {

    @Bean
    public RestTemplateBuilder restTemplateBuilder() {
        return new RestTemplateBuilder()
            .setConnectTimeout(Duration.ofSeconds(5))
            .setReadTimeout(Duration.ofSeconds(5));
    }

    @Bean
    public Jackson2ObjectMapperBuilder jackson2ObjectMapperBuilder() {
        return new Jackson2ObjectMapperBuilder()
            .simpleDateFormat("yyyy-MM-dd HH:mm:ss")
            .timeZone("Asia/Shanghai");
    }
}
```

### 自定义 MockMvc 断言

```java
public class CustomMockMvcMatchers {

    public static ResultMatcher problemDetail(String expectedCode) {
        return result -> {
            String content = result.getResponse().getContentAsString();
            ProblemDetail problem = objectMapper.readValue(content, ProblemDetail.class);
            assertThat(problem.getProperties().get("code")).isEqualTo(expectedCode);
        };
    }

    public static ResultMatcher validationErrors(String... fields) {
        return result -> {
            String content = result.getResponse().getContentAsString();
            ValidationErrorResponse errors = objectMapper.readValue(
                content, ValidationErrorResponse.class
            );
            assertThat(errors.getViolations())
                .extracting("field")
                .containsExactlyInAnyOrder(fields);
        };
    }
}
```