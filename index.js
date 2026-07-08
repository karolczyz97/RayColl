// Kolejność jest istotna i JAWNA (importy i tak są hoistowane razem):
// 1) expo-router/entry rejestruje komponent aplikacji,
// 2) registerPlaybackService rejestruje headless serwis RNTP (Android; web = no-op
//    dzięki wariantowi .native.ts — RNTP nie trafia do bundla webowego).
import 'expo-router/entry';
import './src/services/registerPlaybackService';
