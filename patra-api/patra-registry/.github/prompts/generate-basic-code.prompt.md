---
mode: 'agent'
description: '生成 patra-registry 各层基础代码骨架（仅文件最小内容，不含 CRUD 实现）'
---

# 任务目标

在 **patra-registry** 项目中，为 **api / adapter / app / domain / infra** 生成 **基础代码文件**，仅包含最小内容（类壳、注释、必要注解、字段声明），
**不要编写任何增删改查逻辑或额外方法**。

---

# 技术栈

- Java 21
- Spring Boot 3.2.4
- Spring Cloud 2023.0.1
- Spring Cloud Alibaba 2023.0.1.0
- MyBatis-Plus 3.5.12
- MapStruct（转换器需被 Spring 管理）
- Lombok（强制使用）
- hutool-core（通过 patra-common 引入）

---

# 通用生成规则

1. **Lombok 强制使用**
    - 必须使用 Lombok 注解（如 `@Data`、`@SuperBuilder`、`@NoArgsConstructor`、`@AllArgsConstructor`、`@EqualsAndHashCode`、
      `@Value`）。
    - 严禁手写 getter/setter/toString/equals/hashCode。
    - 继承 `BaseDO` 的类使用 `@SuperBuilder`。

2. **MapStruct 转换器**
    - 必须 `@Mapper(componentModel = "spring")`。
    - 仅保留接口与必要注解，不写复杂表达式或业务逻辑。

3. **Mapper（MyBatis-Plus）**
    - 所有 Mapper 必须 `extends BaseMapper<DO>`。
    - 不允许自定义方法。
    - 如确需 XML，仅创建空文件并包含 `<sql>` 占位。

4. **仅骨架、不写实现**
    - Controller/Service/Repository/Producer/Consumer 仅保留类、注解与占位注释；方法体留空或不生成。
    - 不生成任何 CRUD 语句、业务算法、复杂表达式或网络/IO 调用。

---

# 目录结构（参考）

```
api/
  rest/dto/{request,response}/
  rpc/client/
  events/
  enums/
  error/

adapter/
  rest/controller/
  rest/dto/
  scheduler/
  mq/consumer/
  mq/producer/
  config/

app/
  service/
  usecase/{command,query}/
  mapping/
  security/
  event/publisher/
  tx/
  config/

domain/
  model/aggregate/
  model/vo/
  model/event/
  model/enums/
  port/

infra/
  persistence/entity/
  persistence/mapper/
  persistence/repository/
  mapstruct/
  config/
  resources/mapper/xxxMapper.xml
```

---

# 各层生成要点（含 domain / infra）

## A. api（对外契约）

- 生成请求/响应 DTO（仅字段与 `jakarta.validation` 注解占位）。
- 生成 IntegrationEvent DTO 与 `Topics` 常量类（仅字段与注释）。
- 不依赖 Spring/domain/infra/app；不写任何实现逻辑。

**示例：**

```java
// api/events/Topics.java
package com.patra.registry.api.events;

public final class Topics {
    private Topics() {
    }
    // public static final String SAMPLE_CHANGED = "sample.changed";
}
```

---

## B. adapter（协议适配层）

- 生成 REST Controller、MQ Consumer/Producer、Scheduler 类壳。
- Controller 仅保留类注解与路径；Producer/Consumer 只留构造注入与注释占位。
- 依赖 app + api；不依赖 domain/infra；不写业务。

**示例：**

```java
// adapter/rest/controller/SampleController.java
package com.patra.registry.adapter.rest.controller;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/registry/samples")
public class SampleController {
    // 占位：不实现业务逻辑
}
```

---

## C. app（用例编排层）

- 生成 `*AppService`、`usecase/{command,query}`、`event/*AppEvent`、`event/publisher/EventPublisher` 接口、
  `mapping/*Assembler` 的占位文件。
- 仅保留类与注释或最小方法签名；不写业务实现；不依赖 adapter/infra/api。

**示例：**

```java
// app/event/publisher/EventPublisher.java
package com.patra.registry.app.event.publisher;

import com.patra.registry.app.event.AppEvent;

public interface EventPublisher {
    void publish(AppEvent event);
}
```

---

## D. domain（领域内核）

- 只创建**零注解**的聚合/实体/VO/领域事件/仓储端口占位文件。
- **数据库字段对应的领域枚举必须实现 `CodeEnum<C>(patra-common中已存在)`**（仅保留接口实现占位，不写逻辑）。
- **二值开关使用 `boolean`，不创建枚举**。
- 不依赖任何技术框架；使用 hutool 工具类时仅在注释中说明，不新增自定义工具。

