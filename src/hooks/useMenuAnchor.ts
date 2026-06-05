import { useMemo, useState } from 'react';
import { menuStyles } from '@/theme/menuStyles';

const MENU_TRANSFORM_ORIGIN_STYLE = { transformOrigin: 'top' as const };

export function useMenuAnchor(contentBackgroundColor: string) {
  const [open, setOpen] = useState(false);
  const [anchorWidth, setAnchorWidth] = useState<number | undefined>(undefined);

  const menuStyle = useMemo(
    () => [
      menuStyles.menu,
      anchorWidth ? { width: anchorWidth, maxWidth: anchorWidth } : undefined,
      MENU_TRANSFORM_ORIGIN_STYLE,
    ],
    [anchorWidth],
  );

  const menuContentStyle = useMemo(
    () => [
      menuStyles.menuContent,
      { backgroundColor: contentBackgroundColor },
      anchorWidth ? { width: anchorWidth, maxWidth: anchorWidth } : undefined,
      MENU_TRANSFORM_ORIGIN_STYLE,
    ],
    [anchorWidth, contentBackgroundColor],
  );

  return { open, setOpen, anchorWidth, setAnchorWidth, menuStyle, menuContentStyle };
}
