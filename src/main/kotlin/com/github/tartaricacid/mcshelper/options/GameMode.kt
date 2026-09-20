package com.github.tartaricacid.mcshelper.options

enum class GameMode {
    SURVIVAL,
    CREATIVE;

    val code: Int = ordinal
    val displayName: String
        get() = when (this) {
            SURVIVAL -> "生存模式"
            CREATIVE -> "创造模式"
        }

    companion object {
        fun fromCode(code: Int): GameMode {
            return entries.firstOrNull { it.code == code } ?: CREATIVE
        }
    }
}