**示例：**

```java
// domain/model/enums/SampleStatus.java
package com.patra.registry.domain.model.enums;

// 示例：数据库枚举需实现 CodeEnum<C>
public enum SampleStatus implements com.patra.common.enums.CodeEnum<Integer> {
    ENABLED(1), DISABLED(0);
    private final Integer code;

    SampleStatus(Integer code) {
        this.code = code;
    }

    @Override
    public Integer getCode() {
        return code;
    }
}
```

```java
// domain/port/SampleRepository.java
package com.patra.registry.domain.port;

public interface SampleRepository {
    // 仅占位：以聚合为单位 load/save 的接口签名可后续补充
}
```

```java
// domain/model/aggregate/SampleAggregate.java
package com.patra.registry.domain.model.aggregate;

// 仅占位：零注解、行为驱动；不编写实现
public class SampleAggregate {
}
```

---

## E. infra（基础设施实现层）

- 只生成 DO、Mapper、Repository 实现与 MapStruct 转换器**占位文件**；**不写 CRUD 实现**。
- **Mapper 只能 `extends BaseMapper<DO>`，不得声明任何自定义方法**。
- **DO 的数据库枚举字段使用 domain 的枚举（实现 CodeEnum）**；JSON 字段可用 `String` 或 `JsonNode`（由全局 TypeHandler
  处理，代码仅注释说明）。
- **MapStruct 转换器使用 `@Mapper(componentModel = "spring")`**，仅留空接口或最小签名占位。
- XML（如确需）仅包含 `<sql>` 占位。

**示例：**

```java
// infra/persistence/entity/SampleDO.java
package com.patra.registry.infra.persistence.entity;

import lombok.*;
import lombok.experimental.SuperBuilder;

@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class SampleDO extends BaseDO {
    // private com.patra.registry.domain.model.enums.SampleStatus status; // 示例：使用 domain 枚举
    // private com.fasterxml.jackson.databind.JsonNode recordRemarks;     // 示例：JSON 列可用 JsonNode（baseDO已存在不用写）
}
```

```java
// infra/persistence/mapper/SampleMapper.java
package com.patra.registry.infra.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.patra.registry.infra.persistence.entity.SampleDO;

public interface SampleMapper extends BaseMapper<SampleDO> {
    // 不允许添加任何自定义方法
}
```

```java
// infra/mapstruct/SampleDoConverter.java
package com.patra.registry.infra.mapstruct;

import org.mapstruct.Mapper;
import com.patra.registry.infra.persistence.entity.SampleDO;
import com.patra.registry.domain.model.aggregate.SampleAggregate;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING,
        unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface SampleDoConverter {
    // 仅占位：后续可添加最小 toAggregate/fromAggregate 方法签名
}
```

```java
// infra/persistence/repository/SampleRepositoryImpl.java
package com.patra.registry.infra.persistence.repository;

import org.springframework.stereotype.Repository;

/**
 * 仅占位：实现 domain.port.SampleRepository
 * 不编写 CRUD 逻辑；后续只在此处组合 baseMapper 与转换器。
 */
@Repository
public class SampleRepositoryImpl /* implements SampleRepository */ {
    // private final SampleMapper baseMapper;
    // private final SampleDoConverter converter;
}
```

```xml
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE mapper PUBLIC "-//mybatis.org//DTD Mapper 3.0//EN"
        "http://mybatis.org/dtd/mybatis-3-mapper.dtd">
<mapper namespace="com.patra.registry.infra.persistence.mapper.SampleMapper">
</mapper>
```

---

# 使用方式

在 Copilot Chat 中运行本文件，并指定要生成的层与类名。  
示例：

- “在 domain 生成 SampleAggregate、SampleRepository、SampleStatus（实现 CodeEnum<Integer>）的**占位文件**”
- “在 infra 生成 SampleDO、SampleMapper、SampleRepositoryImpl、SampleDoConverter、XML 占位”
- “在 adapter 生成 SampleController、SampleEventProducer、SampleEventConsumer 占位类”
- “在 api 生成 SampleRequest、SampleResponse、SampleChangedEvent、Topics 常量”
- “在 app 生成 SampleAppService、SampleCommand、EventPublisher 接口与 AppEvent 占位”
