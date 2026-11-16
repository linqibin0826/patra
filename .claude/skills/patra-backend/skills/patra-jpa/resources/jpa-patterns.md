# JPA 实现模板

## Entity 模板

主表继承 `BaseJpaEntity`，其他表继承 `ValueObjectJpaEntity`。索引、唯一索引、列类型写在 Flyway 脚本里，实体不写 `@Index` / `columnDefinition`。

```java
@Entity
@Table(name = "cat_venue")
@Getter
@Setter
public class VenueEntity extends BaseJpaEntity {

    @Column(name = "venue_name", length = 500)
    private String venueName;

    @Column(name = "issn_l", length = 9)
    private String issnL;

    /// JSON 列（库里是 JSONB）
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata")
    private JsonNode metadata;

    /// 嵌入式值对象
    @Embedded
    private AddressEmbeddable address;

    /// 软删除：普通字段，查询自己带未删除条件
    @Column(name = "deleted_at")
    private Instant deletedAt;

    /// 只在查询要用时映射，数据库填、应用不写
    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;
}
```

## Dao 模板

```java
public interface VenueDao extends JpaRepository<VenueEntity, Long> {
    /// 方法命名约定：有 deleted_at 的表带上未删除条件
    Optional<VenueEntity> findByIssnLAndDeletedAtIsNull(String issnL);
    boolean existsByIssnLAndDeletedAtIsNull(String issnL);

    /// 自定义 JPQL
    @Query("SELECT a FROM AuthorEntity a WHERE a.orcid = :orcid")
    Optional<AuthorEntity> findByOrcid(@Param("orcid") String orcid);

    /// 检查数据存在
    @Query("SELECT CASE WHEN COUNT(a) > 0 THEN true ELSE false END FROM AuthorEntity a")
    boolean hasAnyData();

    /// 不经实体的写入：两个标志都要带；写主表显式 SET updatedBy（批量语句不触发审计）
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE VenueEntity v SET v.deletedAt = :at, v.updatedBy = :actorId WHERE v.id = :id")
    int markDeleted(@Param("id") Long id, @Param("at") Instant at, @Param("actorId") Long actorId);
}
```

## MapStruct JpaMapper 模板

```java
@Mapper(componentModel = "spring", unmappedTargetPolicy = IGNORE)
public abstract class VenueJpaMapper {

    /// Entity → Aggregate
    public abstract VenueAggregate toAggregate(VenueEntity entity);

    /// Aggregate → Entity
    public abstract VenueEntity toEntity(VenueAggregate aggregate);

    /// 复杂转换用 @AfterMapping
    @AfterMapping
    protected void afterToEntity(VenueAggregate agg, @MappingTarget VenueEntity entity) {
        // 自定义逻辑
    }
}
```

## RepositoryAdapter 模板

```java
@Repository
@RequiredArgsConstructor
public class VenueRepositoryAdapter implements VenueRepository {
    private final VenueDao dao;
    private final VenueJpaMapper mapper;

    @Override
    public VenueAggregate save(VenueAggregate venue) {
        VenueEntity entity = mapper.toEntity(venue);
        if (entity.getId() == null) {
            entity.setId(SnowflakeIdGenerator.getId());  // 雪花 ID 预分配
        }
        return mapper.toAggregate(dao.save(entity));
    }

    @Override
    public Optional<VenueAggregate> findByIssnL(String issnL) {
        return dao.findByIssnLAndDeletedAtIsNull(issnL).map(mapper::toAggregate);
    }
}
```

## ReadAdapter 模板（CQRS 读端）

```java
@Repository
@RequiredArgsConstructor
public class VenueReadAdapter implements VenueReadPort {
    private final VenueDao dao;
    private final VenueReadModelMapper mapper;

    @Override
    public PageResult<VenueSummaryReadModel> findVenuePage(PagingParams paging, VenueFilter filter) {
        Specification<VenueEntity> spec = buildSpec(filter);
        Pageable pageable = PageRequest.of(paging.page(), paging.size());
        Page<VenueEntity> page = dao.findAll(spec, pageable);
        return PageResult.of(
            page.getContent().stream().map(mapper::toReadModel).toList(),
            page.getTotalElements(),
            paging
        );
    }
}
```

## 批量保存模板

```java
@Override
public void saveBatch(List<VenueAggregate> venues) {
    List<VenueEntity> entities = venues.stream()
        .map(mapper::toEntity)
        .peek(entity -> entity.setId(SnowflakeIdGenerator.getId()))
        .toList();

    int batchSize = 500;
    for (int i = 0; i < entities.size(); i++) {
        dao.save(entities.get(i));
        if (i > 0 && i % batchSize == 0) {
            entityManager.flush();
            entityManager.clear();  // 防止内存溢出
        }
    }
    entityManager.flush();
}
```

## 枚举 AttributeConverter 模板

```java
@Converter(autoApply = true)
public class VenueTypeAttributeConverter
    implements AttributeConverter<VenueType, String> {

    @Override
    public String convertToDatabaseColumn(VenueType type) {
        return type != null ? type.getCode() : null;
    }

    @Override
    public VenueType convertToEntityAttribute(String code) {
        return VenueType.fromCode(code);
    }
}
```

**位置**：`infra/{子域}/persistence/converter/`

## N+1 查询解决方案

```java
/// @EntityGraph 方式
@EntityGraph(attributePaths = {"identifiers", "ratings"})
Optional<VenueEntity> findWithDetailsById(Long id);

/// JOIN FETCH 方式
@Query("SELECT v FROM VenueEntity v LEFT JOIN FETCH v.identifiers WHERE v.id = :id")
Optional<VenueEntity> findWithIdentifiersById(@Param("id") Long id);
```

## 目录结构

按 `.claude/rules/tech/package-layout.md`：

```
infra/{子域}/persistence/
├── VenueRepositoryAdapter.java        # Repository 实现
├── VenueReadAdapter.java              # ReadPort 实现（CQRS 读端）
├── entity/VenueEntity.java            # JPA Entity
├── dao/VenueDao.java                  # Spring Data Repository
├── mapper/VenueJpaMapper.java         # MapStruct JpaMapper
└── converter/VenueTypeAttributeConverter.java   # JPA AttributeConverter
```
