package com.patra.ingest.infra.persistence.repository;

import com.patra.ingest.domain.model.aggregate.PlanAggregate;
import com.patra.ingest.domain.model.vo.plan.PlanId;
import com.patra.ingest.domain.model.vo.plan.PlanKey;
import com.patra.ingest.domain.model.vo.common.ProvenanceCode;
import com.patra.ingest.domain.model.vo.common.OperationCode;
import com.patra.ingest.domain.model.vo.common.StatusCode;
import com.patra.ingest.domain.port.PlanRepository;
import com.patra.ingest.infra.persistence.entity.IngPlanDO;
import com.patra.ingest.infra.persistence.mapper.IngPlanMapper;
import com.patra.ingest.infra.mapstruct.PlanConverter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * 计划仓储实现 - 基于 MyBatis-Plus 的计划聚合持久化。
 *
 * @author linqibin
 * @since 0.1.0
 */
@Slf4j
@Repository
@RequiredArgsConstructor
public class PlanRepositoryMpImpl implements PlanRepository {

    private final IngPlanMapper planMapper;
    private final PlanConverter planConverter;

    @Override
    public PlanAggregate save(PlanAggregate plan) {
        log.debug("Saving plan aggregate: planKey={}, status={}",
                plan.getPlanKey(), plan.getStatusCode());

        try {
            IngPlanDO planDO = planConverter.toEntity(plan);

            if (plan.getId() == null || plan.getId().getValue() == null) {
                // 新增
                planMapper.insert(planDO);
                log.debug("Plan inserted with ID: {}", planDO.getId());
            } else {
                // 更新
                planMapper.updateById(planDO);
                log.debug("Plan updated: ID={}", planDO.getId());
            }

            return planConverter.toDomain(planDO);

        } catch (Exception e) {
            log.error("Failed to save plan aggregate: planKey={}", plan.getPlanKey(), e);
            throw new RuntimeException("Failed to save plan aggregate", e);
        }
    }

    @Override
    public Optional<PlanAggregate> findById(PlanId planId) {
        log.debug("Finding plan by ID: {}", planId);

        if (planId == null || planId.getValue() == null) {
            return Optional.empty();
        }

        try {
            IngPlanDO planDO = planMapper.selectById(planId.getValue());
            return planDO != null ? Optional.of(planConverter.toDomain(planDO)) : Optional.empty();

        } catch (Exception e) {
            log.error("Failed to find plan by ID: {}", planId, e);
            throw new RuntimeException("Failed to find plan by ID", e);
        }
    }

    @Override
    public Optional<PlanAggregate> findByPlanKey(PlanKey planKey) {
        log.debug("Finding plan by key: {}", planKey);

        if (planKey == null) {
            return Optional.empty();
        }

        try {
            IngPlanDO planDO = planMapper.findByPlanKey(planKey.getValue());
            return planDO != null ? Optional.of(planConverter.toDomain(planDO)) : Optional.empty();

        } catch (Exception e) {
            log.error("Failed to find plan by key: {}", planKey, e);
            throw new RuntimeException("Failed to find plan by key", e);
        }
    }

    @Override
    public List<PlanAggregate> findByScheduleInstanceId(Long scheduleInstanceId) {
        log.debug("Finding plans by schedule instance ID: {}", scheduleInstanceId);

        if (scheduleInstanceId == null) {
            return List.of();
        }

        try {
            List<IngPlanDO> planDOs = planMapper.findByScheduleInstanceId(scheduleInstanceId);
            return planDOs.stream()
                    .map(planConverter::toDomain)
                    .collect(Collectors.toList());

        } catch (Exception e) {
            log.error("Failed to find plans by schedule instance ID: {}", scheduleInstanceId, e);
            throw new RuntimeException("Failed to find plans by schedule instance ID", e);
        }
    }

    @Override
    public List<PlanAggregate> findActiveByProvenanceAndOperation(
            ProvenanceCode provenanceCode,
            OperationCode operationCode,
            List<StatusCode> statusCodes) {

        log.debug("Finding active plans: provenanceCode={}, operationCode={}, statusCodes={}",
                provenanceCode, operationCode, statusCodes);

        if (provenanceCode == null || operationCode == null || statusCodes == null || statusCodes.isEmpty()) {
            return List.of();
        }

        try {
            List<String> statusCodeStrings = statusCodes.stream()
                    .map(StatusCode::getCode)
                    .collect(Collectors.toList());

            List<IngPlanDO> planDOs = planMapper.findActiveByProvenanceAndOperation(
                    provenanceCode.getValue(),
                    operationCode.getCode(),
                    statusCodeStrings);

            return planDOs.stream()
                    .map(planConverter::toDomain)
                    .collect(Collectors.toList());

        } catch (Exception e) {
            log.error("Failed to find active plans: provenanceCode={}, operationCode={}",
                    provenanceCode, operationCode, e);
            throw new RuntimeException("Failed to find active plans", e);
        }
    }

    @Override
    public boolean existsByPlanKey(PlanKey planKey) {
        log.debug("Checking if plan key exists: {}", planKey);

        if (planKey == null) {
            return false;
        }

        try {
            return planMapper.countByPlanKey(planKey.getValue()) > 0;

        } catch (Exception e) {
            log.error("Failed to check plan key existence: {}", planKey, e);
            throw new RuntimeException("Failed to check plan key existence", e);
        }
    }

    @Override
    public long countByProvenanceAndOperationAndStatus(
            ProvenanceCode provenanceCode,
            OperationCode operationCode,
            StatusCode statusCode) {

        log.debug("Counting plans: provenanceCode={}, operationCode={}, statusCode={}",
                provenanceCode, operationCode, statusCode);

        if (provenanceCode == null || operationCode == null || statusCode == null) {
            return 0;
        }

        try {
            return planMapper.countByProvenanceAndOperationAndStatus(
                    provenanceCode.getValue(),
                    operationCode.getCode(),
                    statusCode.getCode());

        } catch (Exception e) {
            log.error("Failed to count plans: provenanceCode={}, operationCode={}, statusCode={}",
                    provenanceCode, operationCode, statusCode, e);
            throw new RuntimeException("Failed to count plans", e);
        }
    }

    @Override
    public void deleteById(PlanId planId) {
        log.debug("Soft deleting plan: {}", planId);

        if (planId == null || planId.getValue() == null) {
            return;
        }

        try {
            int affected = planMapper.softDeleteById(planId.getValue());
            if (affected == 0) {
                log.warn("No plan found to delete: {}", planId);
            } else {
                log.debug("Plan soft deleted: {}", planId);
            }

        } catch (Exception e) {
            log.error("Failed to delete plan: {}", planId, e);
            throw new RuntimeException("Failed to delete plan", e);
        }
    }

    @Override
    public int batchUpdateStatus(List<PlanId> planIds, StatusCode statusCode, String remarks) {
        log.debug("Batch updating plan status: planIds={}, statusCode={}", planIds.size(), statusCode);

        if (planIds == null || planIds.isEmpty() || statusCode == null) {
            return 0;
        }

        try {
            List<Long> planIdValues = planIds.stream()
                    .map(PlanId::getValue)
                    .collect(Collectors.toList());

            int affected = planMapper.batchUpdateStatus(planIdValues, statusCode.getCode(), remarks);
            log.debug("Batch updated {} plans to status: {}", affected, statusCode);
            return affected;

        } catch (Exception e) {
            log.error("Failed to batch update plan status: statusCode={}", statusCode, e);
            throw new RuntimeException("Failed to batch update plan status", e);
        }
    }
}
