package com.github.tartaricacid.mcshelper.util

import com.intellij.execution.ExecutionException
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.search.FilenameIndex
import com.intellij.psi.search.GlobalSearchScope
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import javax.swing.filechooser.FileSystemView


class FileUtils {
    companion object {
        fun findFile(project: Project, relativePath: String): VirtualFile? {
            val fileName = relativePath.substringAfterLast('/')
            val scope = GlobalSearchScope.projectScope(project)
            val files = FilenameIndex.getVirtualFilesByName(fileName, scope)

            return files.firstOrNull { file ->
                file.path.replace('\\', '/').endsWith(relativePath)
            } ?: files.firstOrNull()
        }

        const val RETAIL_LAUNCHER_MESSAGE =
            "这是正式服启动器，不能用于开发测试。请选择版本号目录下的 Minecraft.Windows.exe，例如 MinecraftPE_Netease\\3.9.0.401155\\Minecraft.Windows.exe"

        private val versionDirectory = Regex("""(\d+)\.(\d+)\.(\d+)\.(\d+)""")

        /**
         * Mod PC 开发包：版本号目录中的 Minecraft.Windows.exe。PCLauncher 正式服启动器不在其中。
         * 按版本号从旧到新排列，最后一项是最新开发包。
         */
        fun findMinecraftExecutables(): List<String> {
            val paths = mutableListOf<String>()
            val fsv = FileSystemView.getFileSystemView()
            File.listRoots().filter { root ->
                !fsv.isFloppyDrive(root) && root.totalSpace > 0
            }.forEach { drive ->
                val basePath = File("${drive}MCStudioDownload\\game\\MinecraftPE_Netease")
                if (basePath.exists() && basePath.isDirectory) {
                    basePath.listFiles()?.forEach { versionDir ->
                        if (versionDir.isDirectory) {
                            val exePath = File(versionDir, "Minecraft.Windows.exe")
                            if (exePath.exists() && exePath.isFile) {
                                paths.add(exePath.absolutePath)
                            }
                        }
                    }
                }
            }
            return sortDevExecutables(paths)
        }

        fun isDevMinecraftExecutable(path: String): Boolean {
            val file = File(path)
            return file.name.equals("Minecraft.Windows.exe", ignoreCase = true) &&
                versionDirectory.matches(file.parentFile?.name.orEmpty())
        }

        /** 路径分段中带 PCLauncher 的是正式服启动器，MCDK 无法用它加载组件。 */
        fun isRetailLauncher(path: String): Boolean {
            return path.split('\\', '/').any { segment -> segment.contains("PCLauncher", ignoreCase = true) }
        }

        fun sortDevExecutables(paths: Collection<String>): List<String> {
            return paths.filter { isDevMinecraftExecutable(it) && !isRetailLauncher(it) }
                .distinct()
                .sortedWith(compareBy(versionOrder) { versionNumbers(it) })
        }

        private fun versionNumbers(path: String): List<Int> {
            val name = File(path).parentFile?.name.orEmpty()
            val match = versionDirectory.matchEntire(name) ?: return emptyList()
            return match.groupValues.drop(1).map { it.toInt() }
        }

        private val versionOrder = Comparator<List<Int>> { left, right ->
            val size = maxOf(left.size, right.size)
            for (index in 0 until size) {
                val difference = left.getOrElse(index) { 0 }.compareTo(right.getOrElse(index) { 0 })
                if (difference != 0) return@Comparator difference
            }
            0
        }

        fun isMinecraftRunning(): Boolean {
            return ProcessHandle.allProcesses().anyMatch { handle ->
                val command = handle.info().command().orElse("")
                command.endsWith("Minecraft.Windows.exe", ignoreCase = true)
            }
        }

        @Throws(ExecutionException::class)
        fun extractResourceFile(resourcePath: String, targetPath: Path) {
            val stream = FileUtils::class.java.classLoader.getResourceAsStream(resourcePath)
                ?: throw ExecutionException("内置资源未找到：$resourcePath")
            try {
                val parent = targetPath.parent
                if (parent != null && !Files.isDirectory(parent)) {
                    Files.createDirectories(parent)
                }
                stream.use { input ->
                    Files.copy(input, targetPath, StandardCopyOption.REPLACE_EXISTING)
                }
            } catch (e: ExecutionException) {
                throw e
            } catch (e: Exception) {
                throw ExecutionException("从插件中复制文件失败：${e.message}", e)
            }
        }
    }
}