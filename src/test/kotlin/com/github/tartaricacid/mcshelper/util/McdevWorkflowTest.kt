package com.github.tartaricacid.mcshelper.util

import com.google.gson.JsonNull
import java.nio.file.Files
import kotlin.test.*

class McdevWorkflowTest {
    @Test fun schemaCoversEveryDefaultIncludingNestedFields() {
        val defaults = McdevSchema.defaults()
        fun leaves(root: com.google.gson.JsonObject, prefix: String = ""): List<String> = root.entrySet().flatMap {
            val key = prefix + it.key
            if (it.value.isJsonObject) leaves(it.value.asJsonObject, "$key.") else listOf(key)
        }
        assertEquals(leaves(defaults).toSet(), McdevSchema.fields.map { it.path }.toSet())
        McdevSchema.validate(defaults)
        assertEquals("82", McdevSchema.get(defaults, "debug_options.reload_key")!!.asString)
        assertEquals(JsonNull.INSTANCE, defaults.get("world_seed"))
        assertEquals("global", defaults.get("pack_link_mode").asString)
    }

    @Test fun projectValuesKeepEmptyFalseNullAndUnknownFields() {
        val project = McdevSchema.parse("""{"user_name":"tester","keep_inventory":false,"debug_options":{"reload_key":"","future":7},"window_style":{"opacity":null},"world_source_path":"","extension":{"a":1}}""")
        val original = project.deepCopy()
        val first = McdevLaunchConfig.resolve(project)
        val second = McdevLaunchConfig.resolve(project)
        assertEquals(first, second)
        assertFalse(first.get("keep_inventory").asBoolean)
        assertTrue(first.get("auto_join_game").asBoolean)
        assertEquals("tester", first.get("user_name").asString)
        assertEquals("", McdevSchema.get(first, "debug_options.reload_key")!!.asString)
        assertEquals(JsonNull.INSTANCE, McdevSchema.get(first, "window_style.opacity"))
        assertEquals("", first.get("world_source_path").asString)
        assertEquals(7, McdevSchema.get(first, "debug_options.future")!!.asInt)
        assertEquals(1, McdevSchema.get(first, "extension.a")!!.asInt)
        assertEquals(original, project)
        first.addProperty("user_name", "changed snapshot")
        assertEquals("tester", second.get("user_name").asString)
        assertEquals(McdevSchema.defaults(), McdevLaunchConfig.resolve(null))
    }

    @Test fun projectDefaultsUseProjectNameForWorldValues() {
        val defaults = McdevSchema.defaultsForProject("My Mod Project")
        assertEquals("My Mod Project", defaults.get("world_name").asString)
        assertEquals("My Mod Project", defaults.get("world_folder_name").asString)

        val unicode = McdevSchema.defaultsForProject("我的项目")
        assertEquals("我的项目", unicode.get("world_name").asString)
        assertEquals("MC_DEV_WORLD", unicode.get("world_folder_name").asString)
        McdevSchema.validate(unicode)
    }

    @Test fun strictJsonValidationProtectsFilesFromInvalidReplacement() {
        for (text in listOf("[]", "null", "{} {}", "{world_seed: 1}", """{"keep_inventory":"false"}""",
            """{"world_seed":1.5}""", """{"modpc_debugger":{"port":65536}}""", """{"window_style":null}""",
            """{"window_style":{"fixed_size":[0,720]}}""", """{"world_folder_name":"../world"}""",
            """{"included_mod_dirs":[{"enabled":"false"}]}""", """{"pack_link_mode":"save"}""")) {
            assertFails("Should reject $text") { McdevSchema.parse(text) }
        }
        McdevSchema.parse("""{"future_option":{"nested":null},"debug_options":{"reload_key":"-99"},"world_source_path":""}""")
        assertEquals("world", McdevSchema.parse("""{"pack_link_mode":"world"}""").get("pack_link_mode").asString)
    }

    @Test fun snapshotsResolvePathsAndPreserveObjectDirectoryFlags() {
        val projectDir = Files.createTempDirectory("mcdev-test-")
        try {
            val project = McdevSchema.parse("""{"game_executable_path":"game/client.exe","skin_info":{"skin":"skins/test.png"},"included_mod_dirs":["./",{"path":"extra","enabled":false,"hot_reload":false,"future":7}],"future":"relative/keep"}""")
            val original = project.deepCopy()
            val snapshot = McdevLaunchConfig.forSnapshot(McdevLaunchConfig.resolve(project), projectDir)
            assertEquals(projectDir.resolve("game/client.exe").toString().replace('\\', '/'), snapshot.get("game_executable_path").asString)
            assertEquals(projectDir.resolve("skins/test.png").toString().replace('\\', '/'), McdevSchema.get(snapshot, "skin_info.skin")!!.asString)
            assertEquals("", snapshot.get("world_source_path").asString)
            assertEquals(listOf(projectDir), McdevLaunchConfig.enabledModPaths(snapshot))
            val objectEntry = snapshot.getAsJsonArray("included_mod_dirs")[1].asJsonObject
            assertFalse(objectEntry.get("hot_reload").asBoolean)
            assertEquals(7, objectEntry.get("future").asInt)
            assertEquals("relative/keep", snapshot.get("future").asString)
            assertEquals(original, project)
            Files.writeString(projectDir.resolve("level.dat"), "test")
            val world = McdevLaunchConfig.forSnapshot(McdevSchema.defaults(), projectDir)
            assertEquals(projectDir.toString().replace('\\', '/'), world.get("world_source_path").asString)
            assertFalse(world.has("skin_info"))
        } finally {
            Files.deleteIfExists(projectDir.resolve("level.dat")); Files.deleteIfExists(projectDir)
        }
    }

