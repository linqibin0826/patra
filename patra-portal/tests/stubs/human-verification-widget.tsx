import { useEffect } from "react";
import type { HumanVerificationState } from "@/components/auth/human-verification/use-human-verification";

/// 替身：每次重置后立刻“通过验证”。给不关心人机验证的表单测试用。
export function HumanVerificationWidget({
  verification,
}: {
  verification: HumanVerificationState;
}) {
  const { resetKey, onToken } = verification.widgetProps;
  // biome-ignore lint/correctness/useExhaustiveDependencies: 只在重置时发一次令牌
  useEffect(() => {
    onToken(`token-${resetKey}`);
  }, [resetKey]);
  return null;
}
