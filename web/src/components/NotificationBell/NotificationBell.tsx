import { buttonStyles, cn, Icon, RouterLink } from "@graphnous/theme";
import { BellIcon } from "@phosphor-icons/react";

export type NotificationBellProps = {
  /**
   * How many notifications the user has not read; a dot shows while any.
   */
  unread: number;
  href?: string;
  className?: string;
};

/**
 * The toolbar's way to the notifications: a bell, with a dot while some
 * are unread.
 */
export function NotificationBell({ unread, href = "/notifications", className }: NotificationBellProps) {
  const label = unread > 0
    ? `Notifications, ${unread} unread`
    : "Notifications";

  return (
    <RouterLink
      href={href}
      aria-label={label}
      title={label}
      className={buttonStyles({ variant: "ghost", size: "md", className: cn("relative w-10 px-0", className) })}
    >
      <Icon icon={BellIcon} size="md" />
      {unread > 0 && (
        <span
          aria-hidden
          data-testid="unread-dot"
          className="absolute top-2 right-2.5 size-2 rounded-full bg-primary ring-2 ring-surface"
        />
      )}
    </RouterLink>
  );
}
