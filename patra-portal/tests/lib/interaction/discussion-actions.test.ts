// @vitest-environment node
import { makeComment } from "@tests/fixtures/discussion";
import { resetCookieJar } from "@tests/stubs/cookie-jar";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { deleteComment, editComment, publishComment } from "@/lib/interaction/discussion-actions";

vi.mock("next/headers", () => import("@tests/stubs/cookie-jar"));

const fetchMock = vi.fn<typeof fetch>();
const CLEARED = "patra_session=; Path=/; Max-Age=0; HttpOnly; SameSite=lax";
const PAPER = { type: "publication", key: "11" } as const;

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

describe("publishComment", () => {
  it("POST 发表根评论，201 回完整条目", async () => {
    fetchMock.mockResolvedValue(Response.json(makeComment(), { status: 201 }));
    await expect(
      publishComment(PAPER, { body: "每组只有 47 例", parentId: null }),
    ).resolves.toEqual({
      ok: true,
      comment: makeComment(),
    });
    expect(call()[0]).toBe("http://gw.test/patra-interaction/discussions/publication/11/comments");
    expect(call()[1]?.method).toBe("POST");
    expect(JSON.parse(String(call()[1]?.body))).toEqual({ body: "每组只有 47 例", parentId: null });
  });

  it("429 TOO_FREQUENT 带等待秒数", async () => {
    fetchMock.mockResolvedValue(
      Response.json(
        { traits: ["QUOTA_EXCEEDED", "TOO_FREQUENT"] },
        { status: 429, headers: { "Retry-After": "7" } },
      ),
    );
    await expect(publishComment(PAPER, { body: "x", parentId: null })).resolves.toEqual({
      ok: false,
      reason: "too-frequent",
      retryAfterSeconds: 7,
    });
  });

  it.each([
    [429, { traits: ["QUOTA_EXCEEDED", "DAILY_LIMIT_REACHED"] }, "daily-limit"],
    [409, { traits: ["CONFLICT", "EMAIL_UNVERIFIED"] }, "email-unverified"],
    [409, { traits: ["CONFLICT", "ACCOUNT_RESTRICTED"] }, "account-restricted"],
    [409, { traits: ["CONFLICT", "REPLY_TARGET_GONE"] }, "reply-target-gone"],
    [404, { traits: ["NOT_FOUND", "SUBJECT_UNAVAILABLE"] }, "subject-gone"],
    [404, { traits: ["NOT_FOUND", "COMMENT_NOT_FOUND"] }, "comment-gone"],
    [422, { errors: [{ field: "body", code: "TOO_MANY_MENTIONS" }] }, "too-many-mentions"],
    [422, { errors: [{ field: "body", code: "TOO_LONG" }] }, "too-long"],
    [422, { errors: [{ field: "body", code: "REQUIRED" }] }, "empty"],
    [422, { errors: [{ field: "body", code: "INVALID_CHARACTER" }] }, "invalid-character"],
    [403, { traits: ["FORBIDDEN"] }, "forbidden"],
    [500, {}, "unavailable"],
  ])("%i %o → %s", async (status, body, reason) => {
    fetchMock.mockResolvedValue(Response.json(body, { status }));
    const result = await publishComment(PAPER, { body: "x", parentId: "3" });
    expect(result).toMatchObject({ ok: false, reason });
  });

  it("401：signed-out，并清掉会话 Cookie", async () => {
    fetchMock.mockResolvedValue(Response.json({ code: "GW-0401" }, { status: 401 }));
    await expect(publishComment(PAPER, { body: "x", parentId: null })).resolves.toEqual({
      ok: false,
      reason: "signed-out",
    });
    expect(jar.written).toEqual([CLEARED]);
  });

  it("参数不合格：unavailable，不请求", async () => {
    await expect(publishComment(PAPER, { body: "x", parentId: "x" })).resolves.toEqual({
      ok: false,
      reason: "unavailable",
    });
    await expect(
      publishComment(PAPER, { body: 1 as unknown as string, parentId: null }),
    ).resolves.toEqual({ ok: false, reason: "unavailable" });
    await expect(
      publishComment({ type: "journal", key: "1" } as never, { body: "x", parentId: null }),
    ).resolves.toEqual({ ok: false, reason: "unavailable" });
    expect(fetchMock).not.toHaveBeenCalled();
  });
});

describe("editComment / deleteComment", () => {
  it("PUT /comments/{id}", async () => {
    fetchMock.mockResolvedValue(Response.json(makeComment({ edited: true })));
    await expect(editComment("5", "新的")).resolves.toEqual({
      ok: true,
      comment: makeComment({ edited: true }),
    });
    expect(call()[0]).toBe("http://gw.test/patra-interaction/comments/5");
    expect(call()[1]?.method).toBe("PUT");
    expect(JSON.parse(String(call()[1]?.body))).toEqual({ body: "新的" });
  });

  it("DELETE /comments/{id} 回占位与去掉的 ID", async () => {
    fetchMock.mockResolvedValue(Response.json({ placeholder: false, removedIds: ["5", "3"] }));
    await expect(deleteComment("5")).resolves.toEqual({
      ok: true,
      placeholder: false,
      removedIds: ["5", "3"],
    });
    expect(call()[1]?.method).toBe("DELETE");
  });

  it("ID 不合格不请求", async () => {
    await expect(deleteComment("0")).resolves.toEqual({ ok: false, reason: "unavailable" });
    await expect(editComment("x", "a")).resolves.toEqual({ ok: false, reason: "unavailable" });
    expect(fetchMock).not.toHaveBeenCalled();
  });
});
