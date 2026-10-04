import { describe, expect, it } from "vitest";
import {
  buildVenuesFacetsApiQuery,
  buildVenuesPageApiQuery,
  CAS_ZONE_ORDER,
  DEFAULT_PAGE_SIZE,
  deriveActiveChips,
  JCR_QUARTILE_ORDER,
  parseVenueBrowseQuery,
  SORT_CODE_MAP,
  SORT_OPTIONS,
  serializeVenueBrowseQuery,
  toFilters,
  venueFilterCount,
} from "@/lib/portal-api/venue-browse";
import type { VenueBrowseQuery } from "@/types/portal";

// ---- 测试辅助 ----

const DEFAULT_QUERY: VenueBrowseQuery = {
  q: "",
  sort: "if",
  page: 1,
  subject: [],
  jcr: [],
  cas: [],
  casTop: false,
  oa: false,
  doaj: false,
  country: [],
};

/** 生成规范化的 query（合并覆盖默认值） */
function q(overrides: Partial<VenueBrowseQuery> = {}): VenueBrowseQuery {
  return { ...DEFAULT_QUERY, ...overrides };
}

// ---- 常量 ----

describe("常量", () => {
  it("SORT_OPTIONS 包含 4 项且 id 覆盖全部 VenueSortId", () => {
    expect(SORT_OPTIONS).toHaveLength(4);
    const ids = SORT_OPTIONS.map((o) => o.id);
    expect(ids).toContain("if");
    expect(ids).toContain("cas");
    expect(ids).toContain("az");
    expect(ids).toContain("cited");
  });

  it("SORT_OPTIONS if/cited 有 desc=true，az 无 desc", () => {
    const ifOpt = SORT_OPTIONS.find((o) => o.id === "if");
    const azOpt = SORT_OPTIONS.find((o) => o.id === "az");
    const citedOpt = SORT_OPTIONS.find((o) => o.id === "cited");
    expect(ifOpt?.desc).toBe(true);
    expect(citedOpt?.desc).toBe(true);
    expect(azOpt?.desc).toBeUndefined();
  });

  it("JCR_QUARTILE_ORDER 为 Q1→Q4", () => {
    expect(JCR_QUARTILE_ORDER).toEqual(["Q1", "Q2", "Q3", "Q4"]);
  });

  it("CAS_ZONE_ORDER 为 1区→4区", () => {
    expect(CAS_ZONE_ORDER).toEqual(["1区", "2区", "3区", "4区"]);
  });

  it("SORT_CODE_MAP 映射正确", () => {
    expect(SORT_CODE_MAP).toEqual({
      if: "impact_factor",
      cas: "cas_quartile",
      az: "title",
      cited: "cited_by",
    });
  });

  it("DEFAULT_PAGE_SIZE 为 12", () => {
    expect(DEFAULT_PAGE_SIZE).toBe(12);
  });
});

// ---- parseVenueBrowseQuery ----

describe("parseVenueBrowseQuery", () => {
  it("空 sp → 全默认值", () => {
    expect(parseVenueBrowseQuery({})).toEqual(DEFAULT_QUERY);
  });

  it("q 取 string，缺省 ''", () => {
    expect(parseVenueBrowseQuery({ q: "nature" }).q).toBe("nature");
    expect(parseVenueBrowseQuery({}).q).toBe("");
  });

  it("sort 白名单：合法值保留", () => {
    expect(parseVenueBrowseQuery({ sort: "cas" }).sort).toBe("cas");
    expect(parseVenueBrowseQuery({ sort: "az" }).sort).toBe("az");
    expect(parseVenueBrowseQuery({ sort: "cited" }).sort).toBe("cited");
  });

  it("sort 白名单：非法值 / 缺省 → 'if'", () => {
    expect(parseVenueBrowseQuery({ sort: "unknown" }).sort).toBe("if");
    expect(parseVenueBrowseQuery({}).sort).toBe("if");
  });

  it("page parseInt 正常", () => {
    expect(parseVenueBrowseQuery({ page: "3" }).page).toBe(3);
  });

  it("page NaN / ≤0 → 1", () => {
    expect(parseVenueBrowseQuery({ page: "abc" }).page).toBe(1);
    expect(parseVenueBrowseQuery({ page: "0" }).page).toBe(1);
    expect(parseVenueBrowseQuery({ page: "-5" }).page).toBe(1);
  });

  it("多值：CSV 字符串拆成数组", () => {
    expect(parseVenueBrowseQuery({ subject: "Medicine,Biology" }).subject).toEqual([
      "Medicine",
      "Biology",
    ]);
  });

  it("多值：string[] 重复参数保留", () => {
    expect(parseVenueBrowseQuery({ jcr: ["Q1", "Q2"] }).jcr).toEqual(["Q1", "Q2"]);
  });

  it("多值：混合 CSV+数组 统一拆开去空", () => {
    expect(parseVenueBrowseQuery({ cas: ["1区,2区", "3区"] }).cas).toEqual(["1区", "2区", "3区"]);
  });

  it("多值：空字符串去空", () => {
    expect(parseVenueBrowseQuery({ country: "US,,GB" }).country).toEqual(["US", "GB"]);
  });

  it("casTop/oa/doaj: '1' → true", () => {
    const result = parseVenueBrowseQuery({ casTop: "1", oa: "1", doaj: "1" });
    expect(result.casTop).toBe(true);
    expect(result.oa).toBe(true);
    expect(result.doaj).toBe(true);
  });

  it("casTop/oa/doaj: 'true' → true", () => {
    const result = parseVenueBrowseQuery({ casTop: "true", oa: "true", doaj: "true" });
    expect(result.casTop).toBe(true);
    expect(result.oa).toBe(true);
    expect(result.doaj).toBe(true);
  });

  it("casTop/oa/doaj: 其他值 → false", () => {
    const result = parseVenueBrowseQuery({ casTop: "yes", oa: "0", doaj: undefined });
    expect(result.casTop).toBe(false);
    expect(result.oa).toBe(false);
    expect(result.doaj).toBe(false);
  });
});

