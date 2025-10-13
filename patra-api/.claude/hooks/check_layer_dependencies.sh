#!/bin/bash
# Architecture Layer Dependency Validator
# Prevents violations of Hexagonal Architecture dependency rules for Papertrace project
#
# Rules enforced:
# 1. Domain layer: ONLY patra-common (NO Spring/JPA/Hibernate/framework dependencies)
# 2. API layer: NO framework dependencies (external contracts)
# 3. App layer: Can use domain + patra-common + core starter (NO web/infra dependencies)
# 4. Infra layer: Can use domain + mybatis starter (NO adapter dependencies)
# 5. Adapter layer: Can use app + api + web starters (NO direct infra calls)

# Read input from stdin
INPUT=$(cat)
FILE_PATH=$(echo "$INPUT" | jq -r '.tool_input.file_path // ""')
CONTENT=$(echo "$INPUT" | jq -r '.tool_input.new_string // .tool_input.content // ""')

# Exit if no file path
if [ -z "$FILE_PATH" ] || [ "$FILE_PATH" = "null" ]; then
  exit 0
fi

# Only check Java files
if [[ ! "$FILE_PATH" =~ \.java$ ]]; then
  exit 0
fi

# Skip test files (they can have more flexibility)
if [[ "$FILE_PATH" =~ /test/ ]] || [[ "$FILE_PATH" =~ Test\.java$ ]]; then
  exit 0
fi

# ============================================
# 1. DOMAIN LAYER: Pure Java only
# ============================================
if [[ "$FILE_PATH" =~ -domain/src/main/ ]]; then
  # Check for forbidden framework imports
  if echo "$CONTENT" | grep -E 'import (org\.springframework|jakarta\.(persistence|validation|inject|annotation)|org\.hibernate|javax\.persistence)' | grep -v '^//'; then
    echo "❌ BLOCKED: Domain layer cannot have framework dependencies!"
    echo ""
    echo "Violations found:"
    echo "$CONTENT" | grep -E 'import (org\.springframework|jakarta\.(persistence|validation|inject|annotation)|org\.hibernate|javax\.persistence)' | grep -v '^//' | sed 's/^/  /'
    echo ""
    echo "📖 Domain layer rules (from AGENTS-architecture.md):"
    echo "  - Pure Java ONLY (no @Entity, @Service, @Autowired, etc.)"
    echo "  - Only patra-common is allowed as dependency"
    echo "  - Use Port interfaces to define contracts"
    echo ""
    echo "💡 Solutions:"
    echo "  - Use domain interfaces (Ports) instead of framework annotations"
    echo "  - Move persistence concerns to Infrastructure layer"
    echo "  - Move validation to Value Objects with pure Java"
    exit 2
  fi

  # Check for common framework annotations
  if echo "$CONTENT" | grep -E '@(Entity|Table|Service|Component|Autowired|Repository|Transactional|Valid|Validated)' | grep -v '^//'; then
    echo "❌ BLOCKED: Domain layer cannot use framework annotations!"
    echo ""
    echo "Violations found:"
    echo "$CONTENT" | grep -E '@(Entity|Table|Service|Component|Autowired|Repository|Transactional|Valid|Validated)' | grep -v '^//' | sed 's/^/  /'
    echo ""
    echo "📖 Domain layer is the core - must be framework-agnostic"
    echo ""
    echo "💡 Use pure Java patterns instead:"
    echo "  - @Entity → Domain Entity (pure Java class)"
    echo "  - @Service → Domain Service (pure Java class)"
    echo "  - @Valid → Value Object validation methods"
    exit 2
  fi
fi

# ============================================
# 2. API LAYER: No framework dependencies
# ============================================
if [[ "$FILE_PATH" =~ -api/src/main/ ]]; then
  if echo "$CONTENT" | grep -E 'import org\.springframework' | grep -v '^//'; then
    echo "⚠️  WARNING: API layer should not depend on Spring Framework"
    echo ""
    echo "📖 API modules are external contracts - keep them framework-agnostic"
    echo "💡 Use plain Java DTOs and interfaces"
  fi
