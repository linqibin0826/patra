import { z } from "zod";

/// 提醒邮件的三档：关、离站时逐条、每日汇总（PAP-142 第 5.1 节）
export const NOTIFICATION_MODES = ["OFF", "INSTANT", "DIGEST"] as const;
/// 设置页能改的两类（「有帮助」不进偏好）
export const PREFERENCE_CATEGORIES = ["REPLY", "MENTION"] as const;
/// 邮件链接里的类别：逐条邮件带 `REPLY` 或 `MENTION`，汇总邮件带 `DIGEST`
export const LINK_CATEGORIES = ["REPLY", "MENTION", "DIGEST"] as const;
/// 落地页能提交的类别：链接里的类别，加「关闭全部」的 `ALL`
export const UNSUBSCRIBE_CATEGORIES = [...LINK_CATEGORIES, "ALL"] as const;

export type NotificationMode = (typeof NOTIFICATION_MODES)[number];
export type PreferenceCategory = (typeof PREFERENCE_CATEGORIES)[number];
export type LinkCategory = (typeof LINK_CATEGORIES)[number];
export type UnsubscribeCategory = (typeof UNSUBSCRIBE_CATEGORIES)[number];

export const notificationModeSchema = z.enum(NOTIFICATION_MODES);

/// 两类当前的档位（偏好读写的响应）
export const preferencesSchema = z.object({
  reply: notificationModeSchema,
  mention: notificationModeSchema,
});
export type NotificationPreferences = z.infer<typeof preferencesSchema>;

/// 退订落地页读到的链接信息（PAP-142 第 8.2 节）
export const unsubscribeLinkSchema = z.object({
  maskedEmail: z.string().min(1),
  category: z.enum(LINK_CATEGORIES),
  reply: notificationModeSchema,
  mention: notificationModeSchema,
  alreadyUnsubscribed: z.boolean(),
  othersEnabled: z.boolean(),
});
export type UnsubscribeLink = z.infer<typeof unsubscribeLinkSchema>;

/// 一次退订的结果（PAP-142 第 8.3 节）：`turnedOff` 是这一次真的从开变成关的类别，重复退订时为空
export const unsubscribeResultSchema = z.object({
  category: z.enum(UNSUBSCRIBE_CATEGORIES),
  turnedOff: z.array(z.enum(PREFERENCE_CATEGORIES)),
  reply: notificationModeSchema,
  mention: notificationModeSchema,
  othersEnabled: z.boolean(),
});
export type UnsubscribeResult = z.infer<typeof unsubscribeResultSchema>;
