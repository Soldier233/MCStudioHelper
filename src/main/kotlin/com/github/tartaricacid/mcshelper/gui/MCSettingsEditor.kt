package com.github.tartaricacid.mcshelper.gui

import com.github.tartaricacid.mcshelper.options.GameMode
import com.github.tartaricacid.mcshelper.options.LevelType
import com.github.tartaricacid.mcshelper.options.LogLevel
import com.github.tartaricacid.mcshelper.run.MCRunConfiguration
import com.github.tartaricacid.mcshelper.util.FileUtils
import com.intellij.execution.ExecutionException
import com.github.tartaricacid.mcshelper.util.McdkLocator
import com.github.tartaricacid.mcshelper.util.McdevJson
import com.github.tartaricacid.mcshelper.util.PathUtils
import com.intellij.icons.AllIcons
import com.intellij.ide.actions.RevealFileAction
import com.intellij.openapi.fileChooser.FileChooser
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.options.ConfigurationException
import com.intellij.openapi.options.SettingsEditor
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.Messages
import com.intellij.ui.EnumComboBoxModel
import com.intellij.ui.TextFieldWithHistoryWithBrowseButton
import com.intellij.ui.ToolbarDecorator
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBTextField
import com.intellij.ui.components.fields.ExtendableTextComponent
import com.intellij.ui.components.fields.ExtendableTextField
import com.intellij.ui.components.textFieldWithHistoryWithBrowseButton
import com.intellij.ui.dsl.builder.Align
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.dsl.listCellRenderer.textListCellRenderer
import java.awt.Desktop
import java.nio.file.Files
import java.nio.file.Paths
import javax.swing.DefaultListModel
import javax.swing.JComponent
import javax.swing.ListSelectionModel
import kotlin.io.path.absolutePathString
import kotlin.io.path.exists
import kotlin.random.Random

@Suppress("DialogTitleCapitalization")
class MCSettingsEditor : SettingsEditor<MCRunConfiguration>() {
    private val runConfig: JComponent

    private lateinit var mcdkPathField: TextFieldWithHistoryWithBrowseButton
    private lateinit var gameExeField: TextFieldWithHistoryWithBrowseButton

    private lateinit var logLevel: ComboBox<LogLevel>
    private lateinit var includedModDirs: JBList<String>

    private lateinit var worldFolder: ExtendableTextField

    private lateinit var worldSeedField: JBTextField
    private lateinit var userNameField: JBTextField

    private lateinit var gameModeField: ComboBox<GameMode>
    private lateinit var levelTypeField: ComboBox<LevelType>

    private lateinit var enableCheatsField: JBCheckBox
    private lateinit var keepInventoryField: JBCheckBox
    private lateinit var doDaylightCycleField: JBCheckBox
    private lateinit var doWeatherCycleField: JBCheckBox
    private var loadedWorldFolderName: String? = null

    init {
        runConfig = panel {
            row("mcdk 路径：") {
                val fileChooser = FileChooserDescriptorFactory.singleFile()
                    .withTitle("mcdk 路径")
                    .withDescription("请选择 mcdk.exe，留空则使用插件内置版本")
                    .withExtensionFilter("exe")
                mcdkPathField = textFieldWithHistoryWithBrowseButton(
                    null, fileChooser, McdkLocator.Companion::findMcdkExecutables
                )
                cell(mcdkPathField).comment("留空使用插件内置 mcdk v${McdkLocator.BUNDLED_VERSION}").align(Align.FILL)
            }

            row("启动程序路径：") {
                val fileChooser = FileChooserDescriptorFactory.singleFile()
                    .withTitle("启动程序路径")
                    .withDescription("请选择网易开发者游戏启动器所在路径")
                    .withExtensionFilter("exe")
                gameExeField = textFieldWithHistoryWithBrowseButton(
                    null, fileChooser, FileUtils.Companion::findMinecraftExecutables
                )
                cell(gameExeField).comment("写入 .mcdev.json 的 game_executable_path").align(Align.FILL)
            }

            row("日志等级：") {
                logLevel = comboBox(
                    EnumComboBoxModel(LogLevel::class.java),
                    textListCellRenderer { it?.displayName }
                ).comment("控制 PyCharm 输出日志的详细程度").component
            }

            row("同时运行组件：") {
                var includedModDirsData = DefaultListModel<String>()
                includedModDirs = JBList(includedModDirsData)
                includedModDirs.selectionMode = ListSelectionModel.MULTIPLE_INTERVAL_SELECTION
                includedModDirs.setEmptyText("点击左上角加号按钮添加组件目录")

                val toolbar = ToolbarDecorator.createDecorator(includedModDirs)
                    .setVisibleRowCount(3)
                    .setAddAction {
                        val desc = FileChooserDescriptorFactory.multiDirs()
                            .withTitle("选择文件夹")
                            .withDescription("选择要加载的组件目录")
                        val chosen = FileChooser.chooseFiles(desc, null, null)
                        for (f in chosen) {
                            val path = f.path
                            if (!includedModDirsData.contains(path)) {
                                includedModDirsData.addElement(path)
                            }
                        }
                    }.createPanel()

                cell(toolbar).comment("与当前运行的游戏实例一同加载的组件目录").align(Align.FILL)
            }

            collapsibleGroup("世界设置") {
                row("游戏存档：") {
                    worldFolder = cell(ExtendableTextField()).align(Align.FILL).component

                    worldFolder.addExtension(
                        ExtendableTextComponent.Extension.create(
                            AllIcons.Actions.GC,
                            AllIcons.Actions.GC,
                            "删除"
                        ) {
                            if (worldFolder.text.isNotEmpty()) {
                                val path = Paths.get(worldFolder.text)
                                if (Files.exists(path)) {
                                    // 打开 IDEA 弹窗，二次确认是否删除
                                    val result = Messages.showYesNoDialog(
                                        "确定要将存档 \"${path.fileName}\" 移动至回收站吗？",
                                        "删除存档",
                                        "删除",
                                        "取消",
                                        AllIcons.General.WarningDialog
                                    )
                                    if (result == Messages.YES) {
                                        // 移动至回收站
                                        Desktop.getDesktop().moveToTrash(path.toFile())
                                        worldFolder.isEnabled = false
                                    }
                                }
                            }
                        }
                    )

                    worldFolder.addExtension(
                        ExtendableTextComponent.Extension.create(
                            AllIcons.Actions.MenuOpen,
                            AllIcons.Actions.MenuOpen,
                            "浏览"
                        ) {
                            if (worldFolder.text.isNotEmpty()) {
                                val path = Paths.get(worldFolder.text)
                                if (Files.exists(path)) {
                                    RevealFileAction.openDirectory(path.toFile())
                                }
                            }
                        }
                    )
                }

                row("世界种子：") {
                    worldSeedField = textField().align(Align.Companion.FILL).component
                    worldSeedField.emptyText.text = "默认随机种子"
                }

                row("用户名称：") {
                    userNameField = textField().align(Align.Companion.FILL).component
                    userNameField.emptyText.text = "DevOps"
                }

                groupRowsRange("游戏规则：", true, false) {
                    row {
                        gameModeField = comboBox(
                            EnumComboBoxModel(GameMode::class.java),
                            textListCellRenderer { it?.displayName }
                        ).label("游戏模式：").component

                        levelTypeField = comboBox(
                            EnumComboBoxModel(LevelType::class.java),
                            textListCellRenderer { it?.displayName }
                        ).label("世界类型：").component
                    }

                    row {
                        enableCheatsField = checkBox("启用作弊").component
                        keepInventoryField = checkBox("死亡不掉落").component
                        doDaylightCycleField = checkBox("昼夜循环").component
                        doWeatherCycleField = checkBox("天气变化").component
                    }
                }
            }.expanded = true
        }
    }

