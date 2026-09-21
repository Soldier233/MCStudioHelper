package com.github.tartaricacid.mcshelper.log

import com.github.tartaricacid.mcshelper.options.LogLevel
import com.github.tartaricacid.mcshelper.options.MCRunConfigurationOptions
import com.github.tartaricacid.mcshelper.util.PathUtils
import com.github.tartaricacid.mcshelper.util.VersionUtils
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.AnsiEscapeDecoder
import com.intellij.execution.process.KillableProcessHandler
import com.intellij.execution.process.ProcessOutputTypes
import com.intellij.openapi.util.Key
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

const val RESET: String = "\u001B[0m"
const val RED: String = "\u001B[31m"
const val GREEN: String = "\u001B[32m"
const val YELLOW: String = "\u001B[33m"
const val DARK_GRAY: String = "\u001B[90m"
const val GRAY: String = "\u001B[37m"
const val CYAN: String = "\u001B[36m"
const val BOLD: String = "\u001B[1m"

/**
 * 系统日志的正则表达式匹配，一般情况下不需要显示
 */
val SYS_LOG = Regex(
    """
    (?x)                                            # 启用扩展模式：忽略空白并允许使用 '#' 注释
    ^
    \[                                      
    (\d{4}-\d{2}-\d{2}\s\d{2}:\d{2}:\d{2}:\d{3})\s  # 时间戳: yyyy-MM-dd HH:mm:ss:SSS
    (VERBOSE|INFO|WARN|ERROR)\s                     # 日志等级
    (\S+)\s                                         # 模块/标签名
    (\d+)\s                                         # pid，进程 ID
    (\d+)                                           # tid，线程 ID
    ]\s
    (.*)                                            # 日志消息
    $
    """.trimIndent()
)

/**
 * 游戏本体日志的正则表达式匹配，一般情况下需要过滤显示
 */
val GAME_LOG = Regex(
    """
    (?x)                                             # 启用扩展模式：忽略空白并允许使用 '#' 注释                                          
    ^
    \[Python]\s
    \[(\d{4}-\d{2}-\d{2}\s\d{2}:\d{2}:\d{2},\d{3})]  # 时间戳: yyyy-MM-dd HH:mm:ss,SSS
    \s
    (.*)                                             # 剩余部分（含若干 [..]）
    $
    """.trimIndent()
)

const val PYTHON_HEADER = "[Python]"

/**
 * Strip CSI/OSC from mcdk, ConPTY, and cpp-mcp (`\033[32m...`).
 * A lost ESC often shows up as `?` or U+FFFD, producing `?[32m`.
 */
private val ANSI_ESCAPE = Regex(
    """(?:\u001B\[|\u009B|\uFFFD\[)[\d;?=]*[ -/]*[@-~]""" +
        """|\?\[[\d;?=]+[@-~]""" +
        """|\u001B\][^\u0007\u001B]*[\u0007\u001B\\]""" +
        """|\u001B[@-Z\\-_]""" +
        """|[\r\u0008]"""
)

fun stripAnsiEscapes(text: String): String = ANSI_ESCAPE.replace(text, "").trim()

