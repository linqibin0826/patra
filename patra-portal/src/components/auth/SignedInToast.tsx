"use client";

import { useEffect } from "react";
import { toast } from "sonner";
import { TOAST_MESSAGES } from "@/lib/auth/messages";
import { SIGNED_IN_FLAG_COOKIE } from "@/lib/auth/oauth-providers";

/// 第三方登录成功是整页跳转，密码登录那样的提示弹不出来：回调路由留一个短期标记，这里读到就弹「已登录」并清掉。
export function SignedInToast() {
  useEffect(() => {
    if (document.cookie.split("; ").includes(`${SIGNED_IN_FLAG_COOKIE}=1`)) {
      // biome-ignore lint/suspicious/noDocumentCookie: 只清这个没有内容的提示标记，读写都在这一处
      document.cookie = `${SIGNED_IN_FLAG_COOKIE}=; Path=/; Max-Age=0`;
      toast.success(TOAST_MESSAGES.loggedIn);
    }
  }, []);
  return null;
}
