import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import {
  fetchVenueDetail,
  fetchVenues,
  fetchVenuesFacets,
  fetchVenuesPage,
} from "@/lib/portal-api/venues";
import type {
  PageResult,
  VenueBrowse,
  VenueBrowseFilters,
  VenueBrowsePage,
  VenueBrowseQuery,
} from "@/types/portal";

const SAMPLE_VENUE: VenueBrowse = {
  id: "319041872872550658",
  name: "Annals of oncology",
  abbr: "Ann Oncol",
  coverObjectKey: null,
  impactFactor: 65.4,
  jcrQuartile: "Q1",
  jcrSubject: null,
  casMajorCategory: null,
  casMajorQuartile: null,
  casIsTop: null,
  countryCode: null,
  citedByCount: null,
  foundedYear: 1990,
  isOpenAccess: null,
  isInDoaj: null,
  issnL: null,
};

const SAMPLE_PAGE: PageResult<VenueBrowse> = {
  page: 1,
  pageSize: 6,
  total: 1,
  totalPages: 1,
  items: [SAMPLE_VENUE],
};

describe("fetchVenues", () => {
  beforeEach(() => {
    process.env.PATRA_GATEWAY_BASE_URL = "http://gw.test:9528";
  });
  afterEach(() => {
    vi.unstubAllGlobals();
    delete process.env.PATRA_GATEWAY_BASE_URL;
  });

  it("拼出正确的 gateway URL 并解析 PageResult.items", async () => {
    const fetchMock = vi
      .fn()
      .mockResolvedValue(new Response(JSON.stringify(SAMPLE_PAGE), { status: 200 }));
    vi.stubGlobal("fetch", fetchMock);

    const result = await fetchVenues(6);

    expect(fetchMock).toHaveBeenCalledWith(
      "http://gw.test:9528/patra-catalog/portal/venues?sort=impactFactor&pageSize=6",
      expect.objectContaining({
        cache: "no-store",
        signal: expect.any(AbortSignal),
      }),
    );
    expect(result).toEqual([SAMPLE_VENUE]);
  });

  it("pageSize 默认 6", async () => {
    const emptyPage: PageResult<VenueBrowse> = {
      page: 1,
      pageSize: 6,
      total: 0,
      totalPages: 0,
      items: [],
    };
    const fetchMock = vi
      .fn()
      .mockResolvedValue(new Response(JSON.stringify(emptyPage), { status: 200 }));
    vi.stubGlobal("fetch", fetchMock);

    await fetchVenues();

    expect(fetchMock).toHaveBeenCalledWith(
      "http://gw.test:9528/patra-catalog/portal/venues?sort=impactFactor&pageSize=6",
      expect.anything(),
    );
  });

  it("非 2xx 抛错", async () => {
    vi.stubGlobal("fetch", vi.fn().mockResolvedValue(new Response("err", { status: 502 })));
    await expect(fetchVenues()).rejects.toThrow(/502/);
  });

  it("缺 PATRA_GATEWAY_BASE_URL 抛错", async () => {
    delete process.env.PATRA_GATEWAY_BASE_URL;
    await expect(fetchVenues()).rejects.toThrow(/PATRA_GATEWAY_BASE_URL/);
  });

  it("坏 payload（缺 items）降级返回空数组", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn().mockResolvedValue(new Response(JSON.stringify({ page: 1 }), { status: 200 })),
    );
    const result = await fetchVenues();
    expect(result).toEqual([]);
  });
});

// ---- fetchVenuesPage ----

const SAMPLE_BROWSE_QUERY: VenueBrowseQuery = {
  q: "oncology",
  sort: "if",
  page: 2,
  subject: ["Medicine", "Biology"],
  jcr: ["Q1"],
  cas: ["1区"],
  casTop: true,
  oa: true,
  doaj: false,
  country: ["US"],
};

const SAMPLE_VENUES_PAGE: VenueBrowsePage = {
  page: 2,
  pageSize: 12,
  total: 25,
  totalPages: 3,
  items: [SAMPLE_VENUE],
};

