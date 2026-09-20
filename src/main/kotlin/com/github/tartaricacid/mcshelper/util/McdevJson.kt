package com.github.tartaricacid.mcshelper.util

import com.github.tartaricacid.mcshelper.options.GameMode
import com.github.tartaricacid.mcshelper.options.LevelType
import com.github.tartaricacid.mcshelper.options.MCRunConfigurationOptions
import com.google.gson.GsonBuilder
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.intellij.execution.ExecutionException
import com.intellij.openapi.project.Project
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

@Suppress("DialogTitleCapitalization")
class McdevJson {
    companion object {
        const val FILE_NAME = ".mcdev.json"

        private val GSON = GsonBuilder()
            .setPrettyPrinting()
            .disableHtmlEscaping()
            .create()

        fun filePath(project: Project): Path? {
            val base = project.basePath ?: return null
            return Paths.get(base).resolve(FILE_NAME)
        }

        fun read(project: Project): JsonObject? {
            val path = filePath(project) ?: return null
            if (!Files.isRegularFile(path)) {
                return null
            }
            return try {
                Files.newBufferedReader(path).use { reader ->
                    JsonParser.parseReader(reader).asJsonObject
                }
            } catch (_: Exception) {
                null
            }
        }

        data class UiOverlay(
            val gameExecutablePath: String? = null,
            val includedModDirs: List<String>? = null,
            val worldFolderName: String? = null,
            val worldSeed: Long? = null,
            val userName: String? = null,
            val gameMode: GameMode? = null,
            val levelType: LevelType? = null,
            val enableCheats: Boolean? = null,
            val keepInventory: Boolean? = null,
            val doDaylightCycle: Boolean? = null,
            val doWeatherCycle: Boolean? = null
        )

        fun readUiOverlay(project: Project): UiOverlay? {
            val json = read(project) ?: return null
            val projectPath = project.basePath?.let { Paths.get(it) } ?: return null
            return UiOverlay(
                gameExecutablePath = stringValue(json, "game_executable_path")
                    ?.takeIf { it.isNotBlank() }
                    ?.replace('/', '\\'),
                includedModDirs = json.get("included_mod_dirs")
                    ?.takeIf { it.isJsonArray }
                    ?.let { extraModDirs(it.asJsonArray, projectPath) },
                worldFolderName = stringValue(json, "world_folder_name")?.takeIf { it.isNotBlank() },
                worldSeed = longValue(json, "world_seed"),
                userName = stringValue(json, "user_name")?.takeIf { it.isNotBlank() },
                gameMode = intValue(json, "game_mode")?.let { GameMode.fromCode(it) },
                levelType = intValue(json, "world_type")?.let { LevelType.fromCode(it) },
                enableCheats = boolValue(json, "enable_cheats"),
                keepInventory = boolValue(json, "keep_inventory"),
                doDaylightCycle = boolValue(json, "do_daylight_cycle"),
                doWeatherCycle = boolValue(json, "do_weather_cycle")
            )
        }

        @Throws(ExecutionException::class)
        fun mergeAndWrite(project: Project, options: MCRunConfigurationOptions) {
            val projectPath = project.basePath?.let { Paths.get(it) }
                ?: throw ExecutionException("当前项目路径为空")
            val path = projectPath.resolve(FILE_NAME)
            val root = read(project) ?: JsonObject()

            if (!root.has("include_debug_mod")) {
                root.addProperty("include_debug_mod", true)
            }
            if (!root.has("auto_join_game")) {
                root.addProperty("auto_join_game", true)
            }
            if (!root.has("auto_hot_reload_mods")) {
                root.addProperty("auto_hot_reload_mods", true)
            }

            val gamePath = options.gameExecutablePath?.replace('\\', '/') ?: ""
            if (gamePath.isBlank()) {
                throw ExecutionException("启动程序路径不能为空")
            }
            root.addProperty("game_executable_path", gamePath)
            root.addProperty("world_folder_name", options.worldFolderName)
            root.addProperty("world_seed", options.worldSeed)
            root.addProperty("user_name", options.userName)
            root.addProperty("game_mode", options.gameMode.code)
            root.addProperty("world_type", options.levelType.code)
            root.addProperty("enable_cheats", options.enableCheats)
            root.addProperty("keep_inventory", options.keepInventory)
            root.addProperty("do_daylight_cycle", options.doDaylightCycle)
            root.addProperty("do_weather_cycle", options.doWeatherCycle)
            root.add("included_mod_dirs", mergeIncludedModDirs(root.get("included_mod_dirs"), options.includedModDirs))

            try {
                Files.newBufferedWriter(path).use { writer ->
                    GSON.toJson(root, writer)
                    writer.write(System.lineSeparator())
                }
            } catch (e: Exception) {
                throw ExecutionException("写入 $FILE_NAME 失败：${e.message}", e)
            }
        }

        private fun mergeIncludedModDirs(existing: JsonElement?, extraDirs: List<String>): JsonArray {
            val existingByPath = linkedMapOf<String, JsonElement>()
            if (existing != null && existing.isJsonArray) {
                for (element in existing.asJsonArray) {
                    val path = entryPath(element) ?: continue
                    existingByPath.putIfAbsent(normalizeModPath(path), element)
                }
            }

            val result = JsonArray()
            val seen = linkedSetOf<String>()

            fun addPath(rawPath: String) {
                val key = normalizeModPath(rawPath)
                if (!seen.add(key)) {
                    return
                }
                val previous = existingByPath[key]
                if (previous != null && previous.isJsonObject) {
                    result.add(previous)
                } else {
                    result.add(rawPath.replace('\\', '/'))
                }
            }

            addPath("./")
            for (dir in extraDirs) {
                if (dir.isBlank()) {
                    continue
                }
                addPath(dir)
            }
            return result
        }

        private fun extraModDirs(array: JsonArray, projectPath: Path): List<String> {
            val projectKey = normalizeModPath(projectPath.toAbsolutePath().toString())
            return array.mapNotNull { element ->
                val path = entryPath(element) ?: return@mapNotNull null
                val key = normalizeModPath(path)
                if (key == "./" || key == "." || key == projectKey) {
                    null
                } else {
                    path.replace('/', '\\')
                }
            }
        }

        private fun entryPath(element: JsonElement): String? {
            return when {
                element.isJsonPrimitive && element.asJsonPrimitive.isString -> element.asString
                element.isJsonObject -> stringValue(element.asJsonObject, "path")
                else -> null
            }
        }

        private fun normalizeModPath(path: String): String {
            val trimmed = path.trim().replace('\\', '/')
            if (trimmed == "." || trimmed == "./") {
                return "./"
            }
            return trimmed.trimEnd('/').lowercase()
        }

        private fun stringValue(json: JsonObject, key: String): String? {
            val element = json.get(key) ?: return null
            return if (element.isJsonPrimitive && element.asJsonPrimitive.isString) {
                element.asString
            } else {
                null
            }
        }

        private fun intValue(json: JsonObject, key: String): Int? {
            val element = json.get(key) ?: return null
            return if (element.isJsonPrimitive && element.asJsonPrimitive.isNumber) {
                element.asInt
            } else {
                null
            }
        }

        private fun longValue(json: JsonObject, key: String): Long? {
            val element = json.get(key) ?: return null
            return if (element.isJsonPrimitive && element.asJsonPrimitive.isNumber) {
                element.asLong
            } else {
                null
            }
        }

        private fun boolValue(json: JsonObject, key: String): Boolean? {
            val element = json.get(key) ?: return null
            return if (element.isJsonPrimitive && element.asJsonPrimitive.isBoolean) {
                element.asBoolean
            } else {
                null
            }
        }
    }
}
