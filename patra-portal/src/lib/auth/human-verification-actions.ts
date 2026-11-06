"use server";

import { readHumanVerification } from "@/lib/auth/human-verification";
import type { HumanVerificationConfig } from "@/lib/auth/types";

/// 给对话框用：它所在的页面很多，不值得每个页面都在服务端读一次。
export async function fetchHumanVerification(): Promise<HumanVerificationConfig | null> {
  return readHumanVerification();
}
