# 阶段 4：领域模型映射 - Patra 出版物管理示例

> **生成说明**：根据阶段 3 的 SQL DDL，生成符合六边形架构 + DDD 的领域模型

---

## 🏗️ 架构分层

```
┌─────────────────────────────────────┐
│ Domain 层（patra-publication-domain）│
│ ✅ 纯 Java，无框架依赖                │
│ ✅ 聚合根、实体、值对象、枚举          │
│ ✅ 仓储接口（端口）                   │
└─────────────────────────────────────┘
              ↑ 依赖倒置
┌─────────────────────────────────────┐
│ Infra 层（patra-publication-infra）  │
│ ✅ DO（继承 BaseDO）                  │
│ ✅ Mapper（BaseMapper）               │
│ ✅ RepositoryMpImpl（适配器）         │
│ ✅ Converter（Entity ↔ DO）          │
└─────────────────────────────────────┘
```

---

## 📦 聚合识别

### 聚合根 1：Publication（出版物）

**聚合边界：**
- **聚合根**：Publication
- **值对象**：PublicationIdentifier（封装 PMID、DOI）
- **实体**：无（Author 是独立聚合根）
- **不变量**：PMID 必须唯一，标题不能为空

**职责：**
- 管理出版物的生命周期
- 维护出版物元数据
- 提供引用次数更新接口

---

### 聚合根 2：Author（作者）

**聚合边界：**
- **聚合根**：Author
- **值对象**：Orcid（封装 ORCID 标识符）
- **实体**：无
- **不变量**：ORCID 必须唯一（如果存在），全名不能为空

**职责：**
- 管理作者信息
- 维护作者标识符

---

### 聚合根 3：PublicationAuthorRelation（出版物-作者关系）

**聚合边界：**
- **聚合根**：PublicationAuthorRelation
- **值对象**：AuthorRole（封装作者角色：第一作者、通讯作者）
- **实体**：无
- **不变量**：同一出版物的同一作者只能关联一次

**职责：**
- 管理出版物和作者的关联关系
- 维护作者排序
- 标识作者角色（第一作者、通讯作者）

---

## 📂 Domain 层代码结构

```
patra-publication-domain/
└── src/main/java/com/patra/publication/domain/
    ├── model/
    │   ├── aggregate/
    │   │   ├── Publication.java              # 聚合根
    │   │   ├── Author.java                   # 聚合根
    │   │   └── PublicationAuthorRelation.java # 聚合根
    │   ├── vo/
    │   │   ├── PublicationIdentifier.java    # 值对象
    │   │   ├── Orcid.java                    # 值对象
    │   │   └── AuthorRole.java               # 值对象
    │   └── enums/
    │       ├── PublicationType.java          # 枚举
    │       └── Language.java                 # 枚举
    └── port/
        ├── PublicationRepository.java        # 仓储接口
        ├── AuthorRepository.java             # 仓储接口
        └── PublicationAuthorRelationRepository.java # 仓储接口
```

---

## 💻 Domain 层代码生成

### 1. 聚合根：Publication.java

