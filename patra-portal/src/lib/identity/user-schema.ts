import { z } from "zod";

/// 登录、注册响应里的 `user` 与 `/auth/me` 的响应体（三者同构）里门户要用的字段，`accountType` 不读。
/// `status` 只认这两个：封禁、已注销的账号到不了这里（网关或 identity 直接 401）。日期是 ISO 字符串，原样保留。
export const userSchema = z.object({
  userId: z.string().min(1),
  email: z.string().min(1),
  emailVerified: z.boolean(),
  username: z.string().min(1),
  /// 永远非空：没设昵称时 identity 回退成用户名
  displayName: z.string().min(1),
  /// identity 的相对路径 `/users/{用户名}/avatar?v={哈希}`，没有头像时为 null
  avatarUrl: z.string().nullable(),
  /// 默认头像的色号，按用户 ID 算，有没有头像都给
  avatarPalette: z.number().int().min(0).max(11),
  status: z.enum(["ACTIVE", "PENDING_DELETION"]),
  deletionDueAt: z.string().nullable(),
  /// 上次改名 + 30 天；从没改过为 null，冷却过后仍是那个过去的时间
  nextUsernameChangeAt: z.string().nullable(),
});

/// 当前登录的用户。`userId` 是字符串：后端的雪花 ID 超出 JS 的安全整数范围。
export type CurrentUser = z.infer<typeof userSchema>;
