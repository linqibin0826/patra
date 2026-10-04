import { describe, expect, it } from "vitest";
import {
  deriveIdentifiers,
  deriveMetricCards,
  deriveMetrics,
  deriveSubjectAreas,
  deriveYearlyStats,
  formatImpactFactor,
} from "@/lib/portal-api/venue-derive";
import type { VenueDetail } from "@/types/portal";

function makeVenue(overrides: Partial<VenueDetail> = {}): VenueDetail {
  return {
    id: "1",
    title: "Test Journal",
    abbreviatedTitle: null,
    venueType: null,
    issnL: null,
    countryCode: null,
    primaryLanguage: null,
    foundedYear: null,
    coverObjectKey: null,
    homepageUrl: null,
    isOpenAccess: null,
    impactFactor: null,
    jcrQuartile: null,
    jcrSubject: null,
    casMajorCategory: null,
    casMajorQuartile: null,
    casIsTop: null,
    citeScore: null,
    hIndex: null,
    citedByCount: null,
    worksCount: null,
    frequency: null,
    medlineIndexed: null,
    oaType: null,
    apcUsd: null,
    isInDoaj: null,
    jcrRatings: [],
    casRatings: [],
    scopusRatings: [],
    yearlyStats: [],
    identifiers: [],
    ...overrides,
  };
}

describe("deriveMetrics", () => {
  it("percentile/rank 取 jcrRatings 中 year 最大的项", () => {
    const v = makeVenue({
      impactFactor: 50.5,
      jcrQuartile: "Q1",
      jcrSubject: "Oncology",
      jcrRatings: [
        {
          year: 2022,
          impactFactor: 40,
          quartile: "Q1",
          subject: "Oncology",
          jifRank: "9/300",
          jifPercentile: 95,
        },
        {
          year: 2023,
          impactFactor: 50.5,
          quartile: "Q1",
          subject: "Oncology",
          jifRank: "5/300",
          jifPercentile: 98,
        },
      ],
    });
    const m = deriveMetrics(v);
    expect(m.jcr?.impactFactor).toBe(50.5);
    expect(m.jcr?.percentile).toBe(98);
    expect(m.jcr?.rank).toBe("5/300");
  });

  it("评级列表空但顶层有快照 → jcr 仍在，percentile/rank 为 null", () => {
    const m = deriveMetrics(makeVenue({ impactFactor: 12.3, jcrQuartile: "Q1" }));
    expect(m.jcr?.impactFactor).toBe(12.3);
    expect(m.jcr?.percentile).toBeNull();
    expect(m.jcr?.rank).toBeNull();
  });

  it("某套评级完全无信号 → 该 view 为 null", () => {
    const m = deriveMetrics(makeVenue());
    expect(m.jcr).toBeNull();
    expect(m.cas).toBeNull();
    expect(m.scopus).toBeNull();
    expect(m.bibliometric).toBeNull();
  });

  it("citeScore 顶层缺失时退 scopusRatings 最新年", () => {
    const m = deriveMetrics(
      makeVenue({
        scopusRatings: [
          { year: 2022, citeScore: 8.1, sjr: 2.1, snip: 1.5, quartile: "Q1", percentile: 90 },
          { year: 2023, citeScore: 9.4, sjr: 2.3, snip: 1.7, quartile: "Q1", percentile: 92 },
        ],
      }),
    );
    expect(m.scopus?.citeScore).toBe(9.4);
    expect(m.scopus?.sjr).toBe(2.3);
  });
});

describe("deriveSubjectAreas", () => {
  it("jcrSubject + casMajorCategory 去重", () => {
    expect(
      deriveSubjectAreas(makeVenue({ jcrSubject: "Oncology", casMajorCategory: "Oncology" })),
    ).toEqual(["Oncology"]);
    expect(
      deriveSubjectAreas(makeVenue({ jcrSubject: "Oncology", casMajorCategory: "Medicine" })),
    ).toEqual(["Oncology", "Medicine"]);
  });
  it("皆空 → []", () => {
    expect(deriveSubjectAreas(makeVenue())).toEqual([]);
  });
});

