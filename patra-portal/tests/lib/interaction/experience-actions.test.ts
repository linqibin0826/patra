// @vitest-environment node
import { makeDetail } from "@tests/fixtures/experience";
import { resetCookieJar } from "@tests/stubs/cookie-jar";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import {
  deleteExperience,
  editExperience,
  publishExperience,
} from "@/lib/interaction/experience-actions";
import type { ExperienceRequest } from "@/lib/interaction/experience-schema";

vi.mock("next/headers", () => import("@tests/stubs/cookie-jar"));

const fetchMock = vi.fn<typeof fetch>();
const CLEARED = "patra_session=; Path=/; Max-Age=0; HttpOnly; SameSite=lax";

const REQUEST: ExperienceRequest = {
  submittedOn: "2026-03-02",
  firstDecisionOn: "2026-04-13",
  firstDecision: "MAJOR_REVISION",
  finalOutcome: "ACCEPTED",
  finalDecisionOn: null,
  articleType: "ORIGINAL_RESEARCH",
  reviewRounds: null,
  reviewQuality: null,
  editorCommunication: null,
  apcPaid: null,
  previouslyRejected: null,
  body: null,
  anonymous: false,
};

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
const problem = (status: number, body: object, headers: Record<string, string> = {}) =>
  Response.json(body, { status, headers });

describe("publishExperience", () => {
  it("POST 到期刊下，201 回详情", async () => {
    fetchMock.mockResolvedValue(Response.json(makeDetail(), { status: 201 }));
    await expect(publishExperience("100", REQUEST)).resolves.toEqual({
      ok: true,
      detail: makeDetail(),
    });
    expect(call()[0]).toBe("http://gw.test/patra-interaction/journals/100/posts");
    expect(call()[1]?.method).toBe("POST");
    expect(JSON.parse(String(call()[1]?.body))).toEqual(REQUEST);
  });

  it("422 带字段错误 → invalid-fields", async () => {
    fetchMock.mockResolvedValue(
      problem(422, { errors: [{ field: "finalOutcome", code: "INCONSISTENT" }] }),
    );
    await expect(publishExperience("100", REQUEST)).resolves.toEqual({
      ok: false,
      reason: "invalid-fields",
      fieldErrors: [{ field: "finalOutcome", code: "INCONSISTENT" }],
    });
  });

  it("409 DUPLICATE_SUBMISSION 透传已有经验 ID", async () => {
    fetchMock.mockResolvedValue(
      problem(409, {
        traits: ["CONFLICT", "DUPLICATE_SUBMISSION"],
        existingPostId: "77",
        errors: [{ field: "submittedOn", code: "DUPLICATE" }],
      }),
    );
    await expect(publishExperience("100", REQUEST)).resolves.toEqual({
      ok: false,
      reason: "duplicate",
      existingPostId: "77",
    });
  });

  it("429 每日上限带等待秒数", async () => {
    fetchMock.mockResolvedValue(
      problem(
        429,
        { traits: ["QUOTA_EXCEEDED", "DAILY_LIMIT_REACHED"] },
        { "Retry-After": "3600" },
      ),
    );
    await expect(publishExperience("100", REQUEST)).resolves.toEqual({
      ok: false,
      reason: "daily-limit",
      retryAfterSeconds: 3600,
    });
  });

  it.each([
    [409, { traits: ["EMAIL_UNVERIFIED"] }, "email-unverified"],
    [409, { traits: ["ACCOUNT_RESTRICTED"] }, "account-restricted"],
    [404, { traits: ["SUBJECT_UNAVAILABLE"] }, "journal-gone"],
    [404, {}, "journal-gone"],
    [503, { traits: ["DEP_UNAVAILABLE"] }, "unavailable"],
  ])("%i %j → %s", async (status, body, reason) => {
    fetchMock.mockResolvedValue(problem(status, body));
    await expect(publishExperience("100", REQUEST)).resolves.toMatchObject({ ok: false, reason });
  });

  it("401：signed-out，并清掉会话 Cookie", async () => {
    fetchMock.mockResolvedValue(problem(401, { code: "GW-0401" }));
    await expect(publishExperience("100", REQUEST)).resolves.toEqual({
      ok: false,
      reason: "signed-out",
    });
    expect(jar.written).toEqual([CLEARED]);
  });

  it("参数不合格不请求", async () => {
    await expect(publishExperience("abc", REQUEST)).resolves.toEqual({
      ok: false,
      reason: "unavailable",
    });
    await expect(
      publishExperience("100", { ...REQUEST, submittedOn: 1 } as unknown as ExperienceRequest),
    ).resolves.toEqual({ ok: false, reason: "unavailable" });
    expect(fetchMock).not.toHaveBeenCalled();
  });
});

describe("editExperience", () => {
  it("PUT 整体替换，不带 anonymous", async () => {
    const { anonymous: _, ...edit } = REQUEST;
    fetchMock.mockResolvedValue(Response.json(makeDetail()));
    await expect(editExperience("9", edit)).resolves.toEqual({ ok: true, detail: makeDetail() });
    expect(call()[0]).toBe("http://gw.test/patra-interaction/posts/9");
    expect(call()[1]?.method).toBe("PUT");
  });

  it.each([
    [404, { traits: ["SUBJECT_UNAVAILABLE"] }, "post-gone"],
    [404, {}, "post-gone"],
    [403, { traits: ["FORBIDDEN"] }, "forbidden"],
    [409, { traits: ["ACCOUNT_RESTRICTED"] }, "account-restricted"],
  ])("%i %j → %s", async (status, body, reason) => {
    fetchMock.mockResolvedValue(problem(status, body));
    await expect(editExperience("9", REQUEST)).resolves.toMatchObject({ ok: false, reason });
  });
});

describe("deleteExperience", () => {
  it("DELETE 204 → ok", async () => {
    fetchMock.mockResolvedValue(new Response(null, { status: 204 }));
    await expect(deleteExperience("9")).resolves.toEqual({ ok: true });
    expect(call()[1]?.method).toBe("DELETE");
  });

  it("不是作者 → forbidden", async () => {
    fetchMock.mockResolvedValue(problem(403, { traits: ["FORBIDDEN"] }));
    await expect(deleteExperience("9")).resolves.toEqual({ ok: false, reason: "forbidden" });
  });

  it("ID 不合格不请求", async () => {
    await expect(deleteExperience("x")).resolves.toEqual({ ok: false, reason: "unavailable" });
    expect(fetchMock).not.toHaveBeenCalled();
  });
});
