# JPA 实现模板

## Entity 模板

```java
@Entity
@Table(name = "cat_venue", indexes = {
    @Index(name = "uk_issn_l", columnList = "issn_l", unique = true)
})
@Getter
@Setter
public class VenueEntity extends BaseJpaEntity {

    @Column(name = "venue_name", length = 500)
    private String venueName;

    @Column(name = "issn_l", length = 9)
    private String issnL;

    /// JSON 列
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata", columnDefinition = "JSON")
    private JsonNode metadata;

    /// 嵌入式值对象
    @Embedded
    private AddressEmbeddable address;
}
```

## Dao 模板

```java
public interface VenueDao extends JpaRepository<VenueEntity, Long> {
    /// 方法命名约定
    Optional<VenueEntity> findByIssnL(String issnL);
    boolean existsByIssnL(String issnL);

    /// 自定义 JPQL
    @Query("SELECT a FROM AuthorEntity a WHERE a.orcid = :orcid")
    Optional<AuthorEntity> findByOrcid(@Param("orcid") String orcid);

    /// 检查数据存在
    @Query("SELECT CASE WHEN COUNT(a) > 0 THEN true ELSE false END FROM AuthorEntity a")
    boolean hasAnyData();
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
        entity.assignIdIfMissing();  // 雪花 ID 预分配
        return mapper.toAggregate(dao.save(entity));
    }

    @Override
    public Optional<VenueAggregate> findByIssnL(String issnL) {
        return dao.findByIssnL(issnL).map(mapper::toAggregate);
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
        .peek(VenueEntity::assignIdIfMissing)
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

**位置**：`infra/persistence/converter/attribute/`

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

```
infra/
├── adapter/
│   ├── persistence/        # RepositoryAdapter（Port 实现）
│   │   └── VenueRepositoryAdapter.java
│   └── read/               # ReadAdapter（CQRS 读端 Port 实现）
│       └── VenueReadAdapter.java
├── persistence/            # JPA 内部实现（非 Port）
│   ├── entity/             # JPA Entity
│   │   └── VenueEntity.java
│   ├── dao/                # Spring Data Repository
│   │   └── VenueDao.java
│   └── converter/
│       ├── mapper/         # MapStruct JpaMapper
│       │   └── VenueJpaMapper.java
│       └── attribute/      # JPA AttributeConverter
│           └── VenueTypeAttributeConverter.java
```