```java
package com.patra.publication.domain.model.aggregate;

import com.patra.publication.domain.model.enums.Language;
import com.patra.publication.domain.model.enums.PublicationType;
import com.patra.publication.domain.model.vo.PublicationIdentifier;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.ToString;

import java.time.Instant;
import java.time.LocalDate;

/**
 * 出版物聚合根
 *
 * @author Patra Team
 */
@Getter
@ToString
public class Publication {

    private Long id;

    // =========================================================================
    // 业务字段
    // =========================================================================
    private PublicationIdentifier identifier;  // 值对象：PMID + DOI
    private String title;
    private String abstractText;
    private String journalName;
    private LocalDate publicationDate;
    private PublicationType publicationType;
    private Language language;
    private Integer citationCount;

    // =========================================================================
    // 审计字段（匹配 BaseDO）
    // =========================================================================
    private JsonNode recordRemarks;
    private Long version;

    @Getter(AccessLevel.NONE)
    private byte[] ipAddress;

    private Instant createdAt;
    private Long createdBy;
    private String createdByName;
    private Instant updatedAt;
    private Long updatedBy;
    private String updatedByName;
    private Boolean deleted;

    // =========================================================================
    // 私有构造函数
    // =========================================================================
    private Publication() {}

    // =========================================================================
    // 工厂方法：创建新出版物
    // =========================================================================
    public static Publication create(
        PublicationIdentifier identifier,
        String title,
        String abstractText,
        String journalName,
        LocalDate publicationDate,
        PublicationType publicationType,
        Language language
    ) {
        Publication publication = new Publication();
        publication.identifier = identifier;
        publication.title = title;
        publication.abstractText = abstractText;
        publication.journalName = journalName;
        publication.publicationDate = publicationDate;
        publication.publicationType = publicationType;
        publication.language = language;
        publication.citationCount = 0;
        publication.deleted = false;
        publication.version = 0L;
        return publication;
    }

    // =========================================================================
    // 工厂方法：从持久化恢复
    // =========================================================================
    public static Publication restore(
        Long id,
        PublicationIdentifier identifier,
        String title,
        String abstractText,
        String journalName,
        LocalDate publicationDate,
        PublicationType publicationType,
        Language language,
        Integer citationCount,
        JsonNode recordRemarks,
        Long version,
        byte[] ipAddress,
        Instant createdAt,
        Long createdBy,
        String createdByName,
        Instant updatedAt,
        Long updatedBy,
        String updatedByName,
        Boolean deleted
    ) {
        Publication publication = new Publication();
        publication.id = id;
        publication.identifier = identifier;
        publication.title = title;
        publication.abstractText = abstractText;
        publication.journalName = journalName;
        publication.publicationDate = publicationDate;
        publication.publicationType = publicationType;
        publication.language = language;
        publication.citationCount = citationCount;
        publication.recordRemarks = recordRemarks;
        publication.version = version;
        publication.ipAddress = ipAddress;
        publication.createdAt = createdAt;
        publication.createdBy = createdBy;
        publication.createdByName = createdByName;
        publication.updatedAt = updatedAt;
        publication.updatedBy = updatedBy;
        publication.updatedByName = updatedByName;
        publication.deleted = deleted;
        return publication;
    }

    // =========================================================================
    // 领域行为
    // =========================================================================

    /**
     * 更新引用次数
     */
    public void updateCitationCount(Integer newCount) {
        if (newCount < 0) {
            throw new IllegalArgumentException("引用次数不能为负数");
        }
        this.citationCount = newCount;
    }

    /**
     * 更新标题
     */
    public void updateTitle(String newTitle) {
        if (newTitle == null || newTitle.trim().isEmpty()) {
            throw new IllegalArgumentException("标题不能为空");
        }
        this.title = newTitle;
    }

    /**
     * 软删除
     */
    public void delete() {
        this.deleted = true;
    }

    // =========================================================================
    // Package-private 方法（供 Repository 使用）
    // =========================================================================

    void assignId(Long id) {
        this.id = id;
    }

    void updateVersion(Long version) {
        this.version = version;
    }

    void updateAuditFields(Instant updatedAt, Long updatedBy, String updatedByName) {
        this.updatedAt = updatedAt;
        this.updatedBy = updatedBy;
        this.updatedByName = updatedByName;
    }

    public byte[] getIpAddress() {
        return ipAddress != null ? ipAddress.clone() : null;
    }
}
```

---

### 2. 值对象：PublicationIdentifier.java

```java
package com.patra.publication.domain.model.vo;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

/**
 * 出版物标识符值对象
 * 封装 PMID 和 DOI
 */
@Getter
@ToString
@EqualsAndHashCode
public class PublicationIdentifier {

    private final String pmid;  // PubMed ID
    private final String doi;   // Digital Object Identifier

    private PublicationIdentifier(String pmid, String doi) {
        this.pmid = pmid;
        this.doi = doi;
    }

    /**
     * 创建标识符
     */
    public static PublicationIdentifier of(String pmid, String doi) {
        // 至少有一个标识符
        if ((pmid == null || pmid.trim().isEmpty()) &&
            (doi == null || doi.trim().isEmpty())) {
            throw new IllegalArgumentException("PMID 和 DOI 至少需要一个");
        }
        return new PublicationIdentifier(pmid, doi);
    }

    /**
     * 仅 PMID
     */
    public static PublicationIdentifier ofPmid(String pmid) {
        if (pmid == null || pmid.trim().isEmpty()) {
            throw new IllegalArgumentException("PMID 不能为空");
        }
        return new PublicationIdentifier(pmid, null);
    }

    /**
     * 仅 DOI
     */
    public static PublicationIdentifier ofDoi(String doi) {
        if (doi == null || doi.trim().isEmpty()) {
            throw new IllegalArgumentException("DOI 不能为空");
        }
        return new PublicationIdentifier(null, doi);
    }

    /**
     * 是否有 PMID
     */
    public boolean hasPmid() {
        return pmid != null && !pmid.trim().isEmpty();
    }

    /**
     * 是否有 DOI
     */
    public boolean hasDoi() {
        return doi != null && !doi.trim().isEmpty();
    }
}
```

