package com.github.tartaricacid.mcshelper.gui.settings

import com.google.gson.*
import java.awt.BorderLayout
import java.awt.FlowLayout
import javax.swing.*
import javax.swing.table.DefaultTableModel

/** Preserves object extensions and the string shorthand while editing documented fields. */
internal class ModDirectoriesEditor(private val changed: () -> Unit) : JPanel(BorderLayout()) {
    private val originals = mutableListOf<JsonElement>()
    private var loading = false
    private val model = object : DefaultTableModel(arrayOf("组件路径", "启用", "热更新"), 0) {
        override fun getColumnClass(columnIndex: Int): Class<*> = if (columnIndex == 0) String::class.java else java.lang.Boolean::class.java
    }
    private val table = JTable(model).apply {
        putClientProperty("terminateEditOnFocusLost", true)
        preferredScrollableViewportSize = java.awt.Dimension(450, 80)
    }
    private val buttons = JPanel(FlowLayout(FlowLayout.LEFT)).apply {
        add(JButton("添加目录").apply { addActionListener {
            val chooser = JFileChooser().apply { fileSelectionMode = JFileChooser.DIRECTORIES_ONLY }
            if (chooser.showOpenDialog(this@ModDirectoriesEditor) == JFileChooser.APPROVE_OPTION) {
                originals.add(JsonPrimitive(chooser.selectedFile.path.replace('\\', '/')))
                this@ModDirectoriesEditor.model.addRow(arrayOf<Any>(chooser.selectedFile.path.replace('\\', '/'), true, true))
            }
        } })
        add(JButton("添加相对目录").apply { addActionListener {
            originals.add(JsonPrimitive("./")); this@ModDirectoriesEditor.model.addRow(arrayOf<Any>("./", true, true))
        } })
        add(JButton("移除所选").apply { addActionListener {
            table.selectedRows.sortedDescending().forEach { originals.removeAt(it); this@ModDirectoriesEditor.model.removeRow(it) }
        } })
    }
    init {
        table.columnModel.getColumn(0).preferredWidth = 330
        add(JScrollPane(table).apply { setColumnHeaderView(table.tableHeader) }, BorderLayout.CENTER)
        add(buttons, BorderLayout.SOUTH)
        model.addTableModelListener { if (!loading) changed() }
    }
    fun load(value: JsonArray) {
        loading = true
        try {
            model.rowCount = 0; originals.clear()
            value.forEach { entry ->
                originals.add(entry.deepCopy())
                val obj = entry as? JsonObject
                this@ModDirectoriesEditor.model.addRow(arrayOf(obj?.get("path")?.asString ?: if (obj != null) "./" else entry.asString,
                    obj?.get("enabled")?.asBoolean ?: true, obj?.get("hot_reload")?.asBoolean ?: true))
            }
        } finally { loading = false }
    }
    fun value(): JsonArray = JsonArray().apply {
        for (row in 0 until model.rowCount) {
            val path = model.getValueAt(row, 0).toString()
            val enabled = model.getValueAt(row, 1) as Boolean
            val reload = model.getValueAt(row, 2) as Boolean
            val original = originals[row]
            if (original.isJsonPrimitive && enabled && reload) add(path)
            else add((original as? JsonObject)?.deepCopy()?.apply {
                if (has("path") || path != "./") addProperty("path", path)
                if (has("enabled") || !enabled) addProperty("enabled", enabled)
                if (has("hot_reload") || !reload) addProperty("hot_reload", reload)
            } ?: JsonObject().apply { addProperty("path", path); addProperty("enabled", enabled); addProperty("hot_reload", reload) })
        }
    }
}
