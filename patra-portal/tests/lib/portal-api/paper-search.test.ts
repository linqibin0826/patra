import { describe, expect, it } from "vitest";
import {
  buildFacetsApiQuery,
  buildPapersHref,
  buildSearchApiQuery,
  clearAllConditions,
  composerTarget,
  containsIgnoreCase,
  currentPortalYear,
  derivePaperChips,
  EMPTY_PAPER_QUERY,
  filterCount,
  hasSearchConditions,
  isExactLookup,
  isRecentYearsActive,
  MAX_PAGE,
  papersHref,
  parsePaperSearchQuery,
  selectExactYear,
  selectRecentYears,
  serializePaperSearchQuery,
  submitExactLookup,
  submitTextSearch,
  toggleListValue,
  toggleOpenAccess,
  validateSearchInput,
  visibleFacetOptions,
  withSort,
  yearChipLabel,
} from "@/lib/portal-api/paper-search";
import type { PaperSearchQuery } from "@/types/portal";

/** URLSearchParams → Next searchParams 形态（重复参数聚成数组） */
function toRecord(qs: string): Record<string, string | string[]> {
  const out: Record<string, string | string[]> = {};
  for (const [key, value] of new URLSearchParams(qs)) {
    const prev = out[key];
    out[key] = prev === undefined ? value : Array.isArray(prev) ? [...prev, value] : [prev, value];
  }
  return out;
}

const FULL: PaperSearchQuery = {
  ...EMPTY_PAPER_QUERY,
  q: "GLP-1",
  author: "Smith",
  yearFrom: 2024,
  type: ["Review"],
  evidence: ["RANDOMIZED_CONTROLLED_TRIAL"],
  venue: ["123"],
  lang: ["en"],
  oa: true,
  sort: "year",
  page: 2,
};

