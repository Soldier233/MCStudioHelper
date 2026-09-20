package com.github.tartaricacid.mcshelper.util

import com.google.gson.*
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

    fun enabledModPaths(config: JsonObject): List<Path> = config.getAsJsonArray("included_mod_dirs").mapNotNull {
        if (it.isJsonObject) {
            val obj = it.asJsonObject
            if (obj.get("enabled")?.asBoolean == false) null else Path.of(obj.get("path").asString)
        } else Path.of(it.asString)
    }
}
