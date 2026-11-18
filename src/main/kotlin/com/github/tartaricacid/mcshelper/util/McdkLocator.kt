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
        /**
         * 内置 MCDK 版本。
         *
         * 来源：https://github.com/GitHub-Zero123/MCDevTool tag `v1.6.5` 的 `mcdk_win32_x64.zip`
         * （zip SHA-256 d01ee58032ef7d8617c8fb193b48e43921d0f2926252cab6309f838a7f48e1fa）。
         * 该 zip 自 v1.6.5 起不再只含 exe，同目录的 DLL 由 mcdk 按需从 exe 所在目录加载：
         * - mcdk.exe               138761791ae91913e2fdd95eca1ab1d4a6ca214b2eb2de7cfe18e069bf58af29
         * - mcdev-lane-shim.dll    effd454eacf97a3322c6bf513b247d85c656d57905a0097c0e35ed16eb6c17a0
         * - mcdev-tracy-bridge.dll 698801488b44793e65a9b067cd62a01ffd2196ddba287603ad8de846baa3ed27
         *
         * 上游发布包内嵌的版本常量仍写作 1.6.1，运行期无法据此判断版本，故只以本常量表示。
         */
        const val BUNDLED_VERSION = "1.6.5"
        const val RESOURCE_DIR = "bin/native/windows/x64"
        const val EXE_NAME = "mcdk.exe"
        val RESOURCE_FILES = listOf(EXE_NAME, "mcdev-lane-shim.dll", "mcdev-tracy-bridge.dll")

        @Throws(ExecutionException::class)
        fun resolve(overridePath: String?): Path {
            if (!overridePath.isNullOrBlank()) {
                val custom = Paths.get(overridePath)
                if (!Files.isRegularFile(custom) || !custom.fileName.extension.equals("exe", ignoreCase = true)) {
                    throw ExecutionException("mcdk 路径无效：$overridePath")
                }
                return custom.toAbsolutePath().normalize()
            }

            val bundleDir = bundledBundleDir()
            if (isUsableBundle(bundleDir)) {
                return bundleDir.resolve(EXE_NAME)
            }

            extractBundle(bundleDir)
            if (!isUsableBundle(bundleDir)) {
                throw ExecutionException("无法解压内置 mcdk.exe（v$BUNDLED_VERSION）")
            }
            return bundleDir.resolve(EXE_NAME)
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
                                val exe = extDir.resolve(RESOURCE_DIR).resolve(EXE_NAME)
                                if (isUsableExe(exe)) {
                                    paths += exe.absolutePathString()
                                }
                            }
                    }
                }
            }
            return paths.toList()
        }

        private fun bundledBundleDir(): Path {
            return Paths.get(PathManager.getPluginTempPath(), "mcs-helper", "mcdk-$BUNDLED_VERSION")
        }

        /** Extract only what is missing so a half-written cache directory repairs itself. */
        private fun extractBundle(bundleDir: Path) {
            for (file in RESOURCE_FILES) {
                val target = bundleDir.resolve(file)
                if (isUsableFile(target)) continue
                FileUtils.extractResourceFile("$RESOURCE_DIR/$file", target)
            }
        }

        private fun isUsableBundle(bundleDir: Path): Boolean =
            RESOURCE_FILES.all { isUsableFile(bundleDir.resolve(it)) }

        private fun isUsableExe(path: Path): Boolean {
            return Files.isRegularFile(path) &&
                    path.name.equals(EXE_NAME, ignoreCase = true) &&
                    Files.size(path) > 0L
        }

        private fun isUsableFile(path: Path): Boolean {
            return Files.isRegularFile(path) && Files.size(path) > 0L
        }
    }
}
