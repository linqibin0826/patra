# Registry API Module

This module contains the API contracts and error handling definitions for the Registry service.

## Error Code Catalog

The Registry service uses a structured error code system to provide consistent, machine-readable error identification across all operations.

### Key Components

- **`RegistryErrorCode`** - Enum implementing `ErrorCodeLike` with all Registry service error codes
- **Error Code Documentation** - Comprehensive catalog in `ERROR_CODE_CATALOG.md`
- **Package Documentation** - Detailed package information in `package-info.java`

### Error Code Format

All Registry error codes follow the format: **`REG-NNNN`**

- **`REG`** - Registry service context prefix
- **`NNNN`** - Four-digit numeric code

### Categories

- **0xxx Series** - Common HTTP-aligned codes (REG-0400 to REG-0504)
- **14xx Series** - Dictionary operations (REG-1401 to REG-1409)
- **15xx Series** - Registry general operations (REG-1501)

### Usage

```java
import com.patra.registry.api.error.RegistryErrorCode;

// Access error codes
String code = RegistryErrorCode.REG_1401.code(); // "REG-1401"

// Use in error responses
ProblemDetail problem = ProblemDetail.forStatusAndDetail(
    HttpStatus.NOT_FOUND, 
    "Dictionary type not found"
);
problem.setProperty("code", RegistryErrorCode.REG_1401.code());
```

### Domain Exception Mapping

The error codes map directly to existing domain exceptions:

| Error Code | Domain Exception | Description |
|------------|------------------|-------------|
| `REG-1401` | `DictionaryNotFoundException` | Dictionary type not found |
| `REG-1402` | `DictionaryNotFoundException` | Dictionary item not found |
| `REG-1403` | `DictionaryItemDisabled` | Dictionary item disabled |
| `REG-1404` | `DictionaryTypeAlreadyExists` | Dictionary type already exists |
| `REG-1405` | `DictionaryItemAlreadyExists` | Dictionary item already exists |

### Append-Only Policy

This error catalog follows an **append-only principle**:

- ✅ New error codes can be added
- ❌ Existing codes cannot be removed or modified
- ✅ Documentation can be enhanced
- ❌ Code meanings cannot be changed

This ensures API stability and backward compatibility.

## Documentation

For complete documentation, see:
- [ERROR_CODE_CATALOG.md](ERROR_CODE_CATALOG.md) - Comprehensive error code reference
- [package-info.java](src/main/java/com/patra/registry/api/error/package-info.java) - Package documentation