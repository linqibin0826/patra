import { RADIO_INPUT } from "@/lib/portal-ui";
import { cn } from "@/lib/utils";

interface RadioRowProps {
  name: string;
  title: string;
  description: string;
  checked: boolean;
  onSelect: () => void;
  /// 标题后的小字标记，比如通知设置里出厂档位旁的「默认」（选了别的档也一直显示）
  badge?: string;
  /// 追加在行上的 class：通知设置照画板 `.pt-radio-row` 用 12px 上下留白（隐私页保持 8px）
  className?: string;
}

/// 设置页单选的一行：标签 + 一行说明（隐私、通知两页共用）
export function RadioRow({
  name,
  title,
  description,
  checked,
  onSelect,
  badge,
  className,
}: RadioRowProps) {
  return (
    <label className={cn("flex cursor-pointer items-start gap-3 py-2", className)}>
      <input
        type="radio"
        name={name}
        checked={checked}
        onChange={onSelect}
        className={`${RADIO_INPUT} mt-0.5`}
      />
      <div className="flex flex-col gap-0.5">
        <div className="text-base leading-5 font-medium text-fg-1">
          {title}
          {badge && <span className="ml-2 text-xs font-normal text-fg-3">{badge}</span>}
        </div>
        <div className="text-sm leading-5 text-fg-3">{description}</div>
      </div>
    </label>
  );
}
