# Registry Service Error Code Catalog

This document provides a comprehensive catalog of all Registry service error codes. The error codes follow the `REG-NNNN` format and are designed to provide consistent, machine-readable error identification across all Registry service operations.

## Error Code Format

All Registry error codes follow the structured format: **`REG-NNNN`**

- **`REG`** - Registry service context prefix
- **`NNNN`** - Four-digit numeric code

## Error Code Categories

### Common HTTP-Aligned Codes (0xxx Series)

These codes correspond directly to standard HTTP status codes and provide consistent error handling for common scenarios.

| Code | HTTP Status | Description | Usage |
|------|-------------|-------------|-------|
| `REG-0400` | 400 | Bad Request | The request was invalid or malformed |
| `REG-0401` | 401 | Unauthorized | Authentication is required or has failed |
| `REG-0403` | 403 | Forbidden | The request is understood but access is denied |
| `REG-0404` | 404 | Not Found | The requested resource could not be found |
| `REG-0409` | 409 | Conflict | The request conflicts with the current state of the resource |
| `REG-0422` | 422 | Unprocessable Entity | The request is well-formed but contains semantic errors |
| `REG-0429` | 429 | Too Many Requests | Rate limit exceeded or quota exhausted |
| `REG-0500` | 500 | Internal Server Error | An unexpected error occurred on the server |
| `REG-0503` | 503 | Service Unavailable | The service is temporarily unavailable |
| `REG-0504` | 504 | Gateway Timeout | Timeout occurred while waiting for upstream service |

### Business-Specific Codes (1xxx Series)

These codes represent business logic errors specific to Registry service operations and map directly to domain exceptions.

#### Dictionary Operations (14xx Series)

| Code | Domain Exception | Description | Usage | Example Scenario |
|------|------------------|-------------|-------|------------------|
| `REG-1401` | `DictionaryNotFoundException` | Dictionary Type Not Found | The specified dictionary type could not be found | Accessing dictionary type "unknown-type" |
| `REG-1402` | `DictionaryNotFoundException` | Dictionary Item Not Found | The specified dictionary item could not be found | Accessing item "missing-item" in dictionary type "sources" |
| `REG-1403` | `DictionaryItemDisabled` | Dictionary Item Disabled | The specified dictionary item is disabled | Attempting to use disabled dictionary item |
| `REG-1404` | `DictionaryTypeAlreadyExists` | Dictionary Type Already Exists | Attempted to create a dictionary type that already exists | Creating dictionary type "sources" when it already exists |
| `REG-1405` | `DictionaryItemAlreadyExists` | Dictionary Item Already Exists | Attempted to create a dictionary item that already exists | Creating item "pubmed" in type "sources" when it already exists |
| `REG-1406` | `DictionaryTypeDisabled` | Dictionary Type Disabled | The specified dictionary type is disabled | Attempting to use disabled dictionary type |
| `REG-1407` | `DictionaryValidationException` | Dictionary Validation Error | The dictionary data failed validation | Submitting dictionary data with invalid format |
| `REG-1408` | `DictionaryDefaultItemMissing` | Dictionary Default Item Missing | Required default item is missing from dictionary type | Dictionary type missing required default item |
| `REG-1409` | `DictionaryRepositoryException` | Dictionary Repository Error | Database or repository layer error occurred | Database connection failure during dictionary operation |

#### Registry General Operations (15xx Series)

| Code | Domain Exception | Description | Usage | Example Scenario |
|------|------------------|-------------|-------|------------------|
| `REG-1501` | `RegistryQuotaExceeded` | Registry Quota Exceeded | Operation would exceed system quotas or limits | Creating too many dictionary items beyond system limit |

## Error Response Format

All Registry service errors follow the RFC 7807 ProblemDetail format:

```json
{
  "type": "https://errors.example.com/reg-1001",
  "title": "REG-1001",
  "status": 409,
  "detail": "Namespace already exists: medical-literature",
  "code": "REG-1001",
  "traceId": "abc123def456",
  "path": "/api/registry/namespaces",
  "timestamp": "2025-09-19T10:30:00Z"
}
```

## Usage Guidelines

### For API Consumers

1. **Programmatic Handling**: Use the `code` field for programmatic error handling
2. **Human-Readable Messages**: Use the `detail` field for user-facing error messages
3. **Debugging**: Use the `traceId` field for debugging and support requests
4. **Error Classification**: Use the code prefix and series to classify error types

### For Developers

1. **Error Code Selection**: Choose the most specific error code available
2. **Fallback Strategy**: Use HTTP-aligned codes (0xxx series) when no specific business code applies
3. **Error Messages**: Provide clear, actionable error messages in the `detail` field
4. **Consistency**: Maintain consistent error handling patterns across all endpoints

## Append-Only Policy

This error catalog follows an **append-only principle** to ensure API stability:

- ✅ **Allowed**: Adding new error codes
- ✅ **Allowed**: Adding new documentation or examples
- ❌ **Forbidden**: Removing existing error codes
- ❌ **Forbidden**: Changing the meaning of existing error codes
- ❌ **Forbidden**: Modifying existing error code strings

## Integration Examples

### Java Usage

```java
// Domain exceptions already exist and map to error codes
// Dictionary operations
throw new DictionaryNotFoundException("sources"); // Maps to REG-1401
throw new DictionaryNotFoundException("sources", "pubmed"); // Maps to REG-1402
throw new DictionaryItemDisabled("sources", "disabled-item"); // Maps to REG-1403
throw new DictionaryTypeAlreadyExists("sources"); // Maps to REG-1404

// Error codes can be used in application layer for mapping
public class RegistryErrorCodeMapper {
    public static RegistryErrorCode mapException(RegistryException exception) {
        return switch (exception) {
            case DictionaryNotFoundException dnf when dnf.getItemCode() == null -> 
                RegistryErrorCode.REG_1401;
            case DictionaryNotFoundException dnf when dnf.getItemCode() != null -> 
                RegistryErrorCode.REG_1402;
            case DictionaryItemDisabled did -> 
                RegistryErrorCode.REG_1403;
            case DictionaryTypeAlreadyExists dtae -> 
                RegistryErrorCode.REG_1404;
            // ... other mappings
            default -> RegistryErrorCode.REG_0500; // fallback
        };
    }
}
```

### Client-Side Handling

```javascript
// JavaScript/TypeScript client handling
switch (error.code) {
    case 'REG-1401':
        // Dictionary type not found
        showError('The specified dictionary type could not be found.');
        break;
    case 'REG-1402':
        // Dictionary item not found
        showError('The specified dictionary item could not be found.');
        break;
    case 'REG-1403':
        // Dictionary item disabled
        showError('This dictionary item is currently disabled and cannot be used.');
        break;
    case 'REG-1404':
        // Dictionary type already exists
        showError('This dictionary type name is already taken. Please choose a different name.');
        break;
    case 'REG-1405':
        // Dictionary item already exists
        showError('This dictionary item already exists in the specified type.');
        break;
    default:
        showError('An unexpected error occurred. Please try again.');
}
```

## Version History

| Version | Date | Changes |
|---------|------|---------|
| 0.1.0 | 2025-09-19 | Initial error code catalog with HTTP-aligned and business-specific codes |

## Support

For questions about error codes or to request new error codes, please:

1. Check this documentation first
2. Review the source code in `RegistryErrorCode.java`
3. Create an issue with the development team
4. Include the `traceId` from error responses for debugging

---

*This documentation is automatically generated from the `RegistryErrorCode` enum. For the most up-to-date information, refer to the source code.*