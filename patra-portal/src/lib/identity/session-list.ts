import { z } from "zod";

/// 登录设备列表里的一项（`GET /me/sessions`，PAP-99）。`id` 是雪花 ID，后端按字符串序列化（数字会超出 JS 的
/// 安全整数范围、解析时就丢精度，所以不接受数字）。
export const sessionSchema = z.object({
  id: z.string().regex(/^\d{1,19}$/),
  current: z.boolean(),
  deviceName: z.string().nullable(),
  deviceClass: z.enum(["DESKTOP", "MOBILE", "TABLET", "UNKNOWN"]),
  ipAddress: z.string().nullable(),
  ipRegion: z.string().nullable(),
  privateNetwork: z.boolean(),
  signedInAt: z.string().min(1),
  lastActiveAt: z.string().min(1),
});

export const sessionListSchema = z.object({ sessions: z.array(sessionSchema) });

export type SessionItem = z.output<typeof sessionSchema>;
