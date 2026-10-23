"use client";

import { Menu as MenuPrimitive } from "@base-ui/react/menu";
import { cn } from "@/lib/utils";

// shadcn base-nova 的 dropdown-menu，只留门户用到的部件，颜色、圆角、阴影和动效换成 token
// （高保真稿 Spec-Nav）。没用到的子菜单、勾选项、单选项没有画稿可以对照，不带。

function DropdownMenu({ ...props }: MenuPrimitive.Root.Props) {
  return <MenuPrimitive.Root data-slot="dropdown-menu" {...props} />;
}

function DropdownMenuTrigger({ ...props }: MenuPrimitive.Trigger.Props) {
  return <MenuPrimitive.Trigger data-slot="dropdown-menu-trigger" {...props} />;
}

/// 弹层：默认在触发器下方 8px；宽度取内容宽，240 ~ 320px。入场淡入、自上方 8px 落位并从 0.98
/// 缩放到 1，和快速检索相同；减弱动效时只淡入。阴影是 shadow-lg（它自带 1px 发丝描边），不另加描边。
function DropdownMenuContent({
  align = "start",
  alignOffset = 0,
  side = "bottom",
  sideOffset = 8,
  className,
  ...props
}: MenuPrimitive.Popup.Props &
  Pick<MenuPrimitive.Positioner.Props, "align" | "alignOffset" | "side" | "sideOffset">) {
  return (
    <MenuPrimitive.Portal>
      <MenuPrimitive.Positioner
        className="isolate z-50 outline-none"
        align={align}
        alignOffset={alignOffset}
        side={side}
        sideOffset={sideOffset}
      >
        <MenuPrimitive.Popup
          data-slot="dropdown-menu-content"
          className={cn(
            "max-h-(--available-height) w-max min-w-60 max-w-80 origin-(--transform-origin) overflow-x-hidden overflow-y-auto rounded-lg bg-bg-elevated p-1.5 text-fg-1 shadow-lg outline-none transition-opacity motion-safe:transition-[opacity,scale,translate] duration-300 ease-out-expo data-starting-style:opacity-0 motion-safe:data-starting-style:-translate-y-2 motion-safe:data-starting-style:scale-[0.98] data-ending-style:opacity-0 motion-safe:data-ending-style:-translate-y-1 motion-safe:data-ending-style:scale-[0.98]",
            className,
          )}
          {...props}
        />
      </MenuPrimitive.Positioner>
    </MenuPrimitive.Portal>
  );
}

function DropdownMenuGroup({ ...props }: MenuPrimitive.Group.Props) {
  return <MenuPrimitive.Group data-slot="dropdown-menu-group" {...props} />;
}

/// 分组的标题，不是菜单项，方向键不会停在这里；它的文字就是分组的读屏名称。
function DropdownMenuLabel({ className, ...props }: MenuPrimitive.GroupLabel.Props) {
  return (
    <MenuPrimitive.GroupLabel
      data-slot="dropdown-menu-label"
      className={cn("min-w-0 px-2.5 pt-2 pb-2.5", className)}
      {...props}
    />
  );
}

/// 菜单项的样式，普通项与链接项共用
const ITEM =
  "relative flex h-9 w-full cursor-pointer items-center gap-2.5 rounded-md px-2.5 text-left text-base leading-5 text-fg-1 no-underline outline-hidden transition-colors select-none data-highlighted:not-data-disabled:bg-bg-sunken data-disabled:cursor-not-allowed data-disabled:opacity-55 [&_svg]:pointer-events-none [&_svg]:shrink-0 [&_svg:not([class*='size-'])]:size-4";

/// 菜单项：悬停或方向键高亮时换成凹陷底色。禁用时 55% 不透明度、不可点的光标，不关掉指针事件：
/// 关掉的话 `title`（比如「正在退出登录」）就不会显示。base-ui 的禁用项仍能被方向键高亮，
/// 所以高亮底色要排除禁用项。
function DropdownMenuItem({ className, ...props }: MenuPrimitive.Item.Props) {
  return (
    <MenuPrimitive.Item data-slot="dropdown-menu-item" className={cn(ITEM, className)} {...props} />
  );
}

/// 链接菜单项：样式同菜单项，点了关闭菜单。站内链接用 `render={<Link href=… />}` 交给 Next 跳转。
function DropdownMenuLinkItem({ className, ...props }: MenuPrimitive.LinkItem.Props) {
  return (
    <MenuPrimitive.LinkItem
      data-slot="dropdown-menu-link-item"
      closeOnClick
      className={cn(ITEM, className)}
      {...props}
    />
  );
}

/// 分隔线：左右顶到弹层边缘，下方留 6px。
function DropdownMenuSeparator({ className, ...props }: MenuPrimitive.Separator.Props) {
  return (
    <MenuPrimitive.Separator
      data-slot="dropdown-menu-separator"
      className={cn("-mx-1.5 mb-1.5 h-px bg-border-subtle", className)}
      {...props}
    />
  );
}

export {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuGroup,
  DropdownMenuItem,
  DropdownMenuLabel,
  DropdownMenuLinkItem,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
};
