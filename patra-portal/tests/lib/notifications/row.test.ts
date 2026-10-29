import { makeCard } from "@tests/fixtures/discussion";
import { makeNotification } from "@tests/fixtures/notification";
import { describe, expect, it } from "vitest";
import type { NotificationSubject } from "@/lib/interaction/notification-schema";
import {
  actionLabel,
  actorName,
  actorOf,
  contextLabel,
  excerptOf,
  notificationHref,
  tabOf,
} from "@/lib/notifications/row";

const POST: NotificationSubject = {
  type: "post",
  key: "5",
  available: true,
  title: null,
  journal: "Journal of the American Heart Association",
  abbreviation: "J Am Heart Assoc",
  submittedMonth: "2026-03",
  mine: true,
};

describe("触发人", () => {
  it("匿名作者：中性头像、名字「匿名作者」", () => {
    const view = actorOf(makeNotification({ actor: null, actorAnonymous: true }));
    expect(view).toEqual({ kind: "anonymous", self: false });
    expect(actorName(view)).toBe("匿名作者");
  });

  it("actor 为 null 且不匿名、或已注销：「已注销用户」", () => {
    expect(actorName(actorOf(makeNotification({ actor: null })))).toBe("已注销用户");
    const deleted = makeCard({ state: "DELETED", displayName: null, username: null });
    expect(actorName(actorOf(makeNotification({ actor: deleted })))).toBe("已注销用户");
  });

  it("主页不可见与可见：都写昵称", () => {
    const hidden = makeCard({ username: null, avatarUrl: null, profileVisible: false });
    expect(actorOf(makeNotification({ actor: hidden })).kind).toBe("hidden");
    expect(actorName(actorOf(makeNotification()))).toBe("陈宇");
  });
});

describe("动作与标签", () => {
  it("三种动作的文案", () => {
    expect(actionLabel("COMMENT_REPLY")).toBe("回复了你");
    expect(actionLabel("POST_COMMENT")).toBe("评论了你的投稿经验");
    expect(actionLabel("MENTION")).toBe("@ 了你");
  });

  it("@ 归「@ 我的」，其余归「回复我的」", () => {
    expect(tabOf("MENTION")).toBe("mentions");
    expect(tabOf("COMMENT_REPLY")).toBe("replies");
    expect(tabOf("POST_COMMENT")).toBe("replies");
  });
});

describe("摘录", () => {
  it("被截断时末尾加「…」", () => {
    expect(excerptOf(makeNotification({ excerpt: "同意", excerptTruncated: true }))).toBe("同意…");
    expect(excerptOf(makeNotification({ excerpt: "同意" }))).toBe("同意");
  });

  it("对象已不可用时没有摘录", () => {
    expect(excerptOf(makeNotification({ excerpt: null }))).toBeNull();
  });
});

describe("上下文", () => {
  it("文献：交出原样的标题（含内联标记，由组件渲染）；没有标题时「一篇文献的讨论」", () => {
    expect(
      contextLabel({ ...(makeNotification().subject as NotificationSubject), title: "A <i>B</i>" }),
    ).toEqual({ kind: "paper", title: "A <i>B</i>" });
    expect(
      contextLabel({ ...(makeNotification().subject as NotificationSubject), title: null }),
    ).toEqual({ kind: "text", text: "一篇文献的讨论", post: false });
  });

  it("你的经验：你的投稿经验 · 缩写 · YYYY 年 M 月投稿；没有缩写用全称", () => {
    expect(contextLabel(POST)).toEqual({
      kind: "text",
      text: "你的投稿经验 · J Am Heart Assoc · 2026 年 3 月投稿",
      post: true,
    });
    expect(contextLabel({ ...POST, abbreviation: null })).toEqual({
      kind: "text",
      text: "你的投稿经验 · Journal of the American Heart Association · 2026 年 3 月投稿",
      post: true,
    });
  });

  it("别人的经验、没有对象", () => {
    expect(contextLabel({ ...POST, mine: false })).toEqual({
      kind: "text",
      text: "J Am Heart Assoc 的一条投稿经验",
      post: true,
    });
    expect(contextLabel(null)).toBeNull();
  });
});

describe("链接", () => {
  it("文献与经验走评论永久链接", () => {
    expect(notificationHref(makeNotification())).toBe("/papers/11?comment=9");
    expect(notificationHref(makeNotification({ subject: POST }))).toBe("/posts/5?comment=9");
  });

  it("文献下线（对象不可用）时仍走评论永久链接", () => {
    expect(
      notificationHref(
        makeNotification({
          subject: { ...(makeNotification().subject as NotificationSubject), available: false },
        }),
      ),
    ).toBe("/papers/11?comment=9");
  });

  it("key 为空或没有对象时不是链接", () => {
    expect(notificationHref(makeNotification({ subject: { ...POST, key: null } }))).toBeNull();
    expect(notificationHref(makeNotification({ subject: null }))).toBeNull();
  });
});
