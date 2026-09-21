package com.github.tartaricacid.mcshelper.log

import com.github.tartaricacid.mcshelper.options.MCRunConfigurationOptions
import com.google.gson.JsonObject
import com.intellij.execution.configurations.GeneralCommandLine
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LogFilteredProcessHandlerTest {
    private fun handler(): LogFilteredProcessHandler = LogFilteredProcessHandler(
        GeneralCommandLine("cmd.exe", "/c", "exit", "0"),
        MCRunConfigurationOptions(),
        "mcdk.exe",
        false,
        JsonObject().apply { addProperty("log_protocol", 1) }
    )

    @Test
    fun `normal mode keeps MCDK logs and game logs`() {
        val logHandler = handler()

        val mcdk = logHandler.handleNormalLog("[MCDK] [INFO] Safaia connected")
        assertTrue(mcdk!!.contains("[MCDK] [INFO] Safaia connected"))
        assertTrue(mcdk.startsWith(GRAY))

        val python = logHandler.handleNormalLog(
            "[Python] [2026-09-21 12:00:00,123] [INFO][Mod] hello from python"
        )
        assertNotNull(python)
        assertTrue(python.contains("[INFO][Mod] hello from python"))

        val game = logHandler.handleNormalLog(
            "[2026-09-21 12:00:00:123 INFO ScriptEngine 1234 5678] hello from game"
        )
        assertNotNull(game)
        assertTrue(game.contains("hello from game"))
    }

    @Test
    fun `normal mode still drops engine and startup noise`() {
        val logHandler = handler()

        assertNull(logHandler.handleNormalLog("[INFO][Engine] internal message"))
        assertNull(logHandler.handleNormalLog("get_cls"))
        assertNull(logHandler.handleNormalLog("get_cls success!!!"))
        assertNull(logHandler.handleNormalLog("NO LOG FILE!"))
    }

    @Test
    fun `pty color sequences are stripped before matching`() {
        val colored = "\u001B[36m[MCDK] [INFO] IPC Bridge listening on port 1\u001B[0m\r"
        assertEquals("[MCDK] [INFO] IPC Bridge listening on port 1", stripAnsiEscapes(colored))

        val result = handler().handleNormalLog(stripAnsiEscapes(colored))
        assertTrue(result!!.contains("[MCDK] [INFO] IPC Bridge listening on port 1"))
    }

    @Test
    fun `mcp logger ansi is stripped even when ESC becomes a question mark`() {
        val mcp = "\u001B[32m[MCP] [INFO]\u001B[0m \u001B[90m2026-09-21 19:31:14 Starting MCP server on localhost:19133\u001B[0m"
        assertEquals(
            "[MCP] [INFO] 2026-09-21 19:31:14 Starting MCP server on localhost:19133",
            stripAnsiEscapes(mcp)
        )

        val lostEsc = "?[32m[MCP] [INFO]?[0m ?[90m2026-09-21 19:31:14 Starting MCP server on localhost:19133?[0m"
        assertEquals(
            "[MCP] [INFO] 2026-09-21 19:31:14 Starting MCP server on localhost:19133",
            stripAnsiEscapes(lostEsc)
        )

        val result = handler().handleNormalLog(stripAnsiEscapes(lostEsc))
        assertNotNull(result)
        assertTrue(result.contains("[MCP] [INFO]"))
        assertTrue(result.contains("localhost:19133"))
        assertTrue(!result.contains("?[32m") && !result.contains("[32m"))
    }
}