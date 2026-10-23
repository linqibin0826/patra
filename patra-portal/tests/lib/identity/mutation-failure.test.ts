// @vitest-environment node
import { resetCookieJar } from "@tests/stubs/cookie-jar";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import {
  isReauthRequired,
  settleFailure,
  unexpectedProblem,
} from "@/lib/identity/mutation-failure";
import { parseProblem } from "@/lib/identity/problem";

vi.mock("next/headers", () => import("@tests/stubs/cookie-jar"));

const CLEARED = "patra_session=; Path=/; Max-Age=0; HttpOnly; SameSite=lax";

const problemReply = (status: number, body: Record<string, unknown>) =>
  ({ kind: "problem", problem: parseProblem(Response.json(body, { status }), body) }) as const;

beforeEach(() => {
  vi.stubEnv("PATRA_SESSION_COOKIE_SECURE", "false");
  vi.spyOn(console, "error").mockImplementation(() => {});
});

afterEach(() => {
  vi.unstubAllEnvs();
  vi.restoreAllMocks();
});

describe("settleFailure", () => {
  it("没有会话：signed-out，不动 Cookie", async () => {
    const jar = resetCookieJar();
    await expect(settleFailure({ kind: "no-session" })).resolves.toEqual({
      failure: { ok: false, reason: "signed-out" },
    });
    expect(jar.written).toEqual([]);
  });

  it("请求抛错：unavailable", async () => {
    resetCookieJar();
    await expect(settleFailure({ kind: "error" })).resolves.toEqual({
      failure: { ok: false, reason: "unavailable" },
    });
  });

  it("会话失效（401）：signed-out，清掉会话 Cookie", async () => {
    const jar = resetCookieJar({ patra_session: "patra_user_abc" });
    await expect(settleFailure(problemReply(401, { code: "GW-0401" }))).resolves.toEqual({
      failure: { ok: false, reason: "signed-out" },
    });
    expect(jar.written).toEqual([CLEARED]);
  });

  it("密码不对的 401 不是会话失效：交回调用方，Cookie 不动", async () => {
    const jar = resetCookieJar({ patra_session: "patra_user_abc" });
    const result = await settleFailure(
      problemReply(401, { code: "IDN-0401", traits: ["PASSWORD_MISMATCH"] }),
    );
    expect(result).toMatchObject({ problem: { status: 401 } });
    expect(jar.written).toEqual([]);
  });

  it("冷静期（409 + PENDING_DELETION）：pending-deletion", async () => {
    resetCookieJar();
    await expect(
      settleFailure(problemReply(409, { code: "IDN-0409", traits: ["PENDING_DELETION"] })),
    ).resolves.toEqual({ failure: { ok: false, reason: "pending-deletion" } });
  });

  it("其余问题（如只有 CONFLICT 的 409）交回调用方", async () => {
    resetCookieJar();
    const result = await settleFailure(
      problemReply(409, { code: "IDN-0409", traits: ["CONFLICT"] }),
    );
    expect(result).toMatchObject({ problem: { status: 409, code: "IDN-0409" } });
  });
});

describe("unexpectedProblem", () => {
  it("记日志（只有接口、状态码与 code），返回 unavailable", () => {
    const { problem } = problemReply(500, { code: "IDN-0500", detail: "secret" });
    expect(unexpectedProblem("/patra-identity/me/profile", problem)).toEqual({
      ok: false,
      reason: "unavailable",
    });
    expect(console.error).toHaveBeenCalledWith(
      "[auth] /patra-identity/me/profile 返回了意外的结果",
      { status: 500, code: "IDN-0500" },
    );
  });
});

describe("isReauthRequired", () => {
  it("403 且 traits 含 REAUTH_REQUIRED", () => {
    const { problem } = problemReply(403, {
      code: "IDN-0403",
      traits: ["FORBIDDEN", "REAUTH_REQUIRED"],
    });
    expect(isReauthRequired(problem)).toBe(true);
  });

  it.each([
    [403, ["FORBIDDEN"]],
    [401, ["REAUTH_REQUIRED"]],
  ])("%i %j 不算", (status, traits) => {
    const { problem } = problemReply(status, { traits });
    expect(isReauthRequired(problem)).toBe(false);
  });
});
