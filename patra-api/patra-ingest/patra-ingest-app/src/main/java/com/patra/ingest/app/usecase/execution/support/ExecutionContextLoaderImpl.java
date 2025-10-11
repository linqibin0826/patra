package com.patra.ingest.app.usecase.execution.support;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.patra.ingest.domain.model.aggregate.PlanAggregate;
import com.patra.ingest.domain.model.aggregate.PlanSliceAggregate;
import com.patra.ingest.domain.model.aggregate.TaskAggregate;
import com.patra.ingest.domain.model.snapshot.ProvenanceConfigSnapshot;
import com.patra.ingest.domain.model.vo.ExecutionContext;
import com.patra.ingest.domain.model.vo.ExprCompilationRequest;
import com.patra.ingest.domain.model.vo.ExprCompilationResult;
import com.patra.ingest.domain.model.vo.WindowSpec;
import com.patra.ingest.domain.port.ExpressionCompilerPort;
import com.patra.ingest.domain.port.PlanRepository;
import com.patra.ingest.domain.port.PlanSliceRepository;
import com.patra.ingest.domain.port.TaskRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;

import java.time.Instant;

/**
 * Execution context loader implementation.
 * <p>
 * Responsibility: restore config and expression snapshots (Task → Slice → Plan), validate hashes,
 * and compile expressions.
 * </p>
 * <p>
 * Design notes:
 * <ul>
 *   <li>Read Task: obtain sliceId, exprHash, paramsJson, provenanceCode, endpointName.</li>
 *   <li>Read Slice: obtain planId and execution window info.</li>
 *   <li>Read Plan: obtain provenanceConfigSnapshotJson.</li>
 *   <li>Compile expression via ExpressionCompilerPort → query/params/normalizedExpression.</li>
 *   <li>Validate exprHash to ensure configuration integrity.</li>
 *   <li>Return ExecutionContext with all necessary execution info.</li>
 * </ul>
 * </p>
 * <p>
 * Error handling:
 * <ul>
 *   <li>Missing Task/Slice/Plan → IllegalArgumentException.</li>
 *   <li>Expression compilation failure → IllegalStateException.</li>
 *   <li>exprHash mismatch → IllegalStateException (integrity violation).</li>
 * </ul>
 * </p>
 * <p>
 * Logging: INFO on successful context load; WARN when hash validation fails.
 * </p>
 *
 * @author linqibin
 * @since 0.1.0
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ExecutionContextLoaderImpl implements ExecutionContextLoader {

    private final TaskRepository taskRepository;
    private final PlanSliceRepository sliceRepository;
    private final PlanRepository planRepository;
    private final ExpressionCompilerPort expressionCompiler;
    private final ObjectMapper objectMapper;

    /**
     * Loads execution context (config restore + expression compile).
     */
    @Override
    public ExecutionContext loadContext(Long taskId, Long runId) {
        // Query task and delegate to overloaded method
        TaskAggregate task = taskRepository.findById(taskId)
                .orElseThrow(() -> new IllegalArgumentException("Task not found taskId=" + taskId));

        return loadContext(task, runId);
    }

    /**
     * Loads execution context (config restore + expression compile) — optimized to avoid reloading Task.
     */
    @Override
    public ExecutionContext loadContext(TaskAggregate task, Long runId) {
        Long taskId = task.getId();

        // 2) Read Slice
        PlanSliceAggregate slice = sliceRepository.findById(task.getSliceId())
                .orElseThrow(() -> new IllegalArgumentException("Slice not found sliceId=" + task.getSliceId()));

        // 3) Read Plan
        PlanAggregate plan = planRepository.findById(slice.getPlanId())
                .orElseThrow(() -> new IllegalArgumentException("Plan not found planId=" + slice.getPlanId()));

        // 4) Parse config snapshot into ProvenanceConfigSnapshot
        ProvenanceConfigSnapshot configSnapshot = parseConfigSnapshot(plan.getProvenanceConfigSnapshotJson());

        // 5. Compile expression
        // Expression compilation is delegated to ExpressionCompilerPort (implemented in infra layer)
        // The port implementation converts JSON expression snapshot to Expr object and invokes ExprCompiler
        // Use slice's expression snapshot (after plan_slice), not plan's original snapshot
        String exprSnapshotJson = slice.getExprSnapshotJson();
        if (exprSnapshotJson == null || exprSnapshotJson.isBlank()) {
            log.warn("[INGEST][APP] slice exprSnapshotJson is null, fallback to plan's original snapshot sliceId={}", slice.getId());
            exprSnapshotJson = plan.getExprProtoSnapshotJson();
        }

        // Create compilation request without endpointName (defaults to null)
        ExprCompilationRequest compilationRequest = new ExprCompilationRequest(
                task.getProvenanceCode(),
                exprSnapshotJson  // Use slice's expression snapshot
        );

        // Compile expression and validate result
        ExprCompilationResult compilationResult = expressionCompiler.compile(compilationRequest);

        if (!compilationResult.isValid()) {
            throw new IllegalStateException(
                    "Expression compilation failed taskId=" + taskId
                            + " reason=" + compilationResult.validationMessage()
            );
        }

        // 6) Validate exprHash to ensure integrity
        // Compare task's exprHash with slice's exprHash (not plan's)
        if (!task.getExprHash().equals(slice.getExprHash())) {
            throw new IllegalStateException(
                    String.format(
                            "Expression hash mismatch; aborting taskId=%d expected=%s actual=%s",
                            taskId, slice.getExprHash(), task.getExprHash()
                    )
            );
        }

        // 7) Parse window spec from JSON
        WindowSpec windowSpec = parseWindowSpec(slice.getWindowSpecJson());

        // 8) Build ExecutionContext
        log.info("[INGEST][APP] execution context loaded taskId={} runId={} provenanceCode={} endpointName={}",
                taskId, runId, task.getProvenanceCode(), task.getOperationCode());

        return new ExecutionContext(
                taskId,
                runId,
                task.getProvenanceCode(),
                task.getOperationCode(),
                configSnapshot,
                task.getExprHash(),
                compilationResult.query(),
                compilationResult.params(),
                compilationResult.normalizedExpression(),
                windowSpec
        );
    }

    /**
     * Parse WindowSpec from slice window spec JSON.
     * <p>
     * This method uses the polymorphic WindowSpec value object to handle
     * different window types (TIME, ID_RANGE, CURSOR_LANDMARK, VOLUME_BUDGET, SINGLE).
     * </p>
     *
     * @param windowSpecJson window specification JSON string
     * @return WindowSpec instance, or null if JSON is blank
     * @throws IllegalStateException if WindowSpec parsing fails
     */
    private WindowSpec parseWindowSpec(String windowSpecJson) {
        if (windowSpecJson == null || windowSpecJson.isBlank()) {
            return null;
        }
        try {
            JsonNode spec = objectMapper.readTree(windowSpecJson);
            @SuppressWarnings("unchecked")
            Map<String, Object> map = objectMapper.convertValue(spec, Map.class);
            return WindowSpec.fromMap(map);
        } catch (Exception e) {
            log.error("[INGEST][APP] failed to parse WindowSpec from JSON: {}", windowSpecJson, e);
            throw new IllegalStateException("WindowSpec parsing failed for JSON: " + windowSpecJson, e);
        }
    }

    /** Parses a JSON string into JsonNode. */
    private JsonNode parseJson(String json) {
        if (json == null || json.isBlank()) {
            return objectMapper.createObjectNode();
        }
        try {
            return objectMapper.readTree(json);
        } catch (Exception e) {
            log.error("[INGEST][APP] failed to parse json: {}", json, e);
            throw new IllegalStateException("Failed to parse JSON", e);
        }
    }

    /** Parses a JSON string into a ProvenanceConfigSnapshot. */
    private ProvenanceConfigSnapshot parseConfigSnapshot(String json) {
        if (json == null || json.isBlank()) {
            throw new IllegalStateException("Config snapshot JSON must not be blank");
        }
        try {
            return objectMapper.readValue(json, ProvenanceConfigSnapshot.class);
        } catch (Exception e) {
            log.error("[INGEST][APP] failed to parse config snapshot from json: {}", json, e);
            throw new IllegalStateException("Failed to parse config snapshot", e);
        }
    }
}
