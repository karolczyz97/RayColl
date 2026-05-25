import { useState, useEffect, useCallback, useRef } from 'react';
import AsyncStorage from '@react-native-async-storage/async-storage';
import type { User } from 'firebase/auth';
import type { FlashcardGroup, Flashcard, StudyMode } from '../types/models';
import { createNewSrsState } from '../srs/srsEngine';
import { onAuthChange, signInWithGoogle, signOutUser, saveUserData, loadUserData } from '../services/firebase';

function uid(): string {
  if (typeof crypto !== 'undefined' && crypto.randomUUID) {
    return crypto.randomUUID();
  }
  return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, (c) => {
    const r = (Math.random() * 16) | 0;
    const v = c === 'x' ? r : (r & 0x3) | 0x8;
    return v.toString(16);
  });
}

const SEED_VERSION = 3;

export type CardFilter = 'all' | 'new' | 'review' | 'new+review';

export function filterCards(cards: Flashcard[], filter: CardFilter): Flashcard[] {
  const now = Date.now();
  switch (filter) {
    case 'all': return [...cards];
    case 'new': return cards.filter(c => c.srsState.state === 0);
    case 'review': return cards.filter(c => c.srsState.state > 0 && c.srsState.nextReviewTimestamp <= now);
    case 'new+review':
    default: return cards.filter(c => c.srsState.state === 0 || c.srsState.nextReviewTimestamp <= now);
  }
}

function createSeedGroups(): FlashcardGroup[] {
  const mk = (pages: string[]): Flashcard => ({ id: uid(), pages, srsState: createNewSrsState() });
  return [
    {
      id: uid(), name: 'Angielski - Podstawy', activeModeId: 'classic',
      pageLanguages: ['en-US', 'pl-PL'], pageNames: ['Phrase', 'Tłumaczenie'],
      cards: [
        mk(['Good morning', 'Dzień dobry']), mk(['Thank you', 'Dziękuję']),
        mk(['Please', 'Proszę']), mk(['Goodbye', 'Do widzenia']), mk(['Yes', 'Tak']),
      ],
    },
    {
      id: uid(), name: 'Hiszpański - Podstawy', activeModeId: 'classic',
      pageLanguages: ['es-ES', 'pl-PL'], pageNames: ['Palabra', 'Tłumaczenie'],
      cards: [
        mk(['Hola', 'Cześć']), mk(['Gracias', 'Dziękuję']),
        mk(['Por favor', 'Proszę']), mk(['Adiós', 'Do widzenia']),
      ],
    },
  ];
}

function createSeedModes(): StudyMode[] {
  return [
    { id: 'classic', name: 'Klasyczny', steps: [
      { type: 'show_page', pageIndex: 0 },
      { type: 'speak_page', pageIndex: 0, extraPauseMs: 500 },
      { type: 'dynamic_pause', nextPageIndex: 1, extraPauseMs: 1000 },
      { type: 'show_page', pageIndex: 1 },
    ]},
    { id: 'listen-speak', name: 'Audio', steps: [
      { type: 'show_page', pageIndex: 0 },
      { type: 'speak_page', pageIndex: 0, extraPauseMs: 0 },
      { type: 'listen_and_branch', pageIndex: 1, successThreshold: 70, incorrectTtsPageIndex: 1 },
    ]},
  ];
}

export interface FlashcardStore {
  groups: FlashcardGroup[];
  studyModes: StudyMode[];
  activityHeatmap: Record<string, number>;
  isLoading: boolean;
  user: User | null;
  signIn: () => Promise<void>;
  signOut: () => Promise<void>;
  addGroup: (name: string, languages: string[], pageNames: string[]) => string;
  updateGroup: (group: FlashcardGroup) => void;
  deleteGroup: (groupId: string) => void;
  addFlashcard: (groupId: string, pages: string[]) => string;
  updateFlashcard: (groupId: string, card: Flashcard) => void;
  deleteFlashcard: (groupId: string, cardId: string) => void;
  addStudyMode: (mode: StudyMode) => void;
  deleteStudyMode: (modeId: string) => void;
  resetToDefault: () => void;
  recordActivity: () => void;
  getDueCards: (groupId: string) => Flashcard[];
  getGroupProgress: (groupId: string) => number;
  importState: (json: string) => void;
  exportState: () => string;
}

