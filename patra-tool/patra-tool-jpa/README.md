# patra-tool-jpa

## 能力

给服务提供 JPA 数据层的公共装配，自动配置类是 `JpaAutoConfiguration`：JSON 列编解码 `JsonColumnCodec`、审计配置 `JpaAuditingConfiguration`、Hibernate 属性定制 `PatraHibernatePropertiesCustomizer`；类路径上有 patra-tool-web 且 web 错误引擎在场时，再注册 `JpaErrorMappingContributor`。`Clock` 由 patra-tool-launch 提供，本模块不出 `Clock`。

## 配置（前缀 `patra.jpa`）

| 键 | 类型 | 默认值 | 说明 |
|---|---|---|---|
| `enabled` | boolean | `true` | 总开关 |

## 怎么覆盖

```yaml
patra:
  jpa:
    enabled: false
```

## 出哪些默认值

无。
