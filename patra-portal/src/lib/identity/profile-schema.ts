import { z } from "zod";
import { OCCUPATIONS } from "@/lib/identity/occupation";

/// `GET /me/profile` 的响应体（PAP-95）：本人资料的原值。`displayName` 为 null 表示跟随用户名；
/// `organization` 有 ROR（`rorId` + 名称快照）、手填（`rorId` 为 null）、没有（null）三种。
export const myProfileSchema = z.object({
  displayName: z.string().nullable(),
  bio: z.string().nullable(),
  links: z.array(z.object({ title: z.string().nullable(), url: z.string() })),
  occupation: z.enum(OCCUPATIONS).nullable(),
  jobTitle: z.string().nullable(),
  organization: z.object({ rorId: z.string().nullable(), name: z.string() }).nullable(),
  department: z.string().nullable(),
  /// MeSH 主题词：UI + 保存时的英文名快照，按用户排好的顺序
  researchInterests: z.array(z.object({ ui: z.string(), name: z.string() })),
});

export type MyProfile = z.infer<typeof myProfileSchema>;

export const PROFILE_VISIBILITIES = ["PUBLIC", "SIGNED_IN", "SELF"] as const;
export type ProfileVisibility = (typeof PROFILE_VISIBILITIES)[number];

/// `GET /me/privacy` 的响应体，也是 `PUT` 的请求体（三项必填）
export const privacySettingsSchema = z.object({
  profileVisibility: z.enum(PROFILE_VISIBILITIES),
  professionalVisibility: z.enum(["PUBLIC", "SELF"]),
  searchIndexing: z.boolean(),
});

export type PrivacySettings = z.infer<typeof privacySettingsSchema>;
