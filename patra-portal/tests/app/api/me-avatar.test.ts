// @vitest-environment node
import { resetCookieJar } from "@tests/stubs/cookie-jar";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { DELETE, POST } from "@/app/api/me/avatar/route";

vi.mock("next/headers", () => import("@tests/stubs/cookie-jar"));

const fetchMock = vi.fn<typeof fetch>();
const SAME_ORIGIN = { origin: "http://mini:4000", host: "mini:4000" };
const CLEARED = "patra_session=; Path=/; Max-Age=0; HttpOnly; SameSite=lax";

beforeEach(() => {
  vi.stubEnv("PATRA_GATEWAY_BASE_URL", "http://gw.test");
  vi.stubEnv("PATRA_SESSION_COOKIE_SECURE", "false");
  vi.stubGlobal("fetch", fetchMock);
  vi.spyOn(console, "error").mockImplementation(() => {});
  resetCookieJar({ patra_session: "patra_user_abc" }, { "x-forwarded-for": "10.0.0.3" });
});

afterEach(() => {
  vi.unstubAllEnvs();
  vi.unstubAllGlobals();
  vi.restoreAllMocks();
  fetchMock.mockReset();
});

function upload(headers: Record<string, string> = SAME_ORIGIN, withFile = true) {
  const form = new FormData();
  if (withFile) {
    form.append("file", new Blob([new Uint8Array([1, 2, 3])], { type: "image/png" }), "crop.png");
  }
  return POST(
    new Request("http://localhost:4000/api/me/avatar", { method: "POST", headers, body: form }),
  );
}

const remove = (headers: Record<string, string> = SAME_ORIGIN) =>
  DELETE(new Request("http://localhost:4000/api/me/avatar", { method: "DELETE", headers }));

const problem = (status: number, extra: Record<string, unknown> = {}) =>
  fetchMock.mockImplementation(async () =>
    Response.json({ status, code: `IDN-0${status}`, ...extra }, { status }),
  );

describe("POST /api/me/avatar", () => {
  it.each([[{ host: "mini:4000" }], [{ origin: "http://evil.example", host: "mini:4000" }]])(
    "不是同源（%j）：403，不打网关",
    async (headers) => {
      const res = await upload(headers);
      expect(res.status).toBe(403);
      expect(fetchMock).not.toHaveBeenCalled();
    },
  );

  it("没有会话 Cookie：401 signed-out", async () => {
    resetCookieJar();
    const res = await upload();
    expect(res.status).toBe(401);
    await expect(res.json()).resolves.toEqual({ ok: false, reason: "signed-out" });
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it("Content-Length 超过 6 MB：413 too-large，不读体、不打网关", async () => {
    const res = await POST(
      new Request("http://localhost:4000/api/me/avatar", {
        method: "POST",
        headers: { ...SAME_ORIGIN, "content-length": "6291457" },
      }),
    );
    expect(res.status).toBe(413);
    await expect(res.json()).resolves.toEqual({ ok: false, reason: "too-large" });
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it("没有 file：422 rejected REQUIRED，不打网关", async () => {
    const res = await upload(SAME_ORIGIN, false);
    expect(res.status).toBe(422);
    await expect(res.json()).resolves.toEqual({ ok: false, reason: "rejected", code: "REQUIRED" });
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it("成功：multipart 转发 file（文件名、类型保留），带令牌与浏览器来源，回 avatarUrl", async () => {
    fetchMock.mockResolvedValue(
      Response.json({ avatarUrl: "/users/chen-yu/avatar?v=0123456789abcdef" }),
    );
    const res = await upload();
    expect(res.status).toBe(200);
    expect(res.headers.get("cache-control")).toBe("private, no-store");
    await expect(res.json()).resolves.toEqual({
      ok: true,
      avatarUrl: "/users/chen-yu/avatar?v=0123456789abcdef",
    });
    const [url, init] = fetchMock.mock.calls[0] ?? [];
    expect(url).toBe("http://gw.test/patra-identity/me/avatar");
    expect(init?.method).toBe("POST");
    expect(init?.headers).toEqual({
      Authorization: "Bearer patra_user_abc",
      "X-Forwarded-For": "10.0.0.3",
    });
    const form = init?.body;
    expect(form).toBeInstanceOf(FormData);
    const file = (form as FormData).get("file") as File;
    expect(file.name).toBe("crop.png");
    expect(file.type).toBe("image/png");
  });

  it.each([
    [413, {}, 413, { ok: false, reason: "too-large" }],
    [
      422,
      { errors: [{ field: "file", code: "TOO_SMALL" }] },
      422,
      { ok: false, reason: "rejected", code: "TOO_SMALL" },
    ],
    [
      409,
      { traits: ["CONFLICT", "PENDING_DELETION"] },
      409,
      { ok: false, reason: "pending-deletion" },
    ],
    [503, {}, 503, { ok: false, reason: "unavailable" }],
  ])("网关 %i：%i %j", async (status, extra, expectedStatus, body) => {
    problem(status, extra);
    const res = await upload();
    expect(res.status).toBe(expectedStatus);
    await expect(res.json()).resolves.toEqual(body);
  });

  it("网关 401：signed-out，并清掉会话 Cookie", async () => {
    problem(401);
    const jar = resetCookieJar({ patra_session: "patra_user_abc" });
    const res = await upload();
    expect(res.status).toBe(401);
    expect(jar.written).toEqual([CLEARED]);
  });
});

describe("DELETE /api/me/avatar", () => {
  it("不是同源：403", async () => {
    expect((await remove({ host: "mini:4000" })).status).toBe(403);
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it("网关 204：200 { ok, avatarUrl: null }", async () => {
    fetchMock.mockResolvedValue(new Response(null, { status: 204 }));
    const res = await remove();
    expect(res.status).toBe(200);
    await expect(res.json()).resolves.toEqual({ ok: true, avatarUrl: null });
    expect(fetchMock.mock.calls[0]?.[1]?.method).toBe("DELETE");
  });

  it("网关 409 冷静期：pending-deletion", async () => {
    problem(409, { traits: ["PENDING_DELETION"] });
    await expect((await remove()).json()).resolves.toEqual({
      ok: false,
      reason: "pending-deletion",
    });
  });
});
