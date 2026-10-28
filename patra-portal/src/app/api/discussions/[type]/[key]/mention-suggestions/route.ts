import type { NextResponse } from "next/server";
import { fetchMentionSuggestions, isSubject } from "@/lib/interaction/discussion";
import type { SubjectType } from "@/lib/interaction/discussion-schema";
import { readReply } from "@/lib/route-reply";

export const dynamic = "force-dynamic";

/// @ 联想（要登录）：本讨论的参与者在前，再接全站联想；`q` 是 `@` 后面已输入的原文
export async function GET(
  request: Request,
  { params }: { params: Promise<{ type: string; key: string }> },
): Promise<NextResponse> {
  const { type, key } = await params;
  if (!isSubject(type, key)) {
    return readReply({ kind: "not-found" });
  }
  const q = new URL(request.url).searchParams.get("q") ?? "";
  return readReply(await fetchMentionSuggestions({ type: type as SubjectType, key }, q));
}
