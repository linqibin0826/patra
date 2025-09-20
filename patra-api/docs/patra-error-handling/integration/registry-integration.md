# Registry 服务集成示例

完整的 Registry 服务集成 Patra 错误处理系统的工作示例。

## 目录

1. [完整服务设置](#完整服务设置)
2. [领域层示例](#领域层示例)
3. [应用层示例](#应用层示例)
4. [适配器层示例](#适配器层示例)
5. [客户端集成示例](#客户端集成示例)
6. [测试示例](#测试示例)

## 完整服务设置

### 1. 依赖配置 (patra-registry-boot/pom.xml)

```xml
<dependencies>
    <!-- 错误处理 Starters -->
    <dependency>
        <groupId>com.patra</groupId>
        <artifactId>patra-spring-boot-starter-core</artifactId>
    </dependency>
    <dependency>
        <groupId>com.patra</groupId>
        <artifactId>patra-spring-boot-starter-web</artifactId>
    </dependency>
    <dependency>
        <groupId>com.patra</groupId>
        <artifactId>patra-spring-boot-starter-mybatis</artifactId>
    </dependency>
    
    <!-- Registry 模块 -->
    <dependency>
        <groupId>com.patra</groupId>
        <artifactId>patra-registry-adapter</artifactId>
    </dependency>
    <!-- ... 其他依赖 ... -->
</dependencies>
```

### 2. 应用配置 (application.yml)

```yaml
# Registry 服务配置
spring:
  application:
    name: patra-registry
  profiles:
    active: dev

# 错误处理配置
patra:
  error:
    enabled: true
    context-prefix: REG
  web:
    problem:
      enabled: true
      type-base-url: "https://errors.patra.com/"
      include-stack: false
```

## 领域层示例

### 1. 领域异常层次结构

```java
// 基础 Registry 异常
public abstract class RegistryException extends DomainException {
    protected RegistryException(String message) { super(message); }
}

// 语义化基础异常
public abstract class RegistryNotFound extends RegistryException implements HasErrorTraits {
    protected RegistryNotFound(String message) { super(message); }
    
    @Override
    public Set<ErrorTrait> getErrorTraits() {
        return Set.of(ErrorTrait.NOT_FOUND);
    }
}

public abstract class RegistryConflict extends RegistryException implements HasErrorTraits {
    protected RegistryConflict(String message) { super(message); }
    
    @Override
    public Set<ErrorTrait> getErrorTraits() {
        return Set.of(ErrorTrait.CONFLICT);
    }
}
```

### 2. 具体领域异常

```java
// 字典未找到异常
@Getter
public class DictionaryNotFoundException extends RegistryNotFound {
    private final String typeCode;
    private final String itemCode;
    
    // 字典类型未找到
    public DictionaryNotFoundException(String typeCode) {
        super(String.format("Dictionary type not found: %s", typeCode));
        this.typeCode = typeCode;
        this.itemCode = null;
    }
    
    // 字典项未找到
    public DictionaryNotFoundException(String typeCode, String itemCode) {
        super(String.format("Dictionary item not found: typeCode=%s, itemCode=%s", typeCode, itemCode));
        this.typeCode = typeCode;
        this.itemCode = itemCode;
    }
}
```

完整的示例代码请参考原始的 `docs/REGISTRY_INTEGRATION_EXAMPLES.md` 文件。