describe("fetchVenuesPage", () => {
  beforeEach(() => {
    process.env.PATRA_GATEWAY_BASE_URL = "http://gw.test:9528";
  });
  afterEach(() => {
    vi.unstubAllGlobals();
    delete process.env.PATRA_GATEWAY_BASE_URL;
  });

  it("URL 包含 buildVenuesPageApiQuery 产物（多值 CSV、page、pageSize=12、sort 码）", async () => {
    const fetchMock = vi
      .fn()
      .mockResolvedValue(new Response(JSON.stringify(SAMPLE_VENUES_PAGE), { status: 200 }));
    vi.stubGlobal("fetch", fetchMock);

    await fetchVenuesPage(SAMPLE_BROWSE_QUERY);

    const calledUrl = fetchMock.mock.calls[0]?.[0] as string;
    expect(calledUrl).toContain("/patra-catalog/portal/venues?");
    expect(calledUrl).toContain("pageSize=12");
    expect(calledUrl).toContain("page=2");
    // sort=if 省略，不传 sort 参数（buildVenuesPageApiQuery 的行为）
    expect(calledUrl).not.toContain("sort=impact_factor");
    // 多值 CSV
    expect(calledUrl).toContain("subject=Medicine%2CBiology");
    expect(calledUrl).toContain("jcr=Q1");
    expect(calledUrl).toContain("cas=1%E5%8C%BA");
    expect(calledUrl).toContain("casTop=true");
    expect(calledUrl).toContain("oa=true");
    expect(calledUrl).not.toContain("doaj");
    expect(calledUrl).toContain("country=US");
  });

  it("sort=cas 时使用 sort 码 cas_quartile", async () => {
    const fetchMock = vi
      .fn()
      .mockResolvedValue(new Response(JSON.stringify(SAMPLE_VENUES_PAGE), { status: 200 }));
    vi.stubGlobal("fetch", fetchMock);

    await fetchVenuesPage({ ...SAMPLE_BROWSE_QUERY, sort: "cas" });

    const calledUrl = fetchMock.mock.calls[0]?.[0] as string;
    expect(calledUrl).toContain("sort=cas_quartile");
  });

  it("fetch 选项包含 cache:no-store 和 AbortSignal", async () => {
    const fetchMock = vi
      .fn()
      .mockResolvedValue(new Response(JSON.stringify(SAMPLE_VENUES_PAGE), { status: 200 }));
    vi.stubGlobal("fetch", fetchMock);

    await fetchVenuesPage(SAMPLE_BROWSE_QUERY);

    expect(fetchMock).toHaveBeenCalledWith(
      expect.any(String),
      expect.objectContaining({
        cache: "no-store",
        signal: expect.any(AbortSignal),
      }),
    );
  });

  it("200 返回 VenueBrowsePage", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn().mockResolvedValue(new Response(JSON.stringify(SAMPLE_VENUES_PAGE), { status: 200 })),
    );

    const result = await fetchVenuesPage(SAMPLE_BROWSE_QUERY);

    expect(result).toEqual(SAMPLE_VENUES_PAGE);
  });

  it("非 2xx 抛错", async () => {
    vi.stubGlobal("fetch", vi.fn().mockResolvedValue(new Response("err", { status: 502 })));
    await expect(fetchVenuesPage(SAMPLE_BROWSE_QUERY)).rejects.toThrow(/502/);
  });

  it("坏 payload（缺 items/total）降级返回空页，不抛错", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn().mockResolvedValue(new Response(JSON.stringify({ page: 2 }), { status: 200 })),
    );
    const result = await fetchVenuesPage(SAMPLE_BROWSE_QUERY);
    expect(result.items).toEqual([]);
    expect(result.total).toBe(0);
    expect(result.totalPages).toBe(0);
    expect(result.page).toBe(SAMPLE_BROWSE_QUERY.page);
  });

  it("缺 PATRA_GATEWAY_BASE_URL 抛错", async () => {
    delete process.env.PATRA_GATEWAY_BASE_URL;
    await expect(fetchVenuesPage(SAMPLE_BROWSE_QUERY)).rejects.toThrow(/PATRA_GATEWAY_BASE_URL/);
  });
});

