package com.iris.assistant

/** Lightweight Portuguese acknowledgements based on Jarvis fillers.ts.
 * Never invents tool progress or factual results.
 */
class IrisFillers {
    private val ready = listOf("Oi, estou ouvindo.", "Pode falar.", "Estou aqui.")
    private val working = listOf("Um instante.", "Estou verificando.", "Deixe-me pensar.")
    private var lastReady = -1
    private var lastWorking = -1
    private fun next(items: List<String>, previous: Int): Int =
        if (items.size < 2) 0 else (previous + 1) % items.size
    fun attention(): String { lastReady = next(ready, lastReady); return ready[lastReady] }
    fun thinking(): String { lastWorking = next(working, lastWorking); return working[lastWorking] }
}
