// @vitest-environment node
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { fetchMeshSuggestions, fetchOrganizationSuggestions } from "@/lib/portal-api/suggest";

const fetchMock = vi.fn<typeof fetch>();

beforeEach(() => {
  vi.stubEnv("PATRA_GATEWAY_BASE_URL", "http://gw.test");
  vi.stubGlobal("fetch", fetchMock);
});

afterEach(() => {
  vi.unstubAllEnvs();
  vi.unstubAllGlobals();
  fetchMock.mockReset();
});

describe("fetchOrganizationSuggestions", () => {
  it("请求 catalog 的机构建议，limit 10，国家码在服务端换成中文名", async () => {
    fetchMock.mockResolvedValue(
      Response.json({
        items: [
          {
            rorId: "04jztag35",
            displayName: "Peking Union Medical College Hospital",
            zhName: "北京协和医院",
            matchedName: "协和",
            countryCode: "CN",
            cityName: "Beijing",
            types: ["healthcare"],
          },
          {
            rorId: "02xxx0000",
            displayName: "Somewhere",
            zhName: null,
            matchedName: null,
            countryCode: null,
            cityName: null,
            types: [],
          },
        ],
      }),
    );
    const items = await fetchOrganizationSuggestions("北京 协和");
    expect(fetchMock.mock.calls[0]?.[0]).toBe(
      "http://gw.test/patra-catalog/portal/organizations/suggest?q=%E5%8C%97%E4%BA%AC+%E5%8D%8F%E5%92%8C&limit=10",
    );
    expect(fetchMock.mock.calls[0]?.[1]?.headers).toEqual({});
    expect(items).toEqual([
      {
        rorId: "04jztag35",
        displayName: "Peking Union Medical College Hospital",
        zhName: "北京协和医院",
        matchedName: "协和",
        cityName: "Beijing",
        countryName: "中国",
        types: ["healthcare"],
      },
      {
        rorId: "02xxx0000",
        displayName: "Somewhere",
        zhName: null,
        matchedName: null,
        cityName: null,
        countryName: null,
        types: [],
      },
    ]);
  });

  it("非 2xx 抛错", async () => {
    fetchMock.mockResolvedValue(new Response(null, { status: 500 }));
    await expect(fetchOrganizationSuggestions("协和")).rejects.toThrow();
  });
});

describe("fetchMeshSuggestions", () => {
  it("请求 catalog 的 MeSH 建议", async () => {
    const items = [
      { ui: "D009369", name: "Neoplasms", matchedTerm: "Cancer", treeNumbers: ["C04"] },
    ];
    fetchMock.mockResolvedValue(Response.json({ items }));
    await expect(fetchMeshSuggestions("cancer")).resolves.toEqual(items);
    expect(fetchMock.mock.calls[0]?.[0]).toBe(
      "http://gw.test/patra-catalog/portal/mesh/suggest?q=cancer&limit=10",
    );
  });
});
