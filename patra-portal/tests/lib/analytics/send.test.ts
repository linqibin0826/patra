// @vitest-environment node
import { FACTS } from "@tests/fixtures/analytics";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { sendEnvelopes, trackServerEvent } from "@/lib/analytics/server/send";
import type { CollectEnvelope } from "@/lib/analytics/types";
import { lookupSessionByToken } from "@/lib/auth/current-user";

/// `after` 的回调排到响应之后；测试里记下来，手动执行
const scheduled: (() => unknown)[] = [];
vi.mock("next/server", async (importOriginal) => ({
  ...(await importOriginal<typeof import("next/server")>()),
  after: (task: () => unknown) => {
    scheduled.push(task);
  },
}));
vi.mock("@/lib/auth/current-user", () => ({ lookupSessionByToken: vi.fn() }));

const fetchMock = vi.fn<typeof fetch>();
const runScheduled = () => Promise.all(scheduled.splice(0).map((task) => task()));

beforeEach(() => {
  vi.stubEnv("PATRA_GATEWAY_BASE_URL", "http://gw.test");
  vi.stubGlobal("fetch", fetchMock);
  vi.mocked(lookupSessionByToken).mockResolvedValue({ status: "anonymous" });
});

afterEach(() => {
  vi.unstubAllEnvs();
  vi.unstubAllGlobals();
  vi.restoreAllMocks();
  fetchMock.mockReset();
  scheduled.length = 0;
});

const ENVELOPE = { event_id: "e1" } as unknown as CollectEnvelope;

describe("sendEnvelopes", () => {
  it("POST 到采集端点，请求体 { events }，带超时", async () => {
    fetchMock.mockResolvedValue(new Response(null, { status: 202 }));
    await sendEnvelopes([ENVELOPE]);
    const [url, init] = fetchMock.mock.calls[0] ?? [];
    expect(url).toBe("http://gw.test/patra-analytics/events");
    expect(init?.method).toBe("POST");
    expect(JSON.parse(String(init?.body))).toEqual({ events: [ENVELOPE] });
    expect(init?.signal).toBeInstanceOf(AbortSignal);
  });

  it.each([
    ["503（Kafka 不可用）", () => fetchMock.mockResolvedValue(new Response(null, { status: 503 }))],
    ["网络错误", () => fetchMock.mockRejectedValue(new TypeError("fetch failed"))],
    ["超时", () => fetchMock.mockRejectedValue(new DOMException("timed out", "TimeoutError"))],
  ])("%s：丢弃不重试，不抛错，只打一行警告", async (_, arrange) => {
    arrange();
    const warn = vi.spyOn(console, "warn").mockImplementation(() => {});
    const error = vi.spyOn(console, "error").mockImplementation(() => {});
    await expect(sendEnvelopes([ENVELOPE])).resolves.toBeUndefined();
    expect(fetchMock).toHaveBeenCalledTimes(1);
    expect(warn).toHaveBeenCalledTimes(1);
    expect(error).not.toHaveBeenCalled();
  });
});

describe("trackServerEvent", () => {
  const EVENT = { name: "article_viewed", properties: { article_id: "42" } } as const;
  const PAGE = { path: "/papers/42", type: "article" } as const;

  it("排到响应之后：问 identity 拿用户 ID，再发一条信封", async () => {
    fetchMock.mockResolvedValue(new Response(null, { status: 202 }));
    vi.mocked(lookupSessionByToken).mockResolvedValue({
      status: "authenticated",
      user: { userId: "77" } as never,
    });
    trackServerEvent(EVENT, { ...FACTS, token: "tok" }, PAGE);
    expect(fetchMock).not.toHaveBeenCalled();
    await runScheduled();
    expect(lookupSessionByToken).toHaveBeenCalledWith("tok", { client: FACTS.client });
    const body = JSON.parse(String(fetchMock.mock.calls[0]?.[1]?.body));
    expect(body.events[0]).toMatchObject({ event_name: "article_viewed", user_id: "77" });
  });

  it("匿名访客的 user_id 为 null", async () => {
    fetchMock.mockResolvedValue(new Response(null, { status: 202 }));
    trackServerEvent(EVENT, FACTS, PAGE);
    await runScheduled();
    const body = JSON.parse(String(fetchMock.mock.calls[0]?.[1]?.body));
    expect(body.events[0].user_id).toBeNull();
  });

  it("Server Action 的回渲染不发", async () => {
    trackServerEvent(EVENT, { ...FACTS, isActionRequest: true }, PAGE);
    await runScheduled();
    expect(scheduled).toHaveLength(0);
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it("没有埋点上下文（proxy 没写 ID）时不发", async () => {
    trackServerEvent(EVENT, null, PAGE);
    await runScheduled();
    expect(fetchMock).not.toHaveBeenCalled();
  });
});
