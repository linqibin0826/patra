// @vitest-environment node
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { GET } from "@/app/api/users/[username]/avatar/route";

vi.mock("next/headers", () => ({
  headers: async () => new Headers({ "x-forwarded-for": "10.0.0.6", "user-agent": "UA/6" }),
}));

const fetchMock = vi.fn<typeof fetch>();
const V = "0123456789abcdef";

beforeEach(() => {
  vi.stubEnv("PATRA_GATEWAY_BASE_URL", "http://gw.test");
  vi.stubGlobal("fetch", fetchMock);
});

afterEach(() => {
  vi.unstubAllEnvs();
  vi.unstubAllGlobals();
  fetchMock.mockReset();
});

function avatarRequest(username: string, query: string, headers: Record<string, string> = {}) {
  return GET(
    new Request(`http://localhost:4000/api/users/${username}/avatar?${query}`, { headers }),
    {
      params: Promise.resolve({ username }),
    },
  );
}

const AVATAR_HEADERS = {
  "Content-Type": "image/webp",
  "Content-Length": "4",
  ETag: `"${V}-128"`,
  "Cache-Control": "public, max-age=31536000, immutable",
  "Set-Cookie": "leak=1",
  "X-Internal": "1",
};

describe("GET /api/users/[username]/avatar", () => {
  it("转发到 identity，带 If-None-Match 与浏览器来源；200 只透传四个头", async () => {
    fetchMock.mockResolvedValue(new Response("RIFF", { status: 200, headers: AVATAR_HEADERS }));
    const res = await avatarRequest("chen-yu", `v=${V}&size=128`, { "If-None-Match": '"x"' });
    const [url, init] = fetchMock.mock.calls[0] ?? [];
    expect(url).toBe(`http://gw.test/patra-identity/users/chen-yu/avatar?size=128&v=${V}`);
    expect(init?.headers).toEqual({
      "X-Forwarded-For": "10.0.0.6",
      "User-Agent": "UA/6",
      "If-None-Match": '"x"',
    });
    expect(res.status).toBe(200);
    expect(await res.text()).toBe("RIFF");
    expect(Object.fromEntries(res.headers)).toEqual({
      "content-type": "image/webp",
      "content-length": "4",
      etag: `"${V}-128"`,
      "cache-control": "public, max-age=31536000, immutable",
    });
  });

  it("304：无响应体，透传 ETag 与缓存头", async () => {
    fetchMock.mockResolvedValue(new Response(null, { status: 304, headers: AVATAR_HEADERS }));
    const res = await avatarRequest("chen-yu", `v=${V}&size=512`);
    expect(res.status).toBe(304);
    expect(res.headers.get("etag")).toBe(`"${V}-128"`);
  });

  it.each([
    ["chen-yu", `v=${V}&size=256`],
    ["chen-yu", "v=xyz&size=128"],
    ["chen-yu", "size=128"],
    ["chen-yu", `v=${V}`],
    ["a.b", `v=${V}&size=128`],
    ["..", `v=${V}&size=128`],
  ])("参数不合格（%s?%s）：404，不打网关", async (username, query) => {
    const res = await avatarRequest(username, query);
    expect(res.status).toBe(404);
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it("网关 404：404", async () => {
    fetchMock.mockResolvedValue(new Response(null, { status: 404 }));
    expect((await avatarRequest("chen-yu", `v=${V}&size=128`)).status).toBe(404);
  });

  it("网关 503 或连不上：502", async () => {
    fetchMock.mockResolvedValueOnce(new Response(null, { status: 503 }));
    expect((await avatarRequest("chen-yu", `v=${V}&size=128`)).status).toBe(502);
    fetchMock.mockRejectedValueOnce(new TypeError("fetch failed"));
    expect((await avatarRequest("chen-yu", `v=${V}&size=128`)).status).toBe(502);
  });
});
