"use client";

import { FormAlert } from "@/components/auth/FormAlert";
import { TrustTurnWidget } from "@/components/auth/human-verification/TrustTurnWidget";
import { TurnstileWidget } from "@/components/auth/human-verification/TurnstileWidget";
import type { HumanVerificationState } from "@/components/auth/human-verification/use-human-verification";
import { VERIFICATION_MESSAGES } from "@/lib/auth/messages";
import type { HumanVerificationAction, HumanVerificationConfig } from "@/lib/auth/types";

interface HumanVerificationWidgetProps {
  /// 当前厂商。`undefined` 是还在读取，`null` 是读不到
  config: HumanVerificationConfig | null | undefined;
  action: HumanVerificationAction;
  verification: HumanVerificationState;
}

/// 按厂商选实现。不要求验证时什么都不渲染，也就不会碰第三方资源。
export function HumanVerificationWidget({
  config,
  action,
  verification,
}: HumanVerificationWidgetProps) {
  if (!verification.required || config === undefined) {
    return null;
  }
  if (config === null) {
    return <FormAlert>{VERIFICATION_MESSAGES.unavailable}</FormAlert>;
  }
  const Widget = config.provider === "trustturn" ? TrustTurnWidget : TurnstileWidget;
  return (
    <div data-human-verification className="flex flex-col gap-2">
      <Widget siteKey={config.siteKey} action={action} {...verification.widgetProps} />
      {verification.loadFailed && <FormAlert>{VERIFICATION_MESSAGES.loadFailed}</FormAlert>}
    </div>
  );
}