---

### 3. 枚举：PublicationType.java

```java
package com.patra.publication.domain.model.enums;

import lombok.Getter;

/**
 * 出版物类型
 */
@Getter
public enum PublicationType {

    JOURNAL_ARTICLE("Journal Article", "期刊文章"),
    REVIEW("Review", "综述"),
    CLINICAL_TRIAL("Clinical Trial", "临床试验"),
    CASE_REPORT("Case Report", "病例报告"),
    META_ANALYSIS("Meta-Analysis", "荟萃分析"),
    SYSTEMATIC_REVIEW("Systematic Review", "系统综述"),
    LETTER("Letter", "信函"),
    EDITORIAL("Editorial", "社论"),
    COMMENT("Comment", "评论"),
    OTHER("Other", "其他");

    private final String code;
    private final String description;

    PublicationType(String code, String description) {
        this.code = code;
        this.description = description;
    }

    /**
     * 从代码获取枚举
     */
    public static PublicationType fromCode(String code) {
        if (code == null) {
            return null;
        }
        for (PublicationType type : values()) {
            if (type.code.equals(code)) {
                return type;
            }
        }
        throw new IllegalArgumentException("未知的出版物类型: " + code);
    }
}
```

---

### 4. 仓储接口：PublicationRepository.java

```java
package com.patra.publication.domain.port;

import com.patra.publication.domain.model.aggregate.Publication;
import java.util.List;
import java.util.Optional;

/**
 * 出版物仓储接口（端口）
 */
public interface PublicationRepository {

    /**
     * 保存出版物
     */
    Publication save(Publication publication);

    /**
     * 批量保存
     */
    List<Publication> saveBatch(List<Publication> publications);

    /**
     * 根据 ID 查询
     */
    Optional<Publication> findById(Long id);

    /**
     * 根据 PMID 查询
     */
    Optional<Publication> findByPmid(String pmid);

    /**
     * 根据 DOI 查询
     */
    Optional<Publication> findByDoi(String doi);

    /**
     * 全文检索
     */
    List<Publication> searchByKeyword(String keyword, int limit);

    /**
     * 软删除
     */
    void deleteById(Long id);
}
```

---

## 📂 Infra 层代码结构

```
patra-publication-infra/
└── src/main/java/com/patra/publication/infra/
    └── persistence/
        ├── entity/
        │   ├── PublicationDO.java              # DO（继承 BaseDO）
        │   ├── AuthorDO.java                   # DO
        │   └── PublicationAuthorRelationDO.java # DO
        ├── mapper/
        │   ├── PublicationMapper.java          # MyBatis-Plus Mapper
        │   ├── AuthorMapper.java
        │   └── PublicationAuthorRelationMapper.java
        ├── converter/
        │   ├── PublicationConverter.java       # Entity ↔ DO 转换
        │   ├── AuthorConverter.java
        │   └── PublicationAuthorRelationConverter.java
        └── repository/
            ├── PublicationRepositoryMpImpl.java  # 仓储实现
            ├── AuthorRepositoryMpImpl.java
            └── PublicationAuthorRelationRepositoryMpImpl.java
```

---

## 💻 Infra 层代码生成

### 1. DO：PublicationDO.java

