package dev.patra.interaction.collection;

import com.github.tomakehurst.wiremock.extension.ResponseDefinitionTransformerV2;
import com.github.tomakehurst.wiremock.http.ResponseDefinition;
import com.github.tomakehurst.wiremock.stubbing.ServeEvent;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/// 扮演 catalog 的两个内部解析接口：按 catalog ID 或 PMID 查一张内存表。测试里改表就是「catalog 变了」。
final class FakeCatalog implements ResponseDefinitionTransformerV2 {

  private static final JsonMapper JSON = JsonMapper.builder().build();

  private final Map<Long, Publication> publications = new ConcurrentHashMap<>();

  /// 一篇文献。
  ///
  /// @param id catalog ID
  /// @param pmid PMID
  /// @param state `ACTIVE` / `DELETED`
  /// @param journal 期刊
  /// @param publishedDate 出版日期
  record Publication(long id, String pmid, String state, String journal, String publishedDate) {}

  /// 放一篇正常的文献，标题是 `Title {pmid}`。
  ///
  /// @param id catalog ID
  /// @param pmid PMID
  /// @param journal 期刊
  /// @param publishedDate 出版日期
  void put(long id, String pmid, String journal, String publishedDate) {
    publications.put(id, new Publication(id, pmid, "ACTIVE", journal, publishedDate));
  }

  /// 软删一篇。
  ///
  /// @param id catalog ID
  void softDelete(long id) {
    Publication p = publications.get(id);
    publications.put(id, new Publication(id, p.pmid(), "DELETED", p.journal(), p.publishedDate()));
  }

  /// 整行删掉（清库后还没重导）。
  ///
  /// @param id catalog ID
  void remove(long id) {
    publications.remove(id);
  }

  /// 清空。
  void clear() {
    publications.clear();
  }

  /// 按请求体回解析结果。
  ///
  /// @param serveEvent 请求
  /// @return 响应
  @Override
  public ResponseDefinition transform(ServeEvent serveEvent) {
    JsonNode body = JSON.readTree(serveEvent.getRequest().getBodyAsString());
    List<Map<String, Object>> items = new ArrayList<>();
    if (serveEvent.getRequest().getUrl().endsWith("/by-ids")) {
      for (JsonNode id : body.get("ids")) {
        Publication p = publications.get(id.asLong());
        items.add(p == null ? missing(id.asString()) : item(Long.toString(p.id()), p));
      }
    } else {
      for (JsonNode pmid : body.get("pmids")) {
        Publication p =
            publications.values().stream()
                .filter(x -> x.pmid().equals(pmid.asString()))
                .findFirst()
                .orElse(null);
        items.add(p == null ? missing(pmid.asString()) : item(pmid.asString(), p));
      }
    }
    return ResponseDefinition.okForJson(Map.of("items", items));
  }

  /// 扩展名。
  ///
  /// @return 名字
  @Override
  public String getName() {
    return "fake-catalog";
  }

  /// 存在的一行。
  ///
  /// @param key 请求键
  /// @param p 文献
  /// @return 行
  private static Map<String, Object> item(String key, Publication p) {
    Map<String, Object> item = new LinkedHashMap<>();
    item.put("key", key);
    item.put("exists", true);
    item.put("state", p.state());
    item.put("id", Long.toString(p.id()));
    item.put("pmid", p.pmid());
    item.put("title", "Title " + p.pmid());
    item.put("journal", p.journal());
    item.put(
        "year",
        p.publishedDate() == null ? null : Integer.parseInt(p.publishedDate().substring(0, 4)));
    item.put("publishedDate", p.publishedDate());
    return item;
  }

  /// 不存在的一行。
  ///
  /// @param key 请求键
  /// @return 行
  private static Map<String, Object> missing(String key) {
    Map<String, Object> item = new LinkedHashMap<>();
    item.put("key", key);
    item.put("exists", false);
    return item;
  }
}
