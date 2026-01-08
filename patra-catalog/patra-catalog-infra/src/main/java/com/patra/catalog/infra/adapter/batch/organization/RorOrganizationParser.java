package com.patra.catalog.infra.adapter.batch.organization;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.patra.catalog.domain.model.aggregate.OrganizationAggregate;
import com.patra.catalog.domain.model.enums.ExternalIdType;
import com.patra.catalog.domain.model.enums.LinkType;
import com.patra.catalog.domain.model.enums.OrganizationNameType;
import com.patra.catalog.domain.model.enums.OrganizationRelationType;
import com.patra.catalog.domain.model.enums.OrganizationStatus;
import com.patra.catalog.domain.model.enums.OrganizationType;
import com.patra.catalog.domain.model.vo.organization.AdminInfo;
import com.patra.catalog.domain.model.vo.organization.ExternalId;
import com.patra.catalog.domain.model.vo.organization.GeoLocation;
import com.patra.catalog.domain.model.vo.organization.OrganizationLink;
import com.patra.catalog.domain.model.vo.organization.OrganizationName;
import com.patra.catalog.domain.model.vo.organization.OrganizationRelation;
import com.patra.catalog.domain.model.vo.organization.RorId;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/// ROR v2 JSON 解析器。
///
/// 从 ROR Data Dump JSON 文件解析机构数据，并转换为 `OrganizationAggregate`。
///
/// **数据格式**：
///
/// - 输入：ROR Data Dump JSON 文件（JSON 数组格式）
/// - 输出：`Stream<OrganizationAggregate>` 流式处理
///
/// **流式解析**：
///
/// 使用 Jackson Streaming API 逐条解析 JSON 数组中的元素，
/// 避免将整个文件加载到内存中。
///
/// **转换规则**：
///
/// - `id` → 提取后缀作为 `RorId`
/// - `names[ror_display]` → `displayName`
/// - `status` → `OrganizationStatus` 枚举
/// - `types` → `Set<OrganizationType>`
/// - `locations` → `List<GeoLocation>`
/// - `relationships` → `List<OrganizationRelation>`
/// - `external_ids` → `List<ExternalId>`
/// - `links` → `List<OrganizationLink>`
/// - `admin` → `AdminInfo`
///
/// @author linqibin
/// @since 0.1.0
/// @see <a href="https://ror.readme.io/v2/docs/fields">ROR Fields Documentation</a>
@Slf4j
@Component
public class RorOrganizationParser {

  private final ObjectMapper objectMapper;

  /// 创建解析器实例。
  public RorOrganizationParser() {
    this.objectMapper =
        new ObjectMapper()
            .setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
  }

  /// 解析 ROR Data Dump JSON 输入流。
  ///
  /// **注意**：返回的 Stream 在使用完毕后需要关闭以释放资源。
  ///
  /// @param inputStream JSON 输入流（JSON 数组格式）
  /// @return OrganizationAggregate 流
  /// @throws IOException 读取或解析失败时
  public Stream<OrganizationAggregate> parse(InputStream inputStream) throws IOException {
    JsonParser jsonParser = objectMapper.getFactory().createParser(inputStream);

    // 移动到数组开始位置
    if (jsonParser.nextToken() != JsonToken.START_ARRAY) {
      throw new IOException("期望 JSON 数组，但找到：" + jsonParser.currentToken());
    }

    // 创建迭代器
    Iterator<OrganizationAggregate> iterator = new RorRecordIterator(jsonParser);

    // 转换为 Stream，并在关闭时释放资源
    Spliterator<OrganizationAggregate> spliterator =
        Spliterators.spliteratorUnknownSize(iterator, Spliterator.ORDERED | Spliterator.NONNULL);

    return StreamSupport.stream(spliterator, false)
        .onClose(
            () -> {
              try {
                jsonParser.close();
              } catch (IOException e) {
                log.warn("关闭 JsonParser 失败", e);
              }
            });
  }

  /// ROR 记录迭代器。
  ///
  /// 逐条读取 JSON 数组中的元素并转换为 OrganizationAggregate。
  private class RorRecordIterator implements Iterator<OrganizationAggregate> {

    private final JsonParser jsonParser;
    private OrganizationAggregate nextRecord;
    private boolean finished = false;

