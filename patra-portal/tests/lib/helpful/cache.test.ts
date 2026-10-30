import { QueryClient } from "@tanstack/react-query";
import { makeComment, makeReply, makeThread } from "@tests/fixtures/discussion";
import { makeExperience, makeExperiencePage, makeUserExperience } from "@tests/fixtures/experience";
import { describe, expect, it, vi } from "vitest";
import type { SingleData, ThreadsData } from "@/lib/discussion/cache";
import {
  markHelpfulStale,
  patchHelpful,
  withCommentHelpful,
  withJournalPostHelpful,
  withUserPostHelpful,
} from "@/lib/helpful/cache";
import { queryKeys } from "@/lib/query-keys";

const ON = { helpful: true, helpfulCount: 4 };

function threads(...pages: ReturnType<typeof makeThread>[][]): ThreadsData {
  return {
    pages: pages.map((items) => ({ items, nextCursor: null })),
    pageParams: pages.map((_, index) => (index === 0 ? null : `c${index}`)),
  };
}

const rootOf = (data: ThreadsData, page: number, index: number) =>
  data.pages[page]?.items[index]?.root;

describe("withCommentHelpful", () => {
  it("改根评论", () => {
    const data = threads([makeThread({ root: makeComment({ commentId: "5", rootId: "5" }) })]);
    expect(rootOf(withCommentHelpful(data, "5", ON), 0, 0)).toMatchObject({
      helpful: true,
      helpfulCount: 4,
    });
  });

  it("改楼内回复", () => {
    const data = threads([makeThread({ replies: [makeReply("8")] })]);
    const next = withCommentHelpful(data, "8", ON);
    expect(next.pages[0]?.items[0]?.replies[0]).toMatchObject(ON);
    expect(next.pages[0]?.items[0]?.root.helpfulCount).toBe(0);
  });

  it("同一楼在两页各一份时两份都改", () => {
    const thread = makeThread({ root: makeComment({ commentId: "5", rootId: "5" }) });
    const next = withCommentHelpful(threads([thread], [thread]), "5", ON);
    expect(rootOf(next, 0, 0)?.helpfulCount).toBe(4);
    expect(rootOf(next, 1, 0)?.helpfulCount).toBe(4);
  });

  it("不改入参", () => {
    const data = threads([makeThread()]);
    const before = structuredClone(data);
    withCommentHelpful(data, "1", ON);
    expect(data).toEqual(before);
  });

  it("单楼视图与目标不存在", () => {
    const single: SingleData = { targetId: "1", thread: makeThread() };
    expect(withCommentHelpful(single, "1", ON)).toMatchObject({ thread: { root: ON } });
    expect(withCommentHelpful({ missing: true } as SingleData, "1", ON)).toEqual({ missing: true });
  });
});

describe("withJournalPostHelpful / withUserPostHelpful", () => {
  it("期刊列表与主页列表里 postId 相同的那条", () => {
    const journal = {
      pages: [makeExperiencePage([makeExperience(), makeExperience({ postId: "10" })])],
      pageParams: [null],
    };
    expect(withJournalPostHelpful(journal, "9", ON).pages[0]?.items[0]).toMatchObject(ON);
    expect(withJournalPostHelpful(journal, "9", ON).pages[0]?.items[1]?.helpfulCount).toBe(0);
    const user = {
      pages: [{ items: [makeUserExperience()], nextCursor: null }],
      pageParams: [null],
    };
    expect(withUserPostHelpful(user, "9", ON).pages[0]?.items[0]?.experience).toMatchObject(ON);
  });
});

