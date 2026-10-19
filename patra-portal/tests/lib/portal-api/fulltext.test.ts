import { makeFullText } from "@tests/fixtures/fulltext";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { fetchPublicationFullText } from "@/lib/portal-api/fulltext";

describe("fetchPublicationFullText", () => {
  beforeEach(() => {
    process.env.PATRA_GATEWAY_BASE_URL = "http://gw.test:9528";
  });
  afterEach(() => {
    vi.unstubAllGlobals();
    delete process.env.PATRA_GATEWAY_BASE_URL;
  });

  it("请求全文端点并返回校验后的结构", async () => {
    const body = makeFullText();
    const fetchMock = vi.fn().mockResolvedValue(new Response(JSON.stringify(body)));
    vi.stubGlobal("fetch", fetchMock);

    const result = await fetchPublicationFullText("352303012816019646");

    expect(fetchMock).toHaveBeenCalledWith(
      "http://gw.test:9528/patra-catalog/portal/publications/352303012816019646/fulltext",
      expect.objectContaining({ cache: "no-store" }),
    );
    expect(result).toEqual(body);
  });

  it("404（既读不了也下不了 PDF）→ null", async () => {
    vi.stubGlobal("fetch", vi.fn().mockResolvedValue(new Response("{}", { status: 404 })));
    expect(await fetchPublicationFullText("1")).toBeNull();
  });

  it("非法 id 不发请求，直接 null", async () => {
    const fetchMock = vi.fn();
    vi.stubGlobal("fetch", fetchMock);
    expect(await fetchPublicationFullText("../1")).toBeNull();
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it("5xx 抛错（页面显示「全文没能加载」）", async () => {
    vi.stubGlobal("fetch", vi.fn().mockResolvedValue(new Response("x", { status: 503 })));
    await expect(fetchPublicationFullText("1")).rejects.toThrow(/503/);
  });

  it("结构对不上白名单时抛错，不把认不出的内容交给渲染", async () => {
    const bad = { ...makeFullText(), body: { blocks: [{ kind: "html" }] } };
    vi.stubGlobal("fetch", vi.fn().mockResolvedValue(new Response(JSON.stringify(bad))));
    await expect(fetchPublicationFullText("1")).rejects.toThrow(/结构/);
  });
});
