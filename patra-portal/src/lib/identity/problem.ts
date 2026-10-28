import "server-only";
import { isRecord } from "@/lib/auth/gateway-response";
import type { FieldError } from "@/lib/auth/types";

/// 网关或 identity 返回的 ProblemDetail 里门户要用的部分。字段缺失时取安全的默认值。
export interface Problem {
  status: number;
  /// 错误码，如 `IDN-0409`、`GW-0401`
  code: string | null;
  /// 语义特征。后端按集合序列化，顺序不定，只能按「包含」判断
  traits: ReadonlySet<string>;
  /// 字段错误：每项 `field` 必须是字符串，`code` 不是字符串时记为 null
  errors: FieldError[];
  /// 429 时还要等多少秒：先取响应体的 `retryAfterSeconds`，再取 `Retry-After` 头，都要是非负整数
  retryAfterSeconds: number | null;
  /// interaction 409 `DUPLICATE_SUBMISSION`（同刊同投稿日期已有一条经验）时那条经验的 ID；其余情况为 null
  existingPostId: string | null;
}

/// 非负整数，否则 null
function nonNegativeInteger(value: unknown): number | null {
  return typeof value === "number" && Number.isInteger(value) && value >= 0 ? value : null;
}

/// 解析一次失败的响应。`body` 是已经读出的响应体（不是 JSON 时为 undefined）。
export function parseProblem(res: Response, body: unknown): Problem {
  const record = isRecord(body) ? body : {};
  const traits = Array.isArray(record.traits)
    ? record.traits.filter((trait): trait is string => typeof trait === "string")
    : [];
  const errors = Array.isArray(record.errors)
    ? record.errors.flatMap((item): FieldError[] =>
        isRecord(item) && typeof item.field === "string"
          ? [{ field: item.field, code: typeof item.code === "string" ? item.code : null }]
          : [],
      )
    : [];
  const header = res.headers.get("Retry-After");
  return {
    status: res.status,
    code: typeof record.code === "string" ? record.code : null,
    traits: new Set(traits),
    errors,
    retryAfterSeconds:
      nonNegativeInteger(record.retryAfterSeconds) ??
      (header !== null && /^\d+$/.test(header) ? Number(header) : null),
    existingPostId: typeof record.existingPostId === "string" ? record.existingPostId : null,
  };
}

/// 会话失效：401 且不是「密码不对」。`PASSWORD_MISMATCH` 的 401 出在再认证、改密码，会话仍有效，不能登出。
export function isSessionRejected(problem: Problem): boolean {
  return problem.status === 401 && !problem.traits.has("PASSWORD_MISMATCH");
}
