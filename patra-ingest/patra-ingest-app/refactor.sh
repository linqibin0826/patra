#!/bin/bash

# Papertrace patra-ingest-app 重构脚本
# 作用：将旧的目录结构重构为新的 usecase 结构

set -e

BASE_DIR="/Users/linqibin/IdeaProjects/Papertrace-api/patra-ingest/patra-ingest-app/src/main/java/com/patra/ingest/app"

echo "========================================="
echo "开始 patra-ingest-app 重构"
echo "========================================="

cd "$BASE_DIR"

# ==================== 第一步：复制文件到新位置 ====================
echo ""
echo "[1/5] 复制文件到新位置..."

# Planning UseCase - DTO
cp planning/dto/PlanIngestionResult.java usecase/plan/dto/

# Planning UseCase - Slicer Models
cp planning/slice/SliceStrategy.java usecase/plan/slicer/
cp planning/slice/SlicePlanner.java usecase/plan/slicer/
cp planning/slice/SlicePlannerRegistry.java usecase/plan/slicer/
cp planning/slice/TimeSlicePlanner.java usecase/plan/slicer/
cp planning/slice/SingleSlicePlanner.java usecase/plan/slicer/
cp planning/slice/model/SlicePlan.java usecase/plan/slicer/model/
cp planning/slice/model/SlicePlanningContext.java usecase/plan/slicer/model/

# Planning UseCase - Window
cp planning/window/PlanningWindowResolver.java usecase/plan/window/
cp planning/window/DefaultPlanningWindowResolver.java usecase/plan/window/PlanningWindowResolverImpl.java
cp planning/window/support/PlanningWindowSupport.java usecase/plan/window/support/

# Planning UseCase - Expression
cp planning/expression/PlanExpressionDescriptor.java usecase/plan/expression/
cp planning/application/support/PlanExpressionBuilder.java usecase/plan/expression/

# Planning UseCase - Assembler
cp planning/assembly/PlanAssemblyService.java usecase/plan/assembler/PlanAssembler.java
cp planning/assembly/DefaultPlanAssemblyService.java usecase/plan/assembler/PlanAssemblerImpl.java
cp planning/assembly/PlanAssemblyRequest.java usecase/plan/assembler/

# Planning UseCase - Validator
cp validator/PlannerValidator.java usecase/plan/validator/
cp validator/DefaultPlannerValidator.java usecase/plan/validator/PlannerValidatorImpl.java

# Planning UseCase - Publisher
cp planning/outbox/TaskOutboxPublisher.java usecase/plan/publisher/

# Planning UseCase - Main UseCase
cp planning/application/PlanIngestionUseCase.java usecase/plan/
cp planning/application/PlanIngestionApplicationService.java usecase/plan/PlanIngestionOrchestrator.java

# Relay UseCase - DTO
cp relay/dto/RelayReport.java usecase/relay/dto/

# Relay UseCase - Command  
cp relay/command/OutboxRelayInstruction.java usecase/relay/command/OutboxRelayCommand.java

# Relay UseCase - Planner
cp relay/plan/OutboxRelayPlanBuilder.java usecase/relay/planner/RelayPlanBuilder.java

# Relay UseCase - Executor
cp relay/executor/OutboxRelayExecutor.java usecase/relay/executor/

# Relay UseCase - Policy
cp relay/policy/DefaultRelayErrorClassifier.java usecase/relay/policy/RelayErrorClassifierImpl.java

# Relay UseCase - Publisher
cp relay/event/OutboxRelayEventPublisher.java usecase/relay/publisher/RelayEventPublisher.java
cp relay/event/LoggingOutboxRelayEventPublisher.java usecase/relay/publisher/LoggingRelayEventPublisher.java

# Relay UseCase - Config
cp relay/config/OutboxRelayProperties.java usecase/relay/config/
cp relay/config/OutboxRelayConfiguration.java usecase/relay/config/

# Relay UseCase - Support
cp relay/support/OutboxChannels.java usecase/relay/support/

# Relay UseCase - Main UseCase
cp relay/OutboxRelayUseCase.java usecase/relay/
cp relay/OutboxRelayApplicationService.java usecase/relay/OutboxRelayOrchestrator.java

echo "✓ 文件复制完成"

# ==================== 第二步：更新包名 ====================
echo ""
echo "[2/5] 更新包名..."

