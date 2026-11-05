import { z } from "zod";
import { PROVIDERS } from "@/lib/auth/oauth-providers";

/// `GET /me/login-methods` 的响应体（identity 设计第 2.4 节）。没绑的平台不在 `linked` 里，门户按三家画行
export const loginMethodsSchema = z.object({
  hasPassword: z.boolean(),
  linked: z.array(
    z.object({
      provider: z.enum(PROVIDERS),
      /// Google 是邮箱，GitHub 是 `login`，ORCID 是 iD
      accountLabel: z.string().min(1),
      linkedAt: z.string().min(1),
      lastLoginAt: z.string().nullable(),
    }),
  ),
});

export type LoginMethods = z.infer<typeof loginMethodsSchema>;
export type LinkedMethod = LoginMethods["linked"][number];