// ---- serializeVenueBrowseQuery ----

describe("serializeVenueBrowseQuery", () => {
  it("全默认值 → 空字符串", () => {
    expect(serializeVenueBrowseQuery(DEFAULT_QUERY)).toBe("");
  });

  it("sort='if' 省略", () => {
    expect(serializeVenueBrowseQuery(q({ sort: "if" }))).not.toContain("sort=");
  });

  it("sort 非默认值写出", () => {
    expect(serializeVenueBrowseQuery(q({ sort: "cas" }))).toContain("sort=cas");
  });

  it("page≤1 省略", () => {
    expect(serializeVenueBrowseQuery(q({ page: 1 }))).not.toContain("page=");
  });

  it("page>1 写出", () => {
    expect(serializeVenueBrowseQuery(q({ page: 3 }))).toContain("page=3");
  });

  it("q 为空字符串省略", () => {
    expect(serializeVenueBrowseQuery(q({ q: "" }))).not.toContain("q=");
  });

  it("q 有值写出", () => {
    expect(serializeVenueBrowseQuery(q({ q: "nature" }))).toContain("q=nature");
  });

  it("空数组省略", () => {
    const result = serializeVenueBrowseQuery(DEFAULT_QUERY);
    expect(result).not.toContain("subject=");
    expect(result).not.toContain("jcr=");
    expect(result).not.toContain("cas=");
    expect(result).not.toContain("country=");
  });

  it("多值用逗号 join", () => {
    expect(serializeVenueBrowseQuery(q({ jcr: ["Q1", "Q2"] }))).toContain("jcr=Q1%2CQ2");
  });

  it("boolean false 省略，true 写出", () => {
    const result = serializeVenueBrowseQuery(q({ casTop: true, oa: false, doaj: true }));
    expect(result).toContain("casTop=true");
    expect(result).toContain("doaj=true");
    expect(result).not.toContain("oa=");
  });

  it("无前导 ?", () => {
    const result = serializeVenueBrowseQuery(q({ sort: "cas", page: 2 }));
    expect(result.startsWith("?")).toBe(false);
    expect(result).toBe("sort=cas&page=2");
  });
});

// ---- 往返幂等（parse ∘ serialize = normalize） ----

describe("parse/serialize 往返幂等", () => {
  it("空 query 往返", () => {
    const serialized = serializeVenueBrowseQuery(DEFAULT_QUERY);
    const sp = serialized ? Object.fromEntries(new URLSearchParams(serialized)) : {};
    const parsed = parseVenueBrowseQuery(sp);
    expect(parsed).toEqual(DEFAULT_QUERY);
  });

  it("复杂 query 往返一致", () => {
    const original = q({
      q: "cancer",
      sort: "cas",
      page: 3,
      subject: ["Medicine", "Biology"],
      jcr: ["Q1", "Q2"],
      cas: ["1区"],
      casTop: true,
      oa: true,
      doaj: false,
      country: ["US", "GB"],
    });
    const serialized = serializeVenueBrowseQuery(original);
    // 拆成 sp：CSV 字段要作为单值传入 parseVenueBrowseQuery
    const sp = Object.fromEntries(new URLSearchParams(serialized));
    const roundTripped = parseVenueBrowseQuery(sp);
    expect(roundTripped).toEqual(original);
  });

  it("page=1 往返后仍为 1（省略再 parse）", () => {
    const serialized = serializeVenueBrowseQuery(q({ page: 1, sort: "az" }));
    const sp = Object.fromEntries(new URLSearchParams(serialized));
    expect(parseVenueBrowseQuery(sp).page).toBe(1);
  });
});

