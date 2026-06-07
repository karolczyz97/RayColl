import React, { createContext, useContext } from 'react';

export type StudyExitHandler = (navigate: () => void) => void;

interface StudyNavigationGuardContextValue {
  isStudyActive: boolean;
  requestStudyExit: (navigate: () => void) => boolean;
  setStudyActive: (active: boolean) => void;
  setStudyExitHandler: (handler: StudyExitHandler | null) => void;
}

const StudyNavigationGuardContext = createContext<StudyNavigationGuardContextValue>({
  isStudyActive: false,
  requestStudyExit: () => false,
  setStudyActive: () => undefined,
  setStudyExitHandler: () => undefined,
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
