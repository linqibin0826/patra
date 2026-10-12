import { useMutation, useQueryClient } from "@tanstack/react-query";
import { toast } from "sonner";
import { logout } from "@/lib/auth/actions";
import { TOAST_MESSAGES } from "@/lib/auth/messages";
import { queryKeys } from "@/lib/query-keys";

/// 退出登录，桌面的用户菜单和移动端的账号块共用。点了就退，不二次确认；进行中 `isPending` 为 true。
/// - 成功：先取消进行中的当前用户请求，再写入 null，弹「已退出登录」。不先取消的话，一个成功前发出、
///   成功后才回来的 `/api/auth/me` 会把用户写回去。
/// - promise 被拒绝：不写 null，让当前用户重新请求一次，以门户的回答为准；不提示（简报 §4.3）。
export function useLogout() {
  const queryClient = useQueryClient();
  return useMutation({
    // 不直接传 `logout`：useMutation 会把变量和上下文作为参数传进来，Server Action 会把参数序列化发给服务端
    mutationFn: () => logout(),
    onSuccess: async () => {
      await queryClient.cancelQueries({ queryKey: queryKeys.currentUser });
      queryClient.setQueryData(queryKeys.currentUser, null);
      toast.success(TOAST_MESSAGES.loggedOut);
    },
    onError: () => {
      void queryClient.invalidateQueries({ queryKey: queryKeys.currentUser });
    },
  });
}