# 使用 sed 批量替换包名（macOS 使用 BSD sed）
find usecase -name "*.java" -type f -exec sed -i '' 's/package com\.patra\.ingest\.app\.planning\.command/package com.patra.ingest.app.usecase.plan.command/g' {} \;
find usecase -name "*.java" -type f -exec sed -i '' 's/package com\.patra\.ingest\.app\.planning\.dto/package com.patra.ingest.app.usecase.plan.dto/g' {} \;
find usecase -name "*.java" -type f -exec sed -i '' 's/package com\.patra\.ingest\.app\.planning\.slice\.model/package com.patra.ingest.app.usecase.plan.slicer.model/g' {} \;
find usecase -name "*.java" -type f -exec sed -i '' 's/package com\.patra\.ingest\.app\.planning\.slice/package com.patra.ingest.app.usecase.plan.slicer/g' {} \;
find usecase -name "*.java" -type f -exec sed -i '' 's/package com\.patra\.ingest\.app\.planning\.window\.support/package com.patra.ingest.app.usecase.plan.window.support/g' {} \;
find usecase -name "*.java" -type f -exec sed -i '' 's/package com\.patra\.ingest\.app\.planning\.window/package com.patra.ingest.app.usecase.plan.window/g' {} \;
find usecase -name "*.java" -type f -exec sed -i '' 's/package com\.patra\.ingest\.app\.planning\.expression/package com.patra.ingest.app.usecase.plan.expression/g' {} \;
find usecase -name "*.java" -type f -exec sed -i '' 's/package com\.patra\.ingest\.app\.planning\.application\.support/package com.patra.ingest.app.usecase.plan.expression/g' {} \;
find usecase -name "*.java" -type f -exec sed -i '' 's/package com\.patra\.ingest\.app\.planning\.assembly/package com.patra.ingest.app.usecase.plan.assembler/g' {} \;
find usecase -name "*.java" -type f -exec sed -i '' 's/package com\.patra\.ingest\.app\.planning\.outbox/package com.patra.ingest.app.usecase.plan.publisher/g' {} \;
find usecase -name "*.java" -type f -exec sed -i '' 's/package com\.patra\.ingest\.app\.planning\.application/package com.patra.ingest.app.usecase.plan/g' {} \;
find usecase -name "*.java" -type f -exec sed -i '' 's/package com\.patra\.ingest\.app\.validator/package com.patra.ingest.app.usecase.plan.validator/g' {} \;
find usecase -name "*.java" -type f -exec sed -i '' 's/package com\.patra\.ingest\.app\.relay\.command/package com.patra.ingest.app.usecase.relay.command/g' {} \;
find usecase -name "*.java" -type f -exec sed -i '' 's/package com\.patra\.ingest\.app\.relay\.dto/package com.patra.ingest.app.usecase.relay.dto/g' {} \;
find usecase -name "*.java" -type f -exec sed -i '' 's/package com\.patra\.ingest\.app\.relay\.plan/package com.patra.ingest.app.usecase.relay.planner/g' {} \;
find usecase -name "*.java" -type f -exec sed -i '' 's/package com\.patra\.ingest\.app\.relay\.executor/package com.patra.ingest.app.usecase.relay.executor/g' {} \;
find usecase -name "*.java" -type f -exec sed -i '' 's/package com\.patra\.ingest\.app\.relay\.policy/package com.patra.ingest.app.usecase.relay.policy/g' {} \;
find usecase -name "*.java" -type f -exec sed -i '' 's/package com\.patra\.ingest\.app\.relay\.event/package com.patra.ingest.app.usecase.relay.publisher/g' {} \;
find usecase -name "*.java" -type f -exec sed -i '' 's/package com\.patra\.ingest\.app\.relay\.config/package com.patra.ingest.app.usecase.relay.config/g' {} \;
find usecase -name "*.java" -type f -exec sed -i '' 's/package com\.patra\.ingest\.app\.relay\.support/package com.patra.ingest.app.usecase.relay.support/g' {} \;
find usecase -name "*.java" -type f -exec sed -i '' 's/package com\.patra\.ingest\.app\.relay/package com.patra.ingest.app.usecase.relay/g' {} \;

echo "✓ 包名更新完成"

# ==================== 第三步：更新 import 语句 ====================
echo ""
echo "[3/5] 更新 import 语句..."

