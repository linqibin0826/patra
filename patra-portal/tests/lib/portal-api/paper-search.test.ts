import { describe, expect, it } from "vitest";
import { EMPTY_PAPER_QUERY, MAX_PAGE, parsePaperSearchQuery } from "@/lib/portal-api/paper-search";

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