function seeded() {
  const client = new QueryClient({
    defaultOptions: { queries: { staleTime: Number.POSITIVE_INFINITY } },
  });
  const thread = makeThread({ root: makeComment({ commentId: "5", rootId: "5" }) });
  client.setQueryData(queryKeys.discussionThreads("publication", "1", "asc"), threads([thread]));
  client.setQueryData(
    queryKeys.discussionThreads("publication", "1", "helpful"),
    threads([thread]),
  );
  client.setQueryData(queryKeys.discussionSingle("publication", "1", "5"), {
    targetId: "5",
    thread,
  });
  const conversation = { targetId: "5", items: [thread.root] };
  client.setQueryData(queryKeys.discussionConversation("5"), conversation);
  const journal = { pages: [makeExperiencePage([makeExperience()])], pageParams: [null] };
  client.setQueryData(queryKeys.experienceList("100", "latest"), journal);
  client.setQueryData(queryKeys.experienceList("100", "helpful"), journal);
  client.setQueryData(queryKeys.experienceUser("7"), {
    pages: [{ items: [makeUserExperience()], nextCursor: null }],
    pageParams: [null],
  });
  client.setQueryData(queryKeys.experienceSummary("100", "3y"), { count: 1 });
  return { client, conversation };
}

describe("patchHelpful", () => {
  it("评论：列表的每种排序与单楼都改，对话缓存不动", () => {
    const { client, conversation } = seeded();
    patchHelpful(client, { type: "comment", id: "5" }, ON);
    for (const sort of ["asc", "helpful"]) {
      const data = client.getQueryData<ThreadsData>(
        queryKeys.discussionThreads("publication", "1", sort),
      );
      expect(data && rootOf(data, 0, 0)).toMatchObject(ON);
    }
    expect(client.getQueryData(queryKeys.discussionSingle("publication", "1", "5"))).toMatchObject({
      thread: { root: ON },
    });
    expect(client.getQueryData(queryKeys.discussionConversation("5"))).toBe(conversation);
  });

  it("经验：期刊列表两种排序与主页列表都改，汇总不动", () => {
    const { client } = seeded();
    patchHelpful(client, { type: "post", id: "9" }, ON);
    for (const sort of ["latest", "helpful"] as const) {
      expect(
        client.getQueryData<{ pages: { items: unknown[] }[] }>(
          queryKeys.experienceList("100", sort),
        )?.pages[0]?.items[0],
      ).toMatchObject(ON);
    }
    expect(
      client.getQueryData<{ pages: { items: { experience: unknown }[] }[] }>(
        queryKeys.experienceUser("7"),
      )?.pages[0]?.items[0]?.experience,
    ).toMatchObject(ON);
    expect(client.getQueryData(queryKeys.experienceSummary("100", "3y"))).toEqual({ count: 1 });
  });
});

describe("markHelpfulStale", () => {
  it("评论：列表与单楼标过期，不重取", () => {
    const { client } = seeded();
    const fetchSpy = vi.spyOn(client, "fetchQuery");
    markHelpfulStale(client, { type: "comment", id: "5" });
    const cache = client.getQueryCache();
    expect(
      cache.find({ queryKey: queryKeys.discussionThreads("publication", "1", "asc") })?.state
        .isInvalidated,
    ).toBe(true);
    expect(
      cache.find({ queryKey: queryKeys.discussionSingle("publication", "1", "5") })?.state
        .isInvalidated,
    ).toBe(true);
    expect(
      cache.find({ queryKey: queryKeys.experienceList("100", "latest") })?.state.isInvalidated,
    ).toBe(false);
    expect(fetchSpy).not.toHaveBeenCalled();
  });

  it("经验：期刊列表与主页列表标过期，汇总不动", () => {
    const { client } = seeded();
    markHelpfulStale(client, { type: "post", id: "9" });
    const cache = client.getQueryCache();
    expect(
      cache.find({ queryKey: queryKeys.experienceList("100", "helpful") })?.state.isInvalidated,
    ).toBe(true);
    expect(cache.find({ queryKey: queryKeys.experienceUser("7") })?.state.isInvalidated).toBe(true);
    expect(
      cache.find({ queryKey: queryKeys.experienceSummary("100", "3y") })?.state.isInvalidated,
    ).toBe(false);
  });
});