class LogFilteredProcessHandler(
    commandLine: GeneralCommandLine,
    val options: MCRunConfigurationOptions,
    private val mcdkPath: String,
    private val debugEnabled: Boolean,
    private val effectiveConfig: com.google.gson.JsonObject
) : KillableProcessHandler(commandLine), AnsiEscapeDecoder.ColoredTextAcceptor {
    private val myAnsiEscapeDecoder by lazy { AnsiEscapeDecoder() }
    private val buffers = mutableMapOf<Key<*>, StringBuilder>()

    override fun getCharset(): Charset {
        return StandardCharsets.UTF_8
    }

    override fun startNotify() {
        super.startNotify()

        // 启动进程时，按照规范标准开始打印，并添加一些额外系统信息
        val header = this.getRunHeader()

        myAnsiEscapeDecoder.escapeText(
            "${header}开始运行游戏$RESET\n",
            ProcessOutputTypes.STDOUT, this
        )

        myAnsiEscapeDecoder.escapeText(
            "${header}日志记录模式：${options.logLevel.displayName}$RESET\n",
            ProcessOutputTypes.STDOUT, this
        )

        val logProtocol = if (effectiveConfig.get("log_protocol")?.asInt == 1) "Safaia" else "PIPE"
        myAnsiEscapeDecoder.escapeText(
            "${header}日志协议：$logProtocol$RESET\n",
            ProcessOutputTypes.STDOUT, this
        )

        myAnsiEscapeDecoder.escapeText(
            "${header}mcdk 路径：$mcdkPath$RESET\n",
            ProcessOutputTypes.STDOUT, this
        )

        myAnsiEscapeDecoder.escapeText(
            "${header}启动器路径：${effectiveConfig.get("game_executable_path").asString}$RESET\n",
            ProcessOutputTypes.STDOUT, this
        )

        val worldDir = PathUtils.worldsDir()
        if (worldDir != null) {
            val fullWorldPath = worldDir.resolve(effectiveConfig.get("world_folder_name").asString).toAbsolutePath().toString()
            myAnsiEscapeDecoder.escapeText(
                "${header}配置的世界存档路径（额外客户端模式不部署）：${fullWorldPath}$RESET\n",
                ProcessOutputTypes.STDOUT, this
            )
        }

        val modPaths = com.github.tartaricacid.mcshelper.util.McdevLaunchConfig.enabledModPaths(effectiveConfig)
        val includedModsText = if (modPaths.isEmpty()) {
            "无"
        } else {
            modPaths.joinToString(", ")
        }
        myAnsiEscapeDecoder.escapeText(
            "${header}包含组件目录：$includedModsText$RESET\n",
            ProcessOutputTypes.STDOUT, this
        )

        // 检查启动器版本是否是 3.7.0.222545 及以上版本
        // 如果不是，那么提示用户无法使用 LSP4IJ 的断点调试功能
        if (debugEnabled) {
            val isSupportedVersion = VersionUtils.canSupportBreakpointDebug(effectiveConfig.get("game_executable_path").asString)
            if (!isSupportedVersion) {
                myAnsiEscapeDecoder.escapeText(
                    "${header}启动器版本过低，无法使用断点调试功能（需要 3.7.0.222545 及以上版本）$RESET\n",
                    ProcessOutputTypes.STDERR, this
                )
            } else {
                myAnsiEscapeDecoder.escapeText(
                    "${header}已通过 MCDK 在 127.0.0.1:${options.debugPort} 开启 ptvsd 调试服务$RESET\n",
                    ProcessOutputTypes.STDOUT, this
                )
            }
        }
    }

    override fun notifyTextAvailable(text: String, outputType: Key<*>) {
        val buf = buffers.getOrPut(outputType) { StringBuilder() }
        buf.append(text)

        while (true) {
            val newlineIndex = buf.indexOf("\n")
            if (newlineIndex == -1) {
                return
            }

            var line = stripAnsiEscapes(buf.substring(0, newlineIndex + 1))
            buf.delete(0, newlineIndex + 1)
            if (line.isEmpty()) {
                continue
            }

            if (outputType == ProcessOutputTypes.SYSTEM) {
                myAnsiEscapeDecoder.escapeText(line + "\n", outputType, this)
                continue
            }

            line = if (options.logLevel == LogLevel.VERBOSE) {
                handleVerboseLog(line) ?: continue
            } else {
                handleNormalLog(line) ?: continue
            }

            if (line.isEmpty()) {
                continue
            }
            myAnsiEscapeDecoder.escapeText(line + "\n", outputType, this)
        }
    }

    // 进程结束时把缓冲区中剩余没有换行的部分也处理一次
    override fun notifyProcessTerminated(exitCode: Int) {
        try {
            for ((outputType, sb) in buffers) {
                if (sb.isNotEmpty()) {
                    val line = stripAnsiEscapes(sb.toString())
                    if (line.isNotEmpty()) {
                        myAnsiEscapeDecoder.escapeText(line + "\n", outputType, this)
                    }
                }
            }
        } finally {
            buffers.clear()
            super.notifyProcessTerminated(exitCode)

            // 进程结束时，打印退出信息
            myAnsiEscapeDecoder.escapeText(
                "${BOLD}${RED}游戏进程已退出，退出代码：$exitCode$RESET\n",
                ProcessOutputTypes.STDOUT, this
            )
        }
    }

    override fun coloredTextAvailable(text: String, attributes: Key<*>) {
        super.notifyTextAvailable(text, attributes)
    }

    /**
     * 默认模式：保留 MCDK 日志和游戏/Python 日志，过滤引擎噪声。
     */
    fun handleNormalLog(lineInput: String): String? {
        var line = lineInput

        // 由于 [ERROR][Engine] 往往先于 [Python] 头的添加，故检查到含有 [ERROR][Engine] 时，需要手动添加
        if (lineInput.contains("[ERROR][Engine]") && !lineInput.startsWith(PYTHON_HEADER)) {
            line = "$PYTHON_HEADER $lineInput"
        }

        val matchResult = GAME_LOG.find(line)
        if (matchResult == null) {
            // 如果是普通的 [Python] 开头的日志，剔除头部后返回
            if (line.startsWith(PYTHON_HEADER)) {
                val trimLine = line.substring(PYTHON_HEADER.length, line.length).trim()
                val coloredLevel = getColoredLog(trimLine)
                return "$coloredLevel$trimLine$RESET"
            }
            if (line.contains("[INFO][Engine]") || line == "get_cls" ||
                line == "get_cls success!!!" || line.startsWith("NO LOG FILE!")) {
                return null
            }
            return "${getColoredLog(line)}$line$RESET"
        }

        val rest = matchResult.groupValues[2]
        // 提取所有中括号内的 token
        val bracketRe = Regex("""\[(.*?)]""")
        val tokens = bracketRe.findAll(rest).map { it.groupValues[1] }.toList()

        val module = tokens.getOrNull(1) ?: ""
        val level = tokens.getOrNull(0) ?: ""
        // 剔除引擎噪声
        if (module == "Engine" && level == "INFO") {
            return null
        }

        // 将所有中括号内容去掉，剩下的就是消息
        val message = rest.replace(bracketRe, "").trim()
        // 去除 get_cls 噪声
        if (message == "get_cls" || message == "get_cls success!!!") {
            return null
        }

        var coloredLevel = getColoredLog(level)
        // 如果是 Developer 那么，打印成灰色
        if (module == "Developer" && level == "INFO") {
            coloredLevel = DARK_GRAY
        }
        val trimLine = line.substring(PYTHON_HEADER.length, line.length).trim()

        return "$coloredLevel$trimLine$RESET"
    }

    fun handleVerboseLog(line: String): String? {
        val sysLogMatch = SYS_LOG.find(line)
        if (sysLogMatch != null) {
            val level = sysLogMatch.groupValues[2]
            val coloredLevel = getColoredLog(level)
            return "$coloredLevel$line$RESET"
        }

        val matchResult = GAME_LOG.find(line)
        if (matchResult != null) {
            val rest = matchResult.groupValues[2]
            // 提取所有中括号内的 token
            val bracketRe = Regex("""\[(.*?)]""")
            val tokens = bracketRe.findAll(rest).map { it.groupValues[1] }.toList()
            val level = tokens.getOrNull(0) ?: ""
            val coloredLevel = getColoredLog(level)
            return "$coloredLevel$line$RESET"
        }

        var coloredLevel = getColoredLog(line)
        // 开头会刷 NO LOG FILE，着暗灰色
        if (line.startsWith("NO LOG FILE!")) {
            coloredLevel = DARK_GRAY
        }
        return "$coloredLevel$line$RESET"
    }

    fun getColoredLog(line: String): String {
        return when {
            line.contains("ERROR", ignoreCase = false) -> RED
            line.contains("WARN", ignoreCase = false) -> YELLOW
            line.contains("SUC", ignoreCase = false) -> GREEN
            line.contains("INFO", ignoreCase = false) -> GRAY
            line.contains("VERBOSE", ignoreCase = false) -> DARK_GRAY
            else -> RESET
        }
    }

    fun getRunHeader(): String {
        // 启动进程时，按照规范标准开始打印，并添加一些额外系统信息
        val timeStr = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss,SSS"))
        return "$BOLD$CYAN[$timeStr] [INFO] [System] "
    }
}