find usecase -name "*.java" -type f -exec sed -i '' 's/import com\.patra\.ingest\.app\.planning\.command\.PlanIngestionRequest/import com.patra.ingest.app.usecase.plan.command.PlanIngestionCommand/g' {} \;
find usecase -name "*.java" -type f -exec sed -i '' 's/import com\.patra\.ingest\.app\.planning\.dto\./import com.patra.ingest.app.usecase.plan.dto./g' {} \;
find usecase -name "*.java" -type f -exec sed -i '' 's/import com\.patra\.ingest\.app\.planning\.slice\.model\./import com.patra.ingest.app.usecase.plan.slicer.model./g' {} \;
find usecase -name "*.java" -type f -exec sed -i '' 's/import com\.patra\.ingest\.app\.planning\.slice\./import com.patra.ingest.app.usecase.plan.slicer./g' {} \;
find usecase -name "*.java" -type f -exec sed -i '' 's/import com\.patra\.ingest\.app\.planning\.window\.support\./import com.patra.ingest.app.usecase.plan.window.support./g' {} \;
find usecase -name "*.java" -type f -exec sed -i '' 's/import com\.patra\.ingest\.app\.planning\.window\./import com.patra.ingest.app.usecase.plan.window./g' {} \;
find usecase -name "*.java" -type f -exec sed -i '' 's/import com\.patra\.ingest\.app\.planning\.expression\./import com.patra.ingest.app.usecase.plan.expression./g' {} \;
find usecase -name "*.java" -type f -exec sed -i '' 's/import com\.patra\.ingest\.app\.planning\.application\.support\./import com.patra.ingest.app.usecase.plan.expression./g' {} \;
find usecase -name "*.java" -type f -exec sed -i '' 's/import com\.patra\.ingest\.app\.planning\.assembly\.PlanAssemblyService/import com.patra.ingest.app.usecase.plan.assembler.PlanAssembler/g' {} \;
find usecase -name "*.java" -type f -exec sed -i '' 's/import com\.patra\.ingest\.app\.planning\.assembly\.DefaultPlanAssemblyService/import com.patra.ingest.app.usecase.plan.assembler.PlanAssemblerImpl/g' {} \;
find usecase -name "*.java" -type f -exec sed -i '' 's/import com\.patra\.ingest\.app\.planning\.assembly\./import com.patra.ingest.app.usecase.plan.assembler./g' {} \;
find usecase -name "*.java" -type f -exec sed -i '' 's/import com\.patra\.ingest\.app\.planning\.outbox\./import com.patra.ingest.app.usecase.plan.publisher./g' {} \;
find usecase -name "*.java" -type f -exec sed -i '' 's/import com\.patra\.ingest\.app\.planning\.application\.PlanIngestionApplicationService/import com.patra.ingest.app.usecase.plan.PlanIngestionOrchestrator/g' {} \;
find usecase -name "*.java" -type f -exec sed -i '' 's/import com\.patra\.ingest\.app\.planning\.application\./import com.patra.ingest.app.usecase.plan./g' {} \;
find usecase -name "*.java" -type f -exec sed -i '' 's/import com\.patra\.ingest\.app\.validator\.PlannerValidator/import com.patra.ingest.app.usecase.plan.validator.PlannerValidator/g' {} \;
find usecase -name "*.java" -type f -exec sed -i '' 's/import com\.patra\.ingest\.app\.validator\.DefaultPlannerValidator/import com.patra.ingest.app.usecase.plan.validator.PlannerValidatorImpl/g' {} \;
find usecase -name "*.java" -type f -exec sed -i '' 's/import com\.patra\.ingest\.app\.relay\.command\.OutboxRelayInstruction/import com.patra.ingest.app.usecase.relay.command.OutboxRelayCommand/g' {} \;
find usecase -name "*.java" -type f -exec sed -i '' 's/import com\.patra\.ingest\.app\.relay\.dto\./import com.patra.ingest.app.usecase.relay.dto./g' {} \;
find usecase -name "*.java" -type f -exec sed -i '' 's/import com\.patra\.ingest\.app\.relay\.plan\.OutboxRelayPlanBuilder/import com.patra.ingest.app.usecase.relay.planner.RelayPlanBuilder/g' {} \;
find usecase -name "*.java" -type f -exec sed -i '' 's/import com\.patra\.ingest\.app\.relay\.executor\./import com.patra.ingest.app.usecase.relay.executor./g' {} \;
find usecase -name "*.java" -type f -exec sed -i '' 's/import com\.patra\.ingest\.app\.relay\.policy\.DefaultRelayErrorClassifier/import com.patra.ingest.app.usecase.relay.policy.RelayErrorClassifierImpl/g' {} \;
find usecase -name "*.java" -type f -exec sed -i '' 's/import com\.patra\.ingest\.app\.relay\.event\.OutboxRelayEventPublisher/import com.patra.ingest.app.usecase.relay.publisher.RelayEventPublisher/g' {} \;
find usecase -name "*.java" -type f -exec sed -i '' 's/import com\.patra\.ingest\.app\.relay\.event\.LoggingOutboxRelayEventPublisher/import com.patra.ingest.app.usecase.relay.publisher.LoggingRelayEventPublisher/g' {} \;
find usecase -name "*.java" -type f -exec sed -i '' 's/import com\.patra\.ingest\.app\.relay\.config\./import com.patra.ingest.app.usecase.relay.config./g' {} \;
find usecase -name "*.java" -type f -exec sed -i '' 's/import com\.patra\.ingest\.app\.relay\.support\./import com.patra.ingest.app.usecase.relay.support./g' {} \;
find usecase -name "*.java" -type f -exec sed -i '' 's/import com\.patra\.ingest\.app\.relay\.OutboxRelayApplicationService/import com.patra.ingest.app.usecase.relay.OutboxRelayOrchestrator/g' {} \;
find usecase -name "*.java" -type f -exec sed -i '' 's/import com\.patra\.ingest\.app\.relay\.OutboxRelayUseCase/import com.patra.ingest.app.usecase.relay.OutboxRelayUseCase/g' {} \;

