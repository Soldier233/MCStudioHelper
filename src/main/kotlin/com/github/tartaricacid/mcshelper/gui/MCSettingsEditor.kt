package com.github.tartaricacid.mcshelper.gui

import com.github.tartaricacid.mcshelper.util.McdevJson
import com.github.tartaricacid.mcshelper.options.LogLevel
import com.github.tartaricacid.mcshelper.run.MCRunConfiguration
import com.intellij.openapi.options.ConfigurationException
import com.intellij.openapi.options.SettingsEditor
import com.intellij.openapi.ui.Messages
import java.awt.BorderLayout
import java.awt.FlowLayout
import javax.swing.*

class MCSettingsEditor : SettingsEditor<MCRunConfiguration>() {
    private val content = JPanel(BorderLayout())
    private val mcdk = JTextField(35)
    private val logLevel = JComboBox(LogLevel.entries.toTypedArray())
    private val mode = JComboBox(arrayOf("自动", "主客户端", "额外客户端"))
    private val modeValues = listOf("AUTO", "PRIMARY", "SECONDARY")
    private val debugPort = JTextField("5678", 6)
    private var current: MCRunConfiguration? = null

    init {
        val fields = JPanel().apply { layout = BoxLayout(this, BoxLayout.Y_AXIS) }
        fun line(label: String, input: JComponent) = JPanel(FlowLayout(FlowLayout.LEFT)).apply { add(JLabel(label)); add(input) }
        fields.add(line("MCDK 路径（留空使用内置版本）：", JPanel(FlowLayout(FlowLayout.LEFT)).apply {
            add(mcdk)
            add(JButton("浏览…").apply { addActionListener {
                val chooser = JFileChooser()
                if (chooser.showOpenDialog(content) == JFileChooser.APPROVE_OPTION) mcdk.text = chooser.selectedFile.path
            } })
        }))
        logLevel.renderer = object : DefaultListCellRenderer() {
            override fun getListCellRendererComponent(list: JList<*>?, value: Any?, index: Int, selected: Boolean, focus: Boolean): java.awt.Component =
                super.getListCellRendererComponent(list, (value as? LogLevel)?.displayName ?: value, index, selected, focus)
        }
        fields.add(line("控制台日志：", logLevel))
        fields.add(line("客户端模式：", mode))
        fields.add(JLabel("自动：已有游戏时启动额外客户端；主客户端部署世界和组件，额外客户端不重新部署。"))
        fields.add(line("Debug 附加端口：", debugPort))
        fields.add(JLabel("游戏设置读取项目 .mcdev.json；多个 Debug 客户端请设置不同端口。"))
        fields.add(JPanel(FlowLayout(FlowLayout.LEFT)).apply {
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
        content.add(fields, BorderLayout.NORTH)
    }

    override fun createEditor(): JComponent = content
    override fun resetEditorFrom(config: MCRunConfiguration) {
        current = config
        mcdk.text = config.options.mcdkPath.orEmpty()
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
}
