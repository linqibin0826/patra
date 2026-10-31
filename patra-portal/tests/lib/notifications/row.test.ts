import { makeCard } from "@tests/fixtures/discussion";
import { makeNotification } from "@tests/fixtures/notification";
import { describe, expect, it } from "vitest";
import type { NotificationSubject } from "@/lib/notification/inbox-schema";
import {
  actionLabel,
  actorName,
  actorOf,
  bodyOf,
  contextLabel,
  helpfulMore,
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
  experience: {
    firstDecision: "MAJOR_REVISION",
    finalOutcome: "ACCEPTED",
    firstRoundDays: 41,
    totalDays: 96,
  },
};

/// 有帮助条目：默认周敏等 2 人点了你的投稿经验
const helpful = (overrides: Parameters<typeof makeNotification>[0] = {}) =>
  makeNotification({
    type: "POST_HELPFUL",
    actorCount: 2,
    commentId: null,
    subject: POST,
    excerpt: "第二轮小修后编辑直接接收",
    ...overrides,
  });

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
    expect(actionLabel(makeNotification({ type: "COMMENT_REPLY" }))).toBe("回复了你");
    expect(actionLabel(makeNotification({ type: "POST_COMMENT" }))).toBe("评论了你的投稿经验");
    expect(actionLabel(makeNotification({ type: "MENTION" }))).toBe("@ 了你");
  });

  it("有帮助：1 人不写人数；2 人起「等 N 人」；对象只说评论或投稿经验", () => {
    expect(actionLabel(helpful({ type: "COMMENT_HELPFUL", actorCount: 1 }))).toBe(
      "觉得你的评论有帮助",
    );
    expect(actionLabel(helpful())).toBe("等 2 人觉得你的投稿经验有帮助");
    expect(actionLabel(helpful({ type: "COMMENT_HELPFUL", actorCount: 3 }))).toBe(
      "等 3 人觉得你的评论有帮助",
    );
  });

  it("头像角标：有帮助 2 人起 +N−1，其余没有", () => {
    expect(helpfulMore(helpful({ actorCount: 3 }))).toBe("+2");
    expect(helpfulMore(helpful({ actorCount: 1 }))).toBeNull();
    expect(helpfulMore(makeNotification({ actorCount: 1 }))).toBeNull();
  });

  it("@ 归「@ 我的」，有帮助归「有帮助」，其余归「回复我的」", () => {
    expect(tabOf("MENTION")).toBe("mentions");
    expect(tabOf("COMMENT_REPLY")).toBe("replies");
    expect(tabOf("POST_COMMENT")).toBe("replies");
    expect(tabOf("COMMENT_HELPFUL")).toBe("helpful");
    expect(tabOf("POST_HELPFUL")).toBe("helpful");
  });
});

describe("正文", () => {
  it("被截断时末尾加「…」；回复类随未读加深", () => {
    expect(bodyOf(makeNotification({ excerpt: "同意", excerptTruncated: true }))).toEqual({
      text: "同意…",
      muted: false,
    });
    expect(bodyOf(makeNotification({ excerpt: "同意" }))).toEqual({ text: "同意", muted: false });
  });

  it("对象已不可用时没有摘录", () => {
    expect(bodyOf(makeNotification({ excerpt: null }))).toBeNull();
  });

  it("有帮助：你那条内容的摘录，颜色不随未读加深", () => {
    expect(bodyOf(helpful({ excerptTruncated: true }))).toEqual({
      text: "第二轮小修后编辑直接接收…",
      muted: true,
    });
  });

  it("有帮助点了没有正文的经验：写经验列表的结果行", () => {
    expect(bodyOf(helpful({ excerpt: null }))).toEqual({
      text: "首轮 41 天 · 大修 → 录用",
      muted: true,
    });
  });

  it("有帮助既没有摘录也没有经验字段：不出正文", () => {
    expect(bodyOf(helpful({ excerpt: null, subject: { ...POST, experience: null } }))).toBeNull();
    expect(bodyOf(helpful({ excerpt: null, subject: null }))).toBeNull();
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

  it("有帮助：经验直接去经验页；评论去评论永久链接；没有评论 ID 时不带参数", () => {
    expect(notificationHref(helpful())).toBe("/posts/5");
    expect(notificationHref(helpful({ type: "COMMENT_HELPFUL", commentId: "9" }))).toBe(
      "/posts/5?comment=9",
    );
    expect(
      notificationHref(
        makeNotification({ type: "COMMENT_HELPFUL", actorCount: 2, commentId: null }),
      ),
    ).toBe("/papers/11");
  });

  it("key 为空或没有对象时不是链接", () => {
    expect(notificationHref(makeNotification({ subject: { ...POST, key: null } }))).toBeNull();
    expect(notificationHref(makeNotification({ subject: null }))).toBeNull();
  });
});
