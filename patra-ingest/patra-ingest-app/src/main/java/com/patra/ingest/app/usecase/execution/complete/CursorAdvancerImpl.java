package com.patra.ingest.app.usecase.execution.complete;

import com.patra.ingest.domain.model.entity.Cursor;
import com.patra.ingest.domain.model.enums.SliceStrategy;
import com.patra.ingest.domain.model.vo.ExecutionContext;
import com.patra.ingest.domain.model.vo.WindowSpec;
import com.patra.ingest.domain.port.CursorRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Optional;

/**
 * 游标推进器实现。
 * <p>
 * 职责：根据批次执行结果推进游标水位，使用乐观锁防止并发冲突。
 * </p>
 * <p>
 * 设计要点：
 * <ul>
 *   <li>查询游标：根据 provenanceCode/endpointName/cursorKey/namespace 查询当前游标。</li>
 *   <li>计算新水位：从 WindowSpec 根据策略提取新水位（TIME策略使用windowTo）。</li>
 *   <li>乐观锁更新：调用 Cursor.advanceTo() 更新水位，保存时触发版本校验。</li>
 *   <li>异常处理：捕获 OptimisticLockingFailureException，返回 false 表示需重试。</li>
 *   <li>首次推进：游标不存在时创建新游标。</li>
 * </ul>
 * </p>
 * <p>
 * 命名空间策略：
 * <ul>
 *   <li>GLOBAL：全局游标（跨任务共享）。</li>
 *   <li>TASK：任务粒度游标（按 taskId 隔离）。</li>
 *   <li>PLAN：计划粒度游标（按 planId 隔离）。</li>
 * </ul>
 * </p>
 * <p>
 * 日志策略：
 * <ul>
 *   <li>INFO：游标推进成功（记录 from/to 水位）。</li>
 *   <li>WARN：乐观锁冲突（需重试）。</li>
 *   <li>DEBUG：查询游标、创建新游标。</li>
 * </ul>
 * </p>
 *
 * @author linqibin
 * @since 0.1.0
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CursorAdvancerImpl implements CursorAdvancer {

    private final CursorRepository cursorRepository;

    /**
     * 推进游标水位。
     *
     * @param context 执行上下文
     * @param taskId 任务ID
     * @param runId 运行ID
     * @return true 表示推进成功，false 表示乐观锁冲突（需重试）
     */
    @Override
    public boolean advance(ExecutionContext context, Long taskId, Long runId) {
        // 1. 提取游标参数
        String provenanceCode = context.provenanceCode();
        String operationCode = context.operationCode();

        WindowSpec windowSpec = context.windowSpec();
        if (windowSpec == null) {
            log.debug("[INGEST][APP] cursor advance skipped: no window spec taskId={} runId={}",
                     taskId, runId);
            return true;  // 无窗口规格，跳过推进
        }

        // 2. 策略感知的水位提取
        Instant newWatermark = extractWatermark(windowSpec, taskId, runId);
        if (newWatermark == null) {
            log.debug("[INGEST][APP] cursor advance skipped: non-TIME strategy or no watermark " +
                     "strategy={} taskId={} runId={}",
                     windowSpec.strategy(), taskId, runId);
            return true;  // 非TIME策略暂不支持水位推进
        }

        // 3. 确定游标键和命名空间
        String cursorKey = determineCursorKey(windowSpec);
        String namespaceScope = "GLOBAL";
        String namespaceKey = null;

        try {
            // 4. 查询当前游标
            Optional<Cursor> cursorOpt = cursorRepository.find(
                provenanceCode,
                operationCode,
                cursorKey,
                namespaceScope,
                namespaceKey
            );

            Cursor cursor;
            if (cursorOpt.isPresent()) {
                // 4.1 游标存在：更新水位
                cursor = cursorOpt.get();
                Instant oldWatermark = cursor.getCurrentWatermark();

                if (log.isDebugEnabled()) {
                    log.debug("[INGEST][APP] cursor found provenanceCode={} endpointName={} currentWatermark={}",
                             provenanceCode, operationCode, oldWatermark);
                }

                // 推进水位（领域方法会校验水位不倒退）
                cursor.advanceTo(newWatermark);

                log.info("[INGEST][APP] cursor advanced provenanceCode={} endpointName={} from={} to={} taskId={} runId={}",
                         provenanceCode, operationCode, oldWatermark, newWatermark, taskId, runId);
            } else {
                // 4.2 游标不存在：创建新游标
                cursor = Cursor.create(
                    provenanceCode,
                    operationCode,
                    cursorKey,
                    namespaceScope,
                    namespaceKey,
                    newWatermark
                );

                log.info("[INGEST][APP] cursor created provenanceCode={} endpointName={} watermark={} taskId={} runId={}",
                         provenanceCode, operationCode, newWatermark, taskId, runId);
            }

            // 5. 保存游标（乐观锁校验）
            cursorRepository.save(cursor);
            return true;

        } catch (OptimisticLockingFailureException e) {
            // 乐观锁冲突（version 不匹配）
            log.warn("[INGEST][APP] cursor advance conflict provenanceCode={} endpointName={} taskId={} runId={}",
                     provenanceCode, operationCode, taskId, runId);
            return false;  // 返回 false 表示需重试

        } catch (Exception e) {
            log.error("[INGEST][APP] cursor advance failed provenanceCode={} endpointName={} taskId={} runId={}",
                      provenanceCode, operationCode, taskId, runId, e);
            throw new IllegalStateException("游标推进失败", e);
        }
    }

    /**
     * 从WindowSpec提取水位（策略感知）。
     * <p>目前仅TIME策略支持基于时间戳的水位推进。</p>
     *
     * @param windowSpec 窗口规格
     * @param taskId 任务ID（用于日志）
     * @param runId 运行ID（用于日志）
     * @return 水位时间戳，如果策略不支持水位则返回null
     */
    private Instant extractWatermark(WindowSpec windowSpec, Long taskId, Long runId) {
        return switch (windowSpec.strategy()) {
            case TIME -> {
                WindowSpec.Time timeSpec = (WindowSpec.Time) windowSpec;
                yield timeSpec.to();  // TIME策略：使用窗口终点作为水位
            }
            case ID_RANGE, CURSOR_LANDMARK, VOLUME_BUDGET, SINGLE -> {
                // 这些策略暂不使用基于时间的水位
                // 未来：ID_RANGE可实现基于数值ID的水位推进
                yield null;
            }
            case HYBRID -> {
                // 未来：从HYBRID规格中提取时间分量
                log.warn("[INGEST][APP] HYBRID strategy watermark extraction not yet implemented " +
                        "taskId={} runId={}", taskId, runId);
                yield null;
            }
        };
    }

    /**
     * 根据窗口策略确定游标键。
     *
     * @param windowSpec 窗口规格
     * @return 游标键标识
     */
    private String determineCursorKey(WindowSpec windowSpec) {
        return switch (windowSpec.strategy()) {
            case TIME -> "TIME";
            case ID_RANGE -> "ID";
            case CURSOR_LANDMARK -> "CURSOR";
            case VOLUME_BUDGET, SINGLE, HYBRID -> "GLOBAL";
        };
    }
}
