// @vitest-environment node
import { FACTS } from "@tests/fixtures/analytics";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { GET } from "@/app/api/papers/[id]/pdf/route";
import { readHandlerFacts } from "@/lib/analytics/server/context";
import { trackServerEvent } from "@/lib/analytics/server/send";

vi.mock("@/lib/analytics/server/context", async (importOriginal) => ({
  ...(await importOriginal<typeof import("@/lib/analytics/server/context")>()),
  readHandlerFacts: vi.fn(),
}));
vi.mock("@/lib/analytics/server/send", () => ({ trackServerEvent: vi.fn() }));

const fetchMock = vi.fn<typeof fetch>();

beforeEach(() => {
  vi.stubEnv("PATRA_GATEWAY_BASE_URL", "http://gw.test");
  vi.stubGlobal("fetch", fetchMock);
  vi.mocked(readHandlerFacts).mockResolvedValue(FACTS);
});

afterEach(() => {
  vi.unstubAllEnvs();
  vi.unstubAllGlobals();
  fetchMock.mockReset();
  vi.mocked(trackServerEvent).mockReset();
});

const download = (id: string, referer = "http://localhost:4000/papers/7") =>
  GET(
    new Request(`http://localhost:4000/api/papers/${id}/pdf`, {
      headers: { referer, host: "localhost:4000" },
    }),
    {
      params: Promise.resolve({ id }),
    },
  );

describe("PDF Downloaded", () => {
  it("开始回传文件（200）时发一条，页面类型按来源页推断", async () => {
    fetchMock.mockResolvedValue(new Response("%PDF", { status: 200 }));
    expect((await download("7")).status).toBe(200);
    expect(trackServerEvent).toHaveBeenCalledWith(
      { name: "pdf_downloaded", properties: { article_id: "7" } },
      FACTS,
      { path: "/api/papers/7/pdf", type: "article" },
    );
  });

  it("没有 Referer 时页面类型是 other", async () => {
    fetchMock.mockResolvedValue(new Response("%PDF", { status: 200 }));
    await GET(new Request("http://localhost:4000/api/papers/7/pdf"), {
      params: Promise.resolve({ id: "7" }),
    });
    expect(vi.mocked(trackServerEvent).mock.calls[0]?.[2]).toEqual({
      path: "/api/papers/7/pdf",
      type: "other",
    });
  });

  it.each([304, 404, 502])("上游 %i：不发", async (status) => {
    fetchMock.mockResolvedValue(new Response(null, { status }));
    await download("7");
    expect(trackServerEvent).not.toHaveBeenCalled();
  });

  it("ID 不合法：不发", async () => {
    await download("abc");
    expect(trackServerEvent).not.toHaveBeenCalled();
  });
});
