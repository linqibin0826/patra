// @vitest-environment node
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { POST as postCitations } from "@/app/api/publications/citations/route";
import { POST as postExport } from "@/app/api/publications/export/route";

const fetchMock = vi.fn<typeof fetch>();

beforeEach(() => {
  vi.stubEnv("PATRA_GATEWAY_BASE_URL", "http://gw.test");
  vi.stubGlobal("fetch", fetchMock);
  vi.spyOn(console, "error").mockImplementation(() => {});
});

afterEach(() => {
  vi.unstubAllEnvs();
  vi.unstubAllGlobals();
  vi.restoreAllMocks();
  fetchMock.mockReset();
});

function post(path: string, body: unknown) {
  return new Request(`http://localhost:4000${path}`, {
    method: "POST",
    headers: {
      "content-type": "application/json",
      origin: "http://localhost:4000",
      host: "localhost:4000",
    },
    body: JSON.stringify(body),
  });
}

const DISPOSITION =
  "attachment; filename=\"patra-export.ris\"; filename*=UTF-8''patra-%E7%BB%84%E4%BC%9A-2026-10-24.ris";

describe("POST /api/publications/export", () => {
  it("流式转发 catalog 的文件与 Content-Disposition，不带会话", async () => {
    fetchMock.mockResolvedValue(
      new Response("TY  - JOUR", {
        headers: {
          "Content-Type": "application/x-research-info-systems; charset=UTF-8",
          "Content-Disposition": DISPOSITION,
          "Set-Cookie": "leak=1",
        },
      }),
    );
    const res = await postExport(
      post("/api/publications/export", { ids: ["11", "12"], format: "ris", name: "组会" }),
    );
    expect(res.status).toBe(200);
    expect(await res.text()).toBe("TY  - JOUR");
    expect(res.headers.get("content-disposition")).toBe(DISPOSITION);
    expect(res.headers.get("content-type")).toBe(
      "application/x-research-info-systems; charset=UTF-8",
    );
    expect(res.headers.get("set-cookie")).toBeNull();
    const [url, init] = fetchMock.mock.calls[0] ?? [];
    expect(url).toBe("http://gw.test/patra-catalog/portal/publications/export");
    expect(init?.method).toBe("POST");
    expect(init?.headers).not.toHaveProperty("Authorization");
    expect(JSON.parse(String(init?.body))).toEqual({
      ids: ["11", "12"],
      format: "ris",
      name: "组会",
    });
  });

  it.each([
    [{ ids: [], format: "ris" }],
    [{ ids: ["01"], format: "ris" }],
    [{ ids: ["1"], format: "pdf" }],
    [{ ids: Array.from({ length: 5001 }, (_, i) => String(i + 1)), format: "ris" }],
    [{ ids: ["1"], format: "ris", name: "x".repeat(101) }],
  ])("请求不合格：422，不打网关（%#）", async (body) => {
    expect((await postExport(post("/api/publications/export", body))).status).toBe(422);
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it("catalog 失败：502", async () => {
    fetchMock.mockResolvedValue(new Response("x", { status: 500 }));
    const res = await postExport(post("/api/publications/export", { ids: ["1"], format: "csv" }));
    expect(res.status).toBe(502);
  });
});

describe("POST /api/publications/citations", () => {
  it("返回 catalog 的引用条目", async () => {
    const entries = [{ label: "[1]", text: "Chen Y. Title[J]. 2019." }];
    fetchMock.mockResolvedValue(Response.json({ style: "gb-t-7714-2015", entries }));
    const res = await postCitations(
      post("/api/publications/citations", { ids: ["11"], style: "gb-t-7714-2015" }),
    );
    expect(await res.json()).toEqual({ entries });
    expect(fetchMock.mock.calls[0]?.[0]).toBe(
      "http://gw.test/patra-catalog/portal/publications/citations",
    );
  });

  it("样式不认识或超过 100 篇：422", async () => {
    expect(
      (await postCitations(post("/api/publications/citations", { ids: ["1"], style: "apa" })))
        .status,
    ).toBe(422);
    const ids = Array.from({ length: 101 }, (_, i) => String(i + 1));
    expect(
      (await postCitations(post("/api/publications/citations", { ids, style: "apa-7" }))).status,
    ).toBe(422);
  });
});

describe("POST /api/publications/export · 评审修复", () => {
  it("名称 100 个字以内但含 emoji（UTF-16 超过 100）：不拒绝，截到 100 个码元以内且不切坏字符", async () => {
    fetchMock.mockResolvedValue(new Response("x"));
    const name = "👍".repeat(60);
    const res = await postExport(
      post("/api/publications/export", { ids: ["1"], format: "ris", name }),
    );
    expect(res.status).toBe(200);
    const sent = JSON.parse(String(fetchMock.mock.calls[0]?.[1]?.body)).name as string;
    expect(sent.length).toBeLessThanOrEqual(100);
    expect(sent).toBe("👍".repeat(50));
  });
});
