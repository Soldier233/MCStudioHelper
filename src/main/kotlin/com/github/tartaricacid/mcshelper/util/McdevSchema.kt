package com.github.tartaricacid.mcshelper.util

import com.google.gson.*

/** Single source for the form, validation and MCDK 1.6.0 defaults. */
object McdevSchema {
    val gson: Gson = GsonBuilder().serializeNulls().setPrettyPrinting().disableHtmlEscaping().create()
    fun defaults(): JsonObject = javaClass.getResourceAsStream("/mcdev/defaults.json")!!.bufferedReader().use {
        JsonParser.parseReader(it).asJsonObject
    }

    /** Defaults for a newly created project config, using the open project's name for its world. */
    fun defaultsForProject(projectName: String?): JsonObject = defaults().apply {
        val name = projectName?.trim()?.takeIf { it.isNotEmpty() } ?: return@apply
        addProperty("world_name", name)
        addProperty("world_folder_name", worldFolderName(name))
    }

    /** world_folder_name is an ASCII path component in MCDK's config format. */
    private fun worldFolderName(projectName: String): String {
        val cleaned = projectName.map { char ->
            if (char.code in 32..126 && char !in "<>:\"/\\|?*") char else '\u0000'
        }.joinToString("").trim().trimEnd('.', ' ')
        if (cleaned.isNotEmpty() && cleaned.none { it == '\u0000' } && cleaned != "." && cleaned != "..") return cleaned

        return "MC_DEV_WORLD"
    }

    /** Fill missing documented fields without changing the input or discarding extensions. */
    fun withDefaults(projectConfig: JsonObject?): JsonObject {
        val result = projectConfig?.deepCopy() ?: JsonObject()
        validate(result)
        val defaults = defaults()
        fields.forEach { field ->
            if (get(result, field.path) == null) set(result, field.path, get(defaults, field.path))
        }
        return result
    }

    data class Field(val path: String, val label: String, val kind: String,
                     val nullable: Boolean = false, val min: Long? = null, val max: Long? = null,
                     val choices: List<String> = emptyList())

    val fields = listOf(
        Field("game_executable_path", "游戏程序路径（空值自动检测）", "string"),
        Field("included_mod_dirs", "组件目录（相对路径以项目根目录为准）", "mods"),
        Field("world_name", "世界名称", "string"),
        Field("world_folder_name", "存档目录名", "string"),
        Field("world_source_path", "地图源目录（auto 自动识别，空值禁用）", "string", true),
        Field("world_seed", "世界种子", "long", true),
        Field("reset_world", "启动时重置世界", "bool"),
        Field("auto_join_game", "自动进入游戏", "bool"),
        Field("world_type", "世界类型", "int", choices = listOf("0 · 旧版有限", "1 · 无限", "2 · 超平坦")),
        Field("game_mode", "游戏模式", "int", choices = listOf("0 · 生存", "1 · 创造", "2 · 冒险")),
        Field("enable_cheats", "启用作弊", "bool"),
        Field("keep_inventory", "死亡不掉落", "bool"),
        Field("do_weather_cycle", "天气循环", "bool"),
        Field("do_daylight_cycle", "昼夜循环", "bool"),
        Field("user_name", "玩家名称", "string"),
        Field("skin_info.slim", "纤细皮肤模型", "bool"),
        Field("skin_info.skin", "皮肤文件（空值自动生成）", "string"),
        Field("include_debug_mod", "附加调试 MOD", "bool"),
        Field("log_protocol", "日志协议", "int", choices = listOf("0 · PIPE", "1 · Safaia（实验性）")),
        Field("auto_hot_reload_mods", "自动热更新 MOD", "bool"),
        Field("auto_hot_reload_ui", "自动热更新 JSON UI", "bool"),
        Field("auto_hot_reload_shaders", "自动热更新 Shader", "bool"),
        Field("auto_hot_reload_materials", "自动热更新 Material", "bool"),
        Field("auto_hot_reload_particles", "自动热更新 Particle", "bool"),
        Field("debug_options.reload_key", "热更新按键", "key"),
        Field("debug_options.reload_world_key", "重载世界按键", "key"),
        Field("debug_options.reload_addon_key", "重载 Addon 按键", "key"),
        Field("debug_options.reload_shaders_key", "重载 Shader 按键", "key"),
        Field("debug_options.reload_key_global", "按键在所有界面生效", "bool"),
        Field("modpc_debugger.enabled", "启用 MODPC 调试器", "bool"),
        Field("modpc_debugger.port", "MODPC 端口", "int", min = 1, max = 65535),
        Field("ptvsd_debugger.enabled", "启用 ptvsd 调试器", "bool"),
        Field("ptvsd_debugger.ip", "ptvsd 地址", "string"),
        Field("ptvsd_debugger.port", "ptvsd 端口", "int", min = 1, max = 65535),
        Field("experiment_options.data_driven_biomes", "数据驱动生物群系", "bool"),
        Field("experiment_options.upcoming_creator_features", "即将推出的创作者功能", "bool"),
        Field("experiment_options.experimental_creator_cameras", "实验性照相机", "bool"),
        Field("experiment_options.gametest", "Beta API", "bool"),
        Field("experiment_options.deferred_technical_preview", "RenderDragon 预览", "bool"),
        Field("window_style.always_on_top", "窗口置顶", "bool"),
        Field("window_style.hide_title_bar", "隐藏标题栏", "bool"),
        Field("window_style.hide_taskbar_icon", "隐藏任务栏图标", "bool"),
        Field("window_style.title_bar_color", "标题栏颜色 [红, 绿, 蓝]", "rgb", true),
        Field("window_style.opacity", "不透明度（0–255）", "int", true, 0, 255),
        Field("window_style.fixed_size", "固定大小 [宽, 高]", "size", true),
        Field("window_style.fixed_position", "固定位置 [X, Y]", "position", true),
        Field("window_style.lock_corner", "锁定角落（1 左上 / 2 右上 / 3 左下 / 4 右下）", "int", true, 1, 4),
        Field("netease_config.chat_extension", "网易聊天扩展", "bool"),
        Field("mcp_server_config.enabled", "启用 MCP 服务", "bool"),
        Field("mcp_server_config.server_ip", "MCP 地址", "string"),
        Field("mcp_server_config.server_port", "MCP 端口", "int", min = 1, max = 65535)
    )