    RorRecordIterator(JsonParser jsonParser) {
      this.jsonParser = jsonParser;
      advance();
    }

    @Override
    public boolean hasNext() {
      return nextRecord != null;
    }

    @Override
    public OrganizationAggregate next() {
      OrganizationAggregate current = nextRecord;
      advance();
      return current;
    }

    /// 前进到下一条记录。
    private void advance() {
      if (finished) {
        nextRecord = null;
        return;
      }

      try {
        // 检查是否到达数组末尾
        JsonToken token = jsonParser.nextToken();
        if (token == JsonToken.END_ARRAY || token == null) {
          finished = true;
          nextRecord = null;
          return;
        }

        // 读取并转换记录
        if (token == JsonToken.START_OBJECT) {
          RorOrganizationRecord record =
              objectMapper.readValue(jsonParser, RorOrganizationRecord.class);
          nextRecord = toAggregate(record);
        } else {
          log.warn("跳过非对象 token：{}", token);
          advance(); // 递归跳过非对象元素
        }
      } catch (IOException e) {
        log.error("解析 ROR 记录失败", e);
        finished = true;
        nextRecord = null;
        throw new IllegalStateException("解析 ROR 记录失败", e);
      }
    }
  }

  /// 将 RorOrganizationRecord 转换为 OrganizationAggregate。
  ///
  /// @param record ROR 记录
  /// @return 机构聚合根
  OrganizationAggregate toAggregate(RorOrganizationRecord record) {
    // 1. 提取核心字段
    RorId rorId = RorId.of(record.extractRorId());
    String displayName = record.getDisplayName();
    OrganizationStatus status = parseStatus(record.status());

    // 2. 创建聚合根
    OrganizationAggregate aggregate = OrganizationAggregate.fromRor(rorId, displayName, status);

    // 3. 设置基本属性
    if (record.established() != null) {
      aggregate.withEstablished(record.established());
    }

    // 4. 设置域名
    if (record.domains() != null && !record.domains().isEmpty()) {
      aggregate.withDomains(record.domains());
    }

    // 5. 设置管理元数据
    AdminInfo adminInfo = buildAdminInfo(record.admin());
    if (adminInfo != null) {
      aggregate.withAdminInfo(adminInfo);
    }

    // 6. 设置类型
    Set<OrganizationType> types = parseTypes(record.types());
    if (!types.isEmpty()) {
      aggregate.withTypes(types);
    }

    // 7. 添加名称
    List<OrganizationName> names = buildNames(record.names());
    if (!names.isEmpty()) {
      aggregate.withNames(names);
    }

    // 8. 添加链接
    List<OrganizationLink> links = buildLinks(record.links());
    if (!links.isEmpty()) {
      aggregate.withLinks(links);
    }

    // 9. 添加外部标识符
    List<ExternalId> externalIds = buildExternalIds(record.externalIds());
    for (ExternalId extId : externalIds) {
      aggregate.addExternalId(extId);
    }

    // 10. 添加地理位置
    List<GeoLocation> locations = buildLocations(record.locations());
    if (!locations.isEmpty()) {
      aggregate.withLocations(locations);
    }

    // 11. 添加关系
    List<OrganizationRelation> relations = buildRelations(record.relationships());
    if (!relations.isEmpty()) {
      aggregate.withRelations(relations);
    }

    return aggregate;
  }

  /// 解析机构状态。
  private OrganizationStatus parseStatus(String status) {
    if (status == null) {
      return OrganizationStatus.ACTIVE; // 默认活跃
    }
    return switch (status.toLowerCase()) {
      case "active" -> OrganizationStatus.ACTIVE;
      case "inactive" -> OrganizationStatus.INACTIVE;
      case "withdrawn" -> OrganizationStatus.WITHDRAWN;
      default -> {
        log.warn("未知的机构状态：{}，使用默认值 ACTIVE", status);
        yield OrganizationStatus.ACTIVE;
      }
    };
  }

  /// 解析机构类型列表。
  private Set<OrganizationType> parseTypes(List<String> types) {
    if (types == null || types.isEmpty()) {
      return Set.of();
    }

    Set<OrganizationType> result = new HashSet<>();
    for (String type : types) {
      OrganizationType orgType = parseType(type);
      if (orgType != null) {
        result.add(orgType);
      }
    }
    return result;
  }

