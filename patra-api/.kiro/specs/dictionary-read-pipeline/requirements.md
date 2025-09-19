# Requirements Document

## Introduction

This feature implements a complete dictionary read pipeline for the patra-registry service that provides unified access to system dictionary data. The dictionary system replaces traditional ENUM types with a flexible, database-driven approach that supports zero-DDL evolution and external system integration. The read pipeline will serve as the foundation for all dictionary queries across subsystems while maintaining clean hexagonal architecture boundaries.

## Requirements

### Requirement 1

**User Story:** As a subsystem developer, I want to query dictionary items by type and code, so that I can validate and display dictionary values in my application logic.

#### Acceptance Criteria

1. WHEN I query dictionary items by type_code and item_code THEN the system SHALL return the enabled and non-deleted dictionary item with all relevant metadata
2. WHEN I query dictionary items for a non-existent type or item THEN the system SHALL return an empty result without throwing exceptions
3. WHEN I query dictionary items THEN the system SHALL only return items where enabled=1 and deleted=0
4. WHEN I query dictionary items THEN the system SHALL include display_name, description, sort_order, and audit fields in the response

### Requirement 2

**User Story:** As a subsystem developer, I want to retrieve all items for a specific dictionary type, so that I can populate dropdown lists and selection components.

#### Acceptance Criteria

1. WHEN I query all items for a dictionary type THEN the system SHALL return all enabled and non-deleted items for that type
2. WHEN I query items for a dictionary type THEN the system SHALL return items sorted by sort_order ascending, then by item_code ascending
3. WHEN I query items for a non-existent dictionary type THEN the system SHALL return an empty list
4. WHEN I query items for a dictionary type THEN the system SHALL include the default item indicator (is_default flag)

### Requirement 3

**User Story:** As a subsystem developer, I want to get the default item for a dictionary type, so that I can use appropriate fallback values in business logic.

#### Acceptance Criteria

1. WHEN I query the default item for a dictionary type THEN the system SHALL return the single item where is_default=1, enabled=1, and deleted=0
2. WHEN no default item exists for a dictionary type THEN the system SHALL return an empty result
3. WHEN multiple default items exist for a type (data integrity issue) THEN the system SHALL return the first one ordered by id and log a warning
4. WHEN I query the default item THEN the system SHALL include all item metadata including display_name and description

### Requirement 4

**User Story:** As a subsystem developer, I want to resolve external codes to internal dictionary items, so that I can integrate with legacy systems and external data sources.

#### Acceptance Criteria

1. WHEN I query by external code and source system THEN the system SHALL return the corresponding internal dictionary item via alias mapping
2. WHEN I query by external code for a non-existent alias THEN the system SHALL return an empty result
3. WHEN I query by external code THEN the system SHALL only return items that are enabled and non-deleted
4. WHEN I query by external code THEN the system SHALL include both the alias information and the resolved dictionary item details

### Requirement 5

**User Story:** As a system administrator, I want to validate dictionary references in business tables, so that I can ensure data integrity across the system.

#### Acceptance Criteria

1. WHEN I validate a dictionary reference THEN the system SHALL verify that the item_code exists for the specified type_code
2. WHEN I validate a dictionary reference THEN the system SHALL verify that the referenced item is enabled and not deleted
3. WHEN I validate an invalid dictionary reference THEN the system SHALL return validation failure details including the invalid type_code and item_code
4. WHEN I validate a valid dictionary reference THEN the system SHALL return success confirmation

### Requirement 6

**User Story:** As a subsystem developer, I want to query dictionary metadata and health status, so that I can monitor dictionary system health and troubleshoot issues.

#### Acceptance Criteria

1. WHEN I query dictionary health status THEN the system SHALL return counts of types, total items, enabled items, and default items
2. WHEN I query dictionary health status THEN the system SHALL identify any types without default items
3. WHEN I query dictionary health status THEN the system SHALL identify any types with multiple default items (integrity violations)
4. WHEN I query dictionary metadata THEN the system SHALL return information about available dictionary types with their descriptions and item counts