import { CircleAlert } from "lucide-react";
import type { ReactNode } from "react";

/// 交给输入框的属性：`id` 和标签关联，`aria-describedby` 指向下方当前显示的那一行
export interface FieldControl {
  id: string;
  className: string;
  "aria-invalid": boolean;
  "aria-describedby"?: string;
}

interface FormFieldProps {
  id: string;
  label: string;
  /// 标签后面的「（可选）」
  optional?: boolean;
  /// 标签行右端的内容，比如字数计数「22 / 200」
  aside?: ReactNode;
  /// 常驻提示，比如注册页密码的「至少 8 位」。有错误时被错误文案替换
  hint?: ReactNode;
  /// 错误文案，可以带链接（「该邮箱已注册，去登录」）
  error?: ReactNode;
  /// 控件自带外框（下拉、日期、数字原语）时不再套输入井
  bare?: boolean;
  children: (control: FieldControl) => ReactNode;
}

/// 输入框本身透明、无描边，描边和聚焦光晕画在外框上（全局焦点环不管文本框）。移动端字号 17px，
/// iOS 聚焦时不放大页面。自动填充后的底色由 globals.css 的 `input:-webkit-autofill` 规则处理。
export const FIELD_INPUT =
  "h-full min-w-0 flex-1 resize-none bg-transparent p-0 text-md leading-6 text-ink-900 outline-none placeholder:text-fg-4 read-only:text-fg-2 disabled:text-fg-2 max-[880px]:text-lg";

/// 外框：内凹的输入井。悬停加深描边；输入框聚焦时换描边、加光晕；出错时描边变红，聚焦光晕也换成
/// 红色；提交中只读时换凹陷底色，去掉内阴影和光晕。三种状态都按输入框判断：切换按钮聚焦时外框不亮
/// （高保真稿 Spec-Form 02）。不用 `focus-within:`：它生成在 `hover:` 之前，悬停加聚焦时描边会是
/// 悬停色；`has-[…]` 生成在 `hover:` 之后，彼此按方括号里的字母序，只读 > 出错 > 聚焦（三者都写成
/// `:is(input,textarea,select)`，前缀相同，排序只看后半段）。多行文本框外框高度随内容。
/// 里面有切换按钮时右边距收窄，移动端按钮 44px，收得更窄。
export const FIELD_BOX =
  "flex h-12 items-center gap-1 rounded-xl border border-border-strong bg-bg-elevated px-4 shadow-inset ring-clay-500/15 transition-[border-color,box-shadow,background-color] duration-200 hover:border-ink-500 has-[button]:pr-2 has-[select]:pr-3 has-[textarea]:h-auto has-[textarea]:items-start has-[textarea]:py-3 max-[880px]:has-[button]:pr-0.5 has-[:is(input,textarea,select):focus]:border-border-focus has-[:is(input,textarea,select):focus]:ring-4 has-[:is(input,textarea,select)[aria-invalid=true]]:border-status-danger has-[:is(input,textarea,select)[aria-invalid=true]]:ring-rust-500/15 has-[:is(input,textarea,select)[readonly]]:border-border-default has-[:is(input,textarea,select)[readonly]]:bg-bg-sunken has-[:is(input,textarea,select)[readonly]]:shadow-none has-[:is(input,textarea,select)[readonly]]:ring-0";

/// 表单字段：标签、输入框，下面一行是常驻提示或错误文案，错误替换提示，两者不同时出现。
/// 输入框由 `children` 渲染，拿到 `id`、`aria-invalid`、`aria-describedby` 和样式。
/// 错误行出现时淡入下落，参数和折叠区相同，直接用 `.disclosure-body`（高保真稿 Spec-Form 07）。
/// 错误行和提示行的 `key` 不同：错误替换提示时节点重新插入，入场过渡才会触发。
export function FormField({
  id,
  label,
  optional,
  aside,
  hint,
  error,
  bare,
  children,
}: FormFieldProps) {
  const messageId = error ? `${id}-error` : hint ? `${id}-hint` : undefined;
  const control = children({
    id,
    className: FIELD_INPUT,
    "aria-invalid": Boolean(error),
    "aria-describedby": messageId,
  });
  return (
    <div className="flex min-w-0 flex-col gap-2">
      <div className="flex items-baseline justify-between gap-3">
        <label htmlFor={id} className="font-medium text-fg-1 text-sm leading-5">
          {label}
          {optional && <span className="font-normal text-fg-3">（可选）</span>}
        </label>
        {aside}
      </div>
      {bare ? control : <div className={FIELD_BOX}>{control}</div>}
      {error ? (
        <p
          key="error"
          id={messageId}
          className="disclosure-body flex items-start gap-1.5 text-sm text-status-danger leading-5"
        >
          <CircleAlert className="mt-0.75 size-3.5 shrink-0" aria-hidden="true" />
          <span>{error}</span>
        </p>
      ) : hint ? (
        <p key="hint" id={messageId} className="text-fg-3 text-sm leading-5">
          {hint}
        </p>
      ) : null}
    </div>
  );
}