echo "✓ import 语句更新完成"

# ==================== 第四步：更新类名和引用 ====================
echo ""
echo "[4/5] 更新类名和引用..."

# 更新类名和接口引用
find usecase -name "*.java" -type f -exec sed -i '' 's/\bPlanIngestionRequest\b/PlanIngestionCommand/g' {} \;
find usecase -name "*.java" -type f -exec sed -i '' 's/\bOutboxRelayInstruction\b/OutboxRelayCommand/g' {} \;
find usecase -name "*.java" -type f -exec sed -i '' 's/\bPlanAssemblyService\b/PlanAssembler/g' {} \;
find usecase -name "*.java" -type f -exec sed -i '' 's/\bDefaultPlanAssemblyService\b/PlanAssemblerImpl/g' {} \;
find usecase -name "*.java" -type f -exec sed -i '' 's/class DefaultPlanningWindowResolver/class PlanningWindowResolverImpl/g' {} \;
find usecase -name "*.java" -type f -exec sed -i '' 's/\bDefaultPlannerValidator\b/PlannerValidatorImpl/g' {} \;
find usecase -name "*.java" -type f -exec sed -i '' 's/\bPlanIngestionApplicationService\b/PlanIngestionOrchestrator/g' {} \;
find usecase -name "*.java" -type f -exec sed -i '' 's/\bOutboxRelayApplicationService\b/OutboxRelayOrchestrator/g' {} \;
find usecase -name "*.java" -type f -exec sed -i '' 's/\bOutboxRelayPlanBuilder\b/RelayPlanBuilder/g' {} \;
find usecase -name "*.java" -type f -exec sed -i '' 's/\bDefaultRelayErrorClassifier\b/RelayErrorClassifierImpl/g' {} \;
find usecase -name "*.java" -type f -exec sed -i '' 's/\bOutboxRelayEventPublisher\b/RelayEventPublisher/g' {} \;
find usecase -name "*.java" -type f -exec sed -i '' 's/\bLoggingOutboxRelayEventPublisher\b/LoggingRelayEventPublisher/g' {} \;

echo "✓ 类名和引用更新完成"

# ==================== 第五步：更新配置和适配器层引用 ====================
echo ""
echo "[5/5] 更新配置和适配器层引用..."

# 更新 config/IngestAppConfig.java
if [ -f "config/IngestAppConfig.java" ]; then
    sed -i '' 's/import com\.patra\.ingest\.app\.planning\./import com.patra.ingest.app.usecase.plan./g' config/IngestAppConfig.java
    sed -i '' 's/import com\.patra\.ingest\.app\.relay\./import com.patra.ingest.app.usecase.relay./g' config/IngestAppConfig.java
    sed -i '' 's/import com\.patra\.ingest\.app\.validator\./import com.patra.ingest.app.usecase.plan.validator./g' config/IngestAppConfig.java
    echo "✓ IngestAppConfig.java 更新完成"
fi

echo ""
echo "========================================="
echo "✓ 重构完成！"
echo "========================================="
echo ""
echo "下一步："
echo "1. 更新 adapter 层的 import 引用"
echo "2. 运行测试验证"
echo "3. 删除旧的 planning/relay/validator 目录"
echo ""
