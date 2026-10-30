import { makeCard } from "@tests/fixtures/discussion";
import type {
  Experience,
  ExperiencePage,
  PostDetail,
  Revision,
  Summary,
  UserExperience,
} from "@/lib/interaction/experience-schema";

/// 一条经验：默认是陈宇写的、3 月 2 日投稿、4 月 13 日首轮大修、6 月 1 日录用（简报 §0 的流程闭环）
export function makeExperience(overrides: Partial<Experience> = {}): Experience {
  return {
    postId: "9",
    submittedOn: "2026-03-02",
    firstDecisionOn: "2026-04-13",
    firstDecision: "MAJOR_REVISION",
    finalOutcome: "ACCEPTED",
    finalDecisionOn: "2026-06-01",
    articleType: "ORIGINAL_RESEARCH",
    reviewRounds: 2,
    reviewQuality: 4,
    editorCommunication: 3,
    apcPaid: true,
    previouslyRejected: false,
    firstRoundDays: 42,
    totalDays: 91,
    body: "审稿人有两位，一位主要问统计方法，要求补充敏感性分析。",
    createdAt: "2026-06-03T02:00:00Z",
    edited: false,
    author: makeCard(),
    anonymous: false,
    mine: false,
    helpfulCount: 0,
    helpful: false,
    replyCount: 0,
    ...overrides,
  };
}

/// 期刊的一页经验：默认没有下一页，总条数等于这一页的条数
export function makeExperiencePage(
  items: Experience[],
  overrides: Partial<ExperiencePage> = {},
): ExperiencePage {
  return { items, nextCursor: null, total: items.length, ...overrides };
}

/// 经验页详情：默认挂在 J Am Heart Assoc（期刊 ID 100）下
export function makeDetail(overrides: Partial<Experience> = {}): PostDetail {
  const experience = makeExperience(overrides);
  return {
    postId: experience.postId,
    deleted: false,
    journal: {
      key: "100",
      state: "ACTIVE",
      title: "Journal of the American Heart Association",
      abbreviation: "J Am Heart Assoc",
    },
    experience,
  };
}

/// 满 5 条的汇总（PAP-122 交接 3.4 的示例）
export function makeSummary(overrides: Partial<Summary> = {}): Summary {
  return {
    window: "3y",
    count: 15,
    allCount: 20,
    sufficient: true,
    firstRound: { median: 42, p25: 28, p75: 63, sampleSize: 12 },
    deskReject: { median: 9, sampleSize: 3 },
    outcomes: { accepted: 6, rejected: 6, withdrawn: 1, inProgress: 2 },
    acceptance: { rate: 0.5, decidedCount: 12 },
    ...overrides,
  };
}

/// 不到 5 条的汇总
export function makeFewSummary(count: number, allCount = count): Summary {
  return {
    window: "3y",
    count,
    allCount,
    sufficient: false,
    firstRound: null,
    deskReject: null,
    outcomes: null,
    acceptance: null,
  };
}

/// 修改历史的一版：默认与 `makeExperience()` 的字段相同、不是当前版本
export function makeRevision(overrides: Partial<Revision> = {}): Revision {
  const e = makeExperience();
  return {
    submittedOn: e.submittedOn,
    firstDecisionOn: e.firstDecisionOn,
    firstDecision: e.firstDecision,
    finalOutcome: e.finalOutcome,
    finalDecisionOn: e.finalDecisionOn,
    articleType: e.articleType,
    reviewRounds: e.reviewRounds,
    reviewQuality: e.reviewQuality,
    editorCommunication: e.editorCommunication,
    apcPaid: e.apcPaid,
    previouslyRejected: e.previouslyRejected,
    firstRoundDays: e.firstRoundDays,
    totalDays: e.totalDays,
    body: e.body,
    writtenAt: "2026-06-03T02:00:00Z",
    current: false,
    ...overrides,
  };
}

/// 主页标签的一条：作者恒为 null
export function makeUserExperience(overrides: Partial<Experience> = {}): UserExperience {
  return {
    journal: {
      key: "100",
      state: "ACTIVE",
      title: "Journal of the American Heart Association",
      abbreviation: "J Am Heart Assoc",
    },
    experience: makeExperience({ author: null, ...overrides }),
  };
}
