import { useState } from "react";

/// Turnstile 和 TRUSTTURN 两个组件共用的回调和重置信号。
export interface HumanVerificationWidgetProps {
  /// 变了就重新验证
  resetKey: number;
  onToken: (token: string) => void;
  /// 令牌过期了（Turnstile 的过期回调，或 TRUSTTURN 票据到期），表单据此作废已拿到的令牌
  onClear: () => void;
  onLoadError: () => void;
}

export interface HumanVerificationState {
  /// 这次提交要不要先过验证
  required: boolean;
  token: string | null;
  loadFailed: boolean;
  /// 要验证但还没拿到令牌：不能提交
  blocked: boolean;
  /// 后端要求验证时调用，之后一直显示组件
  demand: () => void;
  /// 令牌只能用一次，任何失败响应之后都要调用
  reset: () => void;
  widgetProps: HumanVerificationWidgetProps;
}

/// 表单用的人机验证状态。
export function useHumanVerification(initiallyRequired: boolean): HumanVerificationState {
  const [required, setRequired] = useState(initiallyRequired);
  const [token, setToken] = useState<string | null>(null);
  const [resetKey, setResetKey] = useState(0);
  const [loadFailed, setLoadFailed] = useState(false);
  return {
    required,
    token,
    loadFailed,
    blocked: required && token === null,
    demand: () => setRequired(true),
    reset: () => {
      setToken(null);
      setResetKey((key) => key + 1);
    },
    widgetProps: {
      resetKey,
      onToken: (value) => {
        setToken(value);
        setLoadFailed(false);
      },
      onClear: () => setToken(null),
      onLoadError: () => setLoadFailed(true),
    },
  };
}
