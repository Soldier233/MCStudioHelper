package com.github.tartaricacid.mcshelper.util

import com.google.gson.*
import java.net.InetAddress
import java.net.ServerSocket
import java.nio.file.Files
import java.nio.file.Path

object McdevLaunchConfig {
    fun resolve(projectConfig: JsonObject?): JsonObject = McdevSchema.withDefaults(projectConfig)

    /** MCDK reads from cwd. Resolve every documented path against the project before relocating cwd. */
    fun forSnapshot(config: JsonObject, projectDir: Path): JsonObject = config.deepCopy().apply {
        fun absolute(path: String): String = projectDir.resolve(path).toAbsolutePath().normalize().toString().replace('\\', '/')
        for (key in listOf("game_executable_path", "skin_info.skin")) {
            McdevSchema.get(this, key)?.takeIf { !it.isJsonNull && it.asString.isNotBlank() }?.let {
                McdevSchema.set(this, key, JsonPrimitive(absolute(it.asString)))
            }
        }
        getAsJsonArray("included_mod_dirs")?.let { entries ->
            add("included_mod_dirs", JsonArray().apply {
                entries.forEach { entry ->
                    if (entry.isJsonObject) add(entry.deepCopy().apply {
                        addProperty("path", absolute(get("path")?.asString ?: "./"))
                    }) else add(absolute(entry.asString))
                }
            })
        }
        val source = get("world_source_path")
        if (source != null && !source.isJsonNull) {
            val path = source.asString
            when {
                path == "auto" -> addProperty("world_source_path", if (Files.isRegularFile(projectDir.resolve("level.dat"))) absolute(".") else "")
                path.isNotBlank() -> addProperty("world_source_path", absolute(path))
            }
        }
        // Omission asks MCDK to generate a skin. An empty default object would request a file at "".
        getAsJsonObject("skin_info")?.let { if (it.get("skin")?.asString.isNullOrEmpty()) remove("skin_info") }
    }

    /** MCDK 1.6.5 links addons globally or into the world; configs without the field keep the global default. */
    fun packLinkMode(config: JsonObject): String =
        McdevSchema.get(config, "pack_link_mode")
            ?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }
            ?.asString?.takeIf { it.isNotBlank() } ?: "global"

    /** Same rule MCDK applies before launching, so a bad name fails in the IDE instead of in the console. */
    fun isValidSessionName(name: String): Boolean =
        name.isNotEmpty() && name.length <= 64 && name.all { it in 'a'..'z' || it in 'A'..'Z' || it in '0'..'9' || it == '_' || it == '-' }

    /**
     * MCDK 的 MCP 端口。默认配置就是 `enabled: true` + `19133`，外部 MCP 客户端可以照上游文档直接连；
     * 19133 已被其他实例或程序占用时退回系统分配的空闲端口，避免多开时 MCDK 以“端口被占用”中止启动。
     * 无论拿到哪个端口，MCDK 都会把它写进会话记录，`mcdk logs` / `mcdk call` 自己按记录连接。
     */
    fun mcpPort(config: JsonObject): Int {
        val preferred = McdevSchema.get(config, "mcp_server_config.server_port")
            ?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isNumber }?.asInt
        // `enabled: false` 时仍要给出端口：CLI 的 logs/call 走 MCP，启动参数会把它强制打开。
        return preferred?.takeIf { it in 1..65535 && isLoopbackPortFree(it) } ?: freeLoopbackPort()
    }

    /** 让系统分配一个空闲回环端口；0 表示分配失败，此时不传端口，交给 MCDK 用配置值。 */
    private fun freeLoopbackPort(): Int = runCatching {
        ServerSocket(0, 0, InetAddress.getLoopbackAddress()).use { it.localPort }
    }.getOrDefault(0)

    /** 试探端口能否绑定。Windows 上不带 SO_REUSEADDR 的真实绑定才是可靠的占用判断。 */
    private fun isLoopbackPortFree(port: Int): Boolean = runCatching {
        ServerSocket(port, 0, InetAddress.getLoopbackAddress()).close()
        true
    }.getOrDefault(false)

    /**
     * `mcdk run` arguments.
     *
     * `--project` makes MCDK chdir into the project so session records land in `<project>/.mcdev/sessions`
     * (where `mcdk logs` / `mcdk call` / `mcdk stop` look for them) while the launch snapshot keeps its own
     * temporary directory. `--hot-reload` stops MCDK from disabling the save-triggered reload flags that the
     * project config sets. The MCP server is mandatory for the CLI (`logs` reads `read_logs` over it), so it
     * is switched on explicitly and always given a concrete port: MCDK aborts the launch on a busy port,
     * and the default 19133 is exactly what a second instance would collide with.
     * `mcpPort = 0` means port allocation failed, so the config value is passed through untouched.
     */
    fun cliArguments(projectDir: Path, configFile: Path, sessionName: String, mcpPort: Int): List<String> = buildList {
        add("run")
        add("--project")
        add(projectDir.toAbsolutePath().normalize().toString())
        add("--config")
        add(configFile.toAbsolutePath().normalize().toString())
        add("--hot-reload")
        if (sessionName.isNotBlank()) {
            add("--session")
            add(sessionName)
        }
        add("--set")
        add("mcp_server_config.enabled=true")
        if (mcpPort > 0) {
            add("--set")
            add("mcp_server_config.server_port=$mcpPort")
        }
    }

    fun enabledModPaths(config: JsonObject): List<Path> = config.getAsJsonArray("included_mod_dirs").mapNotNull {
        if (it.isJsonObject) {
            val obj = it.asJsonObject
            if (obj.get("enabled")?.asBoolean == false) null else Path.of(obj.get("path").asString)
        } else Path.of(it.asString)
    }
}
