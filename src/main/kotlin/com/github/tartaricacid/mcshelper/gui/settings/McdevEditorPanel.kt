package com.github.tartaricacid.mcshelper.gui.settings

import com.github.tartaricacid.mcshelper.util.KeyboardTypes
import com.github.tartaricacid.mcshelper.util.McdevSchema
import com.google.gson.*
import com.intellij.ui.components.JBTextField
import java.awt.*
import javax.swing.*
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener

/** The form and JSON tab edit one project document; defaults are only displayed until changed. */
class McdevEditorPanel(
    private val worldAction: ((Boolean, JsonObject) -> Unit)? = null,
    private val onChange: (String) -> Unit = {}
) : JPanel(BorderLayout()) {
    private data class Row(val field: McdevSchema.Field, val input: JComponent, var initial: JsonElement = JsonNull.INSTANCE)
    private val rows = mutableListOf<Row>()
    private var root = JsonObject()
    private var updating = false
    private val error = JLabel(" ")
    private val json = JTextArea(20, 60).apply { name = "json" }
    private val tabs = JTabbedPane()
    private var previousTab = 0

    private fun column(): JPanel = object : JPanel() {
        init { layout = BoxLayout(this, BoxLayout.Y_AXIS); alignmentX = 0f }
        override fun getMaximumSize() = Dimension(Int.MAX_VALUE, preferredSize.height)
    }

    init {
        val form = object : JPanel(), Scrollable {
            init { layout = BoxLayout(this, BoxLayout.Y_AXIS) }
            override fun getPreferredScrollableViewportSize() = Dimension(720, 650)
            override fun getScrollableUnitIncrement(visibleRect: Rectangle, orientation: Int, direction: Int) = 24
            override fun getScrollableBlockIncrement(visibleRect: Rectangle, orientation: Int, direction: Int) = visibleRect.height - 24
            override fun getScrollableTracksViewportWidth() = true
            override fun getScrollableTracksViewportHeight() = false
        }
        fun group(parent: JPanel, title: String, paths: List<String>, checkGrid: Boolean = false): JPanel {
            val section = column().apply { border = BorderFactory.createEmptyBorder(8, 12, 4, 12) }
            section.add(JLabel(title).apply {
                font = font.deriveFont(Font.BOLD); alignmentX = 0f
                border = BorderFactory.createEmptyBorder(0, 0, 6, 0)
            })
            val contents = if (checkGrid) JPanel(GridLayout(0, 2, 12, 3)).apply { alignmentX = 0f } else column()
            paths.forEach { path -> contents.add(createRow(McdevSchema.fields.single { it.path == path })) }
            section.add(contents)
            parent.add(section)
            return section
        }
        group(form, "启动与组件", listOf("game_executable_path", "included_mod_dirs"))
        val world = group(form, "玩家与世界", listOf("user_name", "world_name", "world_folder_name", "world_source_path",
            "world_seed", "world_type", "game_mode"))
        if (worldAction != null) world.add(JPanel(FlowLayout(FlowLayout.LEFT, 0, 4)).apply {
            alignmentX = 0f
            add(JButton("打开存档目录").apply { addActionListener { attempt { worldAction.invoke(false, value()) } } })
            add(JButton("存档移至回收站").apply { addActionListener { attempt { worldAction.invoke(true, value()) } } })
        })
        group(form, "游戏规则", listOf("auto_join_game", "reset_world", "enable_cheats", "keep_inventory", "do_weather_cycle", "do_daylight_cycle"), true)

        val advanced = column().apply { name = "advanced"; isVisible = false }
        form.add(JToggleButton("▶ 高级设置").apply {
            name = "advancedToggle"; alignmentX = 0f
            addActionListener {
                advanced.isVisible = isSelected
                text = if (isSelected) "▼ 高级设置" else "▶ 高级设置"
                form.revalidate(); form.repaint()
            }
        })
        form.add(advanced)
        group(advanced, "日志与热更新", listOf("include_debug_mod", "log_protocol", "auto_hot_reload_mods",
            "auto_hot_reload_ui", "auto_hot_reload_shaders", "auto_hot_reload_materials", "auto_hot_reload_particles"))
        group(advanced, "快捷键", McdevSchema.fields.filter { it.path.startsWith("debug_options.") }.map { it.path })
        group(advanced, "窗口样式", McdevSchema.fields.filter { it.path.startsWith("window_style.") }.map { it.path })
        group(advanced, "皮肤", listOf("skin_info.slim", "skin_info.skin"))
        group(advanced, "实验性功能", McdevSchema.fields.filter { it.path.startsWith("experiment_options.") }.map { it.path })
        group(advanced, "调试服务", McdevSchema.fields.filter { it.path.startsWith("modpc_debugger.") || it.path.startsWith("ptvsd_debugger.") }.map { it.path })
        group(advanced, "网易扩展与 MCP", McdevSchema.fields.filter { it.path.startsWith("netease_config.") || it.path.startsWith("mcp_server_config.") }.map { it.path })

        tabs.addTab("可视化编辑", JScrollPane(form))
        tabs.addTab("JSON 原文", JScrollPane(json))
        tabs.addChangeListener {
            if (!updating) {
                try {
                    if (previousTab == 1) {
                        root = McdevSchema.parse(json.text)
                        render()
                    } else {
                        // Switching views must retain the document's original formatting.
                        buildForm()
                    }
                    previousTab = tabs.selectedIndex
                    error.text = " "
                } catch (e: Exception) {
                    updating = true; tabs.selectedIndex = previousTab; updating = false
                    error.text = "请修正：${e.message}"
                }
            }
        }
        json.document.addDocumentListener(listener {
            if (!updating && tabs.selectedIndex == 1) {
                onChange(json.text)
                attempt { McdevSchema.parse(json.text) }
            }
        })
        add(tabs, BorderLayout.CENTER)
        add(error, BorderLayout.SOUTH)
        load("{}")
    }

    private fun createRow(field: McdevSchema.Field): JComponent {
        val input: JComponent = when {
            field.kind == "bool" -> JCheckBox(field.label)
            field.kind == "key" -> JComboBox((listOf("不绑定") + KeyboardTypes.keys.map { it.toString() }).toTypedArray()).apply { isEditable = true }
            field.choices.isNotEmpty() -> JComboBox(field.choices.toTypedArray())
            field.kind == "mods" -> ModDirectoriesEditor { formChanged() }
            else -> JBTextField().apply {
                emptyText.text = when {
                    field.path == "world_seed" -> "随机生成"
                    field.path == "window_style.fixed_size" || field.path == "window_style.fixed_position" -> "不固定"
                    field.path == "window_style.lock_corner" -> "不锁定"
                    field.path.startsWith("window_style.") && field.nullable -> "使用系统默认"
                    field.path == "game_executable_path" -> "自动检测开发端"
                    else -> ""
                }
            }
        }
        input.name = field.path
        input.toolTipText = "${field.label} · ${field.path}"
        val row = Row(field, input)
        rows.add(row)
        when (input) {
            is javax.swing.text.JTextComponent -> input.document.addDocumentListener(listener { formChanged() })
            is JCheckBox -> input.addActionListener { formChanged() }
            is JComboBox<*> -> {
                input.addActionListener { formChanged() }
                if (input.isEditable) (input.editor.editorComponent as? javax.swing.text.JTextComponent)?.document?.addDocumentListener(listener { formChanged() })
            }
        }
        return object : JPanel(BorderLayout(10, 2)) {
            override fun getMaximumSize() = Dimension(Int.MAX_VALUE, preferredSize.height)
        }.apply {
            alignmentX = 0f; border = BorderFactory.createEmptyBorder(3, 0, 3, 0)
            if (input !is JCheckBox) add(JLabel(field.label.substringBefore('（')).apply {
                toolTipText = input.toolTipText
                preferredSize = Dimension(145, 25)
                labelFor = input
            }, if (input is ModDirectoriesEditor) BorderLayout.NORTH else BorderLayout.WEST)
            add(input, BorderLayout.CENTER)
            if (field.path in listOf("game_executable_path", "skin_info.skin", "world_source_path")) add(JButton("浏览…").apply {
                addActionListener {
                    val chooser = JFileChooser().apply {
                        fileSelectionMode = if (field.path == "world_source_path") JFileChooser.DIRECTORIES_ONLY else JFileChooser.FILES_ONLY
                    }
                    if (chooser.showOpenDialog(this@McdevEditorPanel) == JFileChooser.APPROVE_OPTION)
                        (input as JTextField).text = chooser.selectedFile.path.replace('\\', '/')
                }
            }, BorderLayout.EAST)
        }
    }

    private fun listener(action: () -> Unit) = object : DocumentListener {
        override fun insertUpdate(e: DocumentEvent) = action()
        override fun removeUpdate(e: DocumentEvent) = action()
        override fun changedUpdate(e: DocumentEvent) = action()
    }
    private fun attempt(action: () -> Unit) {
        try { action(); error.text = " " } catch (e: Exception) { error.text = "请修正：${e.message}" }
    }

    fun load(text: String) {
        updating = true
        try {
            json.text = text
            root = McdevSchema.parse(text)
            render()
            error.text = " "
        } catch (e: Exception) {
            tabs.selectedIndex = 1; previousTab = 1
            error.text = "请在 JSON 页修正：${e.message}"
        } finally { updating = false }
    }

    private fun render() {
        val wasUpdating = updating; updating = true
        try {
            val effective = McdevSchema.withDefaults(root)
            rows.forEach { row ->
                val value = McdevSchema.get(effective, row.field.path)!!
                val text = when {
                    value.isJsonNull -> ""
                    value.isJsonPrimitive && value.asJsonPrimitive.isString -> value.asString
                    else -> value.toString()
                }
                when (val input = row.input) {
                    is javax.swing.text.JTextComponent -> input.text = text
                    is ModDirectoriesEditor -> input.load(value.asJsonArray)
                    is JCheckBox -> input.isSelected = value.asBoolean
                    is JComboBox<*> -> input.selectedItem = if (row.field.kind == "key") {
                        if (text.isEmpty()) "不绑定" else KeyboardTypes.keys.firstOrNull { it.code.toString() == text }?.toString() ?: text
                    } else row.field.choices.first { it.substringBefore(' ') == text }
                }
                row.initial = readValue(row)
            }
        } finally { updating = wasUpdating }
    }

    private fun readValue(row: Row): JsonElement = when (val input = row.input) {
        is ModDirectoriesEditor -> input.value()
        is JCheckBox -> JsonPrimitive(input.isSelected)
        else -> {
            val text = when (input) {
                is javax.swing.text.JTextComponent -> input.text
                is JComboBox<*> -> (if (input.isEditable) input.editor.item else input.selectedItem)?.toString().orEmpty()
                else -> ""
            }
            when {
                row.field.kind == "string" -> JsonPrimitive(text)
                row.field.kind == "key" -> JsonPrimitive(if (text == "不绑定") "" else text.substringBefore(" · ").trim())
                row.field.nullable && text.isBlank() -> JsonNull.INSTANCE
                row.field.choices.isNotEmpty() -> JsonParser.parseString(text.substringBefore(' '))
                else -> JsonParser.parseString(text)
            }
        }
    }

    private fun buildForm(): JsonObject = root.deepCopy().also { result ->
        rows.forEach { row ->
            val value = readValue(row)
            if (value != row.initial) McdevSchema.set(result, row.field.path, value)
        }
        McdevSchema.validate(result)
    }

    private fun formChanged() {
        if (updating || tabs.selectedIndex != 0) return
        attempt {
            val text = McdevSchema.gson.toJson(buildForm())
            updating = true
            try { json.text = text } finally { updating = false }
            onChange(text)
        }
    }

    fun value(): JsonObject = if (tabs.selectedIndex == 1) McdevSchema.parse(json.text) else buildForm()
}
