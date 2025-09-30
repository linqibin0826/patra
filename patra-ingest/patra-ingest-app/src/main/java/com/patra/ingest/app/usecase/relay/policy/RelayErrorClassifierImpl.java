package com.patra.ingest.app.usecase.relay.policy;

import cn.hutool.core.exceptions.ExceptionUtil;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.patra.ingest.domain.exception.OutboxRelayExecutionException;
import com.patra.ingest.domain.policy.RelayErrorClassifier;
import org.springframework.stereotype.Component;

/**
 * 默认异常分类策略：最小但实用的 FATAL / TRANSIENT 判定规则。
 * <p>归类标准：
 * <ul>
 *   <li>配置/非法状态/参数问题（IllegalArgument / IllegalState / OutboxRelayExecutionException） → FATAL</li>
 *   <li>JSON 序列化解析失败 → FATAL（通常数据不可恢复）</li>
 *   <li>其余全部 → TRANSIENT，交给重试策略（网络 / 下游短暂故障等）</li>
 * </ul>
 * 扩展：可用组合模式替换本实现，注入更细粒度匹配（如根据异常 errorCode）。
 */
@Component
public class RelayErrorClassifierImpl implements RelayErrorClassifier {

    @Override
    public RelayErrorKind classify(Throwable cause) {
        Throwable root = ExceptionUtil.getRootCause(cause);
        if (root instanceof OutboxRelayExecutionException
                || root instanceof IllegalArgumentException
                || root instanceof IllegalStateException
                || root instanceof JsonProcessingException) {
            return RelayErrorKind.FATAL;
        }
        return RelayErrorKind.TRANSIENT;
    }
}