    override fun createEditor(): JComponent {
        return runConfig
    }

    override fun resetEditorFrom(config: MCRunConfiguration) {
        val options = config.options
        val overlay = McdevJson.readUiOverlay(config.project)

        if (!options.mcdkPath.isNullOrEmpty()) {
            mcdkPathField.text = options.mcdkPath
        } else {
            mcdkPathField.text = ""
        }

        val gamePath = overlay?.gameExecutablePath ?: options.gameExecutablePath
        if (!gamePath.isNullOrEmpty()) {
            gameExeField.text = gamePath
        }

        logLevel.selectedItem = options.logLevel

        var includedModDirsData = includedModDirs.model
        if (includedModDirsData is DefaultListModel<String>) {
            includedModDirsData.clear()
            includedModDirsData.addAll(overlay?.includedModDirs ?: options.includedModDirs)
        }

        // 验证存档目录是否存在，启用/禁用存档管理按钮
        val worldDirPath = PathUtils.worldsDir()
        val worldFolderName = overlay?.worldFolderName ?: options.worldFolderName
        loadedWorldFolderName = worldFolderName
        if (worldDirPath != null) {
            val worldFolder = worldDirPath.resolve(worldFolderName)
            this.worldFolder.text = worldFolder.absolutePathString()
            this.worldFolder.isEnabled = worldFolder.exists()
        } else {
            this.worldFolder.isEnabled = false
        }

        worldSeedField.text = (overlay?.worldSeed ?: options.worldSeed).toString()
        userNameField.text = overlay?.userName ?: options.userName

        gameModeField.selectedItem = overlay?.gameMode ?: options.gameMode
        levelTypeField.selectedItem = overlay?.levelType ?: options.levelType

        enableCheatsField.isSelected = overlay?.enableCheats ?: options.enableCheats
        keepInventoryField.isSelected = overlay?.keepInventory ?: options.keepInventory
        doDaylightCycleField.isSelected = overlay?.doDaylightCycle ?: options.doDaylightCycle
        doWeatherCycleField.isSelected = overlay?.doWeatherCycle ?: options.doWeatherCycle
    }

    override fun applyEditorTo(config: MCRunConfiguration) {
        // 从组件读取最新值写回配置
        config.options.mcdkPath = mcdkPathField.text.trim()

        if (gameExeField.text.isBlank()) {
            throw ConfigurationException("启动程序路径不能为空", "配置错误")
        }
        config.options.gameExecutablePath = gameExeField.text

        config.options.logLevel = logLevel.selectedItem as LogLevel

        config.options.includedModDirs.clear()
        for (i in 0 until includedModDirs.model.size) {
            config.options.includedModDirs += includedModDirs.model.getElementAt(i)
        }

        if (worldSeedField.text.isNullOrEmpty()) {
            worldSeedField.text = Random.nextLong().toString()
        }
        config.options.worldSeed = worldSeedField.text.toLong()

        loadedWorldFolderName?.let { config.options.worldFolderName = it }

        config.options.userName = userNameField.text

        config.options.gameMode = gameModeField.selectedItem as GameMode
        config.options.levelType = levelTypeField.selectedItem as LevelType

        config.options.enableCheats = enableCheatsField.isSelected
        config.options.keepInventory = keepInventoryField.isSelected
        config.options.doDaylightCycle = doDaylightCycleField.isSelected
        config.options.doWeatherCycle = doWeatherCycleField.isSelected

        try {
            McdevJson.mergeAndWrite(config.project, config.options)
        } catch (e: ExecutionException) {
            throw ConfigurationException(e.message ?: "写入 .mcdev.json 失败", "配置错误")
        }
    }
}