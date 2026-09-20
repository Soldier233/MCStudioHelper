package com.github.tartaricacid.mcshelper.run

import com.github.tartaricacid.mcshelper.options.MCRunConfigurationOptions
import com.github.tartaricacid.mcshelper.util.FileUtils
import com.github.tartaricacid.mcshelper.util.McdkLocator
import com.github.tartaricacid.mcshelper.util.McdevJson
import com.github.tartaricacid.mcshelper.util.McdevLaunchConfig
import com.github.tartaricacid.mcshelper.util.McdevSchema
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
        val debugEnabled: Boolean,
        val effectiveConfig: com.google.gson.JsonObject,
        val snapshotDirectory: java.nio.file.Path
    ) {
        fun cleanup() {
            // Only remove the file created by this launch, never recursively remove MCDK output.
            runCatching { Files.deleteIfExists(snapshotDirectory.resolve(McdevJson.FILE_NAME)); Files.deleteIfExists(snapshotDirectory) }
        }
    }

    companion object {
        @Throws(ExecutionException::class)
        fun run(project: Project, config: MCRunConfigurationOptions, debugEnabled: Boolean): LaunchResult {
            if (config.launchMode !in listOf("AUTO", "PRIMARY", "SECONDARY")) throw ExecutionException("无效的客户端模式")
            if (config.debugPort !in 1..65535) throw ExecutionException("Debug 端口必须在 1 到 65535 之间")
            val projectPath = project.basePath ?: throw ExecutionException("当前项目路径为空")
            val effective = try {
                McdevLaunchConfig.forSnapshot(McdevLaunchConfig.resolve(McdevJson.read(project)), Paths.get(projectPath))
            } catch (e: Exception) { throw ExecutionException("配置无效：${e.message}", e) }
            var gamePathText = effective.get("game_executable_path").asString
            if (gamePathText.isBlank()) {
                gamePathText = FileUtils.findMinecraftExecutables().lastOrNull()
                    ?: throw ExecutionException("未检测到游戏程序，请在项目配置中选择游戏程序路径")
                effective.addProperty("game_executable_path", gamePathText.replace('\\', '/'))
            }
            val gamePath = Paths.get(gamePathText)
            if (!Files.isRegularFile(gamePath) || !gamePath.fileName.extension.equals("exe", ignoreCase = true)) {
                throw ExecutionException("启动程序路径错误：$gamePathText")
            }

            val packMaps: java.util.EnumMap<PackUtils.PackType, MutableList<PackUtils.PackInfo>> =
                Maps.newEnumMap(PackUtils.PackType::class.java)
            val worldSource = effective.get("world_source_path")?.takeIf { !it.isJsonNull }?.asString.orEmpty()
            for (extraPath in McdevLaunchConfig.enabledModPaths(effective)) {
                if (!Files.isDirectory(extraPath)) throw ExecutionException("组件目录不存在：$extraPath")
                val found = PackUtils.parsePack(extraPath, packMaps)
                if (!found && !(worldSource.isNotBlank() && Files.isRegularFile(Paths.get(worldSource).resolve("level.dat")))) {
                    throw ExecutionException("目录 \"$extraPath\" 不包含有效的资源包或行为包")
                }
            }

            val mcdkPath = McdkLocator.resolve(config.mcdkPath)
            val running = FileUtils.isMinecraftRunning()
            if (config.launchMode == "PRIMARY" && running) {
                throw ExecutionException("主客户端会重新部署共享组件，请先关闭已有游戏；需要多开时选择「额外客户端」。")
            }

            val env = mutableMapOf(
                "MCDEV_IS_PLUGIN_ENV" to "1",
                "MCDEV_OUTPUT_MODE" to "1"
            )
            env["MCDEV_IS_SUBPROCESS_MODE"] = if (config.launchMode == "SECONDARY" || (config.launchMode == "AUTO" && running)) "1" else "0"
            if (debugEnabled) {
                env["MCDEV_PTVSD_IP"] = "127.0.0.1"
                env["MCDEV_PTVSD_PORT"] = config.debugPort.toString()
            }

            val snapshot = Files.createTempDirectory("mcshelper-launch-")
            try {
                Files.writeString(snapshot.resolve(McdevJson.FILE_NAME), McdevSchema.gson.toJson(effective))
                val commandLine = GeneralCommandLine()
                    .withExePath(mcdkPath.absolutePathString())
                    .withWorkDirectory(snapshot.toFile())
                    .withCharset(StandardCharsets.UTF_8)
                    .withEnvironment(env)

                return LaunchResult(commandLine, mcdkPath.absolutePathString(), debugEnabled, effective, snapshot)
            } catch (e: Exception) {
                runCatching { Files.deleteIfExists(snapshot.resolve(McdevJson.FILE_NAME)); Files.deleteIfExists(snapshot) }
                throw ExecutionException("准备运行配置失败：${e.message}", e)
            }
        }
    }
}
