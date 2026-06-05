import React, { createContext, useContext } from 'react';

interface StudyNavigationGuardContextValue {
  isStudyActive: boolean;
  setStudyActive: (active: boolean) => void;
}

const StudyNavigationGuardContext = createContext<StudyNavigationGuardContextValue>({
  isStudyActive: false,
  setStudyActive: () => undefined,
});

export function StudyNavigationGuardProvider({
  children,
  value,
}: {
  children: React.ReactNode;
  value: StudyNavigationGuardContextValue;
}) {
  return (
    <StudyNavigationGuardContext.Provider value={value}>
      {children}
    </StudyNavigationGuardContext.Provider>
  );
}

export function useStudyNavigationGuard(): StudyNavigationGuardContextValue {
  return useContext(StudyNavigationGuardContext);
}
