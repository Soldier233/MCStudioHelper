package com.github.tartaricacid.mcshelper.util

import com.intellij.execution.ExecutionException
import com.intellij.openapi.application.PathManager
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.io.path.absolutePathString
import kotlin.io.path.extension
import kotlin.io.path.name

@Suppress("DialogTitleCapitalization")
class McdkLocator {
    companion object {
        const val BUNDLED_VERSION = "1.6.0"
        const val RESOURCE_PATH = "bin/native/windows/x64/mcdk.exe"

        @Throws(ExecutionException::class)
        fun resolve(overridePath: String?): Path {
            if (!overridePath.isNullOrBlank()) {
                val custom = Paths.get(overridePath)
                if (!Files.isRegularFile(custom) || !custom.fileName.extension.equals("exe", ignoreCase = true)) {
                    throw ExecutionException("mcdk 路径无效：$overridePath")
                }
                return custom.toAbsolutePath().normalize()
            }

            val cached = cachedExePath()
            if (isUsableExe(cached)) {
                return cached
            }

            FileUtils.extractResourceFile(RESOURCE_PATH, cached)
            if (!isUsableExe(cached)) {
                throw ExecutionException("无法解压内置 mcdk.exe（v$BUNDLED_VERSION）")
            }
            return cached
        }

        fun findMcdkExecutables(): List<String> {
            val paths = linkedSetOf<String>()
            val home = System.getProperty("user.home").orEmpty()
            if (home.isNotBlank()) {
                val extensionsDir = Paths.get(home, ".vscode", "extensions")
                if (Files.isDirectory(extensionsDir)) {
                    Files.list(extensionsDir).use { stream ->
                        stream.filter { Files.isDirectory(it) && it.fileName.toString().startsWith("dofes.mcdev-tools") }
                            .forEach { extDir ->
                                val exe = extDir.resolve("bin").resolve("native").resolve("windows").resolve("x64").resolve("mcdk.exe")
                                if (isUsableExe(exe)) {
                                    paths += exe.absolutePathString()
                                }
                            }
                    }
                }
            }
            return paths.toList()
        }

        private fun cachedExePath(): Path {
            return Paths.get(PathManager.getPluginTempPath(), "mcs-helper", "mcdk-$BUNDLED_VERSION", "mcdk.exe")
        }

        private fun isUsableExe(path: Path): Boolean {
            return Files.isRegularFile(path) &&
                    path.name.equals("mcdk.exe", ignoreCase = true) &&
                    Files.size(path) > 0L
        }
    }
}
