import "server-only";

/// 是不是普通对象，用来安全地读响应体里的字段。
export function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === "object" && value !== null && !Array.isArray(value);
}

/// 读响应体 JSON；不是 JSON（包括空响应体）时返回 undefined。
export async function readJson(res: Response): Promise<unknown> {
  try {
    return await res.json();
  } catch {
    return undefined;
  }
}

/// 网关返回了意外的结果：只记接口、状态码和 ProblemDetail 的 `code`，不记响应体。
export function logUnexpectedResponse(endpoint: string, status: number, body: unknown): void {
  const code = isRecord(body) && typeof body.code === "string" ? body.code : null;
  console.error(`[auth] ${endpoint} 返回了意外的结果`, { status, code });
}

/// 请求网关时抛出了错误（超时、网络错误、缺配置）：只记错误类型和消息。
export function logRequestError(endpoint: string, error: unknown): void {
  const detail = error instanceof Error ? `${error.name}: ${error.message}` : typeof error;
  console.error(`[auth] ${endpoint} 请求失败`, detail);
}
