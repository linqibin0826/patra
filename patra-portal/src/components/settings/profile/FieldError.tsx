import { CircleAlert } from "lucide-react";
import type { ReactNode } from "react";

/// 字段下方的错误行，样式同 FormField 的错误行（选择器、链接行这类不走 FormField 的字段用）
export function FieldError({ id, children }: { id?: string; children: ReactNode }) {
  return (
    <p
      id={id}
      className="disclosure-body flex items-start gap-1.5 text-sm text-status-danger leading-5"
    >
      <CircleAlert className="mt-0.75 size-3.5 shrink-0" aria-hidden="true" />
      <span>{children}</span>
    </p>
  );
}
