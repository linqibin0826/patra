import "server-only";
import { cache } from "react";
import { z } from "zod";
import { isRecord } from "@/lib/auth/gateway-response";
import { OCCUPATIONS } from "@/lib/identity/occupation";
import { PROFILE_VISIBILITIES } from "@/lib/identity/profile-schema";
import { callIdentity } from "@/lib/identity/request";

/// `GET /users/{username}` 的响应体（PAP-95 / 97）：公开身份卡。不含用户 ID、邮箱与设置原值。
const publicProfileSchema = z.object({
  username: z.string().min(1),
  displayName: z.string().min(1),
  avatarUrl: z.string().nullable(),
  avatarPalette: z.number().int().min(0).max(11),
  /// 主页现在对谁可见；邮箱未验证或账号非 ACTIVE 时一律 SELF
  audience: z.enum(PROFILE_VISIBILITIES),
  /// `YYYY-MM`
  joinedAt: z.string().regex(/^\d{4}-\d{2}$/),
  bio: z.string().nullable(),
  /// 邮箱未验证时为空（本人也一样）
  links: z.array(z.object({ title: z.string().nullable(), url: z.string() })),
  /// 对请求者不可见或四项全空时为 null
  professional: z
    .object({
      occupation: z.enum(OCCUPATIONS).nullable(),
      jobTitle: z.string().nullable(),
      organization: z.object({ rorId: z.string().nullable(), name: z.string() }).nullable(),
      department: z.string().nullable(),
    })
    .nullable(),
  researchInterests: z.array(z.object({ ui: z.string(), name: z.string() })),
  indexable: z.boolean(),
});

export type PublicProfile = z.infer<typeof publicProfileSchema>;

/// 取公开主页的结果：找到了、旧用户名（跳到新名）、对请求者不存在
export type PublicProfileLookup =
  | { kind: "found"; profile: PublicProfile }
  | { kind: "moved"; username: string }
  | { kind: "not-found" };

/// 路径里的用户名先粗筛（大小写不敏感，由后端规范化）：不像用户名的直接当不存在，不打网关
const USERNAME = /^[A-Za-z0-9_-]{1,64}$/;

/// 取公开主页（spec §10.1）：有会话令牌就带上（「仅登录用户」与本人视角都靠它），不跟随重定向。
/// 旧用户名回 301，体里是新用户名（`Location` 缺服务前缀，不读）；404 不区分不存在与不可见；
/// 其余失败抛错交给错误页。用 `cache()` 包住，`generateMetadata` 与页面共用一次请求。
export const fetchPublicProfile = cache(async (username: string): Promise<PublicProfileLookup> => {
  if (!USERNAME.test(username)) {
    return { kind: "not-found" };
  }
  const reply = await callIdentity(`/users/${username}`, {}, { auth: "optional" });
  if (reply.kind === "problem" && reply.problem.status === 404) {
    return { kind: "not-found" };
  }
  if (reply.kind === "ok" && (reply.res.status === 301 || reply.res.status === 308)) {
    const moved = isRecord(reply.body) ? reply.body.username : null;
    if (typeof moved === "string" && USERNAME.test(moved)) {
      return { kind: "moved", username: moved };
    }
  }
  if (reply.kind === "ok" && reply.res.status === 200) {
    const parsed = publicProfileSchema.safeParse(reply.body);
    if (parsed.success) {
      return { kind: "found", profile: parsed.data };
    }
  }
  throw new Error(`读取 /u/${username} 失败`);
});

/// 「加入于 2026 年 10 月」：`joinedAt` 已按 identity 的展示时区算好，直接拆，不再换算时区
export function joinedLabel(joinedAt: string): string {
  const [year, month] = joinedAt.split("-");
  return `加入于 ${year} 年 ${Number(month)} 月`;
}

/// 链接的显示文字：有标题用标题，没有用域名（去掉开头的 `www.`）
export function linkLabel(link: { title: string | null; url: string }): string {
  if (link.title) {
    return link.title;
  }
  const host = /^https?:\/\/([^/?#]+)/i.exec(link.url)?.[1];
  return host ? host.replace(/^www\./i, "") : link.url;
}

/// 只渲染 http(s) 链接：后端已校验，前端再挡一次，以后规则变了也漏不出 `javascript:`
export function isSafeHref(url: string): boolean {
  return /^https?:\/\//i.test(url);
}