  /// 解析单个机构类型。
  private OrganizationType parseType(String type) {
    if (type == null) {
      return null;
    }
    return switch (type.toLowerCase()) {
      case "archive" -> OrganizationType.ARCHIVE;
      case "company" -> OrganizationType.COMPANY;
      case "education" -> OrganizationType.EDUCATION;
      case "facility" -> OrganizationType.FACILITY;
      case "funder" -> OrganizationType.FUNDER;
      case "government" -> OrganizationType.GOVERNMENT;
      case "healthcare" -> OrganizationType.HEALTHCARE;
      case "nonprofit" -> OrganizationType.NONPROFIT;
      case "other" -> OrganizationType.OTHER;
      default -> {
        log.warn("未知的机构类型：{}", type);
        yield null;
      }
    };
  }

  /// 构建管理元数据。
  private AdminInfo buildAdminInfo(RorOrganizationRecord.Admin admin) {
    if (admin == null) {
      return null;
    }

    LocalDate createdDate = null;
    String createdSchemaVersion = null;
    LocalDate lastModifiedDate = null;
    String lastModifiedSchemaVersion = null;

    if (admin.created() != null) {
      createdDate = parseDate(admin.created().date());
      createdSchemaVersion = admin.created().schemaVersion();
    }

    if (admin.lastModified() != null) {
      lastModifiedDate = parseDate(admin.lastModified().date());
      lastModifiedSchemaVersion = admin.lastModified().schemaVersion();
    }

    // 如果所有字段都为空，返回 null
    if (createdDate == null && lastModifiedDate == null) {
      return null;
    }

    return AdminInfo.of(
        createdDate, createdSchemaVersion, lastModifiedDate, lastModifiedSchemaVersion);
  }

  /// 解析日期字符串。
  private LocalDate parseDate(String dateStr) {
    if (dateStr == null || dateStr.isBlank()) {
      return null;
    }
    try {
      return LocalDate.parse(dateStr);
    } catch (DateTimeParseException e) {
      log.warn("日期解析失败：{}", dateStr);
      return null;
    }
  }

  /// 构建名称列表。
  private List<OrganizationName> buildNames(List<RorOrganizationRecord.Name> names) {
    if (names == null || names.isEmpty()) {
      return List.of();
    }

    List<OrganizationName> result = new ArrayList<>();
    for (RorOrganizationRecord.Name name : names) {
      if (name.value() == null || name.value().isBlank()) {
        continue;
      }

      Set<OrganizationNameType> types = parseNameTypes(name.types());
      OrganizationName orgName = OrganizationName.create(name.value(), types, name.lang());
      result.add(orgName);
    }
    return result;
  }

  /// 解析名称类型列表。
  private Set<OrganizationNameType> parseNameTypes(List<String> types) {
    if (types == null || types.isEmpty()) {
      return Set.of();
    }

    Set<OrganizationNameType> result = new HashSet<>();
    for (String type : types) {
      OrganizationNameType nameType = parseNameType(type);
      if (nameType != null) {
        result.add(nameType);
      }
    }
    return result;
  }

  /// 解析单个名称类型。
  private OrganizationNameType parseNameType(String type) {
    if (type == null) {
      return null;
    }
    return switch (type.toLowerCase()) {
      case "ror_display" -> OrganizationNameType.ROR_DISPLAY;
      case "label" -> OrganizationNameType.LABEL;
      case "alias" -> OrganizationNameType.ALIAS;
      case "acronym" -> OrganizationNameType.ACRONYM;
      default -> {
        log.warn("未知的名称类型：{}", type);
        yield null;
      }
    };
  }

  /// 构建链接列表。
  private List<OrganizationLink> buildLinks(List<RorOrganizationRecord.Link> links) {
    if (links == null || links.isEmpty()) {
      return List.of();
    }

    List<OrganizationLink> result = new ArrayList<>();
    for (RorOrganizationRecord.Link link : links) {
      if (link.value() == null || link.value().isBlank()) {
        continue;
      }

      LinkType linkType = parseLinkType(link.type());
      if (linkType != null) {
        OrganizationLink orgLink = OrganizationLink.of(linkType, link.value());
        result.add(orgLink);
      }
    }
    return result;
  }

