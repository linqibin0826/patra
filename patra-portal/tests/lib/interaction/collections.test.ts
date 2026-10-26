// @vitest-environment node
import { makePublicCollection, makePublicItem } from "@tests/fixtures/interaction";
import { makePaper } from "@tests/fixtures/paper";
import { resetCookieJar } from "@tests/stubs/cookie-jar";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import {
  fetchPublicCollection,
  fetchPublicCollectionPage,
  fetchUserPublicCollections,
} from "@/lib/interaction/collections";

vi.mock("next/headers", () => import("@tests/stubs/cookie-jar"));

const fetchMock = vi.fn<typeof fetch>();

beforeEach(() => {
  vi.stubEnv("PATRA_GATEWAY_BASE_URL", "http://gw.test");
  vi.stubEnv("PATRA_SESSION_COOKIE_SECURE", "false");
  vi.stubGlobal("fetch", fetchMock);
  vi.spyOn(console, "error").mockImplementation(() => {});
  resetCookieJar();
});

afterEach(() => {
  vi.unstubAllEnvs();
  vi.unstubAllGlobals();
  vi.restoreAllMocks();
  fetchMock.mockReset();
});

describe("fetchPublicCollection", () => {
  it("匿名也请求，不带令牌", async () => {
    fetchMock.mockResolvedValue(Response.json(makePublicCollection()));
    await expect(fetchPublicCollection("9")).resolves.toEqual({
      kind: "ok",
      data: makePublicCollection(),
    });
    const [url, init] = fetchMock.mock.calls[0] ?? [];
    expect(url).toBe("http://gw.test/patra-interaction/collections/9");
    expect(init?.headers).not.toHaveProperty("Authorization");
  });

  it("访客已登录：带会话", async () => {
    resetCookieJar({ patra_session: "patra_user_abc" });
    fetchMock.mockResolvedValue(Response.json(makePublicCollection()));
    await fetchPublicCollection("9");
    expect(fetchMock.mock.calls[0]?.[1]?.headers).toMatchObject({
      Authorization: "Bearer patra_user_abc",
    });
  });

  it("不合格的 ID：not-found，不打网关", async () => {
    await expect(fetchPublicCollection("01")).resolves.toEqual({ kind: "not-found" });
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it("404：not-found；503（identity 不可用）：unavailable，不能当 404", async () => {
    fetchMock.mockResolvedValueOnce(Response.json({}, { status: 404 }));
    await expect(fetchPublicCollection("9")).resolves.toEqual({ kind: "not-found" });
    fetchMock.mockResolvedValueOnce(
      Response.json({ traits: ["DEP_UNAVAILABLE"] }, { status: 503 }),
    );
    await expect(fetchPublicCollection("9")).resolves.toEqual({ kind: "unavailable" });
  });
});

describe("fetchPublicCollectionPage", () => {
  it("条目 + 卡片", async () => {
    const item = makePublicItem({ key: "11" });
    const paper = makePaper({ id: "11" });
    fetchMock.mockImplementation(async (url) =>
      String(url).includes("/patra-catalog/")
        ? Response.json({ items: [paper] })
        : Response.json({ page: 1, pageSize: 50, total: 1, totalPages: 1, items: [item] }),
    );
    const read = await fetchPublicCollectionPage("9", "journal", 1);
    expect(fetchMock.mock.calls[0]?.[0]).toBe(
      "http://gw.test/patra-interaction/collections/9/items?sort=journal&page=1&size=50",
    );
    expect(read).toMatchObject({ kind: "ok", data: { items: [{ item, paper }] } });
  });
});

describe("fetchUserPublicCollections", () => {
  it("按用户 ID 取，登录可选", async () => {
    fetchMock.mockResolvedValue(Response.json({ collections: [] }));
    await expect(fetchUserPublicCollections("352303128713027814")).resolves.toEqual({
      kind: "ok",
      data: [],
    });
    expect(fetchMock.mock.calls[0]?.[0]).toBe(
      "http://gw.test/patra-interaction/users/352303128713027814/collections",
    );
  });
});
