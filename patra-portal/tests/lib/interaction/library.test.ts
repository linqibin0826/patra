// @vitest-environment node
import { makeItemStatus, makeLibraryItem, makeOverview } from "@tests/fixtures/interaction";
import { makePaper } from "@tests/fixtures/paper";
import { resetCookieJar } from "@tests/stubs/cookie-jar";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import {
  fetchLibraryOverview,
  fetchLibraryPage,
  fetchLibraryStatus,
  isEntityId,
  parseLibrarySort,
  parsePage,
} from "@/lib/interaction/library";

vi.mock("next/headers", () => import("@tests/stubs/cookie-jar"));

const fetchMock = vi.fn<typeof fetch>();

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

const page = <T>(items: T[]) => ({
  page: 1,
  pageSize: 50,
  total: items.length,
  totalPages: 1,
  items,
});
const urls = () => fetchMock.mock.calls.map(([url]) => String(url));

/// 按地址分派：interaction 的回 `interaction`，catalog 批量卡片回 `cards`，批量对象统计回 `stats`
function respond(
  interaction: Response,
  cards: Response = Response.json({ items: [] }),
  stats: Response = Response.json({ items: [] }),
) {
  fetchMock.mockImplementation(async (url) => {
    if (String(url).includes("/patra-catalog/")) {
      return cards.clone();
    }
    return String(url).includes("/subjects/stats") ? stats.clone() : interaction.clone();
  });
}

describe("参数解析", () => {
  it("排序只认三种，其余回落加入时间", () => {
    expect(parseLibrarySort("published")).toBe("published");
    expect(parseLibrarySort("journal")).toBe("journal");
    expect(parseLibrarySort("x")).toBe("added");
    expect(parseLibrarySort(undefined)).toBe("added");
  });

  it("页码为正整数，其余回落 1", () => {
    expect(parsePage("3")).toBe(3);
    expect(parsePage("0")).toBe(1);
    expect(parsePage("1.5")).toBe(1);
    expect(parsePage(["2"])).toBe(1);
  });

  it("ID：正整数、无前导零、最多 19 位", () => {
    expect(isEntityId("9")).toBe(true);
    expect(isEntityId("01")).toBe(false);
    expect(isEntityId("abc")).toBe(false);
    expect(isEntityId("12345678901234567890")).toBe(false);
  });
});

describe("fetchLibraryStatus", () => {
  it("POST 批量查，3 秒超时", async () => {
    const timeout = vi.spyOn(AbortSignal, "timeout");
    respond(Response.json({ items: [makeItemStatus({ key: "11", collected: true })] }));
    await expect(fetchLibraryStatus(["11"])).resolves.toEqual({
      kind: "ok",
      data: [makeItemStatus({ key: "11", collected: true })],
    });
    expect(urls()).toEqual(["http://gw.test/patra-interaction/me/library/status"]);
    expect(JSON.parse(String(fetchMock.mock.calls[0]?.[1]?.body))).toEqual({
      type: "publication",
      keys: ["11"],
    });
    expect(timeout).toHaveBeenCalledWith(3000);
  });

  it("没有会话：signed-out", async () => {
    resetCookieJar();
    await expect(fetchLibraryStatus(["11"])).resolves.toEqual({ kind: "signed-out" });
  });
});

describe("fetchLibraryOverview", () => {
  it("200：ok", async () => {
    respond(Response.json(makeOverview()));
    await expect(fetchLibraryOverview()).resolves.toEqual({ kind: "ok", data: makeOverview() });
  });

  it("401：signed-out", async () => {
    respond(Response.json({ code: "GW-0401" }, { status: 401 }));
    await expect(fetchLibraryOverview()).resolves.toEqual({ kind: "signed-out" });
  });

  it.each([
    ["503", () => respond(Response.json({}, { status: 503 }))],
    ["形状不对", () => respond(Response.json({ total: "x" }))],
    ["超时", () => fetchMock.mockRejectedValue(new DOMException("t", "TimeoutError"))],
  ])("%s：unavailable", async (_label, arrange) => {
    arrange();
    await expect(fetchLibraryOverview()).resolves.toEqual({ kind: "unavailable" });
  });
});

