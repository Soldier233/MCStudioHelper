package com.github.tartaricacid.mcshelper.util

import com.github.tartaricacid.mcshelper.gui.settings.McdevEditorPanel
import java.awt.Component
import java.awt.Container
import javax.swing.*
import kotlin.test.*

class McdevEditorPanelTest {
    private fun children(root: Component): List<Component> = listOf(root) +
        if (root is Container) root.components.flatMap(::children) else emptyList()
    private fun named(panel: Component, name: String) = children(panel).single { it.name == name }

    @Test fun directEditingOnlyChangesTheEditedFieldAndCanReturnToOriginal() {
        SwingUtilities.invokeAndWait {
            var writes = 0
            val panel = McdevEditorPanel { writes++ }
            val original = McdevSchema.parse("""{"window_style":{},"future":{"value":3},"included_mod_dirs":["./",{"path":"extra","enabled":false,"future_flag":7}]}""")
            panel.load(McdevSchema.gson.toJson(original))
            assertEquals(original, panel.value())
            assertEquals(0, writes)
            val player = named(panel, "user_name") as JTextField
            assertTrue(player.isEditable)
            assertEquals("developer", player.text)
            player.text = "Alice"
            assertEquals(original.deepCopy().apply { addProperty("user_name", "Alice") }, panel.value())
            player.text = "developer"
            assertEquals(original, panel.value())
            val inventory = named(panel, "keep_inventory") as JCheckBox
            assertTrue(inventory.isEnabled)
            assertTrue(inventory.isSelected)
            inventory.doClick()
            assertEquals(original.deepCopy().apply { addProperty("keep_inventory", false) }, panel.value())
        }
    }

    @Test fun rawJsonIsLeftToTheIdeTextEditor() {
        SwingUtilities.invokeAndWait {
            val panel = McdevEditorPanel()
            assertTrue(children(panel).none { it is JTabbedPane })
            panel.load("{  \"future\": 42 }\n")
            assertEquals(42, panel.value().get("future").asInt)
            panel.load("{ invalid")
            assertEquals(42, panel.value().get("future").asInt)
            assertTrue((children(panel).filterIsInstance<JLabel>().single { it.text.contains("Text") }).isVisible)
        }
    }

    @Test fun invalidVisualInputIsPreservedUntilFixed() {
        SwingUtilities.invokeAndWait {
            val panel = McdevEditorPanel()
            val port = named(panel, "mcp_server_config.server_port") as JTextField
            port.text = "70000"
            assertFails { panel.value() }
            assertEquals("70000", port.text)
            port.text = "19134"
            assertEquals(19134, McdevSchema.get(panel.value(), "mcp_server_config.server_port")!!.asInt)
        }
    }

    @Test fun advancedFieldsAreCollapsedButCompleteAndKeyboardSupportsCustomValues() {
        SwingUtilities.invokeAndWait {
            val panel = McdevEditorPanel()
            val advanced = named(panel, "advanced") as JPanel
            assertFalse(advanced.isVisible)
            (named(panel, "advancedToggle") as JToggleButton).doClick()
            assertTrue(advanced.isVisible)
            for (field in McdevSchema.fields) assertTrue(named(panel, field.path).isEnabled)
            val key = named(panel, "debug_options.reload_key") as JComboBox<*>
            key.selectedItem = "不绑定"
            assertEquals("", McdevSchema.get(panel.value(), "debug_options.reload_key")!!.asString)
            key.editor.item = "-99"
            assertEquals("-99", McdevSchema.get(panel.value(), "debug_options.reload_key")!!.asString)
            key.editor.item = "9999"
            assertEquals("9999", McdevSchema.get(panel.value(), "debug_options.reload_key")!!.asString)
            key.editor.item = "KEY_INVALID"
            assertFails { panel.value() }
            val seed = named(panel, "world_seed") as JTextField
            key.editor.item = "82"
            seed.text = "123"
            assertEquals(123, panel.value().get("world_seed").asInt)
            seed.text = ""
            assertFalse(panel.value().has("world_seed"))
            panel.load("""{"world_seed":123,"world_source_path":null}""")
            seed.text = ""
            assertTrue(panel.value().get("world_seed").isJsonNull)
            assertTrue(panel.value().get("world_source_path").isJsonNull)
        }
    }

    @Test fun directoryEditsPreserveExtensionsAndPerDirectoryFlags() {
        SwingUtilities.invokeAndWait {
            val panel = McdevEditorPanel()
            panel.load("""{"included_mod_dirs":[{"path":"extra","enabled":false,"hot_reload":false,"custom":7}]}""")
            val table = children(panel).filterIsInstance<JTable>().single()
            table.model.setValueAt("renamed", 0, 0)
            val entry = panel.value().getAsJsonArray("included_mod_dirs")[0].asJsonObject
            assertEquals("renamed", entry.get("path").asString)
            assertFalse(entry.get("enabled").asBoolean)
            assertFalse(entry.get("hot_reload").asBoolean)
            assertEquals(7, entry.get("custom").asInt)
        }
    }

    @Test fun renderFormForLayoutReview() {
        SwingUtilities.invokeAndWait {
            val panel = McdevEditorPanel(worldAction = { _, _ -> })
            fun layout(component: Component) {
                if (component is Container) { component.doLayout(); component.components.forEach(::layout) }
            }
            fun capture(name: String) {
                panel.setSize(900, 820)
                layout(panel)
                val image = java.awt.image.BufferedImage(900, 820, java.awt.image.BufferedImage.TYPE_INT_RGB)
                val graphics = image.createGraphics()
                try { panel.printAll(graphics) } finally { graphics.dispose() }
                val destination = java.nio.file.Path.of("build/reports/$name.png")
                java.nio.file.Files.createDirectories(destination.parent)
                javax.imageio.ImageIO.write(image, "png", destination.toFile())
            }
            capture("mcdev-settings-preview")
            (named(panel, "advancedToggle") as JToggleButton).doClick()
            capture("mcdev-settings-expanded")
        }
    }
}