fi

# ============================================
# 3. APP LAYER: No business logic, no infra/adapter deps
# ============================================
if [[ "$FILE_PATH" =~ -app/src/main/ ]]; then
  # Check for direct Infrastructure imports (repositories should be injected via ports)
  if echo "$CONTENT" | grep -E 'import .*\.infra\.' | grep -v '^//'; then
    echo "⚠️  WARNING: App layer should not directly import Infrastructure classes"
    echo ""
    echo "📖 Use Dependency Inversion - depend on Domain Ports, not concrete implementations"
    echo "💡 Inject Port interfaces (e.g., ProvenanceRepositoryPort) instead of RepositoryImpl"
  fi

  # Check for direct Adapter imports
  if echo "$CONTENT" | grep -E 'import .*\.adapter\.' | grep -v '^//'; then
    echo "❌ BLOCKED: App layer cannot import Adapter layer!"
    echo ""
    echo "📖 Dependency flow: Adapter → App → Domain ← Infra"
    echo "App layer should never know about Adapters"
    exit 2
  fi

  # Check for business logic patterns in Orchestrator files
  if [[ "$FILE_PATH" =~ Orchestrator\.java$ ]]; then
    # Look for complex business logic (multiple if/for loops, calculations)
    if echo "$CONTENT" | grep -E 'for\s*\(.*\)\s*\{[^}]{200,}' > /dev/null; then
      echo "⚠️  INFO: Orchestrator contains complex logic - consider moving to Domain"
      echo "📖 Orchestrators should coordinate, not implement business rules"
    fi
  fi
fi

# ============================================
# 4. INFRA LAYER: No adapter deps
# ============================================
if [[ "$FILE_PATH" =~ -infra/src/main/ ]]; then
  # Check for Adapter imports
  if echo "$CONTENT" | grep -E 'import .*\.adapter\.' | grep -v '^//'; then
    echo "❌ BLOCKED: Infrastructure layer cannot import Adapter layer!"
    echo ""
    echo "📖 Dependency flow: Adapter → App → Domain ← Infra"
    echo "Infra implements Domain ports, should not know about Adapters"
    exit 2
  fi

  # Check if DO classes are properly isolated (not exposed outside)
  if [[ "$FILE_PATH" =~ DO\.java$ ]]; then
    # DO classes should not be public outside infra package
    if echo "$CONTENT" | grep -E 'public class.*DO\s' > /dev/null; then
      echo "⚠️  INFO: DO classes should be package-private or not exposed outside Infra"
      echo "💡 Use MapStruct to convert DO ↔ Domain objects"
    fi
  fi
fi

# ============================================
# 5. ADAPTER LAYER: No direct infra calls
# ============================================
if [[ "$FILE_PATH" =~ -adapter/src/main/ ]]; then
  # Check for direct Repository imports (should use orchestrators)
  if echo "$CONTENT" | grep -E 'import .*\.(infra|repository)\..*Repository' | grep -v '^//'; then
    echo "❌ BLOCKED: Adapter layer should not directly call Infrastructure repositories!"
    echo ""
    echo "Violations found:"
    echo "$CONTENT" | grep -E 'import .*\.(infra|repository)\..*Repository' | grep -v '^//' | sed 's/^/  /'
    echo ""
    echo "📖 Dependency flow: Adapter → App (Orchestrator) → Domain Ports ← Infra"
    echo ""
    echo "💡 Solutions:"
    echo "  - Controllers should call Orchestrators"
    echo "  - Jobs should call Orchestrators"
    echo "  - MQ Listeners should call Orchestrators"
    echo "  - Never inject repositories directly in adapters"
    exit 2
  fi
fi

# ============================================
# Success - no violations detected
# ============================================
exit 0
