package com.github.tartaricacid.mcshelper.gui

import java.awt.Component
import java.awt.Container
import java.awt.Rectangle
import javax.swing.JButton
import javax.swing.JLabel
import javax.swing.JTextArea
import javax.swing.SwingUtilities
import kotlin.test.Test
import kotlin.test.assertTrue

class MCSettingsEditorTest {
    private fun children(root: Component): List<Component> = listOf(root) +
        if (root is Container) root.components.flatMap(::children) else emptyList()

    private fun layout(component: Component) {
        if (component is Container) {
            component.doLayout()
            component.components.forEach(::layout)
        }
    }

    @Test fun runSettingsKeepLabelsButtonsAndNotesInsideTheEditor() {
        SwingUtilities.invokeAndWait {
            val editor = MCSettingsEditor()
            val panel = editor.editorComponent()
            fun layoutAt(width: Int) {
                panel.setSize(width, 800)
                repeat(2) { layout(panel) }
                panel.setSize(width, panel.preferredSize.height.coerceAtLeast(1))
                repeat(2) { layout(panel) }
            }
            for (width in listOf(420, 640)) {
                layoutAt(width)
                children(panel).filterIsInstance<JLabel>().filter { it.isVisible && it.text.isNotBlank() }.forEach { label ->
                    val textWidth = label.getFontMetrics(label.font).stringWidth(label.text)
                    assertTrue(label.width >= textWidth, "clipped label '${label.text}' text=$textWidth width=${label.width} at $width")
                }
                children(panel).filterIsInstance<JTextArea>().forEach { note ->
                    val metrics = note.getFontMetrics(note.font)
                    val inner = (note.width - note.insets.left - note.insets.right).coerceAtLeast(1)
                    val lines = ((metrics.stringWidth(note.text) + inner - 1) / inner).coerceAtLeast(1)
                    assertTrue(note.height + 1 >= note.insets.top + note.insets.bottom + lines * metrics.height,
                        "note clipped '${note.text}' lines=$lines height=${note.height} at $width")
                }
                children(panel).filterIsInstance<JButton>().forEach { button ->
                    val parent = button.parent
                    assertTrue(button.width + 1 >= button.preferredSize.width, "${button.text} squeezed at $width")
                    assertTrue(Rectangle(button.x, button.y, button.width, button.height).let { bounds ->
                        bounds.x >= 0 && bounds.y >= 0 &&
                            bounds.x + bounds.width <= parent.width + 1 &&
                            bounds.y + bounds.height <= parent.height + 1
                    }, "${button.text} at (${button.x},${button.y},${button.width}x${button.height}) parent ${parent.javaClass.simpleName} ${parent.width}x${parent.height} panel ${panel.width}x${panel.height}")
                }
            }
        }
    }
}
