package com.raycoll.app.playback

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Głośnik i mikrofon na zmianę: każda wypowiedź TTS i każdy nasłuch STT to jedna tura.
 * Następna tura startuje dopiero, gdy poprzednia się skończy — także przerwana, razem
 * z jej sprzątaniem (zatrzymanie syntezy, zniszczenie rozpoznawania). Czekająca tura,
 * którą anulowano (szybkie „Następna/Poprzednia”, pauza), po prostu wypada z kolejki.
 * Sama flaga „mówię” nie wystarcza: kończąca się stara wypowiedź zerowałaby flagę nowej.
 */
class AudioTurn {
    private val mutex = Mutex()

    suspend fun <T> withTurn(block: suspend () -> T): T = mutex.withLock { block() }
}
