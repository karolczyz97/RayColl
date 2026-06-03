import React from 'react';

interface NavigationShellContextValue {
  isCompact: boolean;
  isMedium: boolean;
  isExpanded: boolean;
  contentWidth: number;
  showPersistentNavigation: boolean;
  navWidth: number;
}

const NavigationShellContext = React.createContext<NavigationShellContextValue>({
  isCompact: true,
  isMedium: false,
  isExpanded: false,
  contentWidth: 0,
  showPersistentNavigation: false,
  navWidth: 0,
});

export function NavigationShellProvider({
  children,
  value,
}: {
  children: React.ReactNode;
  value: NavigationShellContextValue;
}) {
  return (
    <NavigationShellContext.Provider value={value}>
      {children}
    </NavigationShellContext.Provider>
  );
}

export function useNavigationShell() {
  return React.useContext(NavigationShellContext);
}
