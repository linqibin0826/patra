// @vitest-environment node
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { GET as getFigure } from "@/app/api/papers/[id]/figures/[file]/route";
import { GET as getPdf } from "@/app/api/papers/[id]/pdf/route";

const fetchMock = vi.fn<typeof fetch>();

beforeEach(() => {
  vi.stubEnv("PATRA_GATEWAY_BASE_URL", "http://gw.test:9528");
  vi.stubGlobal("fetch", fetchMock);
});

afterEach(() => {
  vi.unstubAllEnvs();
  vi.unstubAllGlobals();
  fetchMock.mockReset();
});

const PDF_HEADERS = {
  "Content-Type": "application/pdf",
  "Content-Length": "5",
  ETag: '"fdbf75f98b42f9504029adfadccea386"',
  "Cache-Control": "public, max-age=86400",
  "Content-Disposition": 'attachment; filename="PMC12833634.pdf"',
  "Set-Cookie": "leak=1",
};

function pdfRequest(id: string, headers: Record<string, string> = {}) {
  return getPdf(new Request(`http://localhost:4000/api/papers/${id}/pdf`, { headers }), {
    params: Promise.resolve({ id }),
  });
}

function figureRequest(id: string, file: string) {
  return getFigure(new Request(`http://localhost:4000/api/papers/${id}/figures/${file}`), {
    params: Promise.resolve({ id, file }),
  });
}

describe("GET /api/papers/[id]/pdf", () => {
  it("转发到 catalog 的 PDF 端点，响应体与下载相关的头原样透传", async () => {
    fetchMock.mockResolvedValue(new Response("%PDF-", { status: 200, headers: PDF_HEADERS }));

    const res = await pdfRequest("352303052032763159");

    expect(fetchMock.mock.calls[0]?.[0]).toBe(
      "http://gw.test:9528/patra-catalog/portal/publications/352303052032763159/fulltext/pdf",
    );
    expect(res.status).toBe(200);
    expect(await res.text()).toBe("%PDF-");
    expect(res.headers.get("Content-Type")).toBe("application/pdf");
    expect(res.headers.get("Content-Length")).toBe("5");
    expect(res.headers.get("ETag")).toBe('"fdbf75f98b42f9504029adfadccea386"');
    expect(res.headers.get("Cache-Control")).toBe("public, max-age=86400");
    expect(res.headers.get("Content-Disposition")).toBe('attachment; filename="PMC12833634.pdf"');
  });

  it("不透传白名单外的头", async () => {
    fetchMock.mockResolvedValue(new Response("%PDF-", { status: 200, headers: PDF_HEADERS }));
    const res = await pdfRequest("1");
    expect(res.headers.get("Set-Cookie")).toBeNull();
  });

  it("浏览器带来的 If-None-Match 转给网关，网关回 304 时门户也回 304", async () => {
    fetchMock.mockResolvedValue(
      new Response(null, { status: 304, headers: { ETag: PDF_HEADERS.ETag } }),
    );

    const res = await pdfRequest("1", { "If-None-Match": PDF_HEADERS.ETag });

    expect(new Headers(fetchMock.mock.calls[0]?.[1]?.headers).get("If-None-Match")).toBe(
      PDF_HEADERS.ETag,
    );
    expect(res.status).toBe(304);
    expect(res.headers.get("ETag")).toBe(PDF_HEADERS.ETag);
  });

  it("网关 404（不可下载）→ 404", async () => {
    fetchMock.mockResolvedValue(new Response('{"code":"CATALOG-0404"}', { status: 404 }));
    const res = await pdfRequest("1");
    expect(res.status).toBe(404);
  });

  it("网关 5xx 或连不上 → 502", async () => {
    fetchMock.mockResolvedValueOnce(new Response("x", { status: 503 }));
    expect((await pdfRequest("1")).status).toBe(502);

    fetchMock.mockRejectedValueOnce(new TypeError("fetch failed"));
    expect((await pdfRequest("1")).status).toBe(502);
  });

  it("非法 id 直接 404，不打网关", async () => {
    expect((await pdfRequest("0")).status).toBe(404);
    expect((await pdfRequest("abc")).status).toBe(404);
    expect(fetchMock).not.toHaveBeenCalled();
  });
});

describe("GET /api/papers/[id]/figures/[file]", () => {
  it("转发到 catalog 的图片端点并透传类型与缓存头", async () => {
    fetchMock.mockResolvedValue(
      new Response("RIFF", {
        status: 200,
        headers: {
          "Content-Type": "image/webp",
          "Cache-Control": "public, max-age=604800, immutable",
        },
      }),
    );

    const res = await figureRequest("1", "bmjopen-16-9-g001.webp");

    expect(fetchMock.mock.calls[0]?.[0]).toBe(
      "http://gw.test:9528/patra-catalog/portal/publications/1/fulltext/figures/bmjopen-16-9-g001.webp",
    );
    expect(res.status).toBe(200);
    expect(res.headers.get("Content-Type")).toBe("image/webp");
    expect(res.headers.get("Cache-Control")).toBe("public, max-age=604800, immutable");
  });

  it("文件名只认字母、数字、点、下划线、连字符：路径穿越与编码字符 404，不打网关", async () => {
    for (const file of ["..", ".", "a%2Fb.png", "a b.png", "..%2F..%2Fetc"]) {
      expect((await figureRequest("1", file)).status, file).toBe(404);
    }
    expect(fetchMock).not.toHaveBeenCalled();
  });
});
