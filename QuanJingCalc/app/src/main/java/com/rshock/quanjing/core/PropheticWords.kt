package com.rshock.quanjing.core

import android.content.Context

object PropheticWords {
    var words: List<List<String>> = emptyList()
        private set

    fun load(ctx: Context) {
        words = JsonLoader.readProphecy(ctx)
    }
}