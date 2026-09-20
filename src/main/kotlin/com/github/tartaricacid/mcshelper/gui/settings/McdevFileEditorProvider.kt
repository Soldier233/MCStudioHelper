package com.github.tartaricacid.mcshelper.gui.settings

import com.github.tartaricacid.mcshelper.util.*
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.fileEditor.*
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.UserDataHolderBase
import com.intellij.openapi.vfs.VirtualFile
import java.beans.PropertyChangeListener
import javax.swing.JComponent

class McdevFileEditorProvider : FileEditorProvider, DumbAware {
    override fun accept(project: Project, file: VirtualFile) = !file.isDirectory && file.name == McdevJson.FILE_NAME
    override fun createEditor(project: Project, file: VirtualFile): FileEditor = McdevFileEditor(project, file)
    override fun getEditorTypeId() = "mcdev.visual"
    override fun getPolicy() = FileEditorPolicy.PLACE_BEFORE_DEFAULT_EDITOR
}

private class McdevFileEditor(project: Project, private val virtualFile: VirtualFile) : UserDataHolderBase(), FileEditor {
    private val document = FileDocumentManager.getInstance().getDocument(virtualFile)!!
    private var writing = false
    private val changes = java.beans.PropertyChangeSupport(this)
    private var modified = false
    private val editor = McdevEditorPanel(worldAction = { trash, json -> McdevWorldActions.manage(project, trash, json) }) { text ->
        if (document.text != text) {
            writing = true
            try {
                WriteCommandAction.runWriteCommandAction(project, "编辑 MC Dev 配置", "mcdev.configuration", Runnable { document.setText(text) })
            } finally { writing = false }
        }
    }

    init {
        editor.load(document.text)
        document.addDocumentListener(object : DocumentListener {
            override fun documentChanged(event: DocumentEvent) {
                if (!writing) editor.load(document.text)
                val current = isModified
                changes.firePropertyChange(FileEditor.getPropModified(), modified, current)
                modified = current
            }
        }, this)
    }

    override fun getComponent(): JComponent = editor
    override fun getPreferredFocusedComponent(): JComponent = editor
    override fun getName() = "MC Dev 配置"
    override fun getFile() = virtualFile
    override fun setState(state: FileEditorState) {}
    override fun isModified() = FileDocumentManager.getInstance().isDocumentUnsaved(document)
    override fun isValid() = virtualFile.isValid
    override fun addPropertyChangeListener(listener: PropertyChangeListener) = changes.addPropertyChangeListener(listener)
    override fun removePropertyChangeListener(listener: PropertyChangeListener) = changes.removePropertyChangeListener(listener)
    override fun dispose() {}
}