describe("parsePaperSearchQuery", () => {
  it("空参数 → 浏览态默认查询", () => {
    expect(parsePaperSearchQuery({})).toEqual(EMPTY_PAPER_QUERY);
  });

  it("读取文本与年份，文本去首尾空白", () => {
    const q = parsePaperSearchQuery({
      q: " GLP-1 ",
      author: "Smith",
      yearFrom: "2024",
      yearTo: "2026",
    });
    expect(q).toMatchObject({ q: "GLP-1", author: "Smith", yearFrom: 2024, yearTo: 2026 });
  });

  it("多值重复参数：含逗号的类型值原样保留，去空白与重复", () => {
    const q = parsePaperSearchQuery({
      type: ["Clinical Trial, Phase III", "Review", "Review", " "],
    });
    expect(q.type).toEqual(["Clinical Trial, Phase III", "Review"]);
    expect(parsePaperSearchQuery({ type: "Review" }).type).toEqual(["Review"]);
  });

  it("类型去重忽略大小写（与 BE 一致），保留首次写法", () => {
    expect(parsePaperSearchQuery({ type: ["Review", "review", "REVIEW"] }).type).toEqual([
      "Review",
    ]);
  });

  it("语言统一小写（BE 语言比较区分大小写）", () => {
    expect(parsePaperSearchQuery({ lang: ["EN", "en", "De"] }).lang).toEqual(["en", "de"]);
  });

  it("evidence 统一大写、去重，非法枚举名丢弃", () => {
    const q = parsePaperSearchQuery({
      evidence: ["randomized_controlled_trial", "RANDOMIZED_CONTROLLED_TRIAL", "BOGUS"],
    });
    expect(q.evidence).toEqual(["RANDOMIZED_CONTROLLED_TRIAL"]);
  });

  it("venue 只保留 1..Long.MAX 的正整数字符串，不经 Number 损失精度", () => {
    const q = parsePaperSearchQuery({
      venue: ["123", "0", "-1", "abc", "0123", "9223372036854775807", "9223372036854775808"],
    });
    expect(q.venue).toEqual(["123", "9223372036854775807"]);
  });

  it("非法年份丢弃（非 4 位数字 / 越界）", () => {
    expect(parsePaperSearchQuery({ yearFrom: "20x6" }).yearFrom).toBeNull();
    expect(parsePaperSearchQuery({ yearFrom: "999" }).yearFrom).toBeNull();
    expect(parsePaperSearchQuery({ yearTo: "10000" }).yearTo).toBeNull();
  });

  it("yearFrom > yearTo 时自动对调", () => {
    expect(parsePaperSearchQuery({ yearFrom: "2026", yearTo: "2024" })).toMatchObject({
      yearFrom: 2024,
      yearTo: 2026,
    });
  });

  it("文本超过 200 字视为未传", () => {
    expect(parsePaperSearchQuery({ q: "a".repeat(201) }).q).toBe("");
    expect(parsePaperSearchQuery({ q: "a".repeat(200) }).q).toHaveLength(200);
  });

  it("sort 只接受 latest / year（大小写不敏感），其余回默认", () => {
    expect(parsePaperSearchQuery({ sort: "year" }).sort).toBe("year");
    expect(parsePaperSearchQuery({ sort: "YEAR" }).sort).toBe("year");
    expect(parsePaperSearchQuery({ sort: "cited" }).sort).toBe("latest");
  });

  it("page：非正整数或超过 MAX_PAGE 时取 1", () => {
    expect(parsePaperSearchQuery({ page: "3" }).page).toBe(3);
    expect(parsePaperSearchQuery({ page: "0" }).page).toBe(1);
    expect(parsePaperSearchQuery({ page: "-2" }).page).toBe(1);
    expect(parsePaperSearchQuery({ page: "abc" }).page).toBe(1);
    expect(parsePaperSearchQuery({ page: String(MAX_PAGE) }).page).toBe(MAX_PAGE);
    expect(parsePaperSearchQuery({ page: String(MAX_PAGE + 1) }).page).toBe(1);
    expect(parsePaperSearchQuery({ page: "2147483648" }).page).toBe(1);
  });

  it("MAX_PAGE 保证 BE offset 不超过 Integer.MAX_VALUE", () => {
    expect(MAX_PAGE).toBe(107374182);
    expect((MAX_PAGE - 1) * 20).toBeLessThanOrEqual(2_147_483_647);
  });

  it("oa 只认 true", () => {
    expect(parsePaperSearchQuery({ oa: "true" }).oa).toBe(true);
    expect(parsePaperSearchQuery({ oa: "1" }).oa).toBe(false);
  });

  it("精确定位：pmid 合法时只保留 pmid，丢弃其余条件、排序与页码", () => {
    const q = parsePaperSearchQuery({
      pmid: "41605285",
      q: "x",
      type: "Review",
      sort: "year",
      page: "2",
    });
    expect(q).toEqual({ ...EMPTY_PAPER_QUERY, pmid: "41605285" });
  });

  it("pmid 非法时丢弃，按普通检索解析", () => {
    const q = parsePaperSearchQuery({ pmid: "abc", q: "x" });
    expect(q).toMatchObject({ pmid: "", q: "x" });
  });

  it("doi 模式：只保留 doi（去首尾空白）", () => {
    const q = parsePaperSearchQuery({ doi: " 10.1016/j.x ", q: "x", page: "4" });
    expect(q).toEqual({ ...EMPTY_PAPER_QUERY, doi: "10.1016/j.x" });
  });

  it("pmid 优先于 doi", () => {
    const q = parsePaperSearchQuery({ pmid: "1", doi: "10.1/x" });
    expect(q).toEqual({ ...EMPTY_PAPER_QUERY, pmid: "1" });
  });
});

describe("serializePaperSearchQuery", () => {
  it("默认查询序列化为空串", () => {
    expect(serializePaperSearchQuery(EMPTY_PAPER_QUERY)).toBe("");
  });

  it("参数顺序稳定：条件 → sort → page", () => {
    expect(serializePaperSearchQuery(FULL)).toBe(
      "q=GLP-1&author=Smith&yearFrom=2024&type=Review&evidence=RANDOMIZED_CONTROLLED_TRIAL&venue=123&lang=en&oa=true&sort=year&page=2",
    );
  });

  it("多值用重复参数，含逗号的值被编码而不是拆开", () => {
    const qs = serializePaperSearchQuery({
      ...EMPTY_PAPER_QUERY,
      type: ["Clinical Trial, Phase III", "Review"],
    });
    expect(qs).toBe("type=Clinical+Trial%2C+Phase+III&type=Review");
  });

  it("往返一致：parse(serialize(q)) 等于 q", () => {
    expect(parsePaperSearchQuery(toRecord(serializePaperSearchQuery(FULL)))).toEqual(FULL);
    const exact = { ...EMPTY_PAPER_QUERY, doi: "10.1016/j.x" };
    expect(parsePaperSearchQuery(toRecord(serializePaperSearchQuery(exact)))).toEqual(exact);
  });

  it("MAX_PAGE 往返保持", () => {
    const q = { ...EMPTY_PAPER_QUERY, page: MAX_PAGE };
    expect(parsePaperSearchQuery(toRecord(serializePaperSearchQuery(q))).page).toBe(MAX_PAGE);
  });
});

