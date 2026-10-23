import { useQueryClient } from "@tanstack/react-query";
import { useRouter } from "next/navigation";
import { type ReactNode, startTransition, useRef, useState } from "react";
import { toast } from "sonner";
import { type CredentialField, fieldErrorMessages } from "@/lib/auth/messages";
import { deletionPendingHref } from "@/lib/auth/next-path";
import type { CurrentUser, FieldError } from "@/lib/auth/types";
import { queryKeys } from "@/lib/query-keys";

/// action 的 promise 被拒绝时，按服务不可用处理
const UNAVAILABLE = { ok: false, reason: "unavailable" } as const;

interface AuthSubmitOptions<I, F> {
  /// 成功后跳去的地址，已经过 `safeNextPath`
  next: string;
  /// `login` 或 `register`
  action: (input: I) => Promise<{ ok: true; user: CurrentUser } | F>;
  /// 成功提示
  successToast: string;
  /// 成功、跳转之前调用，比如注册后开始重发验证邮件的冷却
  onSuccess?: (user: CurrentUser) => void;
  /// 失败时调用，拿到失败结果和这次提交的值
  onFailure: (failure: F | typeof UNAVAILABLE, input: I) => void;
}

/// 两张表单共用的提交流程，表单通过校验后调用 `submit`（spec 第 9.5、9.6 节）：
/// 1. 提交中再次调用一律忽略，只发一次请求。
/// 2. 先预取目标页，再调用 action。promise 被拒绝时按服务不可用处理。
/// 3. 成功：先取消进行中的当前用户请求，再写入用户，弹成功提示，在 transition 里替换到 `next`。
///    冷静期账号（`status` 是 `PENDING_DELETION`）不弹提示，替换到取消注销页（简报 §2.2 规则 2）。
///    之后一直保持提交中，直到跳转完成、表单卸载。表单提前卸载了，这几步照样做完。
/// 4. 失败：退出提交中，交给 `onFailure`。
export function useAuthSubmit<I, F extends { ok: false }>({
  next,
  action,
  successToast,
  onSuccess,
  onFailure,
}: AuthSubmitOptions<I, F>) {
  const router = useRouter();
  const queryClient = useQueryClient();
  const busy = useRef(false);
  const [submitting, setSubmitting] = useState(false);

  async function submit(input: I) {
    if (busy.current) {
      return;
    }
    busy.current = true;
    setSubmitting(true);
    router.prefetch(next);
    const result = await action(input).catch(() => UNAVAILABLE);
    if (result.ok) {
      await queryClient.cancelQueries({ queryKey: queryKeys.currentUser });
      queryClient.setQueryData(queryKeys.currentUser, result.user);
      onSuccess?.(result.user);
      if (result.user.status === "PENDING_DELETION") {
        startTransition(() => router.replace(deletionPendingHref(next)));
        return;
      }
      toast.success(successToast);
      startTransition(() => router.replace(next));
      return;
    }
    busy.current = false;
    setSubmitting(false);
    onFailure(result, input);
  }

  return { submitting, submit };
}

/// 有后端字段错误的字段，按页面上的顺序
const CREDENTIAL_FIELDS: readonly CredentialField[] = ["email", "username", "password"];

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
    /// 在这些字段下方显示错误（比如注册时的「该邮箱已注册」「这个用户名已被使用」），替换之前的全部
    show(shown: Partial<Record<CredentialField, ServerFieldError>>) {
      setErrors(shown);
    },
    /// 显示 `invalid-fields` 里的字段错误，返回第一个出错的字段。一条都对不上时什么也不显示，返回 null
    /// `values` 只有表单里有的字段（登录页没有用户名），返回值也只会是其中之一
    showFieldErrors<K extends CredentialField>(
      fieldErrors: FieldError[],
      values: Partial<Record<K, string>>,
    ): K | null {
      const messages = fieldErrorMessages(fieldErrors);
      const shown: Partial<Record<CredentialField, ServerFieldError>> = {};
      let first: K | null = null;
      for (const field of CREDENTIAL_FIELDS.filter((f): f is K => Object.hasOwn(values, f))) {
        const message = messages[field];
        const value = values[field];
        if (message !== undefined && value !== undefined) {
          shown[field] = { value, message };
          first ??= field;
        }
      }
      setErrors(shown);
      return first;
    },
    clear() {
      setErrors({});
    },
  };
}
