package com.github.tartaricacid.mcshelper.util

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FileUtilsTest {
    @Test fun devClientsKeepVersionDirectoriesAndDropRetailLauncher() {
        val dev = """D:\MCStudioDownload\game\MinecraftPE_Netease\3.9.0.401155\Minecraft.Windows.exe"""
        val newer = """D:\MCStudioDownload\game\MinecraftPE_Netease\3.10.0.1\Minecraft.Windows.exe"""
        val retail = """D:\MCStudioDownload\game\MinecraftPE_Netease\PCLauncher_x64\Minecraft.Windows.exe"""
        assertTrue(FileUtils.isDevMinecraftExecutable(dev))
        assertTrue(FileUtils.isDevMinecraftExecutable(newer.replace('\\', '/')))
        assertFalse(FileUtils.isDevMinecraftExecutable(retail))
        assertFalse(FileUtils.isDevMinecraftExecutable("""D:\a\3.9.0.401155\not.exe"""))
        assertTrue(FileUtils.isRetailLauncher(retail))
        assertTrue(FileUtils.isRetailLauncher(retail.replace('\\', '/')))
        assertFalse(FileUtils.isRetailLauncher(dev))
        assertEquals(listOf(dev, newer), FileUtils.sortDevExecutables(listOf(newer, retail, dev, """D:\a\3.9.0.401155\not.exe""")))
        assertTrue(FileUtils.RETAIL_LAUNCHER_MESSAGE.contains("正式服"))
        assertTrue(FileUtils.RETAIL_LAUNCHER_MESSAGE.contains("3.9.0.401155"))
    }

    @Test fun scannedExecutablesExcludeRetailLaunchers() {
        val found = FileUtils.findMinecraftExecutables()
        assertTrue(found.all { FileUtils.isDevMinecraftExecutable(it) && !FileUtils.isRetailLauncher(it) })
        assertEquals(found, FileUtils.sortDevExecutables(found))
    }
}