describe("papersHref / buildPapersHref", () => {
  it("无条件时为 /papers", () => {
    expect(papersHref(EMPTY_PAPER_QUERY)).toBe("/papers");
  });

  it("按部分字段拼出 /papers 链接（全站入口用）", () => {
    expect(buildPapersHref({ q: "GLP-1" })).toBe("/papers?q=GLP-1");
    expect(buildPapersHref({ venue: ["123"] })).toBe("/papers?venue=123");
    expect(buildPapersHref({ pmid: "41605285" })).toBe("/papers?pmid=41605285");
  });
});

describe("后端 query", () => {
  it("search：条件 + sort + page + pageSize=20", () => {
    expect(buildSearchApiQuery(EMPTY_PAPER_QUERY)).toBe("sort=latest&page=1&pageSize=20");
    expect(buildSearchApiQuery({ ...EMPTY_PAPER_QUERY, type: ["A", "B"], page: 3 })).toBe(
      "type=A&type=B&sort=latest&page=3&pageSize=20",
    );
  });

  it("facets：只带条件，不带 sort / page", () => {
    expect(buildFacetsApiQuery({ ...EMPTY_PAPER_QUERY, q: "x", sort: "year", page: 3 })).toBe(
      "q=x",
    );
    expect(buildFacetsApiQuery(EMPTY_PAPER_QUERY)).toBe("");
  });
});

describe("状态判定", () => {
  it("isExactLookup：pmid 或 doi 非空", () => {
    expect(isExactLookup(EMPTY_PAPER_QUERY)).toBe(false);
    expect(isExactLookup({ ...EMPTY_PAPER_QUERY, pmid: "1" })).toBe(true);
    expect(isExactLookup({ ...EMPTY_PAPER_QUERY, doi: "10.1/x" })).toBe(true);
  });

  it("hasSearchConditions：排序与页码不算条件", () => {
    expect(hasSearchConditions(EMPTY_PAPER_QUERY)).toBe(false);
    expect(hasSearchConditions({ ...EMPTY_PAPER_QUERY, sort: "year", page: 2 })).toBe(false);
    expect(hasSearchConditions({ ...EMPTY_PAPER_QUERY, q: "x" })).toBe(true);
    expect(hasSearchConditions({ ...EMPTY_PAPER_QUERY, pmid: "1" })).toBe(true);
    expect(hasSearchConditions({ ...EMPTY_PAPER_QUERY, oa: true })).toBe(true);
  });

  it("filterCount：年份算 1 项，文本检索不计入", () => {
    expect(filterCount({ ...EMPTY_PAPER_QUERY, yearFrom: 2024, yearTo: 2025 })).toBe(1);
    expect(filterCount({ ...EMPTY_PAPER_QUERY, type: ["A", "B"], oa: true, q: "x" })).toBe(3);
  });
});

const roundTrip = (q: PaperSearchQuery) =>
  parsePaperSearchQuery(toRecord(serializePaperSearchQuery(q)));

describe("精确定位模式下的筛选操作", () => {
  const exact = { ...EMPTY_PAPER_QUERY, pmid: "1" };

  it("筛选 / 年份 / 开放获取 / 排序操作退出精确模式；序列化再解析后条件保留", () => {
    expect(roundTrip(toggleListValue(exact, "type", "Review"))).toMatchObject({
      pmid: "",
      type: ["Review"],
    });
    expect(roundTrip(toggleOpenAccess(exact))).toMatchObject({ pmid: "", oa: true });
    expect(roundTrip(selectExactYear(exact, 2025))).toMatchObject({
      pmid: "",
      yearFrom: 2025,
      yearTo: 2025,
    });
    expect(roundTrip(selectRecentYears(exact, 3, 2026))).toMatchObject({
      pmid: "",
      yearFrom: 2024,
    });
    expect(roundTrip(withSort({ ...EMPTY_PAPER_QUERY, doi: "10.1/x" }, "year"))).toMatchObject({
      doi: "",
      sort: "year",
    });
  });
});

