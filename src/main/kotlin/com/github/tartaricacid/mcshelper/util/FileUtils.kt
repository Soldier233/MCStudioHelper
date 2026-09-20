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

        /**
         * 寻找开发启动器的可执行文件路径
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
            return paths
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