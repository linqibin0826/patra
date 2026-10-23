import { z } from "zod";

/// 待确认的改邮箱（`GET /me/email/change` 的 200、`POST /me/email/change` 的 202）：新邮箱与确认链接的过期时间
export const pendingEmailChangeSchema = z.object({
  newEmail: z.string().min(1),
  expiresAt: z.string().min(1),
});

export type PendingEmailChange = z.infer<typeof pendingEmailChangeSchema>;
