package dev.patra.interaction.support;

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

/// 扮演 catalog 的文献与期刊内部解析接口（各子域的流程测试共用）：按 catalog ID、PMID 或 NLM ID 查内存表。测试里改表就是「catalog 变了」。
public final class FakeCatalog implements ResponseDefinitionTransformerV2 {

  private static final JsonMapper JSON = JsonMapper.builder().build();

  private final Map<Long, Publication> publications = new ConcurrentHashMap<>();
  private final Map<Long, Journal> journals = new ConcurrentHashMap<>();

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
  public void put(long id, String pmid, String journal, String publishedDate) {
    publications.put(id, new Publication(id, pmid, "ACTIVE", journal, publishedDate));
  }

  /// 软删一篇。
  ///
  /// @param id catalog ID
  public void softDelete(long id) {
    Publication p = publications.get(id);
    publications.put(id, new Publication(id, p.pmid(), "DELETED", p.journal(), p.publishedDate()));
  }

  /// 整行删掉（清库后还没重导）。
  ///
  /// @param id catalog ID
  public void remove(long id) {
    publications.remove(id);
  }

  /// 清空。
  public void clear() {
    publications.clear();
    journals.clear();
  }

  /// 按请求体回解析结果。
  ///
  /// @param serveEvent 请求
  /// @return 响应
  @Override
  public ResponseDefinition transform(ServeEvent serveEvent) {
    JsonNode body = JSON.readTree(serveEvent.getRequest().getBodyAsString());
    if (serveEvent.getRequest().getUrl().contains("/venues/")) {
      return venues(serveEvent.getRequest().getUrl(), body);
    }
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

  // ---- 期刊解析（PAP-122 起）：`/_internal/venues/resolve/by-ids`、`by-nlm-ids` ----

  /// 一本期刊。
  ///
  /// @param id catalog 期刊 ID
  /// @param nlmId 主 NLM ID
  /// @param state `ACTIVE` / `DELETED`
  /// @param title 全称
  /// @param abbreviation MEDLINE 缩写，可空
  record Journal(long id, String nlmId, String state, String title, String abbreviation) {}

  /// 放一本正常的期刊。
  ///
  /// @param id catalog 期刊 ID
  /// @param nlmId 主 NLM ID
  /// @param title 全称
  /// @param abbreviation 缩写，可空
  public void putJournal(long id, String nlmId, String title, String abbreviation) {
    journals.put(id, new Journal(id, nlmId, "ACTIVE", title, abbreviation));
  }

  /// 软删一本期刊。
  ///
  /// @param id catalog 期刊 ID
  public void softDeleteJournal(long id) {
    Journal j = journals.get(id);
    journals.put(id, new Journal(id, j.nlmId(), "DELETED", j.title(), j.abbreviation()));
  }

  /// 期刊解析：按期刊 ID（`ids`）或 NLM ID（`nlmIds`），每个请求键一行。
  ///
  /// @param url 请求地址
  /// @param body 请求体
  /// @return 响应
  private ResponseDefinition venues(String url, JsonNode body) {
    List<Map<String, Object>> items = new ArrayList<>();
    if (url.endsWith("/by-ids")) {
      for (JsonNode id : body.get("ids")) {
        Journal j = journals.get(id.asLong());
        items.add(j == null ? missingJournal(id.asString()) : journal(id.asString(), j));
      }
    } else {
      for (JsonNode nlm : body.get("nlmIds")) {
        Journal j =
            journals.values().stream()
                .filter(x -> x.nlmId().equals(nlm.asString()))
                .findFirst()
                .orElse(null);
        items.add(j == null ? missingJournal(nlm.asString()) : journal(nlm.asString(), j));
      }
    }
    return ResponseDefinition.okForJson(Map.of("items", items));
  }

  /// 存在的一行期刊。
  ///
  /// @param key 请求键
  /// @param j 期刊
  /// @return 行
  private static Map<String, Object> journal(String key, Journal j) {
    Map<String, Object> item = new LinkedHashMap<>();
    item.put("key", key);
    item.put("exists", true);
    item.put("state", j.state());
    item.put("id", Long.toString(j.id()));
    item.put("nlmId", j.nlmId());
    item.put("title", j.title());
    item.put("abbreviatedTitle", j.abbreviation());
    return item;
  }

  /// 不存在的一行期刊。
  ///
  /// @param key 请求键
  /// @return 行
  private static Map<String, Object> missingJournal(String key) {
    Map<String, Object> item = new LinkedHashMap<>();
    item.put("key", key);
    item.put("exists", false);
    item.put("state", null);
    item.put("id", null);
    item.put("nlmId", null);
    item.put("title", null);
    item.put("abbreviatedTitle", null);
    return item;
  }
}