  /// 解析链接类型。
  private LinkType parseLinkType(String type) {
    if (type == null) {
      return null;
    }
    return switch (type.toLowerCase()) {
      case "website" -> LinkType.WEBSITE;
      case "wikipedia" -> LinkType.WIKIPEDIA;
      default -> {
        log.warn("未知的链接类型：{}", type);
        yield null;
      }
    };
  }

  /// 构建外部标识符列表。
  private List<ExternalId> buildExternalIds(List<RorOrganizationRecord.ExternalId> externalIds) {
    if (externalIds == null || externalIds.isEmpty()) {
      return List.of();
    }

    List<ExternalId> result = new ArrayList<>();
    for (RorOrganizationRecord.ExternalId extId : externalIds) {
      ExternalIdType type = parseExternalIdType(extId.type());
      if (type == null) {
        continue;
      }

      ExternalId domainExtId = ExternalId.create(type, extId.all(), extId.preferred());
      result.add(domainExtId);
    }
    return result;
  }

  /// 解析外部标识符类型。
  private ExternalIdType parseExternalIdType(String type) {
    if (type == null) {
      return null;
    }
    return switch (type.toLowerCase()) {
      case "isni" -> ExternalIdType.ISNI;
      case "wikidata" -> ExternalIdType.WIKIDATA;
      case "fundref" -> ExternalIdType.FUNDREF;
      case "grid" -> ExternalIdType.GRID;
      case "ringgold" -> ExternalIdType.RINGGOLD;
      default -> {
        log.warn("未知的外部标识符类型：{}", type);
        yield null;
      }
    };
  }

  /// 构建地理位置列表。
  private List<GeoLocation> buildLocations(List<RorOrganizationRecord.Location> locations) {
    if (locations == null || locations.isEmpty()) {
      return List.of();
    }

    List<GeoLocation> result = new ArrayList<>();
    for (RorOrganizationRecord.Location loc : locations) {
      if (loc.geonamesId() == null) {
        continue;
      }

      GeoLocation.Builder builder = GeoLocation.builder().geonamesId(loc.geonamesId());

      if (loc.geonamesDetails() != null) {
        RorOrganizationRecord.GeonamesDetails details = loc.geonamesDetails();
        builder
            .continentCode(details.continentCode())
            .continentName(details.continentName())
            .countryCode(details.countryCode())
            .countryName(details.countryName())
            .subdivisionCode(details.countrySubdivisionCode())
            .subdivisionName(details.countrySubdivisionName())
            .cityName(details.name());

        if (details.lat() != null) {
          builder.latitude(BigDecimal.valueOf(details.lat()));
        }
        if (details.lng() != null) {
          builder.longitude(BigDecimal.valueOf(details.lng()));
        }
      }

      result.add(builder.build());
    }
    return result;
  }

  /// 构建关系列表。
  private List<OrganizationRelation> buildRelations(
      List<RorOrganizationRecord.Relationship> relationships) {
    if (relationships == null || relationships.isEmpty()) {
      return List.of();
    }

    List<OrganizationRelation> result = new ArrayList<>();
    for (RorOrganizationRecord.Relationship rel : relationships) {
      OrganizationRelationType type = parseRelationType(rel.type());
      if (type == null) {
        continue;
      }

      String relatedRorIdStr = rel.extractRelatedRorId();
      if (relatedRorIdStr == null) {
        continue;
      }

      RorId relatedRorId = RorId.of(relatedRorIdStr);
      OrganizationRelation relation = OrganizationRelation.create(type, relatedRorId, rel.label());
      result.add(relation);
    }
    return result;
  }

  /// 解析关系类型。
  private OrganizationRelationType parseRelationType(String type) {
    if (type == null) {
      return null;
    }
    return switch (type.toLowerCase()) {
      case "parent" -> OrganizationRelationType.PARENT;
      case "child" -> OrganizationRelationType.CHILD;
      case "related" -> OrganizationRelationType.RELATED;
      case "successor" -> OrganizationRelationType.SUCCESSOR;
      case "predecessor" -> OrganizationRelationType.PREDECESSOR;
      default -> {
        log.warn("未知的关系类型：{}", type);
        yield null;
      }
    };
  }
}
