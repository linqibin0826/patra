import type { HumanVerificationState } from "@/components/auth/human-verification/use-human-verification";
import type { HumanVerificationConfig } from "@/lib/auth/types";

/// 替身：显示当前的重置次数，点按钮才“通过验证”。令牌带着重置次数，能看出提交的是不是新令牌。
export function HumanVerificationWidget({
  config,
  action,
  verification,
}: {
  config: HumanVerificationConfig | null | undefined;
  action: string;
  verification: HumanVerificationState;
}) {
  if (!verification.required || config === undefined) {
    return null;
  }
  const { resetKey, onToken } = verification.widgetProps;
  return (
    <div>
      <span>
        组件 {config === null ? "不可用" : config.provider} {action}
      </span>
      {config !== null && (
        <button type="button" onClick={() => onToken(`token-${resetKey}`)}>
          通过验证
        </button>
      )}
    </div>
  );
}
