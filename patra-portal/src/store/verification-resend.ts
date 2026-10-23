import { create } from "zustand";

interface VerificationResendState {
  /// 什么时候可以再点「重新发送」（毫秒时间戳）；null 表示现在就可以
  availableAt: number | null;
  /// 这次等待是不是被限流（429）：文案不同
  limited: boolean;
  /// 发出去了（或刚注册，首封计入冷却），`until` 之前不能再发
  startCooldown(until: number): void;
  /// 被限流，`until` 之前不能再发
  markLimited(until: number): void;
  reset(): void;
}

/// 重发验证邮件的冷却（简报 §3.4）：横幅、主页提示条（以后还有账号页、验证落地页）共用一份，同一标签页里
/// 换页不丢。整页刷新会丢，这时点了会得到 429，按限流显示（简报 §10 第 8 条）。
export const useVerificationResendStore = create<VerificationResendState>((set) => ({
  availableAt: null,
  limited: false,
  startCooldown: (until) => set({ availableAt: until, limited: false }),
  markLimited: (until) => set({ availableAt: until, limited: true }),
  reset: () => set({ availableAt: null, limited: false }),
}));
