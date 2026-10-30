import { z } from "zod";
import { ARTICLE_TYPES, FINAL_OUTCOMES, FIRST_DECISIONS } from "@/lib/experience/labels";
import { cardSchema } from "@/lib/interaction/discussion-schema";

/// interaction 投稿经验接口（后端工程 spec 2026-10-27-interaction-post-design，摘要见共享笔记 PAP-122 交接第 3 节）
/// 的响应体与请求体。ID 一律是字符串，日期 `yyyy-MM-dd`，时刻 UTC ISO 8601。不加 `server-only`：客户端组件要用类型

const id = z.string().min(1);
const date = z.string().regex(/^\d{4}-\d{2}-\d{2}$/);

export const firstDecisionSchema = z.enum(FIRST_DECISIONS);
export const finalOutcomeSchema = z.enum(FINAL_OUTCOMES);
export const articleTypeSchema = z.enum(ARTICLE_TYPES);

/// 期刊页经验列表的排序：最新投稿（默认）/ 最有帮助
export const experienceSortSchema = z.enum(["latest", "helpful"]);
export type ExperienceSort = z.infer<typeof experienceSortSchema>;

/// 汇总的统计窗口：近 3 年（默认）/ 全部
export const summaryWindowSchema = z.enum(["3y", "all"]);
export type SummaryWindow = z.infer<typeof summaryWindowSchema>;

/// 一条经验（列表条目，也是详情里的 `experience`）。匿名且访客不是作者时 `author` 为 null；
/// identity 不认识这个人时也是 null（按已注销用户显示）。`helpful` 是访客点过有帮助没有，没有会话时为 null
export const experienceSchema = z.object({
  postId: id,
  submittedOn: date,
  firstDecisionOn: date,
  firstDecision: firstDecisionSchema,
  finalOutcome: finalOutcomeSchema,
  finalDecisionOn: date.nullable(),
  articleType: articleTypeSchema,
  reviewRounds: z.number().int().nullable(),
  reviewQuality: z.number().int().nullable(),
  editorCommunication: z.number().int().nullable(),
  apcPaid: z.boolean().nullable(),
  previouslyRejected: z.boolean().nullable(),
  firstRoundDays: z.number().int(),
  /// 投稿到最终决定；首轮终局时等于首轮用时；修改类没填最终日期时为 null
  totalDays: z.number().int().nullable(),
  body: z.string().nullable(),
  createdAt: z.string(),
  edited: z.boolean(),
  author: cardSchema.nullable(),
  anonymous: z.boolean(),
  mine: z.boolean(),
  helpfulCount: z.number().int().nonnegative(),
  helpful: z.boolean().nullable(),
});
export type Experience = z.infer<typeof experienceSchema>;

/// 经验挂在哪本期刊下：登记时的快照。`key` 是 catalog 期刊 ID；`state` 不是 `ACTIVE` 时期刊页已不在
export const postJournalSchema = z.object({
  key: id.nullable(),
  state: z.string(),
  title: z.string(),
  abbreviation: z.string().nullable(),
});
export type PostJournal = z.infer<typeof postJournalSchema>;

/// `/posts/{id}` 与写、改的返回。已删除时 `experience` 为 null
export const postDetailSchema = z.object({
  postId: id,
  deleted: z.boolean(),
  journal: postJournalSchema,
  experience: experienceSchema.nullable(),
});
export type PostDetail = z.infer<typeof postDetailSchema>;

/// 期刊的一页经验。`total` 是期刊下未删除的经验数（不受分页影响）；后端是 `long`，数字与数字字符串都收
export const experiencePageSchema = z.object({
  items: z.array(experienceSchema),
  nextCursor: z.string().nullable(),
  total: z.coerce.number().int().nonnegative(),
});
export type ExperiencePage = z.infer<typeof experiencePageSchema>;

/// 期刊页汇总。`sufficient` 为假（窗口内不到 5 条）时后四项为 null
export const summarySchema = z.object({
  window: summaryWindowSchema,
  count: z.number().int(),
  allCount: z.number().int(),
  sufficient: z.boolean(),
  firstRound: z
    .object({ median: z.number(), p25: z.number(), p75: z.number(), sampleSize: z.number() })
    .nullable(),
  deskReject: z.object({ median: z.number(), sampleSize: z.number() }).nullable(),
  outcomes: z
    .object({
      accepted: z.number(),
      rejected: z.number(),
      withdrawn: z.number(),
      inProgress: z.number(),
    })
    .nullable(),
  acceptance: z.object({ rate: z.number(), decidedCount: z.number() }).nullable(),
});
export type Summary = z.infer<typeof summarySchema>;

/// 修改历史的一版（不带作者）。第一项是当前版本
export const revisionSchema = experienceSchema
  .pick({
    submittedOn: true,
    firstDecisionOn: true,
    firstDecision: true,
    finalOutcome: true,
    finalDecisionOn: true,
    articleType: true,
    reviewRounds: true,
    reviewQuality: true,
    editorCommunication: true,
    apcPaid: true,
    previouslyRejected: true,
    firstRoundDays: true,
    totalDays: true,
    body: true,
  })
  .extend({ writtenAt: z.string(), current: z.boolean() });
export type Revision = z.infer<typeof revisionSchema>;

export const revisionsSchema = z.object({ items: z.array(revisionSchema) });

/// 主页「投稿经验」标签的一条：`experience.author` 恒为 null（作者就是主页主人）
export const userExperienceSchema = z.object({
  journal: postJournalSchema,
  experience: experienceSchema,
});
export type UserExperience = z.infer<typeof userExperienceSchema>;

export const userExperiencePageSchema = z.object({
  items: z.array(userExperienceSchema),
  nextCursor: z.string().nullable(),
});
export type UserExperiencePage = z.infer<typeof userExperiencePageSchema>;

/// 写、改的请求体。选填没填一律 `null`，不发 `false` / `0`（否则编辑会多出一版）；`anonymous` 只在新建时发
export const experienceRequestSchema = z.object({
  submittedOn: date,
  firstDecisionOn: date,
  firstDecision: firstDecisionSchema,
  finalOutcome: finalOutcomeSchema,
  finalDecisionOn: date.nullable(),
  articleType: articleTypeSchema,
  reviewRounds: z.number().int().nullable(),
  reviewQuality: z.number().int().nullable(),
  editorCommunication: z.number().int().nullable(),
  apcPaid: z.boolean().nullable(),
  previouslyRejected: z.boolean().nullable(),
  body: z.string().nullable(),
  anonymous: z.boolean().optional(),
});
export type ExperienceRequest = z.infer<typeof experienceRequestSchema>;
