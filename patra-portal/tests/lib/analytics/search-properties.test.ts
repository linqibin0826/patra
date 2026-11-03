import { validateAgainst } from "@tests/support/analytics-schema";
import { describe, expect, it } from "vitest";
import { isBrowseState, searchPerformedProperties } from "@/lib/analytics/server/search-properties";
import { EMPTY_PAPER_QUERY } from "@/lib/portal-api/paper-search";
import type { PaperSearchQuery } from "@/types/portal";

const SEARCH_ID = "0193e1a3-2222-7a22-8b33-445566778899";
const props = (query: Partial<PaperSearchQuery>, count = 12) =>
  searchPerformedProperties({ ...EMPTY_PAPER_QUERY, ...query }, SEARCH_ID, count);

describe("searchPerformedProperties", () => {
  it("关键词检索：去首尾空白，没用的筛选键不出现", () => {
    expect(props({ q: "  sepsis  " })).toEqual({
      search_id: SEARCH_ID,
      mode: "keyword",
      query: "sepsis",
      filters: {},
      sort: "latest",
      page: 1,
      result_count: 12,
    });
  });

  it("精确定位 PMID：query 是 PMID 本身", () => {
    expect(props({ pmid: "39871245" }, 0)).toMatchObject({
      mode: "exact_pmid",
      query: "39871245",
      result_count: 0,
    });
  });

  it("精确定位 DOI：query 是 DOI 原文", () => {
    expect(props({ doi: "10.1056/NEJMoa1802345" })).toMatchObject({
      mode: "exact_doi",
      query: "10.1056/NEJMoa1802345",
    });
  });

  it("只用筛选没写检索词时 query 为空串", () => {
    expect(props({ type: ["Review"] }).query).toBe("");
  });

  it("全部筛选键按契约命名；oa 只在为真时出现", () => {
    const result = props({
      author: "Marik",
      yearFrom: 2017,
      yearTo: 2024,
      type: ["Review", "Review"],
      evidence: ["RANDOMIZED_CONTROLLED_TRIAL"],
      lang: ["eng"],
      venue: ["7"],
      oa: true,
      sort: "year",
      page: 3,
    });
    expect(result.filters).toEqual({
      author: "Marik",
      year_from: 2017,
      year_to: 2024,
      type: ["Review"],
      evidence: ["RANDOMIZED_CONTROLLED_TRIAL"],
      lang: ["eng"],
      venue: ["7"],
      oa: true,
    });
    expect(result).toMatchObject({ sort: "year", page: 3 });
    expect(validateAgainst("events/search_performed", result)).toEqual([]);
    expect(props({ oa: false }).filters).toEqual({});
  });

  it("超出契约上限的值截断，仍合格", () => {
    const result = props({
      q: "x".repeat(300),
      author: "a".repeat(300),
      type: Array.from({ length: 25 }, (_, i) => `t${i}`),
    });
    expect(result.query).toHaveLength(200);
    expect(result.filters.author).toHaveLength(200);
    expect(result.filters.type).toHaveLength(20);
    expect(validateAgainst("events/search_performed", result)).toEqual([]);
  });
});

describe("isBrowseState：无任何条件的浏览不算检索", () => {
  it.each([
    ["默认", {}, true],
    ["第 2 页", { page: 2 }, false],
    ["按年份排序", { sort: "year" as const }, false],
    ["有检索词", { q: "x" }, false],
    ["只有作者", { author: "Marik" }, false],
    ["只有年份", { yearFrom: 2020 }, false],
    ["只有开放获取", { oa: true }, false],
    ["精确定位", { pmid: "1" }, false],
  ])("%s → %s", (_, query, expected) => {
    expect(isBrowseState({ ...EMPTY_PAPER_QUERY, ...query })).toBe(expected);
  });
});
