package com.github.tartaricacid.mcshelper.options

import com.intellij.execution.configurations.RunConfigurationOptions

class MCRunConfigurationOptions : RunConfigurationOptions() {
    private val mcdkPathProperty = string("").provideDelegate(this, "mcdkPath")
    private val logLevelProperty = enum(LogLevel.NORMAL).provideDelegate(this, "logLevel")
    private val launchModeProperty = string("AUTO").provideDelegate(this, "launchMode")
    private val debugPortProperty = property(5678).provideDelegate(this, "debugPort")

    var mcdkPath: String?
        get() = mcdkPathProperty.getValue(this)
        set(value) = mcdkPathProperty.setValue(this, value)
    var logLevel: LogLevel
        get() = logLevelProperty.getValue(this)
        set(value) = logLevelProperty.setValue(this, value)
    var launchMode: String
        get() = launchModeProperty.getValue(this) ?: "AUTO"
        set(value) = launchModeProperty.setValue(this, value)
    var debugPort: Int
        get() = debugPortProperty.getValue(this)
        set(value) = debugPortProperty.setValue(this, value)
}
