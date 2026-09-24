package com.github.tartaricacid.mcshelper.gui

import com.github.tartaricacid.mcshelper.options.LogLevel
import com.github.tartaricacid.mcshelper.run.MCRunConfiguration
import com.github.tartaricacid.mcshelper.util.McdevJson
import com.intellij.openapi.options.ConfigurationException
import com.intellij.openapi.options.SettingsEditor
import com.intellij.openapi.ui.Messages
import com.intellij.ui.components.JBTextField
import java.awt.*
import javax.swing.*

class MCSettingsEditor : SettingsEditor<MCRunConfiguration>() {
    private val mcdk = FittingField()
    private val logLevel = JComboBox(LogLevel.entries.toTypedArray())
    private val mode = JComboBox(arrayOf("自动", "主客户端", "额外客户端"))
    private val modeValues = listOf("AUTO", "PRIMARY", "SECONDARY")
    private val debugPort = JBTextField("5678").apply { columns = 6 }
    private var current: MCRunConfiguration? = null

    private val content = object : JPanel() {
        init {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            border = BorderFactory.createEmptyBorder(8, 12, 8, 12)
        }

        override fun getMinimumSize(): Dimension = Dimension(280, preferredSize.height)
        override fun getMaximumSize(): Dimension = Dimension(Int.MAX_VALUE, preferredSize.height)
    }

    init {
        logLevel.renderer = object : DefaultListCellRenderer() {
            override fun getListCellRendererComponent(list: JList<*>?, value: Any?, index: Int, selected: Boolean, focus: Boolean): Component =
                super.getListCellRendererComponent(list, (value as? LogLevel)?.displayName ?: value, index, selected, focus)
        }
        mcdk.name = "mcdkPath"
        val browse = JButton("浏览…").apply {
            name = "browseMcdk"
            addActionListener {
                val chooser = JFileChooser()
                if (chooser.showOpenDialog(content) == JFileChooser.APPROVE_OPTION) {
                    mcdk.text = chooser.selectedFile.path
                    showTail(mcdk)
                }
            }
        }
        content.add(section(
            JLabel("MCDK 路径（留空使用内置版本）："),
            fieldLine(mcdk, browse)
        ))
        content.add(section(JLabel("控制台日志："), compact(logLevel)))
        content.add(section(JLabel("客户端模式："), compact(mode)))
        content.add(note("自动：已有游戏时启动额外客户端；主客户端部署世界和组件，额外客户端不重新部署。"))
        content.add(section(JLabel("Debug 附加端口："), compact(debugPort)))
        content.add(note("游戏设置读取项目 .mcdev.json；多个 Debug 客户端请设置不同端口。"))
        content.add(ButtonRow().apply {
            add(JButton("打开 .mcdev.json").apply { addActionListener {
                current?.let {
                    try { McdevJson.openEditor(it.project) }
                    catch (e: Exception) { Messages.showErrorDialog(it.project, e.message ?: "无法打开项目配置", "打开配置失败") }
                }
            } })
            add(JButton("生成默认配置").apply { addActionListener {
                current?.let {
                    try {
                        val created = McdevJson.generateDefault(it.project)
                        if (created) McdevJson.openEditor(it.project)
                        else Messages.showInfoMessage(it.project, "项目中已存在 .mcdev.json，未覆盖现有配置。", "生成默认配置")
                    } catch (e: Exception) { Messages.showErrorDialog(it.project, e.message ?: "无法生成项目配置", "生成配置失败") }
                }
            } })
        })
    }

    override fun createEditor(): JComponent = content

    internal fun editorComponent(): JComponent = content

    override fun resetEditorFrom(config: MCRunConfiguration) {
        current = config
        mcdk.text = config.options.mcdkPath.orEmpty()
        showTail(mcdk)
        logLevel.selectedItem = config.options.logLevel
        mode.selectedIndex = modeValues.indexOf(config.options.launchMode).coerceAtLeast(0)
        debugPort.text = config.options.debugPort.toString()
    }

    override fun applyEditorTo(config: MCRunConfiguration) {
        val port = debugPort.text.toIntOrNull()
        if (port == null || port !in 1..65535) throw ConfigurationException("Debug 端口必须在 1 到 65535 之间")
        config.options.mcdkPath = mcdk.text.trim()
        config.options.logLevel = logLevel.selectedItem as LogLevel
        config.options.launchMode = modeValues[mode.selectedIndex]
        config.options.debugPort = port
    }