// ---- toFilters ----

describe("toFilters", () => {
  it("去掉 page 和 sort", () => {
    const result = toFilters(q({ sort: "cas", page: 5 }));
    expect(result).not.toHaveProperty("page");
    expect(result).not.toHaveProperty("sort");
  });

  it("保留所有筛选维度", () => {
    const result = toFilters(
      q({
        q: "cancer",
        subject: ["Medicine"],
        jcr: ["Q1"],
        cas: ["1区"],
        casTop: true,
        oa: true,
        doaj: true,
        country: ["US"],
      }),
    );
    expect(result.q).toBe("cancer");
    expect(result.subject).toEqual(["Medicine"]);
    expect(result.jcr).toEqual(["Q1"]);
    expect(result.cas).toEqual(["1区"]);
    expect(result.casTop).toBe(true);
    expect(result.oa).toBe(true);
    expect(result.doaj).toBe(true);
    expect(result.country).toEqual(["US"]);
  });
});

// ---- buildVenuesPageApiQuery ----

describe("buildVenuesPageApiQuery", () => {
  it("默认 query → 含 pageSize，无 sort（if 可省略），page=1", () => {
    const result = buildVenuesPageApiQuery(DEFAULT_QUERY);
    expect(result).toContain(`pageSize=${DEFAULT_PAGE_SIZE}`);
    expect(result).toContain("page=1");
  });

  it("sort 映射到 BE 字段名（if→impact_factor，不含 sort=if）", () => {
    const result = buildVenuesPageApiQuery(q({ sort: "if" }));
    // if 可省略，但如果带 sort 必须是 impact_factor，不能是 if
    expect(result).not.toContain("sort=if");
  });

  it("sort=cas → sort=cas_quartile", () => {
    const result = buildVenuesPageApiQuery(q({ sort: "cas" }));
    expect(result).toContain("sort=cas_quartile");
  });

  it("sort=az → sort=title", () => {
    const result = buildVenuesPageApiQuery(q({ sort: "az" }));
    expect(result).toContain("sort=title");
  });

  it("sort=cited → sort=cited_by", () => {
    const result = buildVenuesPageApiQuery(q({ sort: "cited" }));
    expect(result).toContain("sort=cited_by");
  });

  it("多值用逗号 CSV", () => {
    const result = buildVenuesPageApiQuery(q({ jcr: ["Q1", "Q2"], cas: ["1区", "2区"] }));
    expect(result).toContain("jcr=Q1%2CQ2");
    expect(result).toContain("cas=1%E5%8C%BA%2C2%E5%8C%BA");
  });

  it("boolean 维度 true → 写出 'true'，false → 不写", () => {
    const result = buildVenuesPageApiQuery(q({ casTop: true, oa: false, doaj: true }));
    expect(result).toContain("casTop=true");
    expect(result).toContain("doaj=true");
    expect(result).not.toContain("oa=");
  });

  it("空数组不写", () => {
    const result = buildVenuesPageApiQuery(DEFAULT_QUERY);
    expect(result).not.toContain("subject=");
    expect(result).not.toContain("jcr=");
  });

  it("无前导 ?", () => {
    const result = buildVenuesPageApiQuery(DEFAULT_QUERY);
    expect(result.startsWith("?")).toBe(false);
  });
});

// ---- buildVenuesFacetsApiQuery ----

describe("buildVenuesFacetsApiQuery", () => {
  it("不含 page、sort、pageSize", () => {
    const filters = toFilters(q({ sort: "cas", page: 3 }));
    const result = buildVenuesFacetsApiQuery(filters);
    expect(result).not.toContain("page=");
    expect(result).not.toContain("sort=");
    expect(result).not.toContain("pageSize=");
  });

  it("含 q + 筛选维度", () => {
    const filters = toFilters(q({ q: "cancer", subject: ["Medicine"], jcr: ["Q1"], casTop: true }));
    const result = buildVenuesFacetsApiQuery(filters);
    expect(result).toContain("q=cancer");
    expect(result).toContain("subject=Medicine");
    expect(result).toContain("jcr=Q1");
    expect(result).toContain("casTop=true");
  });

  it("无前导 ?", () => {
    const result = buildVenuesFacetsApiQuery(toFilters(DEFAULT_QUERY));
    expect(result.startsWith("?")).toBe(false);
  });
});