describe("大小写与 BE 一致", () => {
  it("类型勾选 / 取消忽略大小写", () => {
    expect(containsIgnoreCase(["review"], "Review")).toBe(true);
    expect(
      toggleListValue({ ...EMPTY_PAPER_QUERY, type: ["review"] }, "type", "Review").type,
    ).toEqual([]);
  });

  it("语言勾选统一小写", () => {
    expect(toggleListValue(EMPTY_PAPER_QUERY, "lang", "EN").lang).toEqual(["en"]);
  });

  it("visibleFacetOptions：已选值与 facet 值仅大小写不同时不重复追加", () => {
    const options = [{ value: "Review", count: 5 }];
    expect(visibleFacetOptions(options, ["review"], false, 6)).toEqual(options);
  });
});

describe("validateSearchInput", () => {
  it("PMID 须为纯数字；其余检索词不超过 200 字", () => {
    expect(validateSearchInput("pmid", "38491203")).toBeNull();
    expect(validateSearchInput("pmid", "abc")).toBe("PMID 应为纯数字");
    expect(validateSearchInput("keyword", "a".repeat(200))).toBeNull();
    expect(validateSearchInput("author", "a".repeat(201))).toBe("检索词最长 200 字");
    expect(validateSearchInput("doi", "a".repeat(201))).toBe("检索词最长 200 字");
  });
});

describe("检索提交", () => {
  it("submitTextSearch：替换关键词、清除 pmid/doi、保留筛选、回第 1 页", () => {
    const base = { ...EMPTY_PAPER_QUERY, author: "Smith", type: ["Review"], page: 4 };
    expect(submitTextSearch(base, "q", " GLP-1 ")).toEqual({ ...base, q: "GLP-1", page: 1 });
    expect(submitTextSearch(base, "author", "")).toEqual({ ...base, author: "", page: 1 });
  });

  it("submitTextSearch：从精确定位模式切回时清掉 pmid", () => {
    const exact = { ...EMPTY_PAPER_QUERY, pmid: "999" };
    expect(submitTextSearch(exact, "q", "GLP-1")).toEqual({ ...EMPTY_PAPER_QUERY, q: "GLP-1" });
  });

  it("submitExactLookup：生成只含 pmid / doi 的全新查询", () => {
    expect(submitExactLookup("pmid", " 41605285 ")).toEqual({
      ...EMPTY_PAPER_QUERY,
      pmid: "41605285",
    });
    expect(submitExactLookup("doi", "10.1/x")).toEqual({ ...EMPTY_PAPER_QUERY, doi: "10.1/x" });
  });
});

describe("筛选变换", () => {
  it("toggleListValue：加入 / 移除，并回第 1 页", () => {
    const added = toggleListValue({ ...EMPTY_PAPER_QUERY, page: 3 }, "type", "Review");
    expect(added).toMatchObject({ type: ["Review"], page: 1 });
    expect(toggleListValue(added, "type", "Review").type).toEqual([]);
  });

  it("toggleListValue：evidence 非法值不改变查询", () => {
    expect(toggleListValue(EMPTY_PAPER_QUERY, "evidence", "BOGUS")).toBe(EMPTY_PAPER_QUERY);
    expect(toggleListValue(EMPTY_PAPER_QUERY, "evidence", "CASE_REPORT").evidence).toEqual([
      "CASE_REPORT",
    ]);
  });

  it("toggleOpenAccess / withSort 回第 1 页", () => {
    expect(toggleOpenAccess({ ...EMPTY_PAPER_QUERY, page: 5 })).toMatchObject({
      oa: true,
      page: 1,
    });
    expect(withSort({ ...EMPTY_PAPER_QUERY, page: 5 }, "year")).toMatchObject({
      sort: "year",
      page: 1,
    });
  });

  it("clearAllConditions：清空条件但保留排序", () => {
    expect(clearAllConditions(FULL)).toEqual({ ...EMPTY_PAPER_QUERY, sort: "year" });
  });
});

