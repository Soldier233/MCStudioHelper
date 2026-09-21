package com.github.tartaricacid.mcshelper.options

enum class LogLevel {
    /**
     * 默认级别：MCDK 日志 + 游戏/Python 日志，过滤引擎噪声
     */
    NORMAL,

    /**
     * 全部级别：再包含游戏系统日志
     */
    VERBOSE;

    val code: Int = ordinal
    val displayName: String
        get() = when (this) {
            NORMAL -> "默认"
            VERBOSE -> "全部"
        }
}