// ---- fetchVenuesFacets ----

const SAMPLE_FILTERS: VenueBrowseFilters = {
  q: "cancer",
  subject: ["Medicine"],
  jcr: ["Q1"],
  cas: [],
  casTop: false,
  oa: false,
  doaj: false,
  country: [],
};

// BE 返回的原始字段名
const BE_FACETS_RESPONSE = {
  subjects: [
    { value: "Medicine", count: 120 },
    { value: "Biology", count: 80 },
  ],
  jcrQuartiles: [{ value: "Q1", count: 50 }],
  casQuartiles: [{ value: "1区", count: 30 }],
  countries: [{ value: "US", count: 200 }],
  casTop: 15,
  openAccess: 45,
  doaj: 10,
};

describe("fetchVenuesFacets", () => {
  beforeEach(() => {
    process.env.PATRA_GATEWAY_BASE_URL = "http://gw.test:9528";
  });
  afterEach(() => {
    vi.unstubAllGlobals();
    delete process.env.PATRA_GATEWAY_BASE_URL;
  });

  it("命中 /portal/venues/facets 端点", async () => {
    const fetchMock = vi
      .fn()
      .mockResolvedValue(new Response(JSON.stringify(BE_FACETS_RESPONSE), { status: 200 }));
    vi.stubGlobal("fetch", fetchMock);

    await fetchVenuesFacets(SAMPLE_FILTERS);

    const calledUrl = fetchMock.mock.calls[0]?.[0] as string;
    expect(calledUrl).toContain("/patra-catalog/portal/venues/facets?");
  });

  it("键名归一：BE subjects→subject、jcrQuartiles→jcr、casQuartiles→cas、countries→country、openAccess→oa", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn().mockResolvedValue(new Response(JSON.stringify(BE_FACETS_RESPONSE), { status: 200 })),
    );

    const result = await fetchVenuesFacets(SAMPLE_FILTERS);

    // 数组字段归一
    expect(result.subject).toEqual(BE_FACETS_RESPONSE.subjects);
    expect(result.jcr).toEqual(BE_FACETS_RESPONSE.jcrQuartiles);
    expect(result.cas).toEqual(BE_FACETS_RESPONSE.casQuartiles);
    expect(result.country).toEqual(BE_FACETS_RESPONSE.countries);
    // 数值字段归一
    expect(result.oa).toBe(BE_FACETS_RESPONSE.openAccess);
    expect(result.casTop).toBe(BE_FACETS_RESPONSE.casTop);
    expect(result.doaj).toBe(BE_FACETS_RESPONSE.doaj);

    // 原 BE 键不暴露在 FE 结果
    expect(result).not.toHaveProperty("subjects");
    expect(result).not.toHaveProperty("jcrQuartiles");
    expect(result).not.toHaveProperty("casQuartiles");
    expect(result).not.toHaveProperty("countries");
    expect(result).not.toHaveProperty("openAccess");
  });

  it("FacetOption {value, count} 结构原样保留", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn().mockResolvedValue(new Response(JSON.stringify(BE_FACETS_RESPONSE), { status: 200 })),
    );

    const result = await fetchVenuesFacets(SAMPLE_FILTERS);

    expect(result.subject[0]).toEqual({ value: "Medicine", count: 120 });
    expect(result.subject[1]).toEqual({ value: "Biology", count: 80 });
  });

  it("fetch 选项包含 cache:no-store 和 AbortSignal", async () => {
    const fetchMock = vi
      .fn()
      .mockResolvedValue(new Response(JSON.stringify(BE_FACETS_RESPONSE), { status: 200 }));
    vi.stubGlobal("fetch", fetchMock);

    await fetchVenuesFacets(SAMPLE_FILTERS);

    expect(fetchMock).toHaveBeenCalledWith(
      expect.any(String),
      expect.objectContaining({
        cache: "no-store",
        signal: expect.any(AbortSignal),
      }),
    );
  });

  it("非 2xx 抛错", async () => {
    vi.stubGlobal("fetch", vi.fn().mockResolvedValue(new Response("err", { status: 503 })));
    await expect(fetchVenuesFacets(SAMPLE_FILTERS)).rejects.toThrow(/503/);
  });

  it("坏 payload（缺数组字段）降级为空数组 + 数值 0，不抛错", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn().mockResolvedValue(new Response(JSON.stringify({}), { status: 200 })),
    );
    const result = await fetchVenuesFacets(SAMPLE_FILTERS);
    expect(result.subject).toEqual([]);
    expect(result.jcr).toEqual([]);
    expect(result.cas).toEqual([]);
    expect(result.country).toEqual([]);
    expect(result.casTop).toBe(0);
    expect(result.oa).toBe(0);
    expect(result.doaj).toBe(0);
  });

  it("缺 PATRA_GATEWAY_BASE_URL 抛错", async () => {
    delete process.env.PATRA_GATEWAY_BASE_URL;
    await expect(fetchVenuesFacets(SAMPLE_FILTERS)).rejects.toThrow(/PATRA_GATEWAY_BASE_URL/);
  });
});