    fun get(root: JsonObject, path: String): JsonElement? {
        var value: JsonElement = root
        for (part in path.split('.')) value = (value as? JsonObject)?.get(part) ?: return null
        return value
    }

    fun set(root: JsonObject, path: String, value: JsonElement?) {
        val parts = path.split('.')
        var parent = root
        for (part in parts.dropLast(1)) {
            val next = parent.get(part) as? JsonObject
            if (next == null && value == null) return
            parent = next ?: JsonObject().also { parent.add(part, it) }
        }
        if (value == null && !parent.has(parts.last())) return
        if (value == null) parent.remove(parts.last()) else parent.add(parts.last(), value.deepCopy())
        // Remove empty containers after deleting their last field.
        if (value == null && parts.size == 2 && parent.size() == 0) root.remove(parts.first())
    }

    fun parse(text: String): JsonObject {
        val reader = com.google.gson.stream.JsonReader(java.io.StringReader(text))
        reader.setStrictness(Strictness.STRICT)
        val value = JsonParser.parseReader(reader)
        require(reader.peek() == com.google.gson.stream.JsonToken.END_DOCUMENT && value.isJsonObject) {
            "配置必须是一个完整的 JSON 对象"
        }
        return value.asJsonObject.also(::validate)
    }

    fun validate(root: JsonObject) {
        for (group in fields.mapNotNull { it.path.substringBefore('.', "").takeIf(String::isNotEmpty) }.distinct()) {
            require(!root.has(group) || root.get(group).isJsonObject) { "$group 必须是对象" }
        }
        for (field in fields) {
            val value = get(root, field.path) ?: continue
            require(valid(field, value)) { "${field.path} 的值无效（${field.label}）" }
        }
        get(root, "world_folder_name")?.asString?.let {
            require(it.isNotBlank() && it !in listOf(".", "..") && it.none { c -> c.code !in 32..126 || c in "<>:\"/\\|?*" }) {
                "world_folder_name 必须为 ASCII 目录名，不能包含路径分隔符"
            }
        }
    }

    private fun integer(value: JsonElement): Long? = if (value.isJsonPrimitive && value.asJsonPrimitive.isNumber)
        value.toString().toLongOrNull() else null

    private fun valid(field: Field, value: JsonElement): Boolean {
        if (value.isJsonNull) return field.nullable
        return when (field.kind) {
            "string" -> value.isJsonPrimitive && value.asJsonPrimitive.isString
            "key" -> value.isJsonPrimitive && value.asJsonPrimitive.isString &&
                (value.asString.isEmpty() || value.asString.toIntOrNull() != null)
            "bool" -> value.isJsonPrimitive && value.asJsonPrimitive.isBoolean
            "long", "int" -> integer(value)?.let { number ->
                (field.min == null || number >= field.min) && (field.max == null || number <= field.max) &&
                    (field.choices.isEmpty() || field.choices.any { it.substringBefore(' ').toLong() == number })
            } ?: false
            "rgb", "size", "position" -> value.isJsonArray && value.asJsonArray.size() == (if (field.kind == "rgb") 3 else 2) &&
                value.asJsonArray.all { v -> integer(v)?.let { when (field.kind) {
                    "rgb" -> it in 0..255; "size" -> it in 1..32768; else -> it in -32768..32768
                } } ?: false }
            "mods" -> value.isJsonArray && value.asJsonArray.all { entry ->
                (entry.isJsonPrimitive && entry.asJsonPrimitive.isString && entry.asString.isNotBlank()) ||
                    (entry.isJsonObject && (!entry.asJsonObject.has("path") ||
                        entry.asJsonObject.get("path").let { it.isJsonPrimitive && it.asJsonPrimitive.isString && it.asString.isNotBlank() }) &&
                        listOf("enabled", "hot_reload").all { key -> !entry.asJsonObject.has(key) ||
                            entry.asJsonObject.get(key).let { it.isJsonPrimitive && it.asJsonPrimitive.isBoolean } })
            }
            else -> false
        }
    }
}
