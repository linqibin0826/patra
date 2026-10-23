// @vitest-environment node
import { MY_PROFILE } from "@tests/fixtures/profile";
import { resetCookieJar } from "@tests/stubs/cookie-jar";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { savePrivacy, saveProfile } from "@/lib/identity/profile-actions";
import { toFormValues } from "@/lib/identity/profile-form";

vi.mock("next/headers", () => import("@tests/stubs/cookie-jar"));

const fetchMock = vi.fn<typeof fetch>();
const VALUES = toFormValues(MY_PROFILE);
const CLEARED = "patra_session=; Path=/; Max-Age=0; HttpOnly; SameSite=lax";

beforeEach(() => {
  vi.stubEnv("PATRA_GATEWAY_BASE_URL", "http://gw.test");
  vi.stubEnv("PATRA_SESSION_COOKIE_SECURE", "false");
  vi.stubGlobal("fetch", fetchMock);
  vi.spyOn(console, "error").mockImplementation(() => {});
  resetCookieJar({ patra_session: "patra_user_abc" });
});

afterEach(() => {
  vi.unstubAllEnvs();
  vi.unstubAllGlobals();
  vi.restoreAllMocks();
  fetchMock.mockReset();
});

const reply = (status: number, body: unknown) => Response.json(body, { status });

describe("saveProfile", () => {
  it("PUT 整份资料，200 返回保存后的资料", async () => {
    fetchMock.mockResolvedValue(reply(200, MY_PROFILE));
    await expect(saveProfile(VALUES)).resolves.toEqual({ ok: true, profile: MY_PROFILE });
    const [url, init] = fetchMock.mock.calls[0] ?? [];
    expect(url).toBe("http://gw.test/patra-identity/me/profile");
    expect(init?.method).toBe("PUT");
    expect(JSON.parse(String(init?.body))).toMatchObject({
      organization: { rorId: "04jztag35" },
      researchInterests: ["D006333", "D001281"],
    });
  });

  it("422：带回字段错误", async () => {
    fetchMock.mockResolvedValue(
      reply(422, { code: "IDN-0422", errors: [{ field: "links[1].url", code: "INVALID_FORMAT" }] }),
    );
    await expect(saveProfile(VALUES)).resolves.toEqual({
      ok: false,
      reason: "invalid-fields",
      fieldErrors: [{ field: "links[1].url", code: "INVALID_FORMAT" }],
    });
  });

  it("503：catalog-unavailable", async () => {
    fetchMock.mockResolvedValue(reply(503, { code: "IDN-0503", traits: ["DEP_UNAVAILABLE"] }));
    await expect(saveProfile(VALUES)).resolves.toEqual({
      ok: false,
      reason: "catalog-unavailable",
    });
  });

  it("网关自己的 503（identity 连不上）：unavailable，不说 catalog", async () => {
    fetchMock.mockResolvedValue(reply(503, { code: "GW-0503", traits: ["DEP_UNAVAILABLE"] }));
    await expect(saveProfile(VALUES)).resolves.toEqual({ ok: false, reason: "unavailable" });
  });

  it("409 只有 CONFLICT：自动重试一次，第二次成功就算成功", async () => {
    fetchMock
      .mockResolvedValueOnce(reply(409, { code: "IDN-0409", traits: ["CONFLICT"] }))
      .mockResolvedValueOnce(reply(200, MY_PROFILE));
    await expect(saveProfile(VALUES)).resolves.toEqual({ ok: true, profile: MY_PROFILE });
    expect(fetchMock).toHaveBeenCalledTimes(2);
  });

  it("409 重试后仍是 409：conflict", async () => {
    fetchMock.mockImplementation(async () =>
      reply(409, { code: "IDN-0409", traits: ["CONFLICT"] }),
    );
    await expect(saveProfile(VALUES)).resolves.toEqual({ ok: false, reason: "conflict" });
    expect(fetchMock).toHaveBeenCalledTimes(2);
  });

  it("409 冷静期：不重试，pending-deletion", async () => {
    fetchMock.mockResolvedValue(
      reply(409, { code: "IDN-0409", traits: ["CONFLICT", "PENDING_DELETION"] }),
    );
    await expect(saveProfile(VALUES)).resolves.toEqual({ ok: false, reason: "pending-deletion" });
    expect(fetchMock).toHaveBeenCalledTimes(1);
  });

  it("401：signed-out，并清掉会话 Cookie", async () => {
    fetchMock.mockResolvedValue(reply(401, { code: "GW-0401" }));
    const jar = resetCookieJar({ patra_session: "patra_user_abc" });
    await expect(saveProfile(VALUES)).resolves.toEqual({ ok: false, reason: "signed-out" });
    expect(jar.written).toEqual([CLEARED]);
  });

  it("入参形状不对：unavailable，不请求网关", async () => {
    await expect(saveProfile({ ...VALUES, links: "x" } as never)).resolves.toEqual({
      ok: false,
      reason: "unavailable",
    });
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it("其他状态码或网络错误：unavailable", async () => {
    fetchMock.mockResolvedValue(reply(500, { code: "IDN-0500" }));
    await expect(saveProfile(VALUES)).resolves.toEqual({ ok: false, reason: "unavailable" });
  });
});

describe("savePrivacy", () => {
  const SETTINGS = {
    profileVisibility: "SIGNED_IN",
    professionalVisibility: "SELF",
    searchIndexing: true,
  } as const;

  it("PUT 三项，200 返回保存后的设置", async () => {
    fetchMock.mockResolvedValue(reply(200, SETTINGS));
    await expect(savePrivacy(SETTINGS)).resolves.toEqual({ ok: true, settings: SETTINGS });
    const [url, init] = fetchMock.mock.calls[0] ?? [];
    expect(url).toBe("http://gw.test/patra-identity/me/privacy");
    expect(init?.method).toBe("PUT");
    expect(JSON.parse(String(init?.body))).toEqual(SETTINGS);
  });

  it.each([
    [409, { traits: ["CONFLICT", "PENDING_DELETION"] }, "pending-deletion"],
    [401, { code: "GW-0401" }, "signed-out"],
    [422, { errors: [{ field: "profileVisibility", code: "INVALID_FORMAT" }] }, "unavailable"],
    [503, {}, "unavailable"],
  ])("%i：%s", async (status, body, reason) => {
    fetchMock.mockResolvedValue(reply(status, body));
    await expect(savePrivacy(SETTINGS)).resolves.toEqual({ ok: false, reason });
  });

  it("入参形状不对：unavailable，不请求网关", async () => {
    await expect(savePrivacy({ ...SETTINGS, searchIndexing: "yes" } as never)).resolves.toEqual({
      ok: false,
      reason: "unavailable",
    });
    expect(fetchMock).not.toHaveBeenCalled();
  });
});
