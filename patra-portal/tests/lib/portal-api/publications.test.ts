import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { fetchFeed, fetchPublicationDetail } from "@/lib/portal-api/publications";
import type { PageResult, Paper } from "@/types/portal";

const EMPTY: PageResult<Paper> = { page: 1, pageSize: 14, total: 0, totalPages: 0, items: [] };

describe("fetchFeed", () => {
  beforeEach(() => {
    process.env.PATRA_GATEWAY_BASE_URL = "http://gw.test:9528";
  });
  afterEach(() => {
    vi.unstubAllGlobals();
    delete process.env.PATRA_GATEWAY_BASE_URL;
  });

  it("拼出正确的 gateway URL 并解析 JSON", async () => {
    const fetchMock = vi
      .fn()
      .mockResolvedValue(new Response(JSON.stringify(EMPTY), { status: 200 }));
    vi.stubGlobal("fetch", fetchMock);

    const result = await fetchFeed("cited", 2, 20);

    expect(fetchMock).toHaveBeenCalledWith(
      "http://gw.test:9528/patra-catalog/portal/publications?tab=cited&page=2&pageSize=20",
      expect.objectContaining({
        cache: "no-store",
        signal: expect.any(AbortSignal),
      }),
    );
    expect(result).toEqual(EMPTY);
  });

  it("非 2xx 抛错", async () => {
    vi.stubGlobal("fetch", vi.fn().mockResolvedValue(new Response("err", { status: 502 })));
    await expect(fetchFeed("recent")).rejects.toThrow(/502/);
  });

  it("缺 PATRA_GATEWAY_BASE_URL 抛错", async () => {
    delete process.env.PATRA_GATEWAY_BASE_URL;
    await expect(fetchFeed("recent")).rejects.toThrow(/PATRA_GATEWAY_BASE_URL/);
  });
});

describe("fetchPublicationDetail", () => {
  beforeEach(() => {
    process.env.PATRA_GATEWAY_BASE_URL = "http://gw.test:9528";
  });
  afterEach(() => {
    vi.unstubAllGlobals();
    delete process.env.PATRA_GATEWAY_BASE_URL;
  });

  it("0 与带前导零的 id 不是合法 id：直接 null，不发起请求（BE 对 0 返回 422）", async () => {
    const fetchMock = vi.fn();
    vi.stubGlobal("fetch", fetchMock);
    expect(await fetchPublicationDetail("0")).toBeNull();
    expect(await fetchPublicationDetail("007")).toBeNull();
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it.each([400, 422])("BE %s（id 不合法）按不存在处理 → 返回 null", async (status) => {
    vi.stubGlobal("fetch", vi.fn().mockResolvedValue(new Response(null, { status })));
    expect(await fetchPublicationDetail("123")).toBeNull();
  });

  it("拼出正确的 by-id URL 并解析 JSON", async () => {
    const detail = { id: "319041872872550658", title: "T" };
    const fetchMock = vi
      .fn()
      .mockResolvedValue(new Response(JSON.stringify(detail), { status: 200 }));
    vi.stubGlobal("fetch", fetchMock);

    const result = await fetchPublicationDetail("319041872872550658");

    expect(fetchMock).toHaveBeenCalledWith(
      "http://gw.test:9528/patra-catalog/portal/publications/319041872872550658",
      expect.objectContaining({ cache: "no-store", signal: expect.any(AbortSignal) }),
    );
    expect(result).toEqual(detail);
  });

  it("非数字 id 直接返回 null，不发请求", async () => {
    const fetchMock = vi.fn();
    vi.stubGlobal("fetch", fetchMock);
    expect(await fetchPublicationDetail("not-a-real-id")).toBeNull();
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it("404 返回 null", async () => {
    vi.stubGlobal("fetch", vi.fn().mockResolvedValue(new Response("", { status: 404 })));
    expect(await fetchPublicationDetail("999")).toBeNull();
  });

  it("其他非 2xx 抛错", async () => {
    vi.stubGlobal("fetch", vi.fn().mockResolvedValue(new Response("err", { status: 500 })));
    await expect(fetchPublicationDetail("1")).rejects.toThrow(/500/);
  });

  it("缺 PATRA_GATEWAY_BASE_URL 抛错（数字 id）", async () => {
    delete process.env.PATRA_GATEWAY_BASE_URL;
    await expect(fetchPublicationDetail("1")).rejects.toThrow(/PATRA_GATEWAY_BASE_URL/);
  });
});