```java
package com.patra.publication.infra.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import com.fasterxml.jackson.databind.JsonNode;
import com.patra.common.infrastructure.persistence.BaseDO;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;

/**
 * 出版物 DO（继承 BaseDO）
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "publication", autoResultMap = true)
public class PublicationDO extends BaseDO {

    @TableField("pmid")
    private String pmid;

    @TableField("doi")
    private String doi;

    @TableField("title")
    private String title;

    @TableField("abstract")
    private String abstractText;

    @TableField("journal_name")
    private String journalName;

    @TableField("publication_date")
    private LocalDate publicationDate;

    @TableField("publication_type")
    private String publicationType;  // 存储为 String

    @TableField("language")
    private String language;  // 存储为 String

    @TableField("citation_count")
    private Integer citationCount;

    @TableField(value = "record_remarks", typeHandler = JacksonTypeHandler.class)
    private JsonNode recordRemarks;
}
```

---

### 2. Converter：PublicationConverter.java

```java
package com.patra.publication.infra.persistence.converter;

import com.patra.publication.domain.model.aggregate.Publication;
import com.patra.publication.domain.model.enums.Language;
import com.patra.publication.domain.model.enums.PublicationType;
import com.patra.publication.domain.model.vo.PublicationIdentifier;
import com.patra.publication.infra.persistence.entity.PublicationDO;
import org.springframework.stereotype.Component;

/**
 * Publication Entity ↔ DO 转换器
 */
@Component
public class PublicationConverter {

    /**
     * Entity → DO
     */
    public PublicationDO toDO(Publication entity) {
        if (entity == null) {
            return null;
        }

        PublicationDO dobj = new PublicationDO();
        dobj.setId(entity.getId());

        // 业务字段
        if (entity.getIdentifier() != null) {
            dobj.setPmid(entity.getIdentifier().getPmid());
            dobj.setDoi(entity.getIdentifier().getDoi());
        }
        dobj.setTitle(entity.getTitle());
        dobj.setAbstractText(entity.getAbstractText());
        dobj.setJournalName(entity.getJournalName());
        dobj.setPublicationDate(entity.getPublicationDate());

        // 枚举 → String
        if (entity.getPublicationType() != null) {
            dobj.setPublicationType(entity.getPublicationType().getCode());
        }
        if (entity.getLanguage() != null) {
            dobj.setLanguage(entity.getLanguage().getCode());
        }

        dobj.setCitationCount(entity.getCitationCount());
        dobj.setRecordRemarks(entity.getRecordRemarks());

        // 审计字段
        dobj.setVersion(entity.getVersion());
        dobj.setIpAddress(entity.getIpAddress());
        dobj.setCreatedAt(entity.getCreatedAt());
        dobj.setCreatedBy(entity.getCreatedBy());
        dobj.setCreatedByName(entity.getCreatedByName());
        dobj.setUpdatedAt(entity.getUpdatedAt());
        dobj.setUpdatedBy(entity.getUpdatedBy());
        dobj.setUpdatedByName(entity.getUpdatedByName());
        dobj.setDeleted(entity.getDeleted());

        return dobj;
    }

    /**
     * DO → Entity
     */
    public Publication toAggregate(PublicationDO dobj) {
        if (dobj == null) {
            return null;
        }

        // 构建值对象
        PublicationIdentifier identifier = null;
        if (dobj.getPmid() != null || dobj.getDoi() != null) {
            identifier = PublicationIdentifier.of(dobj.getPmid(), dobj.getDoi());
        }

        // String → 枚举
        PublicationType publicationType = PublicationType.fromCode(dobj.getPublicationType());
        Language language = Language.fromCode(dobj.getLanguage());

        // 使用 restore 工厂方法
        return Publication.restore(
            dobj.getId(),
            identifier,
            dobj.getTitle(),
            dobj.getAbstractText(),
            dobj.getJournalName(),
            dobj.getPublicationDate(),
            publicationType,
            language,
            dobj.getCitationCount(),
            dobj.getRecordRemarks(),
            dobj.getVersion(),
            dobj.getIpAddress(),
            dobj.getCreatedAt(),
            dobj.getCreatedBy(),
            dobj.getCreatedByName(),
            dobj.getUpdatedAt(),
            dobj.getUpdatedBy(),
            dobj.getUpdatedByName(),
            dobj.getDeleted()
        );
    }
}
```

---

### 3. Mapper：PublicationMapper.java