// ---- deriveActiveChips ----

describe("deriveActiveChips", () => {
  it("全空 query → []", () => {
    expect(deriveActiveChips(DEFAULT_QUERY)).toEqual([]);
  });

  it("subject 生成 chip，group='学科'", () => {
    const chips = deriveActiveChips(q({ subject: ["Medicine"] }));
    expect(chips).toHaveLength(1);
    expect(chips.at(0)?.group).toBe("学科");
    expect(chips.at(0)?.value).toBe("Medicine");
    expect(chips.at(0)?.label).toBe("Medicine");
  });

  it("全大写学科名的 chip 标签转为标题式大小写，取值保持原样", () => {
    const [chip] = deriveActiveChips(q({ subject: ["MEDICINE, GENERAL & INTERNAL"] }));
    expect(chip?.value).toBe("MEDICINE, GENERAL & INTERNAL");
    expect(chip?.label).toBe("Medicine, General & Internal");
  });

  it("jcr 生成 chip，group='JCR 分区'", () => {
    const chips = deriveActiveChips(q({ jcr: ["Q1", "Q2"] }));
    expect(chips).toHaveLength(2);
    expect(chips.at(0)?.group).toBe("JCR 分区");
    expect(chips.at(1)?.group).toBe("JCR 分区");
  });

  it("cas 生成 chip，group='中科院分区'", () => {
    const chips = deriveActiveChips(q({ cas: ["1区"] }));
    expect(chips.at(0)?.group).toBe("中科院分区");
  });

  it("country 生成 chip，group='国家'", () => {
    const chips = deriveActiveChips(q({ country: ["US"] }));
    expect(chips.at(0)?.group).toBe("国家");
  });

  it("casTop=true 生成 chip，group='中科院 Top'", () => {
    const chips = deriveActiveChips(q({ casTop: true }));
    expect(chips).toHaveLength(1);
    expect(chips.at(0)?.group).toBe("中科院 Top");
    expect(chips.at(0)?.value).toBe("casTop");
  });

  it("oa=true 生成 chip，group='开放获取'", () => {
    const chips = deriveActiveChips(q({ oa: true }));
    expect(chips.at(0)?.group).toBe("开放获取");
    expect(chips.at(0)?.value).toBe("oa");
  });

  it("doaj=true 生成 chip，group='DOAJ'", () => {
    const chips = deriveActiveChips(q({ doaj: true }));
    expect(chips.at(0)?.group).toBe("DOAJ");
    expect(chips.at(0)?.value).toBe("doaj");
  });

  it("chip.next 移除对应项后不含该值", () => {
    const original = q({ jcr: ["Q1", "Q2"] });
    const chips = deriveActiveChips(original);
    const removeQ1 = chips.find((c) => c.value === "Q1");
    expect(removeQ1).toBeDefined();
    // next 的 jcr 应只含 Q2
    expect(removeQ1?.next.jcr).toEqual(["Q2"]);
  });

  it("chip.next 移除 casTop=true 后 casTop=false", () => {
    const original = q({ casTop: true });
    const chips = deriveActiveChips(original);
    expect(chips.at(0)?.next.casTop).toBe(false);
  });

  it("chip.next 保留其他维度不变", () => {
    const original = q({ jcr: ["Q1"], country: ["US"] });
    const chips = deriveActiveChips(original);
    const jcrChip = chips.find((c) => c.group === "JCR 分区");
    expect(jcrChip?.next.country).toEqual(["US"]);
  });

  it("多维度混合，生成正确数量的 chips", () => {
    const original = q({
      subject: ["Medicine"],
      jcr: ["Q1", "Q2"],
      cas: ["1区"],
      casTop: true,
      oa: true,
      country: ["US", "GB"],
    });
    const chips = deriveActiveChips(original);
    // subject:1 + jcr:2 + cas:1 + casTop:1 + oa:1 + country:2 = 8
    expect(chips).toHaveLength(8);
  });
});

describe("venueFilterCount", () => {
  it("无筛选为 0；检索词、排序、页码不算筛选", () => {
    expect(venueFilterCount(parseVenueBrowseQuery({ q: "nature", sort: "if", page: "3" }))).toBe(0);
  });

  it("多值维度按选项数计，布尔开关各计 1", () => {
    const query = parseVenueBrowseQuery({
      subject: "Oncology,Medicine",
      jcr: "Q1,Q2",
      cas: "1区",
      casTop: "true",
      oa: "true",
      doaj: "true",
      country: "US",
    });
    expect(venueFilterCount(query)).toBe(9);
  });
});
