package com.github.tartaricacid.mcshelper.util

import kotlin.test.*

class McdkLocatorTest {
    @Test
    fun bundledBundleIsComplete() {
        assertTrue(McdkLocator.RESOURCE_FILES.contains(McdkLocator.EXE_NAME))
        for (name in McdkLocator.RESOURCE_FILES) {
            val resource = "${McdkLocator.RESOURCE_DIR}/$name"
            val stream = McdkLocator::class.java.classLoader.getResourceAsStream(resource)
            assertNotNull(stream, "缺少内置资源：$resource")
            stream.use { assertTrue(it.readBytes().isNotEmpty(), "内置资源为空：$resource") }
        }
    }

    @Test
    fun bundledVersionIsASemanticVersion() {
        assertTrue(Regex("""\d+\.\d+\.\d+""").matches(McdkLocator.BUNDLED_VERSION))
    }
}
