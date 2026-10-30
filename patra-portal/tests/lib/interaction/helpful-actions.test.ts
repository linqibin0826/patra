// @vitest-environment node
import { resetCookieJar } from "@tests/stubs/cookie-jar";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { setHelpful } from "@/lib/interaction/helpful-actions";

vi.mock("next/headers", () => import("@tests/stubs/cookie-jar"));

const fetchMock = vi.fn<typeof fetch>();
const CLEARED = "patra_session=; Path=/; Max-Age=0; HttpOnly; SameSite=lax";
const COMMENT = { type: "comment", id: "5" } as const;

let jar: ReturnType<typeof resetCookieJar>;

beforeEach(() => {
  vi.stubEnv("PATRA_GATEWAY_BASE_URL", "http://gw.test");
  vi.stubEnv("PATRA_SESSION_COOKIE_SECURE", "false");
  vi.stubGlobal("fetch", fetchMock);
  vi.spyOn(console, "error").mockImplementation(() => {});
  jar = resetCookieJar({ patra_session: "patra_user_abc" });
});

afterEach(() => {
  vi.unstubAllEnvs();
  vi.unstubAllGlobals();
  vi.restoreAllMocks();
  fetchMock.mockReset();
});

const call = () => fetchMock.mock.calls[0] ?? [];

describe("setHelpful", () => {
  it("点评论：PUT，回操作后的状态", async () => {
    fetchMock.mockResolvedValue(Response.json({ helpful: true, helpfulCount: 3 }));
    await expect(setHelpful(COMMENT, true)).resolves.toEqual({
      ok: true,
      result: { helpful: true, helpfulCount: 3 },
    });
    expect(String(call()[0])).toBe("http://gw.test/patra-interaction/comments/5/helpful");
    expect(call()[1]?.method).toBe("PUT");
  });

  it("取消：DELETE", async () => {
    fetchMock.mockResolvedValue(Response.json({ helpful: false, helpfulCount: 2 }));
    await expect(setHelpful(COMMENT, false)).resolves.toMatchObject({ ok: true });
    expect(call()[1]?.method).toBe("DELETE");
  });

  it("经验：/posts/{id}/helpful", async () => {
    fetchMock.mockResolvedValue(Response.json({ helpful: true, helpfulCount: 1 }));
    await setHelpful({ type: "post", id: "9" }, true);
    expect(String(call()[0])).toBe("http://gw.test/patra-interaction/posts/9/helpful");
  });

  it.each([
    [429, { traits: ["QUOTA_EXCEEDED", "DAILY_LIMIT_REACHED"] }, "daily-limit"],
    [409, { traits: ["CONFLICT", "EMAIL_UNVERIFIED"] }, "email-unverified"],
    [409, { traits: ["CONFLICT", "ACCOUNT_RESTRICTED"] }, "account-restricted"],
    [404, { traits: ["NOT_FOUND", "HELPFUL_TARGET_NOT_FOUND"] }, "gone"],
    [403, { traits: ["FORBIDDEN", "SELF_HELPFUL"] }, "self"],
    [500, {}, "unavailable"],
  ])("%i %o → %s", async (status, body, reason) => {
    fetchMock.mockResolvedValue(Response.json(body, { status }));
    await expect(setHelpful(COMMENT, true)).resolves.toMatchObject({ ok: false, reason });
  });

  it("401：signed-out，并清掉会话 Cookie", async () => {
    fetchMock.mockResolvedValue(Response.json({ code: "GW-0401" }, { status: 401 }));
    await expect(setHelpful(COMMENT, true)).resolves.toEqual({ ok: false, reason: "signed-out" });
    expect(jar.written).toEqual([CLEARED]);
  });

  it.each([
    [{ type: "publication", id: "5" }, true],
    [{ type: "comment", id: "x" }, true],
    [COMMENT, "yes"],
  ])("参数不合格 %o %o：unavailable，不请求", async (target, on) => {
    await expect(setHelpful(target as typeof COMMENT, on as boolean)).resolves.toEqual({
      ok: false,
      reason: "unavailable",
    });
    expect(fetchMock).not.toHaveBeenCalled();
  });
});
