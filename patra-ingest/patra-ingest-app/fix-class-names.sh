#!/bin/bash

# 修复类定义中的类名

set -e

BASE_DIR="/Users/linqibin/IdeaProjects/Papertrace-api/patra-ingest/patra-ingest-app/src/main/java/com/patra/ingest/app/usecase"

echo "修复类定义中的类名..."

cd "$BASE_DIR"

# Planning UseCase
sed -i '' 's/public record PlanIngestionRequest(/public record PlanIngestionCommand(/g' plan/command/PlanIngestionCommand.java
sed -i '' 's/public interface PlanAssemblyService/public interface PlanAssembler/g' plan/assembler/PlanAssembler.java
sed -i '' 's/public class DefaultPlanAssemblyService implements PlanAssemblyService/public class PlanAssemblerImpl implements PlanAssembler/g' plan/assembler/PlanAssemblerImpl.java
sed -i '' 's/public class DefaultPlannerValidator/public class PlannerValidatorImpl/g' plan/validator/PlannerValidatorImpl.java
sed -i '' 's/public class PlanIngestionApplicationService/public class PlanIngestionOrchestrator/g' plan/PlanIngestionOrchestrator.java

# Relay UseCase  
sed -i '' 's/public record OutboxRelayInstruction(/public record OutboxRelayCommand(/g' relay/command/OutboxRelayCommand.java
sed -i '' 's/public class OutboxRelayPlanBuilder/public class RelayPlanBuilder/g' relay/planner/RelayPlanBuilder.java
sed -i '' 's/public class DefaultRelayErrorClassifier/public class RelayErrorClassifierImpl/g' relay/policy/RelayErrorClassifierImpl.java
sed -i '' 's/public interface OutboxRelayEventPublisher/public interface RelayEventPublisher/g' relay/publisher/RelayEventPublisher.java
sed -i '' 's/public class LoggingOutboxRelayEventPublisher implements OutboxRelayEventPublisher/public class LoggingRelayEventPublisher implements RelayEventPublisher/g' relay/publisher/LoggingRelayEventPublisher.java
sed -i '' 's/public class OutboxRelayApplicationService/public class OutboxRelayOrchestrator/g' relay/OutboxRelayOrchestrator.java

echo "✓ 类定义名称修复完成"