export function useFlashcardStore(): FlashcardStore {
  const [groups, setGroups] = useState<FlashcardGroup[]>([]);
  const [studyModes, setStudyModes] = useState<StudyMode[]>([]);
  const [heatmap, setHeatmap] = useState<Record<string, number>>({});
  const [user, setUser] = useState<User | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [seedVer, setSeedVer] = useState<number>(0);

  // Refs to access the latest state inside callbacks
  const groupsRef = useRef(groups);
  const studyModesRef = useRef(studyModes);
  const heatmapRef = useRef(heatmap);
  useEffect(() => { groupsRef.current = groups; }, [groups]);
  useEffect(() => { studyModesRef.current = studyModes; }, [studyModes]);
  useEffect(() => { heatmapRef.current = heatmap; }, [heatmap]);

  const prevUserRef = useRef<User | null>(null);

  const persist = useCallback((g: FlashcardGroup[], m: StudyMode[], h: Record<string, number>) => {
    if (user) {
      saveUserData(user.uid, { groups: g, studyModes: m, activityHeatmap: h }).catch(() => {});
      AsyncStorage.setItem(`fiszki-user-groups-${user.uid}`, JSON.stringify(g)).catch(() => {});
      AsyncStorage.setItem(`fiszki-user-modes-${user.uid}`, JSON.stringify(m)).catch(() => {});
      AsyncStorage.setItem(`fiszki-user-heatmap-${user.uid}`, JSON.stringify(h)).catch(() => {});
    } else {
      AsyncStorage.setItem('fiszki-local-groups', JSON.stringify(g)).catch(() => {});
      AsyncStorage.setItem('fiszki-local-modes', JSON.stringify(m)).catch(() => {});
      AsyncStorage.setItem('fiszki-local-heatmap', JSON.stringify(h)).catch(() => {});
    }
  }, [user]);

  // Asynchronous Loading of Initial Data
  useEffect(() => {
    async function initStore() {
      try {
        const cachedSeed = await AsyncStorage.getItem('fiszki-seed-ver');
        const initialSeedVer = cachedSeed ? Number(cachedSeed) : 0;
        setSeedVer(initialSeedVer);

        let localGroups: FlashcardGroup[] = [];
        let localModes: StudyMode[] = [];
        let localHeatmap: Record<string, number> = {};

        if (user) {
          const cachedGroups = await AsyncStorage.getItem(`fiszki-user-groups-${user.uid}`);
          const cachedModes = await AsyncStorage.getItem(`fiszki-user-modes-${user.uid}`);
          const cachedHeatmap = await AsyncStorage.getItem(`fiszki-user-heatmap-${user.uid}`);

          if (cachedGroups && cachedModes && cachedHeatmap) {
            localGroups = JSON.parse(cachedGroups);
            localModes = JSON.parse(cachedModes);
            localHeatmap = JSON.parse(cachedHeatmap);
          } else {
            const data = await loadUserData(user.uid);
            if (data) {
              localGroups = data.groups;
              localModes = data.studyModes;
              localHeatmap = data.activityHeatmap;

              await AsyncStorage.setItem(`fiszki-user-groups-${user.uid}`, JSON.stringify(data.groups));
              await AsyncStorage.setItem(`fiszki-user-modes-${user.uid}`, JSON.stringify(data.studyModes));
              await AsyncStorage.setItem(`fiszki-user-heatmap-${user.uid}`, JSON.stringify(data.activityHeatmap));
            }
          }
        } else {
          const storedGroups = await AsyncStorage.getItem('fiszki-local-groups');
          const storedModes = await AsyncStorage.getItem('fiszki-local-modes');
          const storedHeatmap = await AsyncStorage.getItem('fiszki-local-heatmap');

          localGroups = storedGroups ? JSON.parse(storedGroups) : [];
          localModes = storedModes ? JSON.parse(storedModes) : [];
          localHeatmap = storedHeatmap ? JSON.parse(storedHeatmap) : {};
        }

        // Apply seeding logic if needed
        if (initialSeedVer < SEED_VERSION) {
          if (initialSeedVer === 0 && localGroups.length === 0) {
            localGroups = createSeedGroups();
            await AsyncStorage.setItem('fiszki-local-groups', JSON.stringify(localGroups));
          }
          const defaultModes = createSeedModes();
          const customModes = localModes.filter(m => m.id !== 'classic' && m.id !== 'listen-speak');
          localModes = [...defaultModes, ...customModes];
          await AsyncStorage.setItem('fiszki-local-modes', JSON.stringify(localModes));
          await AsyncStorage.setItem('fiszki-seed-ver', String(SEED_VERSION));
          setSeedVer(SEED_VERSION);
        }

        setGroups(localGroups.length > 0 ? localGroups : createSeedGroups());
        setStudyModes(localModes.length > 0 ? localModes : createSeedModes());
        setHeatmap(localHeatmap);
      } catch (err) {
        console.error('Failed to initialize flashcard store:', err);
      } finally {
        setIsLoading(false);
      }
    }
    
    initStore();
  }, [user]);

  // Auth listener
  useEffect(() => onAuthChange(u => setUser(u)), []);

  // Handle user login / logout changes
  useEffect(() => {
    const wasLoggedOut = prevUserRef.current === null;
    const isNowLoggedIn = user !== null;
    const isNowLoggedOut = user === null;

    if (wasLoggedOut && isNowLoggedIn) {
      setIsLoading(true);
      AsyncStorage.getItem(`fiszki-user-groups-${user.uid}`).then(cachedGroups => {
        if (cachedGroups) {
          Promise.all([
            AsyncStorage.getItem(`fiszki-user-modes-${user.uid}`),
            AsyncStorage.getItem(`fiszki-user-heatmap-${user.uid}`),
          ]).then(([cachedModes, cachedHeatmap]) => {
            setGroups(JSON.parse(cachedGroups));
            setStudyModes(cachedModes ? JSON.parse(cachedModes) : createSeedModes());
            setHeatmap(cachedHeatmap ? JSON.parse(cachedHeatmap) : {});
            setIsLoading(false);
          });
        }
        
        loadUserData(user.uid).then(data => {
          if (data) {
            setGroups(data.groups);
            setStudyModes(data.studyModes);
            setHeatmap(data.activityHeatmap);
            AsyncStorage.setItem(`fiszki-user-groups-${user.uid}`, JSON.stringify(data.groups)).catch(() => {});
            AsyncStorage.setItem(`fiszki-user-modes-${user.uid}`, JSON.stringify(data.studyModes)).catch(() => {});
            AsyncStorage.setItem(`fiszki-user-heatmap-${user.uid}`, JSON.stringify(data.activityHeatmap)).catch(() => {});
          }
          setIsLoading(false);
        }).catch(() => setIsLoading(false));
      });
    } else if (!wasLoggedOut && isNowLoggedOut) {
      setIsLoading(true);
      Promise.all([
        AsyncStorage.getItem('fiszki-local-groups'),
        AsyncStorage.getItem('fiszki-local-modes'),
        AsyncStorage.getItem('fiszki-local-heatmap'),
      ]).then(([g, m, h]) => {
        const parsedGroups = g ? JSON.parse(g) : [];
        const parsedModes = m ? JSON.parse(m) : [];
        const parsedHeatmap = h ? JSON.parse(h) : {};
        
        if (parsedGroups.length === 0) {
          const seedG = createSeedGroups();
          const seedM = createSeedModes();
          setGroups(seedG);
          setStudyModes(seedM);
          setHeatmap({});
          AsyncStorage.setItem('fiszki-local-groups', JSON.stringify(seedG)).catch(() => {});
          AsyncStorage.setItem('fiszki-local-modes', JSON.stringify(seedM)).catch(() => {});
          AsyncStorage.setItem('fiszki-local-heatmap', JSON.stringify({})).catch(() => {});
        } else {
          setGroups(parsedGroups);
          setStudyModes(parsedModes);
          setHeatmap(parsedHeatmap);
        }
        setIsLoading(false);
      }).catch(() => setIsLoading(false));
    }
    prevUserRef.current = user;
  }, [user]);

  const addGroup = useCallback((name: string, languages: string[], pageNames: string[]) => {
    const id = uid();
    const g: FlashcardGroup = { id, name, cards: [], activeModeId: 'classic', pageLanguages: languages, pageNames };
    setGroups(prev => {
      const next = [...prev, g];
      persist(next, studyModesRef.current, heatmapRef.current);
      return next;
    });
    return id;
  }, [persist]);

  const updateGroup = useCallback((group: FlashcardGroup) => {
    setGroups(prev => {
      const next = prev.map(g => g.id === group.id ? group : g);
      persist(next, studyModesRef.current, heatmapRef.current);
      return next;
    });
  }, [persist]);

  const deleteGroup = useCallback((id: string) => {
    setGroups(prev => {
      const next = prev.filter(g => g.id !== id);
      persist(next, studyModesRef.current, heatmapRef.current);
      return next;
    });
  }, [persist]);

  const addFlashcard = useCallback((groupId: string, pages: string[]) => {
    const card: Flashcard = { id: uid(), pages, srsState: createNewSrsState() };
    setGroups(prev => {
      const next = prev.map(g => g.id === groupId ? { ...g, cards: [...g.cards, card] } : g);
      persist(next, studyModesRef.current, heatmapRef.current);
      return next;
    });
    return card.id;
  }, [persist]);

  const updateFlashcard = useCallback((groupId: string, card: Flashcard) => {
    setGroups(prev => {
      const next = prev.map(g => g.id === groupId ? { ...g, cards: g.cards.map(c => c.id === card.id ? card : c) } : g);
      persist(next, studyModesRef.current, heatmapRef.current);
      return next;
    });
  }, [persist]);

  const deleteFlashcard = useCallback((groupId: string, cardId: string) => {
    setGroups(prev => {
      const next = prev.map(g => g.id === groupId ? { ...g, cards: g.cards.filter(c => c.id !== cardId) } : g);
      persist(next, studyModesRef.current, heatmapRef.current);
      return next;
    });
  }, [persist]);

  const addStudyMode = useCallback((mode: StudyMode) => {
    setStudyModes(prev => {
      const next = [...prev, mode];
      persist(groupsRef.current, next, heatmapRef.current);
      return next;
    });
  }, [persist]);

  const deleteStudyMode = useCallback((id: string) => {
    setStudyModes(prev => {
      const next = prev.filter(m => m.id !== id);
      persist(groupsRef.current, next, heatmapRef.current);
      return next;
    });
  }, [persist]);

  const resetToDefault = useCallback(() => {
    const g = createSeedGroups(), m = createSeedModes(), h: Record<string, number> = {};
    setGroups(g);
    setStudyModes(m);
    setHeatmap(h);
    setSeedVer(SEED_VERSION);
    AsyncStorage.setItem('fiszki-seed-ver', String(SEED_VERSION)).catch(() => {});
    persist(g, m, h);
  }, [persist]);

  const recordActivity = useCallback(() => {
    const today = new Date().toISOString().slice(0, 10);
    setHeatmap(prev => {
      const next = { ...prev, [today]: (prev[today] || 0) + 1 };
      persist(groupsRef.current, studyModesRef.current, next);
      return next;
    });
  }, [persist]);

  const getDueCards = useCallback((groupId: string): Flashcard[] => {
    const group = groups.find(g => g.id === groupId);
    if (!group) return [];
    return filterCards(group.cards, group.studyFilter || 'new+review');
  }, [groups]);

  const getGroupProgress = useCallback((groupId: string): number => {
    const group = groups.find(g => g.id === groupId);
    if (!group || group.cards.length === 0) return 0;
    const learned = group.cards.filter(c => c.srsState.repetitions >= 1).length;
    return Math.round((learned / group.cards.length) * 100);
  }, [groups]);

  const signIn = useCallback(async () => { await signInWithGoogle(); }, []);
  const signOut = useCallback(async () => { await signOutUser(); }, []);

  const exportState = useCallback(() => {
    return JSON.stringify({ groups, studyModes, activityHeatmap: heatmap }, null, 2);
  }, [groups, studyModes, heatmap]);

  const importState = useCallback((json: string) => {
    try {
      const data = JSON.parse(json);
      const g = data.groups || [];
      const m = data.studyModes || [];
      const h = data.activityHeatmap || {};
      setGroups(g);
      setStudyModes(m);
      setHeatmap(h);
      persist(g, m, h);
    } catch {
      console.error('Invalid JSON import');
    }
  }, [persist]);

  return {
    groups, studyModes, activityHeatmap: heatmap, isLoading, user,
    signIn, signOut, addGroup, updateGroup, deleteGroup,
    addFlashcard, updateFlashcard, deleteFlashcard,
    addStudyMode, deleteStudyMode, resetToDefault, recordActivity,
    getDueCards, getGroupProgress, importState, exportState,
  };
}
