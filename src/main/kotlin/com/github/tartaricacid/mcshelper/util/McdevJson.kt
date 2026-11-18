package com.github.tartaricacid.mcshelper.util

import com.google.gson.JsonObject
import com.intellij.execution.ExecutionException
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import java.nio.file.Files
import java.nio.file.Path

class McdevJson {
    companion object {
        const val FILE_NAME = ".mcdev.json"
        fun filePath(project: Project): Path? = project.basePath?.let { Path.of(it, FILE_NAME) }
        fun read(project: Project): JsonObject? = filePath(project)?.let(::read)

        fun read(path: Path): JsonObject? {
            if (!Files.isRegularFile(path)) return null
            try {
                val text = ReadAction.compute<String?, RuntimeException> {
                    LocalFileSystem.getInstance().findFileByNioFile(path)?.let {
                        FileDocumentManager.getInstance().getCachedDocument(it)?.text
                    }
                } ?: Files.readString(path)
                return McdevSchema.parse(text)
            } catch (e: Exception) { throw ExecutionException("读取 $path 失败：${e.message}", e) }
        }

        fun openEditor(project: Project) {
            val path = filePath(project) ?: throw ExecutionException("当前项目路径为空")
            try {
                val directory = LocalFileSystem.getInstance().refreshAndFindFileByNioFile(path.parent)
                    ?: throw ExecutionException("项目目录不存在：${path.parent}")
                var file = LocalFileSystem.getInstance().refreshAndFindFileByNioFile(path)
                if (file == null) WriteCommandAction.runWriteCommandAction(project, "创建 .mcdev.json", null, Runnable {
                    file = directory.findChild(FILE_NAME) ?: directory.createChildData(this, FILE_NAME).also {
                        it.setBinaryContent((McdevSchema.gson.toJson(defaultsFor(project)) + "\n").toByteArray(Charsets.UTF_8))
                    }
                })
                val target = file ?: throw ExecutionException("无法打开 $path")
                require(!target.isDirectory) { "$path 是目录，无法作为配置文件打开" }
                FileEditorManager.getInstance(project).apply {
                    openFile(target, true)
                    setSelectedEditor(target, "mcdev.visual")
                }
            } catch (e: Exception) { throw ExecutionException("打开 $path 失败：${e.message}", e) }
        }

        /** Create the full built-in configuration only when the project has no config yet. */
        fun generateDefault(project: Project): Boolean {
            val path = filePath(project) ?: throw ExecutionException("当前项目路径为空")
            if (Files.exists(path)) return false
            val directory = LocalFileSystem.getInstance().refreshAndFindFileByNioFile(path.parent)
                ?: throw ExecutionException("项目目录不存在：${path.parent}")
            WriteCommandAction.runWriteCommandAction(project, "生成默认 .mcdev.json", null, Runnable {
                if (directory.findChild(FILE_NAME) == null) {
                    directory.createChildData(this, FILE_NAME).setBinaryContent(
                        (McdevSchema.gson.toJson(defaultsFor(project)) + "\n").toByteArray(Charsets.UTF_8)
                    )
                }
            })
            return Files.exists(path)
        }

        private fun defaultsFor(project: Project): JsonObject =
            McdevSchema.defaultsForProject(project.name)

        /**
         * Record the MCDK executable actually used by a launch in the project config, so an agent
         * (or a human) can discover it from the project alone. This is the one field the plugin
         * maintains on the user's behalf: every other value, including unknown extensions, stays
         * untouched. The cached document is reused so unsaved editor changes are not overwritten.
         */
        fun writeMcdkPath(project: Project, mcdkExecutable: java.nio.file.Path) {
            val path = filePath(project) ?: return
            val normalized = mcdkExecutable.toAbsolutePath().normalize().toString().replace('\\', '/')
            val directory = LocalFileSystem.getInstance().refreshAndFindFileByNioFile(path.parent) ?: return
            WriteCommandAction.runWriteCommandAction(project, "记录 MCDK 路径", null, Runnable {
                val existing = directory.findChild(FILE_NAME)
                val file = existing ?: directory.createChildData(this, FILE_NAME)
                val cached = FileDocumentManager.getInstance().getCachedDocument(file)
                val config = when {
                    cached != null -> runCatching { McdevSchema.parse(cached.text) }.getOrNull()
                    existing != null -> runCatching { read(path) }.getOrNull()
                    else -> null
                } ?: McdevSchema.defaults()
                val current = config.get("mcdk_path")
                if (current != null && current.isJsonPrimitive && current.asJsonPrimitive.isString &&
                    current.asString == normalized) return@Runnable
                config.addProperty("mcdk_path", normalized)
                val text = McdevSchema.gson.toJson(config) + "\n"
                if (cached != null) cached.setText(text) else file.setBinaryContent(text.toByteArray(Charsets.UTF_8))
            })
        }
    }
}
