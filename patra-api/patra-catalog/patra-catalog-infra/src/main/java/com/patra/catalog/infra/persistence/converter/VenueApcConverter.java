package com.patra.catalog.infra.persistence.converter;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.patra.catalog.domain.model.vo.venue.ApcInfo;
import com.patra.catalog.domain.model.vo.venue.ApcInfo.ApcPrice;
import com.patra.catalog.infra.persistence.entity.VenueApcDO;
import java.util.ArrayList;
import java.util.List;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

/// VenueApcDO 转换器。
///
/// **职责**：
///
/// 在 `ApcInfo` 值对象和 `VenueApcDO` 数据库实体之间转换。
///
/// **映射关系**：
///
/// | ApcInfo 字段 | VenueApcDO 字段 |
/// |--------------|-----------------|
/// | usd | apc_usd |
/// | prices | apc_prices (JSON) |
///
/// **JSON 格式**：
///
/// ```json
/// [
///   {"price": 3000, "currency": "USD"},
///   {"price": 2500, "currency": "EUR"}
/// ]
/// ```
///
/// @author linqibin
/// @since 0.1.0
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface VenueApcConverter {

  ObjectMapper OBJECT_MAPPER = new ObjectMapper();

  /// 将值对象转换为数据库实体。
  ///
  /// **注意**：`id` 和 `venueId` 需要在调用方设置。
  ///
  /// @param apcInfo 值对象
  /// @return 数据库实体
  @Mapping(target = "id", ignore = true)
  @Mapping(target = "venueId", ignore = true)
  @Mapping(target = "apcUsd", source = "usd")
  @Mapping(target = "apcPrices", expression = "java(pricesToJson(apcInfo.prices()))")
  VenueApcDO toDO(ApcInfo apcInfo);

  /// 将数据库实体转换为值对象。
  ///
  /// @param doEntity 数据库实体
  /// @return 值对象
  default ApcInfo toEntity(VenueApcDO doEntity) {
    if (doEntity == null) {
      return null;
    }
    List<ApcPrice> prices = jsonToPrices(doEntity.getApcPrices());
    return ApcInfo.of(doEntity.getApcUsd(), prices);
  }

  /// 将价格列表转换为 JSON。
  ///
  /// @param prices 价格列表
  /// @return JSON 节点
  default JsonNode pricesToJson(List<ApcPrice> prices) {
    if (prices == null || prices.isEmpty()) {
      return null;
    }
    ArrayNode arrayNode = OBJECT_MAPPER.createArrayNode();
    for (ApcPrice price : prices) {
      arrayNode.addObject().put("price", price.price()).put("currency", price.currency());
    }
    return arrayNode;
  }

  /// 将 JSON 转换为价格列表。
  ///
  /// @param json JSON 节点
  /// @return 价格列表
  default List<ApcPrice> jsonToPrices(JsonNode json) {
    if (json == null || !json.isArray()) {
      return List.of();
    }
    List<ApcPrice> prices = new ArrayList<>();
    for (JsonNode node : json) {
      int price = node.path("price").asInt(0);
      String currency = node.path("currency").asText(null);
      if (currency != null) {
        prices.add(ApcPrice.of(price, currency));
      }
    }
    return prices;
  }
}
