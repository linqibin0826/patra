import "server-only";
import { z } from "zod";
import { READ_TIMEOUT_MS, type Read, toRead } from "@/lib/interaction/read";
import { callInteraction } from "@/lib/interaction/request";
import { type ExportKeys, exportKeysSchema } from "@/lib/interaction/schema";
import { isEntityId, LIBRARY_SORTS } from "@/lib/library/params";

const entityId = z.string().refine(isEntityId);
const sort = z.enum(LIBRARY_SORTS);

/// 导出 / 复制引用的整体范围（单篇、选中的由页面自己给 key，不经这里）
export const exportScopeSchema = z.discriminatedUnion("kind", [
  z.object({ kind: z.literal("library"), view: z.enum(["all", "unfiled"]), sort }),
  z.object({ kind: z.literal("collection"), id: entityId, sort }),
  z.object({ kind: z.literal("public-collection"), id: entityId, sort }),
]);
export type ExportScope = z.infer<typeof exportScopeSchema>;

/// 范围里的文献 ID（顺序同该排序下的列表）与跳过的已失效篇数；超 5,000 篇是 `too-large`。
/// 公开收藏夹的范围访客也能取（2026-10-24 用户定），有会话就带。
export async function fetchScopeKeys(
  scope: ExportScope,
): Promise<Read<ExportKeys> | { kind: "too-large" }> {
  const path =
    scope.kind === "library"
      ? `/me/library/keys?view=${scope.view}&sort=${scope.sort}`
      : scope.kind === "collection"
        ? `/me/collections/${scope.id}/keys?sort=${scope.sort}`
        : `/collections/${scope.id}/keys?sort=${scope.sort}`;
  const reply = await callInteraction(
    path,
    { timeoutMs: READ_TIMEOUT_MS },
    { auth: scope.kind === "public-collection" ? "optional" : "required" },
  );
  if (
    reply.kind === "problem" &&
    reply.problem.status === 422 &&
    reply.problem.traits.has("EXPORT_TOO_LARGE")
  ) {
    return { kind: "too-large" };
  }
  return toRead(reply, exportKeysSchema, path);
}
