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

    @Test fun strictJsonValidationProtectsFilesFromInvalidReplacement() {
        for (text in listOf("[]", "null", "{} {}", "{world_seed: 1}", """{"keep_inventory":"false"}""",
            """{"world_seed":1.5}""", """{"modpc_debugger":{"port":65536}}""", """{"window_style":null}""",
            """{"window_style":{"fixed_size":[0,720]}}""", """{"world_folder_name":"../world"}""",
            """{"included_mod_dirs":[{"enabled":"false"}]}""")) {
            assertFails("Should reject $text") { McdevSchema.parse(text) }
        }
        McdevSchema.parse("""{"future_option":{"nested":null},"debug_options":{"reload_key":"-99"},"world_source_path":""}""")
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