describe("fetchLibraryPage", () => {
  const active = makeLibraryItem({ itemId: "501", key: "11" });
  const second = makeLibraryItem({ itemId: "502", key: "12" });
  const gone = makeLibraryItem({ itemId: "503", key: null, state: "UNAVAILABLE", title: "旧标题" });

  it("取一页条目，再一次批量取正常条目的卡片与计数，按条目顺序拼好", async () => {
    const paper = makePaper({ id: "11" });
    respond(Response.json(page([active, gone])), Response.json({ items: [paper] }));
    const read = await fetchLibraryPage("all", "published", 2);
    expect(urls()).toEqual([
      "http://gw.test/patra-interaction/me/library/items?view=all&sort=published&page=2&size=50",
      "http://gw.test/patra-catalog/portal/publications/batch?id=11",
      "http://gw.test/patra-interaction/subjects/stats",
    ]);
    expect(fetchMock.mock.calls[1]?.[1]?.headers).not.toHaveProperty("Authorization");
    expect(fetchMock.mock.calls[2]?.[1]?.headers).not.toHaveProperty("Authorization");
    expect(read).toEqual({
      kind: "ok",
      data: {
        ...page([]),
        total: 2,
        items: [
          { item: active, paper, stats: null },
          { item: gone, paper: null, stats: null },
        ],
      },
    });
  });

  it("catalog 少回一条：那一条没有卡片（按已失效外观）", async () => {
    respond(
      Response.json(page([active, second])),
      Response.json({ items: [makePaper({ id: "12" })] }),
    );
    const read = await fetchLibraryPage("unfiled", "added", 1);
    expect(read.kind === "ok" && read.data.items.map((entry) => entry.paper?.id ?? null)).toEqual([
      null,
      "12",
    ]);
  });

  it("这一页没有可取卡片的条目时不请求 catalog", async () => {
    respond(Response.json(page([gone])));
    await fetchLibraryPage("all", "added", 1);
    expect(urls()).toHaveLength(1);
  });

  it("收藏夹视图走 /me/collections/{id}/items", async () => {
    respond(Response.json(page([])));
    await fetchLibraryPage({ collectionId: "9" }, "journal", 1);
    expect(urls()).toEqual([
      "http://gw.test/patra-interaction/me/collections/9/items?sort=journal&page=1&size=50",
    ]);
  });

  it("收藏夹不是自己的或不存在：not-found", async () => {
    respond(Response.json({ code: "INTERACTION-0404" }, { status: 404 }));
    await expect(fetchLibraryPage({ collectionId: "9" }, "added", 1)).resolves.toEqual({
      kind: "not-found",
    });
  });

  it("should_attach_stats_per_entry_fetched_with_cards", async () => {
    respond(
      Response.json(page([active, second, gone])),
      Response.json({ items: [makePaper({ id: "11" }), makePaper({ id: "12" })] }),
      Response.json({ items: [{ key: "11", collectorCount: 2, discussionCount: 5 }] }),
    );
    const read = await fetchLibraryPage("all", "added", 1);
    const statsCall = fetchMock.mock.calls.find(([url]) => String(url).includes("/subjects/stats"));
    expect(JSON.parse(String(statsCall?.[1]?.body))).toEqual({
      type: "publication",
      keys: ["11", "12"],
    });
    expect(read.kind === "ok" && read.data.items.map((entry) => entry.stats)).toEqual([
      { collectorCount: 2, discussionCount: 5 },
      null,
      null,
    ]);
  });

  it("should_stay_ok_with_null_stats_when_stats_unavailable", async () => {
    respond(
      Response.json(page([active])),
      Response.json({ items: [makePaper({ id: "11" })] }),
      new Response("x", { status: 502 }),
    );
    const read = await fetchLibraryPage("all", "added", 1);
    expect(read.kind).toBe("ok");
    expect(read.kind === "ok" && read.data.items[0]?.stats).toBeNull();
    expect(read.kind === "ok" && read.data.items[0]?.paper?.id).toBe("11");
  });

  it("卡片取不到：unavailable", async () => {
    respond(Response.json(page([active])), new Response("x", { status: 502 }));
    await expect(fetchLibraryPage("all", "added", 1)).resolves.toEqual({ kind: "unavailable" });
  });
});
