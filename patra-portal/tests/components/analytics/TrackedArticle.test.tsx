import { act, render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { TrackedArticle } from "@/components/analytics/TrackedArticle";
import { TrackedList } from "@/components/analytics/TrackedList";
import { track } from "@/lib/analytics/client/queue";

vi.mock("@/lib/analytics/client/queue", () => ({ track: vi.fn() }));

/// jsdom 没有 IntersectionObserver：记下每个实例，测试里手动喂可见比例
class FakeObserver {
  static instances: FakeObserver[] = [];
  readonly targets: Element[] = [];
  constructor(readonly callback: IntersectionObserverCallback) {
    FakeObserver.instances.push(this);
  }
  observe(target: Element) {
    this.targets.push(target);
  }
  unobserve() {}
  disconnect() {}
  takeRecords() {
    return [];
  }
}

/// 让包住某篇文献的观察器报告一个可见比例
function show(articleText: string, ratio: number) {
  const card = screen.getByText(articleText).closest("article");
  for (const observer of FakeObserver.instances) {
    if (observer.targets.includes(card as Element)) {
      observer.callback(
        [{ intersectionRatio: ratio, target: card } as unknown as IntersectionObserverEntry],
        observer as unknown as IntersectionObserver,
      );
    }
  }
}

const LIST_ID = "0193e1a3-2222-7a22-8b33-445566778899";
const UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-7[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/;

function Card({ id, title }: { id: string; title: string }) {
  return (
    <article>
      <a href={`/papers/${id}`}>{title}</a>
      <a href="/journals/3">刊名</a>
    </article>
  );
}

beforeEach(() => {
  vi.useFakeTimers();
  FakeObserver.instances = [];
  vi.stubGlobal("IntersectionObserver", FakeObserver);
  vi.mocked(track).mockReset();
});

afterEach(() => {
  vi.useRealTimers();
  vi.unstubAllGlobals();
});

describe("TrackedArticle：曝光", () => {
  it("同一列表实例里同一篇只算一次", () => {
    render(
      <TrackedList listType="search" listId={LIST_ID}>
        <TrackedArticle articleId="42" position={1}>
          <Card id="42" title="第一处" />
        </TrackedArticle>
        <TrackedArticle articleId="42" position={2}>
          <Card id="42" title="第二处" />
        </TrackedArticle>
      </TrackedList>,
    );
    act(() => {
      show("第一处", 0.6);
      show("第二处", 0.6);
      vi.advanceTimersByTime(1_000);
    });
    expect(track).toHaveBeenCalledTimes(1);
    expect(track).toHaveBeenCalledWith("article_impressed", {
      article_id: "42",
      list_type: "search",
      list_id: LIST_ID,
      position: 1,
    });
  });

  it("两个列表实例各算各的；不传 listId 时生成 UUIDv7", () => {
    render(
      <>
        <TrackedList listType="feed_latest">
          <TrackedArticle articleId="42" position={1}>
            <Card id="42" title="首页" />
          </TrackedArticle>
        </TrackedList>
        <TrackedList listType="search" listId={LIST_ID}>
          <TrackedArticle articleId="42" position={3}>
            <Card id="42" title="检索" />
          </TrackedArticle>
        </TrackedList>
      </>,
    );
    act(() => {
      show("首页", 1);
      show("检索", 1);
      vi.advanceTimersByTime(1_000);
    });
    expect(track).toHaveBeenCalledTimes(2);
    const feed = vi
      .mocked(track)
      .mock.calls.find(([, p]) => "list_type" in p && p.list_type === "feed_latest");
    expect(feed?.[1]).toMatchObject({ list_id: expect.stringMatching(UUID) });
  });

  it("父组件重渲染不打断计时（列表 context 不能每次换新对象）", () => {
    function Parent({ tick }: { tick: number }) {
      return (
        <TrackedList listType="library" listId={LIST_ID}>
          <span>{tick}</span>
          <TrackedArticle articleId="42" position={1}>
            <Card id="42" title="标题" />
          </TrackedArticle>
        </TrackedList>
      );
    }
    const { rerender } = render(<Parent tick={0} />);
    act(() => {
      show("标题", 0.8);
      vi.advanceTimersByTime(500);
    });
    rerender(<Parent tick={1} />);
    act(() => vi.advanceTimersByTime(500));
    expect(track).toHaveBeenCalledTimes(1);
  });

  it("不足 1 秒：不算", () => {
    render(
      <TrackedList listType="search" listId={LIST_ID}>
        <TrackedArticle articleId="42" position={1}>
          <Card id="42" title="标题" />
        </TrackedArticle>
      </TrackedList>,
    );
    act(() => {
      show("标题", 0.9);
      vi.advanceTimersByTime(999);
    });
    expect(track).not.toHaveBeenCalled();
  });

  it("浏览器没有 IntersectionObserver：不做曝光、不报错", () => {
    vi.stubGlobal("IntersectionObserver", undefined);
    expect(() =>
      render(
        <TrackedList listType="search" listId={LIST_ID}>
          <TrackedArticle articleId="42" position={1}>
            <Card id="42" title="标题" />
          </TrackedArticle>
        </TrackedList>,
      ),
    ).not.toThrow();
  });
});

describe("TrackedArticle：点击", () => {
  beforeEach(() => {
    vi.useRealTimers();
  });

  it("点文献链接：article_clicked 带列表与位置", async () => {
    const user = userEvent.setup();
    render(
      <TrackedList listType="search" listId={LIST_ID}>
        <TrackedArticle articleId="42" position={21}>
          <Card id="42" title="标题" />
        </TrackedArticle>
      </TrackedList>,
    );
    await user.click(screen.getByRole("link", { name: "标题" }));
    expect(track).toHaveBeenCalledWith("article_clicked", {
      article_id: "42",
      list_type: "search",
      list_id: LIST_ID,
      position: 21,
    });
  });

  it("点刊名链接：不算", async () => {
    const user = userEvent.setup();
    render(
      <TrackedList listType="search" listId={LIST_ID}>
        <TrackedArticle articleId="42" position={1}>
          <Card id="42" title="标题" />
        </TrackedArticle>
      </TrackedList>,
    );
    await user.click(screen.getByRole("link", { name: "刊名" }));
    expect(track).not.toHaveBeenCalled();
  });

  it("不在列表里时什么也不做", async () => {
    const user = userEvent.setup();
    render(
      <TrackedArticle articleId="42" position={1}>
        <Card id="42" title="标题" />
      </TrackedArticle>,
    );
    await user.click(screen.getByRole("link", { name: "标题" }));
    expect(track).not.toHaveBeenCalled();
  });
});
