import type { HumanVerificationState } from "@/components/auth/human-verification/use-human-verification";
import type { HumanVerificationConfig } from "@/lib/auth/types";

/// 替身：显示厂商和 action，点按钮才“通过验证”。发出的令牌是 `token-<重置次数>`，测试靠它判断提交的是不是重置后的新令牌。
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
