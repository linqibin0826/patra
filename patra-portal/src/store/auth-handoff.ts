import { create } from "zustand";

/// 带过去的内容：邮箱，和登录页要显示的提示
export interface AuthHandoff {
  email: string | null;
  notice: "password-reset" | null;
}

interface AuthHandoffState extends AuthHandoff {
  /// 放进去，替换之前的
  put(value: AuthHandoff): void;
  /// 取出并清空：读一次就没了，刷新页面或再进一次都不会重复出现
  take(): AuthHandoff;
}

const EMPTY: AuthHandoff = { email: null, notice: null };

/// 登录页、忘记密码页、重设密码页之间带数据（spec §8.1，简报 §2.2 规则 6）：登录页已输入的邮箱带到忘记密码页；
/// 重设成功后的邮箱与「密码已重设」提示带到登录页。只在内存里，邮箱不进地址栏。
export const useAuthHandoffStore = create<AuthHandoffState>((set, get) => ({
  ...EMPTY,
  put: (value) => set(value),
  take: () => {
    const { email, notice } = get();
    set(EMPTY);
    return { email, notice };
  },
}));
