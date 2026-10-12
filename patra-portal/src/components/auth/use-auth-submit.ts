import { useQueryClient } from "@tanstack/react-query";
import { useRouter } from "next/navigation";
import { type ReactNode, startTransition, useRef, useState } from "react";
import { toast } from "sonner";
import { fieldErrorMessages } from "@/lib/auth/messages";
import type { Credentials, CurrentUser, FieldError } from "@/lib/auth/types";
import { queryKeys } from "@/lib/query-keys";

/// action 的 promise 被拒绝时，按服务不可用处理
const UNAVAILABLE = { ok: false, reason: "unavailable" } as const;

interface AuthSubmitOptions<F> {
  /// 成功后跳去的地址，已经过 `safeNextPath`
  next: string;
  /// `login` 或 `register`
  action: (credentials: Credentials) => Promise<{ ok: true; user: CurrentUser } | F>;
  /// 成功提示
  successToast: string;
  /// 失败时调用，拿到失败结果和这次提交的值
  onFailure: (failure: F | typeof UNAVAILABLE, credentials: Credentials) => void;
}

/// 两张表单共用的提交流程，表单通过校验后调用 `submit`（spec 第 9.5、9.6 节）：
/// 1. 提交中再次调用一律忽略，只发一次请求。
/// 2. 先预取目标页，再调用 action。promise 被拒绝时按服务不可用处理。
/// 3. 成功：先取消进行中的当前用户请求，再写入用户，弹成功提示，在 transition 里替换到 `next`。
///    之后一直保持提交中，直到跳转完成、表单卸载。表单提前卸载了，这几步照样做完。
/// 4. 失败：退出提交中，交给 `onFailure`。
export function useAuthSubmit<F extends { ok: false }>({
  next,
  action,
  successToast,
  onFailure,
}: AuthSubmitOptions<F>) {
  const router = useRouter();
  const queryClient = useQueryClient();
  const busy = useRef(false);
  const [submitting, setSubmitting] = useState(false);

  async function submit(credentials: Credentials) {
    if (busy.current) {
      return;
    }
    busy.current = true;
    setSubmitting(true);
    router.prefetch(next);
    const result = await action(credentials).catch(() => UNAVAILABLE);
    if (result.ok) {
      await queryClient.cancelQueries({ queryKey: queryKeys.currentUser });
      queryClient.setQueryData(queryKeys.currentUser, result.user);
      toast.success(successToast);
      startTransition(() => router.replace(next));
      return;
    }
    busy.current = false;
    setSubmitting(false);
    onFailure(result, credentials);
  }

  return { submitting, submit };
}

/// 有后端字段错误的两个字段，按页面上的顺序
const CREDENTIAL_FIELDS = ["email", "password"] as const;
type CredentialField = (typeof CREDENTIAL_FIELDS)[number];

/// 后端返回的一个字段错误，和提交时字段的值绑在一起
interface ServerFieldError {
  value: string;
  message: ReactNode;
}

/// 后端返回的字段错误放在 RHF 之外（spec 第 9.3 节）：字段的值和提交时相同就一直显示，值一改立即
/// 消失。放进 RHF 的错误里不行：用户一移出焦点，失焦校验就会把它清掉。
export function useServerFieldErrors() {
  const [errors, setErrors] = useState<Partial<Record<CredentialField, ServerFieldError>>>({});
  return {
    /// 字段当前的值（邮箱传去掉空白后的值）和提交时相同时，返回它的错误文案
    messageFor(field: CredentialField, value: string): ReactNode {
      const error = errors[field];
      return error?.value === value ? error.message : undefined;
    },
    /// 在一个字段下方显示错误，比如注册时的「该邮箱已注册」
    show(field: CredentialField, value: string, message: ReactNode) {
      setErrors({ [field]: { value, message } });
    },
    /// 显示 `invalid-fields` 里的字段错误，返回第一个出错的字段。一条都对不上时什么也不显示，返回 null
    showFieldErrors(fieldErrors: FieldError[], credentials: Credentials): CredentialField | null {
      const messages = fieldErrorMessages(fieldErrors);
      const shown: Partial<Record<CredentialField, ServerFieldError>> = {};
      for (const field of CREDENTIAL_FIELDS) {
        const message = messages[field];
        if (message !== undefined) {
          shown[field] = { value: credentials[field], message };
        }
      }
      setErrors(shown);
      return CREDENTIAL_FIELDS.find((field) => shown[field] !== undefined) ?? null;
    },
    clear() {
      setErrors({});
    },
  };
}
