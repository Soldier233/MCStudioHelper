package com.github.tartaricacid.mcshelper.gui.settings

import com.github.tartaricacid.mcshelper.util.FileUtils
import com.github.tartaricacid.mcshelper.util.McdevSchema
import com.github.tartaricacid.mcshelper.util.PathUtils
import com.google.gson.JsonObject
import com.intellij.icons.AllIcons
import com.intellij.ide.actions.RevealFileAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import java.awt.Desktop
import java.nio.file.Files

internal object McdevWorldActions {
    fun manage(project: Project, trash: Boolean, json: JsonObject) {
        val effective = McdevSchema.withDefaults(json)
        val worlds = PathUtils.worldsDir()?.toAbsolutePath()?.normalize() ?: error("未找到游戏存档目录")
        val world = worlds.resolve(effective.get("world_folder_name").asString).normalize()
        require(world.parent == worlds) { "存档路径无效" }
        require(Files.isDirectory(world)) { "存档尚未创建：$world" }
        if (!trash) RevealFileAction.openDirectory(world.toFile())
        else {
            check(!FileUtils.isMinecraftRunning()) { "请先关闭游戏再管理存档" }
            if (Messages.showYesNoDialog(project, "确定将存档 ${world.fileName} 移动至回收站吗？",
                    "存档移至回收站", AllIcons.General.WarningDialog) == Messages.YES) {
                check(Desktop.getDesktop().moveToTrash(world.toFile())) { "无法移动至回收站：$world" }
            }
        }
    }
}
