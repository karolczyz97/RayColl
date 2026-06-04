import { useState } from 'react';

export function useHiddenPagesToggle() {
  const [showHidden, setShowHidden] = useState(false);

  return {
    showHidden,
    setShowHidden,
  };
}