describe("年份单选", () => {
  it("selectExactYear：选中写 from=to；再点取消", () => {
    const picked = selectExactYear(EMPTY_PAPER_QUERY, 2025);
    expect(picked).toMatchObject({ yearFrom: 2025, yearTo: 2025, page: 1 });
    expect(selectExactYear(picked, 2025)).toMatchObject({ yearFrom: null, yearTo: null });
  });

  it("selectRecentYears：近 3 年 = yearFrom 当年−2，yearTo 为空；再点取消；与逐年项互斥", () => {
    const exact = selectExactYear(EMPTY_PAPER_QUERY, 2025);
    const recent = selectRecentYears(exact, 3, 2026);
    expect(recent).toMatchObject({ yearFrom: 2024, yearTo: null });
    expect(isRecentYearsActive(recent, 3, 2026)).toBe(true);
    expect(isRecentYearsActive(recent, 1, 2026)).toBe(false);
    expect(selectRecentYears(recent, 3, 2026)).toMatchObject({ yearFrom: null, yearTo: null });
  });

  it("yearChipLabel 四种文案", () => {
    expect(yearChipLabel(null, null, 2026)).toBeNull();
    expect(yearChipLabel(2025, 2025, 2026)).toBe("2025 年");
    expect(yearChipLabel(2023, 2025, 2026)).toBe("2023–2025 年");
    expect(yearChipLabel(2024, null, 2026)).toBe("近 3 年");
    expect(yearChipLabel(2020, null, 2026)).toBe("2020 年起");
    expect(yearChipLabel(null, 2025, 2026)).toBe("2025 年及以前");
  });

  it("currentPortalYear 按 Asia/Shanghai 计算", () => {
    expect(currentPortalYear(new Date("2026-12-31T17:00:00Z"))).toBe(2027);
    expect(currentPortalYear(new Date("2026-06-01T00:00:00Z"))).toBe(2026);
  });
});

describe("visibleFacetOptions", () => {
  const options = Array.from({ length: 8 }, (_, i) => ({ value: `T${i}`, count: 100 - i }));

  it("折叠时显示前 limit 项", () => {
    expect(visibleFacetOptions(options, [], false, 6).map((o) => o.value)).toEqual([
      "T0",
      "T1",
      "T2",
      "T3",
      "T4",
      "T5",
    ]);
  });

  it("已选项不在前 limit 时追加在后，保留其计数", () => {
    const visible = visibleFacetOptions(options, ["T7"], false, 6);
    expect(visible.at(-1)).toEqual({ value: "T7", count: 93 });
  });

  it("已选项不在 facet 结果中时以计数 0 追加", () => {
    expect(visibleFacetOptions(options, ["Gone"], true, 6).at(-1)).toEqual({
      value: "Gone",
      count: 0,
    });
  });

  it("展开时显示全部", () => {
    expect(visibleFacetOptions(options, [], true, 6)).toHaveLength(8);
  });
});

describe("derivePaperChips", () => {
  const ctx = { currentYear: 2026, venueNames: { "123": "The Lancet" } };

  it("无条件时为空", () => {
    expect(derivePaperChips(EMPTY_PAPER_QUERY, ctx)).toEqual([]);
  });

  it("按 关键词→作者→年份→类型→证据→期刊→语言→开放获取 排列并给出中文文案", () => {
    const chips = derivePaperChips({ ...FULL, venue: ["123", "456"] }, ctx);
    expect(chips.map((c) => [c.group, c.label])).toEqual([
      ["关键词", "GLP-1"],
      ["作者", "Smith"],
      ["年份", "近 3 年"],
      ["类型", "综述"],
      ["证据等级", "随机对照试验"],
      ["期刊", "The Lancet"],
      ["期刊", "期刊 #456"],
      ["语言", "英文"],
      ["开放获取", "仅开放获取"],
    ]);
  });

  it("next 为移除该项后的查询，并回第 1 页", () => {
    const chips = derivePaperChips({ ...EMPTY_PAPER_QUERY, type: ["A", "B"], page: 3 }, ctx);
    expect(chips[0]?.next).toMatchObject({ type: ["B"], page: 1 });
  });
});

describe("composerTarget（首页搜索框）", () => {
  it("四个 tab 拼出对应链接", () => {
    expect(composerTarget("keyword", " GLP-1 ")).toEqual({ kind: "ok", href: "/papers?q=GLP-1" });
    expect(composerTarget("author", "Topol")).toEqual({ kind: "ok", href: "/papers?author=Topol" });
    expect(composerTarget("pmid", "38491203")).toEqual({
      kind: "ok",
      href: "/papers?pmid=38491203",
    });
    expect(composerTarget("doi", "10.1016/j.x")).toEqual({
      kind: "ok",
      href: "/papers?doi=10.1016%2Fj.x",
    });
  });

  it("空内容不跳转；PMID 非数字 / 超长检索词给出提示（不静默变成全库浏览）", () => {
    expect(composerTarget("keyword", "  ")).toEqual({ kind: "empty" });
    expect(composerTarget("pmid", "abc")).toEqual({ kind: "invalid", message: "PMID 应为纯数字" });
    expect(composerTarget("keyword", "a".repeat(201))).toEqual({
      kind: "invalid",
      message: "检索词最长 200 字",
    });
  });
});
