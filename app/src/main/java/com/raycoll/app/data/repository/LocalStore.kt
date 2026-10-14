package com.raycoll.app.data.repository

import androidx.room.withTransaction
import com.raycoll.app.data.local.db.RayCollDatabase
import com.raycoll.app.data.local.mappers.toActivityEntities
import com.raycoll.app.data.local.mappers.toDomain
import com.raycoll.app.data.local.mappers.toEntity
import com.raycoll.app.data.local.mappers.toHeatmap
import com.raycoll.app.domain.merge.mergeUserData
import com.raycoll.app.domain.model.StoreData
import com.raycoll.app.domain.normalize.normalizeStoreData
import com.raycoll.app.domain.seed.SEED_MODES
import com.raycoll.app.domain.seed.SeedData
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Operacje na całych danych jednej przestrzeni (talie + karty + tryby + aktywność):
 * snapshot do synchronizacji i kopii zapasowej, podmiana wszystkiego, dane startowe.
 */
@Singleton
class LocalStore @Inject constructor(
    private val db: RayCollDatabase,
    private val namespaceManager: NamespaceManager,
    private val dataMutations: DataMutations
) {
    private val deckDao = db.deckDao()
    private val cardDao = db.cardDao()
    private val modeDao = db.studyModeDao()
    private val activityDao = db.activityDao()

    suspend fun snapshot(): StoreData = snapshotOf(namespaceManager.current())

    /**
     * Pełny stan przestrzeni RAZEM z tombstonami (usunięte talie, karty i tryby) —
     * bez nich usunięcie nie dotarłoby do chmury, a inne urządzenie przywróciłoby dane.
     */
    suspend fun snapshotOf(ns: String): StoreData = db.withTransaction {
        StoreData(
            groups = withCards(deckDao.allDecks(ns), cardDao.allCards(ns)),
            studyModes = modeDao.allModes(ns).map { it.toDomain() },
            activityHeatmap = activityDao.allActivity(ns).toHeatmap()
        )
    }

    /**
     * Zastępuje całe dane aktywnej przestrzeni — atomowo. [notifySync] = false, gdy
     * wołający sam zajmuje się wysyłką do chmury (scalenie, migracja).
     */
    suspend fun replaceAll(data: StoreData, notifySync: Boolean = true) {
        val ns = namespaceManager.current()
        val normalized = normalizeStoreData(data)
        db.withTransaction {
            deckDao.deleteAllDecks(ns) // karty znikają kaskadowo
            modeDao.deleteAllModes(ns)
            activityDao.deleteAllActivity(ns)
            normalized.groups.forEach { deck ->
                deckDao.upsertDeck(deck.toEntity(ns))
                cardDao.upsertCards(deck.cards.map { it.toEntity(ns, deck.id) })
            }
            modeDao.upsertModes(normalized.studyModes.map { it.toEntity(ns) })
            activityDao.upsertActivity(normalized.activityHeatmap.toActivityEntities(ns))
        }
        if (notifySync) dataMutations.signal(CloudWriteMode.IMMEDIATE)
    }

    /**
     * Scala stan chmury z lokalnym i zapisuje wynik. Odczyt, scalenie i zapis w jednej
     * transakcji — edycja zrobiona w międzyczasie nie zostanie nadpisana.
     */
    suspend fun mergeWithCloud(cloud: StoreData): StoreData = db.withTransaction {
        val merged = mergeUserData(snapshot(), cloud)
        replaceAll(merged, notifySync = false)
        merged
    }

    /**
     * Dane startowe w języku interfejsu. [idScope] (uid) = stałe id dla nowego konta (patrz [SeedData]);
     * reset danych zostaje przy losowych id — stałe zderzyłyby się z tombstone'ami dawnych talii.
     */
    fun seedData(language: String = Locale.getDefault().language, idScope: String? = null): StoreData =
        SeedData.createSeedData(language, idScope)

    /**
     * Pusta przestrzeń dostaje przykładowe talie i tryby wbudowane (w przestrzeni konta ze
     * stałymi id — dwa nowe urządzenia jednego konta tworzą te same dokumenty). Wbudowane tryby,
     * których użytkownik nigdy nie edytował, dostają najnowszą wersję kroków.
     */
    suspend fun seedIfEmpty(language: String = Locale.getDefault().language) {
        val ns = namespaceManager.current()
        db.withTransaction {
            if (deckDao.allDecks(ns).isEmpty()) {
                SeedData.createSeedGroups(language, seedIdScope(ns)).forEach { deck ->
                    deckDao.upsertDeck(deck.toEntity(ns))
                    cardDao.upsertCards(deck.cards.map { it.toEntity(ns, deck.id) })
                }
            }
            val existing = modeDao.allModes(ns).associateBy { it.id }
            val missingOrUntouched = SEED_MODES.filter { seed ->
                val current = existing[seed.id]
                current == null ||
                    (current.isBuiltIn && current.updatedAt == 0L && current.toDomain().steps != seed.steps)
            }
            modeDao.upsertModes(missingOrUntouched.map { it.toEntity(ns) })
        }
    }

    /** Konto z chmurą też dostaje nowe kroki; nie tworzymy przy tym danych w pustej przestrzeni. */
    suspend fun refreshUntouchedBuiltInModes() {
        val ns = namespaceManager.current()
        db.withTransaction {
            val existing = modeDao.allModes(ns).associateBy { it.id }
            val outdated = SEED_MODES.filter { seed ->
                val current = existing[seed.id]
                current != null && current.isBuiltIn && current.updatedAt == 0L &&
                    current.toDomain().steps != seed.steps
            }
            modeDao.upsertModes(outdated.map { it.toEntity(ns) })
        }
    }
}

/** Konto: stałe id seedów (uid); gość: losowe. */
private fun seedIdScope(namespace: String): String? = namespace.takeIf { it != NamespaceManager.GUEST }
