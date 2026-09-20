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
    }
}
