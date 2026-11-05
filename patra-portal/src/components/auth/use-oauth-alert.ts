import { useEffect, useState } from "react";
import type { OAuthAlert } from "@/lib/auth/messages";
import type { OAuthNotice } from "@/lib/auth/oauth-notice";
import { writeQuery } from "@/lib/write-query";

/// 从第三方回到发起页时的提示条。地址栏的两个参数显示后就清掉，刷新不再出现；用户开始下一次操作时调 `dismiss` 收起。
/// `toAlert` 决定文案，返回 null 时没有提示条（绑定成功是 toast）。
export function useOAuthAlert(
  notice: OAuthNotice | null,
  toAlert: (notice: OAuthNotice) => OAuthAlert | null,
): { oauthAlert: OAuthAlert | null; dismiss: () => void } {
  const [oauthAlert, setAlert] = useState(() => (notice ? toAlert(notice) : null));
  const arrived = notice !== null;
  useEffect(() => {
    if (arrived) {
      writeQuery((query) => {
        query.delete("oauth");
        query.delete("provider");
      }, "replace");
    }
  }, [arrived]);
  return { oauthAlert, dismiss: () => setAlert(null) };
}
