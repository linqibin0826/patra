package com.patra.ingest.infra.messaging.converter;

import cn.hutool.core.text.CharSequenceUtil;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.patra.ingest.domain.exception.OutboxRelayExecutionException;
import com.patra.ingest.domain.model.vo.TaskReadyMessage;
import com.patra.ingest.domain.model.entity.OutboxMessage;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Outbox 消息载荷 → 领域发布消息 {@link TaskReadyMessage} 的转换器。
 * <p>
 * 设计要点：
 * <ul>
 *   <li>对 OutboxMessage 中的 payloadJson / headersJson 进行反序列化，映射为强类型值对象。</li>
 *   <li>允许 headersJson 为空（表示无扩展头）。</li>
 *   <li>解析失败抛出 {@link OutboxRelayExecutionException} 交由上层判定重试 / 死信。</li>
 *   <li>保持纯转换（无副作用 / 无日志），方便单元测试。</li>
 * </ul>
 * </p>
 *
 * @author linqibin
 * @since 0.1.0
 */
@Component
public class TaskReadyMessageConverter {

    /** JSON 映射器（线程安全：ObjectMapper 由 Spring 单例管理） */
    private final ObjectMapper objectMapper;

    public TaskReadyMessageConverter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * 将 OutboxMessage 映射为 TaskReadyMessage。
     * <p>空 headersJson → header=null；payload 必须存在且可反序列化。</p>
     * @param message 出站消息（必须包含 payloadJson）
     * @return 领域发布消息
     * @throws OutboxRelayExecutionException JSON 结构不合法或字段缺失
     */
    public TaskReadyMessage convert(OutboxMessage message) {
        try {
            JsonNode payloadNode = objectMapper.readTree(message.getPayloadJson());
            JsonNode headerNode = CharSequenceUtil.isBlank(message.getHeadersJson())
                    ? null
                    : objectMapper.readTree(message.getHeadersJson());
            TaskReadyMessage.Payload payload = objectMapper.treeToValue(payloadNode, TaskReadyMessage.Payload.class);
            TaskReadyMessage.Header header = headerNode == null
                    ? null
                    : objectMapper.treeToValue(headerNode, TaskReadyMessage.Header.class);
            return new TaskReadyMessage(payload, header);
        } catch (IOException e) {
            throw new OutboxRelayExecutionException("Failed to map outbox payload to TaskReadyMessage", e);
        }
    }
}