describe("deriveMetricCards", () => {
  it("全量 → 3 张卡（IF accent / JCR 分区 / 中科院）", () => {
    const m = deriveMetrics(
      makeVenue({
        impactFactor: 50.5,
        jcrQuartile: "Q1",
        jcrSubject: "Oncology",
        casMajorCategory: "医学",
        casMajorQuartile: "1区",
        casIsTop: true,
        jcrRatings: [
          {
            year: 2023,
            impactFactor: 50.5,
            quartile: "Q1",
            subject: "Oncology",
            jifRank: "5/300",
            jifPercentile: 98,
          },
        ],
      }),
    );
    const cards = deriveMetricCards(m);
    expect(cards.map((c) => c.key)).toEqual(["if", "q", "cas"]);
    expect(cards[0]?.accent).toBe(true);
    expect(cards[0]?.value).toBe("50.5");
    expect(cards[1]?.sub).toBe("排名 5/300");
    expect(cards[2]?.sub).toContain("Top");
  });

  it("无中科院但有 Scopus → 第 3 卡退 CiteScore", () => {
    const m = deriveMetrics(makeVenue({ impactFactor: 10, jcrQuartile: "Q2", citeScore: 9.4 }));
    expect(deriveMetricCards(m).map((c) => c.key)).toEqual(["if", "q", "cs"]);
  });

  it("无 IF → 不出第 1 卡", () => {
    const cards = deriveMetricCards(deriveMetrics(makeVenue({ jcrQuartile: "Q1" })));
    expect(cards.find((c) => c.key === "if")).toBeUndefined();
  });
});

describe("deriveYearlyStats", () => {
  it("按 year 升序排列", () => {
    const v = makeVenue({
      yearlyStats: [
        { year: 2023, worksCount: 10, citedByCount: 100, oaWorksCount: 5 },
        { year: 2021, worksCount: 8, citedByCount: 80, oaWorksCount: 3 },
        { year: 2022, worksCount: 9, citedByCount: 90, oaWorksCount: 4 },
      ],
    });
    expect(deriveYearlyStats(v).map((s) => s.year)).toEqual([2021, 2022, 2023]);
  });

  it("空 → []", () => {
    expect(deriveYearlyStats(makeVenue())).toEqual([]);
  });

  it("不修改原数组", () => {
    const v = makeVenue({
      yearlyStats: [
        { year: 2023, worksCount: 10, citedByCount: 100, oaWorksCount: 5 },
        { year: 2021, worksCount: 8, citedByCount: 80, oaWorksCount: 3 },
      ],
    });
    const before = v.yearlyStats.map((s) => s.year);
    deriveYearlyStats(v);
    expect(v.yearlyStats.map((s) => s.year)).toEqual(before);
  });
});

describe("formatImpactFactor", () => {
  it("保留一位小数", () => {
    expect(formatImpactFactor(65.4)).toBe("65.4");
    expect(formatImpactFactor(12)).toBe("12.0");
  });

  it("缺值与非正数显示 —（后端用 0 表示没有影响因子）", () => {
    expect(formatImpactFactor(null)).toBe("—");
    expect(formatImpactFactor(undefined)).toBe("—");
    expect(formatImpactFactor(0)).toBe("—");
    expect(formatImpactFactor(-1)).toBe("—");
  });
});

describe("deriveIdentifiers", () => {
  const raw = [
    { type: "NLM", value: "9007735", primary: false },
    { type: "ISSN_L", value: "0923-7534", primary: false },
    { type: "CODEN", value: "ANONE2", primary: false },
    { type: "ISSN", value: "0923-7534", primary: false },
    { type: "ISSN", value: "1569-8041", primary: false },
    { type: "OPENALEX", value: "S41454044", primary: false },
  ];

  it("原始类型换成可读的键名，按原顺序输出", () => {
    expect(deriveIdentifiers(raw)).toEqual([
      { label: "NLM ID", value: "9007735" },
      { label: "CODEN", value: "ANONE2" },
      { label: "ISSN", value: "0923-7534" },
      { label: "ISSN", value: "1569-8041" },
      { label: "OpenAlex", value: "S41454044" },
    ]);
  });

  it("ISSN-L 与某个 ISSN 相同时不重复显示；不同时保留并写作 ISSN-L", () => {
    expect(deriveIdentifiers(raw).some((i) => i.label === "ISSN-L")).toBe(false);
    expect(deriveIdentifiers([{ type: "ISSN_L", value: "1111-2222", primary: false }])).toEqual([
      { label: "ISSN-L", value: "1111-2222" },
    ]);
  });

  it("空值与 ISSN 占位符 XXXX-XXXX 不显示；未知类型原样作键名", () => {
    expect(
      deriveIdentifiers([
        { type: "ISSN", value: "XXXX-XXXX", primary: false },
        { type: "ISSN", value: "", primary: false },
        { type: "WIKIDATA", value: "Q123", primary: false },
      ]),
    ).toEqual([{ label: "WIKIDATA", value: "Q123" }]);
  });
});