describe("fetchVenueDetail", () => {
  beforeEach(() => {
    process.env.PATRA_GATEWAY_BASE_URL = "http://gw.test:9528";
  });
  afterEach(() => {
    vi.unstubAllGlobals();
    delete process.env.PATRA_GATEWAY_BASE_URL;
  });

  it("非数字 id 直接返回 null，不发起请求", async () => {
    const fetchMock = vi.fn();
    vi.stubGlobal("fetch", fetchMock);
    expect(await fetchVenueDetail("abc")).toBeNull();
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it("0 与带前导零的 id 不是合法 id：直接 null，不发起请求（BE 对 0 返回 422）", async () => {
    const fetchMock = vi.fn();
    vi.stubGlobal("fetch", fetchMock);
    expect(await fetchVenueDetail("0")).toBeNull();
    expect(await fetchVenueDetail("007")).toBeNull();
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it.each([400, 422])("BE %s（id 不合法）按不存在处理 → 返回 null", async (status) => {
    vi.stubGlobal("fetch", vi.fn().mockResolvedValue(new Response(null, { status })));
    expect(await fetchVenueDetail("123")).toBeNull();
  });

  it("BE 404 → 返回 null", async () => {
    vi.stubGlobal("fetch", vi.fn().mockResolvedValue(new Response(null, { status: 404 })));
    expect(await fetchVenueDetail("123")).toBeNull();
  });

  it("成功 → 返回解析后的 VenueDetail", async () => {
    vi.stubGlobal(
      "fetch",
      vi
        .fn()
        .mockResolvedValue(
          new Response(JSON.stringify({ id: "123", title: "Nature" }), { status: 200 }),
        ),
    );
    const v = await fetchVenueDetail("123");
    expect(v).toEqual({ id: "123", title: "Nature" });
  });

  it("拼出正确的 gateway URL", async () => {
    const fetchMock = vi
      .fn()
      .mockResolvedValue(new Response(JSON.stringify({ id: "123" }), { status: 200 }));
    vi.stubGlobal("fetch", fetchMock);
    await fetchVenueDetail("123");
    expect(fetchMock).toHaveBeenCalledWith(
      "http://gw.test:9528/patra-catalog/portal/venues/123",
      expect.objectContaining({ cache: "no-store", signal: expect.any(AbortSignal) }),
    );
  });

  it("5xx → 抛错冒泡到 error boundary", async () => {
    vi.stubGlobal("fetch", vi.fn().mockResolvedValue(new Response("err", { status: 503 })));
    await expect(fetchVenueDetail("123")).rejects.toThrow(/503/);
  });

  it("缺 PATRA_GATEWAY_BASE_URL 抛错", async () => {
    delete process.env.PATRA_GATEWAY_BASE_URL;
    await expect(fetchVenueDetail("123")).rejects.toThrow(/PATRA_GATEWAY_BASE_URL/);
  });
});
