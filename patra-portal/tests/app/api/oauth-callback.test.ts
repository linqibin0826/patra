// @vitest-environment node
import { ME_RESPONSE } from "@tests/fixtures/user";
import { resetCookieJar } from "@tests/stubs/cookie-jar";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { GET } from "@/app/api/auth/oauth/callback/[provider]/route";

vi.mock("next/headers", () => import("@tests/stubs/cookie-jar"));

const fetchMock = vi.fn<typeof fetch>();
const DEVICE = "d".repeat(43);
const FROM_LOGIN = "from=login&next=%2Fpapers%2F1";
const FROM_REGISTER = "from=register&next=%2F";
const FROM_SETTINGS = "from=settings&next=%2Fsettings%2Fsecurity";

beforeEach(() => {
  vi.stubEnv("PATRA_GATEWAY_BASE_URL", "http://gw.test");
  vi.stubEnv("PATRA_SESSION_COOKIE_SECURE", "false");
  vi.stubGlobal("fetch", fetchMock);
  vi.spyOn(console, "error").mockImplementation(() => {});
});

afterEach(() => {
  vi.unstubAllEnvs();
  vi.unstubAllGlobals();
  vi.restoreAllMocks();
  fetchMock.mockReset();
});

function callback(provider: string, query = "?code=secret-code&state=s-1") {
  return GET(new Request(`http://portal.test/api/auth/oauth/callback/${provider}${query}`), {
    params: Promise.resolve({ provider }),
  });
}

function cookiesFor(ret: string, extra: Record<string, string> = {}) {
  return resetCookieJar(
    { patra_oauth_state: "s-1", patra_oauth_return: ret, patra_device: DEVICE, ...extra },
    { "x-forwarded-for": "203.0.113.7", "user-agent": "UA" },
  );
}

function problem(status: number, traits: string[] = []) {
  return Response.json({ code: `IDN-0${status}`, traits }, { status });
}