    @Test fun cliArgumentsSwitchToRunAndExposeTheSession() {
        val projectDir = Files.createTempDirectory("mcdev-cli-")
        try {
            val configFile = projectDir.resolve(".mcdev.json")
            val arguments = McdevLaunchConfig.cliArguments(projectDir, configFile, "", 19133)
            assertEquals("run", arguments.first())
            assertEquals(projectDir.toAbsolutePath().normalize().toString(), arguments[arguments.indexOf("--project") + 1])
            assertEquals(configFile.toAbsolutePath().normalize().toString(), arguments[arguments.indexOf("--config") + 1])
            assertTrue(arguments.contains("--hot-reload"))
            assertFalse(arguments.contains("--session"))
            assertTrue(arguments.contains("mcp_server_config.enabled=true"))
            assertTrue(arguments.contains("mcp_server_config.server_port=19133"))

            val named = McdevLaunchConfig.cliArguments(projectDir, configFile, "dev-1", 19133)
            assertEquals("dev-1", named[named.indexOf("--session") + 1])
            assertTrue(named.contains("mcp_server_config.server_port=19133"))
        } finally {
            Files.deleteIfExists(projectDir)
        }
    }

    @Test fun sessionNamesMatchMcdkRules() {
        assertTrue(McdevLaunchConfig.isValidSessionName("dev-1_a"))
        assertFalse(McdevLaunchConfig.isValidSessionName(""))
        assertFalse(McdevLaunchConfig.isValidSessionName("dev 1"))
        assertFalse(McdevLaunchConfig.isValidSessionName("会话"))
        assertFalse(McdevLaunchConfig.isValidSessionName("a".repeat(65)))
    }

    @Test fun mcpIsOnByDefaultAndPortFallsBackWhenBusy() {
        // 默认配置就启用 MCP，端口默认 19133；MCDK 会把它写进会话记录，CLI 自己按记录连接。
        assertTrue(McdevSchema.get(McdevSchema.defaults(), "mcp_server_config.enabled")!!.asBoolean)
        assertEquals(19133, McdevSchema.get(McdevSchema.defaults(), "mcp_server_config.server_port")!!.asInt)

        // 配的端口空闲就照用，外部 MCP 客户端（含默认的 19133）能按 .mcdev.json 直接连上。
        val free = java.net.ServerSocket(0, 0, java.net.InetAddress.getLoopbackAddress()).use { it.localPort }
        val preferred = McdevSchema.parse("""{"mcp_server_config":{"enabled":true,"server_port":$free}}""")
        assertEquals(free, McdevLaunchConfig.mcpPort(preferred))

        // 被占用时退回空闲端口，避免 MCDK 因“端口被占用”中止启动（多开、或 19133 已被别的实例占用）。
        java.net.ServerSocket(0, 0, java.net.InetAddress.getLoopbackAddress()).use { busy ->
            val config = McdevSchema.parse(
                """{"mcp_server_config":{"enabled":true,"server_port":${busy.localPort}}}"""
            )
            val port = McdevLaunchConfig.mcpPort(config)
            assertNotEquals(busy.localPort, port)
            assertTrue(port in 1..65535, "应回退到有效端口，实际 $port")
        }
    }

    @Test fun staticKeyboardChoicesContainAllSupportedKeys() {
        val keys = KeyboardTypes.keys
        assertEquals(101, keys.size)
        assertEquals(keys.size, keys.map { it.name }.toSet().size)
        assertEquals(-97, keys.single { it.name == "KEY_MOUSE_Middle" }.code)
        assertEquals(-99, keys.single { it.name == "KEY_MOUSE_LEFT" }.code)
        assertEquals(82, keys.single { it.name == "KEY_R" }.code)
        assertEquals(124, keys.single { it.name == "KEY_F13" }.code)
        assertTrue(keys.single { it.name == "KEY_GOBACK" }.description.contains("弃用"))
        assertTrue(keys.all { it.description.isNotBlank() })
    }
}