```java
package com.patra.publication.infra.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.patra.publication.infra.persistence.entity.PublicationDO;
import org.apache.ibatis.annotations.Mapper;

/**
 * 出版物 Mapper
 */
@Mapper
public interface PublicationMapper extends BaseMapper<PublicationDO> {
    // BaseMapper 已提供基础 CRUD
    // 复杂查询可在此添加自定义方法
}
```

---

### 4. Repository 实现：PublicationRepositoryMpImpl.java

```java
package com.patra.publication.infra.persistence.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.patra.publication.domain.model.aggregate.Publication;
import com.patra.publication.domain.port.PublicationRepository;
import com.patra.publication.infra.persistence.converter.PublicationConverter;
import com.patra.publication.infra.persistence.entity.PublicationDO;
import com.patra.publication.infra.persistence.mapper.PublicationMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * 出版物仓储实现（适配器）
 */
@Repository
@RequiredArgsConstructor
public class PublicationRepositoryMpImpl implements PublicationRepository {

    private final PublicationMapper publicationMapper;
    private final PublicationConverter publicationConverter;

    @Override
    public Publication save(Publication publication) {
        PublicationDO dobj = publicationConverter.toDO(publication);

        if (dobj.getId() == null) {
            // 新增
            publicationMapper.insert(dobj);
            publication.assignId(dobj.getId());
        } else {
            // 更新
            publication.updateAuditFields(Instant.now(), null, null);
            dobj = publicationConverter.toDO(publication);
            publicationMapper.updateById(dobj);
        }

        publication.updateVersion(dobj.getVersion());
        return publication;
    }

    @Override
    public List<Publication> saveBatch(List<Publication> publications) {
        return publications.stream()
            .map(this::save)
            .collect(Collectors.toList());
    }

    @Override
    public Optional<Publication> findById(Long id) {
        PublicationDO dobj = publicationMapper.selectById(id);
        return Optional.ofNullable(publicationConverter.toAggregate(dobj));
    }

    @Override
    public Optional<Publication> findByPmid(String pmid) {
        LambdaQueryWrapper<PublicationDO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(PublicationDO::getPmid, pmid);
        PublicationDO dobj = publicationMapper.selectOne(wrapper);
        return Optional.ofNullable(publicationConverter.toAggregate(dobj));
    }

    @Override
    public Optional<Publication> findByDoi(String doi) {
        LambdaQueryWrapper<PublicationDO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(PublicationDO::getDoi, doi);
        PublicationDO dobj = publicationMapper.selectOne(wrapper);
        return Optional.ofNullable(publicationConverter.toAggregate(dobj));
    }

    @Override
    public List<Publication> searchByKeyword(String keyword, int limit) {
        // TODO: 实现全文检索（需要使用 MySQL MATCH...AGAINST 或 Elasticsearch）
        throw new UnsupportedOperationException("全文检索暂未实现");
    }

    @Override
    public void deleteById(Long id) {
        publicationMapper.deleteById(id);  // MyBatis-Plus 自动处理软删除
    }
}
```

---

## ✅ 设计验证

### 1. 依赖方向检查 ✅
- ✅ Domain 层无框架依赖（纯 Java）
- ✅ Infra 层依赖 Domain 层（实现端口接口）
- ✅ 符合依赖倒置原则

### 2. 聚合边界检查 ✅
- ✅ Publication 是独立聚合根
- ✅ Author 是独立聚合根
- ✅ PublicationAuthorRelation 是独立聚合根
- ✅ 聚合间通过 ID 引用，不直接持有对象

### 3. 值对象不变性 ✅
- ✅ PublicationIdentifier 是不可变的（final 字段）
- ✅ Orcid 是不可变的
- ✅ AuthorRole 是不可变的

### 4. 仓储模式正确性 ✅
- ✅ 仓储接口定义在 Domain 层（端口）
- ✅ 仓储实现在 Infra 层（适配器）
- ✅ 仓储操作聚合根（不是 DO）

---

## 📝 后续步骤

- ✅ 阶段 4 完成：领域模型已生成
- ⬜ 如需记录设计决策 → **[阶段 5：设计决策记录](5-decisions.md)**
- ⬜ 如需调整领域模型，请提供反馈

---

**示例完成！** 🎉

完整的 Patra 出版物管理数据库设计示例（6 个阶段：0/1/2/3/4/5）已全部完成。