describe("GET /api/auth/oauth/callback/[provider] 核对 state", () => {
  it("不认识的平台：404，不转发", async () => {
    cookiesFor(FROM_LOGIN);
    const res = await callback("wechat");
    expect(res.status).toBe(404);
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it("缺 state Cookie：不转发，回发起页说已失效", async () => {
    const jar = resetCookieJar({ patra_oauth_return: FROM_LOGIN });
    const res = await callback("github");
    expect(res.status).toBe(302);
    expect(res.headers.get("location")).toBe(
      "/login?next=%2Fpapers%2F1&oauth=rejected&provider=github",
    );
    expect(fetchMock).not.toHaveBeenCalled();
    expect(jar.written.some((line) => line.startsWith("patra_oauth_state=;"))).toBe(true);
  });

  it("两个 Cookie 都没有：回登录页", async () => {
    resetCookieJar();
    const res = await callback("github");
    expect(res.headers.get("location")).toBe("/login?oauth=rejected&provider=github");
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it.each([
    ["和 Cookie 不相等", "?code=c&state=other"],
    ["查询串里没有", "?code=c"],
    ["查询串里有两个", "?code=c&state=s-1&state=s-1"],
    ["查询串里有两个且第二个不同", "?code=c&state=s-1&state=other"],
  ])("state %s：不转发", async (_name, query) => {
    cookiesFor(FROM_REGISTER);
    const res = await callback("orcid", query);
    expect(res.headers.get("location")).toBe("/register?oauth=rejected&provider=orcid");
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it("改坏的 return Cookie：回登录页，不跳站外", async () => {
    resetCookieJar({ patra_oauth_return: "from=evil&next=https%3A%2F%2Fevil.com" });
    const res = await callback("github");
    expect(res.headers.get("location")).toBe("/login?oauth=rejected&provider=github");
  });
});

describe("转发", () => {
  it("查询串原样带上，带来源、设备、会话；核对后清 Cookie；不缓存", async () => {
    const jar = cookiesFor(FROM_LOGIN, { patra_session: "patra_user_abc" });
    fetchMock.mockResolvedValue(
      Response.json({ result: "AUTHENTICATED", sessionToken: "new", user: ME_RESPONSE }),
    );
    const res = await callback("github", "?code=a%2Bb&state=s-1&scope=read%3Auser");
    const [url, init] = fetchMock.mock.calls[0] ?? [];
    expect(url).toBe(
      "http://gw.test/patra-identity/login/oauth2/code/github?code=a%2Bb&state=s-1&scope=read%3Auser",
    );
    expect(init?.headers).toMatchObject({
      "X-Forwarded-For": "203.0.113.7",
      "User-Agent": "UA",
      "X-Patra-Device-Id": DEVICE,
      Authorization: "Bearer patra_user_abc",
    });
    expect(res.headers.get("cache-control")).toBe("private, no-store");
    expect(jar.written.some((line) => /^patra_oauth_state=;.*Max-Age=0/.test(line))).toBe(true);
    expect(jar.written.some((line) => /^patra_oauth_return=;.*Max-Age=0/.test(line))).toBe(true);
  });

  it("没有会话 Cookie：不带 Authorization", async () => {
    cookiesFor(FROM_LOGIN);
    fetchMock.mockResolvedValue(problem(503));
    await callback("github");
    expect(fetchMock.mock.calls[0]?.[1]?.headers).not.toHaveProperty("Authorization");
  });
});

describe("成功结果", () => {
  it("老用户：写会话和提示标记，回 next", async () => {
    const jar = cookiesFor(FROM_LOGIN);
    fetchMock.mockResolvedValue(
      Response.json({ result: "AUTHENTICATED", sessionToken: "patra_user_new", user: ME_RESPONSE }),
    );
    const res = await callback("github");
    expect(res.headers.get("location")).toBe("/papers/1");
    expect(jar.written.some((line) => line.startsWith("patra_session=patra_user_new;"))).toBe(true);
    expect(jar.written.some((line) => line.startsWith("patra_oauth_signed_in=1;"))).toBe(true);
  });

  it("冷静期账号：写会话，去取消注销页，不弹已登录", async () => {
    const jar = cookiesFor(FROM_LOGIN);
    fetchMock.mockResolvedValue(
      Response.json({
        result: "AUTHENTICATED",
        sessionToken: "patra_user_new",
        user: { ...ME_RESPONSE, status: "PENDING_DELETION", deletionDueAt: "2026-11-19T00:00:00Z" },
      }),
    );
    const res = await callback("github");
    expect(res.headers.get("location")).toBe("/account/deletion-pending?next=%2Fpapers%2F1");
    expect(jar.written.some((line) => line.startsWith("patra_session=patra_user_new;"))).toBe(true);
    expect(jar.written.some((line) => line.startsWith("patra_oauth_signed_in="))).toBe(false);
  });

  it("新用户：ticket 只进 Cookie，去补填页，带 next", async () => {
    const jar = cookiesFor(FROM_LOGIN);
    fetchMock.mockResolvedValue(
      Response.json({
        result: "REGISTRATION_REQUIRED",
        ticket: "ticket-1",
        provider: "github",
        accountLabel: "octocat",
        suggestedUsername: "octocat",
        suggestedEmail: null,
        expiresAt: "2026-11-04T09:30:00Z",
      }),
    );
    const res = await callback("github");
    expect(res.headers.get("location")).toBe("/register/complete?next=%2Fpapers%2F1");
    expect(res.headers.get("location")).not.toContain("ticket-1");
    expect(jar.written.some((line) => line.startsWith("patra_oauth_ticket=ticket-1;"))).toBe(true);
  });

  it("绑定成功：回设置页", async () => {
    cookiesFor(FROM_SETTINGS, { patra_session: "patra_user_abc" });
    fetchMock.mockResolvedValue(
      Response.json({ result: "LINKED", provider: "orcid", accountLabel: "0000-0002-1825-0097" }),
    );
    const res = await callback("orcid");
    expect(res.headers.get("location")).toBe("/settings/security?oauth=linked&provider=orcid");
  });
});

describe("错误结果", () => {
  it.each([
    ["用户取消", problem(422, ["RULE_VIOLATION", "OAUTH_CANCELLED"]), "cancelled"],
    ["协议失败", problem(422, ["RULE_VIOLATION", "OAUTH_REJECTED"]), "rejected"],
    ["连不上平台", problem(503, ["DEP_UNAVAILABLE"]), "unreachable"],
    ["封禁", problem(403, ["FORBIDDEN"]), "banned"],
    ["其他状态码", problem(500), "unavailable"],
  ])("登录流程 · %s", async (_name, reply, kind) => {
    cookiesFor(FROM_LOGIN);
    fetchMock.mockResolvedValue(reply);
    const res = await callback("github");
    expect(res.headers.get("location")).toBe(
      `/login?next=%2Fpapers%2F1&oauth=${kind}&provider=github`,
    );
  });

  it.each([
    ["会话对不上", problem(403, ["FORBIDDEN", "LINK_SESSION_MISMATCH"]), "rejected"],
    ["已绑给别人", problem(409, ["CONFLICT", "PROVIDER_ACCOUNT_TAKEN"]), "taken"],
    ["已绑同一家", problem(409, ["CONFLICT", "PROVIDER_ALREADY_LINKED"]), "already-linked"],
    ["用户取消", problem(422, ["RULE_VIOLATION", "OAUTH_CANCELLED"]), "cancelled"],
  ])("绑定流程 · %s：回设置页", async (_name, reply, kind) => {
    cookiesFor(FROM_SETTINGS, { patra_session: "patra_user_abc" });
    fetchMock.mockResolvedValue(reply);
    const res = await callback("orcid");
    expect(res.headers.get("location")).toBe(`/settings/security?oauth=${kind}&provider=orcid`);
  });

  it("绑定流程 · 会话没了：清会话，去登录页，登录后回设置页", async () => {
    const jar = cookiesFor(FROM_SETTINGS, { patra_session: "patra_user_abc" });
    fetchMock.mockResolvedValue(problem(401));
    const res = await callback("orcid");
    expect(res.headers.get("location")).toBe("/login?next=%2Fsettings%2Fsecurity");
    expect(jar.written.some((line) => /^patra_session=;.*Max-Age=0/.test(line))).toBe(true);
  });

  it("响应体不对：服务错误，不写会话", async () => {
    const jar = cookiesFor(FROM_LOGIN);
    fetchMock.mockResolvedValue(Response.json({ result: "AUTHENTICATED" }));
    const res = await callback("github");
    expect(res.headers.get("location")).toContain("oauth=unavailable");
    expect(jar.written.some((line) => line.startsWith("patra_session="))).toBe(false);
  });

  it("网关不可达：服务错误", async () => {
    cookiesFor(FROM_LOGIN);
    fetchMock.mockRejectedValue(new TypeError("fetch failed"));
    const res = await callback("github");
    expect(res.headers.get("location")).toContain("oauth=unavailable");
  });

  it("日志里没有 code、state、令牌和 ticket", async () => {
    cookiesFor(FROM_LOGIN);
    const spy = vi.mocked(console.error);
    fetchMock.mockResolvedValueOnce(Response.json({ result: "?", sessionToken: "secret-token" }));
    await callback("github");
    cookiesFor(FROM_LOGIN);
    fetchMock.mockRejectedValueOnce(new TypeError("fetch failed"));
    await callback("github");
    const logged = JSON.stringify(spy.mock.calls);
    expect(spy).toHaveBeenCalled();
    expect(logged).not.toContain("secret-code");
    expect(logged).not.toContain("s-1");
    expect(logged).not.toContain("secret-token");
  });
});
