import type { NextResponse } from "next/server";
import { fetchExperienceSummary } from "@/lib/interaction/experience";
import { summaryWindowSchema } from "@/lib/interaction/experience-schema";
import { invalidRequest, readReply } from "@/lib/route-reply";

export const dynamic = "force-dynamic";

/// 期刊页「同行经验」汇总：`window` 缺省是近 3 年，不认识时 422、不请求网关
export async function GET(
  request: Request,
  { params }: { params: Promise<{ id: string }> },
): Promise<NextResponse> {
  const { id } = await params;
  const window = summaryWindowSchema.safeParse(
    new URL(request.url).searchParams.get("window") ?? "3y",
  );
  if (!window.success) {
    return invalidRequest();
  }
  return readReply(await fetchExperienceSummary(id, window.data));
}
