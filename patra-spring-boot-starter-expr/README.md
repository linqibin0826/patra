# patra-spring-boot-starter-expr

> Expression engine integration — auto-configures expression builders and validators.

## 📌 Purpose

Integrates **patra-expr-kernel** with Spring Boot:
- Expression builder beans
- Canonicalizer auto-configured
- Expression validation support
- JSON codec beans

## 🔧 Auto-Configurations

### Expression Utilities
- `ExprCanonicalizer` bean (singleton)
- `ExprJsonCodec` bean
- Expression validator beans

## 🔗 Dependencies

```xml
<dependency>
    <groupId>com.papertrace</groupId>
    <artifactId>patra-spring-boot-starter-expr</artifactId>
</dependency>
```

Includes: `patra-expr-kernel`, Jackson

## 🚀 Usage

```java
@Service
@RequiredArgsConstructor
public class PlanExpressionBuilder {

    public PlanExpressionDescriptor build(PlanTriggerNorm norm, ProvenanceConfigSnapshot config) {
        Expr expr = Exprs.and(List.of(
            Exprs.rangeDate("date", norm.windowFrom(), norm.windowTo())
        ));

        ExprCanonicalSnapshot snapshot = ExprCanonicalizer.canonicalize(expr);
        return new PlanExpressionDescriptor(snapshot.hash(), snapshot.canonicalJson());
    }
}
```

---

**Last Updated**: 2025-01-12