    private fun section(vararg parts: JComponent) = object : JPanel() {
        init {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            alignmentX = 0f
            border = BorderFactory.createEmptyBorder(0, 0, 8, 0)
            parts.forEach {
                it.alignmentX = 0f
                add(it)
            }
        }
        override fun getMaximumSize(): Dimension = Dimension(Int.MAX_VALUE, preferredSize.height)
    }

    private fun fieldLine(field: JComponent, trailing: JComponent) = object : JPanel(BorderLayout(6, 0)) {
        init {
            alignmentX = 0f
            add(field, BorderLayout.CENTER)
            add(trailing, BorderLayout.EAST)
        }
        override fun getMaximumSize(): Dimension = Dimension(Int.MAX_VALUE, preferredSize.height)
    }

    private fun compact(field: JComponent) = object : JPanel(BorderLayout()) {
        init {
            alignmentX = 0f
            add(field, BorderLayout.WEST)
        }
        override fun getMaximumSize(): Dimension = Dimension(Int.MAX_VALUE, preferredSize.height)
    }

    private fun note(text: String) = WrappingNote(text).apply {
        alignmentX = 0f
        border = BorderFactory.createEmptyBorder(0, 0, 8, 0)
    }

    private fun showTail(field: JTextField) {
        if (!field.isFocusOwner && field.text.isNotEmpty()) field.caretPosition = field.text.length
    }

    /** Keeps a long path field from forcing the run dialog wider than the window. */
    private class FittingField : JBTextField() {
        override fun getPreferredSize(): Dimension {
            val size = super.getPreferredSize()
            return Dimension(size.width.coerceAtMost(240), size.height)
        }
        override fun getMinimumSize(): Dimension = Dimension(80, super.getMinimumSize().height)
        override fun getMaximumSize(): Dimension = Dimension(Int.MAX_VALUE, getPreferredSize().height)
    }

    private class WrappingNote(text: String) : JTextArea(text) {
        init {
            lineWrap = true
            wrapStyleWord = false
            isEditable = false
            isOpaque = false
            isFocusable = false
            font = UIManager.getFont("Label.font") ?: font
            foreground = UIManager.getColor("Label.disabledForeground") ?: foreground
            margin = Insets(0, 0, 0, 0)
        }

        override fun getMinimumSize(): Dimension = Dimension(40, font.size + 4)
        override fun getMaximumSize(): Dimension = Dimension(Int.MAX_VALUE, preferredSize.height)

        override fun getPreferredSize(): Dimension {
            val available = availableWidth()
            val metrics = getFontMetrics(font)
            val inner = (available - insets.left - insets.right).coerceAtLeast(1)
            val lines = ((metrics.stringWidth(text) + inner - 1) / inner).coerceAtLeast(1)
            return Dimension(available, insets.top + insets.bottom + lines * metrics.height)
        }

        private fun availableWidth(): Int {
            if (width > 20) return width
            var current: Container? = parent
            while (current != null) {
                if (current.width > 20) return (current.width - current.insets.left - current.insets.right).coerceAtLeast(40)
                current = current.parent
            }
            return 420
        }
    }

    private class ButtonRow : JPanel(FlowLayout(FlowLayout.LEFT, 8, 4)) {
        private val gapX = 8
        private val gapY = 4
        init { alignmentX = 0f }

        override fun getPreferredSize(): Dimension = wrapped(true)
        override fun getMinimumSize(): Dimension = wrapped(false)
        override fun getMaximumSize(): Dimension = Dimension(Int.MAX_VALUE, preferredSize.height)

        private fun wrapped(preferred: Boolean): Dimension {
            val host = parent
            val limit = when {
                width > 20 -> width
                host != null && host.width > 20 -> host.width - host.insets.left - host.insets.right
                else -> Int.MAX_VALUE
            }
            val insets = this.insets
            val available = (limit - insets.left - insets.right - gapX * 2).coerceAtLeast(0)
            var rowWidth = 0
            var rowHeight = 0
            var totalHeight = insets.top + insets.bottom + gapY
            var seen = false
            for (component in components) {
                if (!component.isVisible) continue
                val size = if (preferred) component.preferredSize else component.minimumSize
                if (rowWidth > 0 && rowWidth + gapX + size.width > available) {
                    totalHeight += rowHeight + gapY
                    rowWidth = 0
                    rowHeight = 0
                }
                if (rowWidth > 0) rowWidth += gapX
                rowWidth += size.width
                rowHeight = maxOf(rowHeight, size.height)
                seen = true
            }
            if (seen) totalHeight += rowHeight + gapY
            return Dimension(rowWidth + insets.left + insets.right + gapX * 2, totalHeight)
        }
    }
}
