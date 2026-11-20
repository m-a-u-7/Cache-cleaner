package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.syssweep.data.PreferredBackend
import com.example.syssweep.data.PrivilegeMode
import com.example.syssweep.engine.PrivilegeBridge
import com.example.syssweep.util.FormatUtils
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class SysSweepLogicTest {

  @Test
  fun `formatBytes handles zero and positive values`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    assertEquals("0 B", FormatUtils.formatBytes(context, 0L))
    assertEquals("0 B", FormatUtils.formatBytes(context, -5L))
    val kb = FormatUtils.formatBytes(context, 2048L)
    assertFalse(kb.isBlank())
  }

  @Test
  fun `inspectPrivileges reports non-system mode in standard sandbox`() = runTest {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val bridge = PrivilegeBridge(context)
    val diag = bridge.inspectPrivileges(PreferredBackend.SYSTEM_PRIV_APP)
    assertFalse(diag.isSystemPartitionApp)
    assertEquals(PrivilegeMode.LIMITED_NONE, diag.activeMode)
  }
}
