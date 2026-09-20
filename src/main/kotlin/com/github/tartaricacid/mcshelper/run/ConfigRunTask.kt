package com.github.tartaricacid.mcshelper.run

import com.github.tartaricacid.mcshelper.options.MCRunConfigurationOptions
import com.github.tartaricacid.mcshelper.util.FileUtils
import com.github.tartaricacid.mcshelper.util.McdkLocator
import com.github.tartaricacid.mcshelper.util.McdevJson
import com.github.tartaricacid.mcshelper.util.PackUtils
import com.google.common.collect.Maps
import com.intellij.execution.ExecutionException
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.openapi.project.Project
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Paths
import kotlin.io.path.absolutePathString
import kotlin.io.path.extension

@Suppress("DialogTitleCapitalization")
class ConfigRunTask {
    data class LaunchResult(
        val commandLine: GeneralCommandLine,
        val mcdkPath: String,
        val debugEnabled: Boolean
    )

    companion object {
        @Throws(ExecutionException::class)
        fun run(project: Project, config: MCRunConfigurationOptions, debugEnabled: Boolean): LaunchResult {
            val gamePath = Paths.get(config.gameExecutablePath ?: "")
            if (!Files.isRegularFile(gamePath) || !gamePath.fileName.extension.equals("exe", ignoreCase = true)) {
                throw ExecutionException("启动程序路径错误：${config.gameExecutablePath}")
            }

            val projectPath = project.basePath ?: throw ExecutionException("当前项目路径为空")
            val packMaps: java.util.EnumMap<PackUtils.PackType, MutableList<PackUtils.PackInfo>> =
                Maps.newEnumMap(PackUtils.PackType::class.java)
            val projectIsFound = PackUtils.parsePack(Paths.get(projectPath), packMaps)
            if (!projectIsFound) {
                throw ExecutionException("当前项目不包含有效的资源包或行为包")
            }

            for (extraPackPath in config.includedModDirs) {
                val extraPath = Paths.get(extraPackPath)
                val found = PackUtils.parsePack(extraPath, packMaps)
                if (!found) {
                    throw ExecutionException("目录 \"$extraPackPath\" 不包含有效的资源包或行为包")
                }
            }

            val mcdkPath = McdkLocator.resolve(config.mcdkPath)
            McdevJson.mergeAndWrite(project, config)

            val env = mutableMapOf(
                "MCDEV_IS_PLUGIN_ENV" to "1",
                "MCDEV_OUTPUT_MODE" to "1"
            )
            if (FileUtils.isMinecraftRunning()) {
                env["MCDEV_IS_SUBPROCESS_MODE"] = "1"
            }
            if (debugEnabled) {
                env["MCDEV_PTVSD_IP"] = "127.0.0.1"
                env["MCDEV_PTVSD_PORT"] = "5678"
            }

            val commandLine = GeneralCommandLine()
                .withExePath(mcdkPath.absolutePathString())
                .withWorkDirectory(projectPath)
                .withCharset(StandardCharsets.UTF_8)
                .withEnvironment(env)

            return LaunchResult(commandLine, mcdkPath.absolutePathString(), debugEnabled)
        }
    }
}
