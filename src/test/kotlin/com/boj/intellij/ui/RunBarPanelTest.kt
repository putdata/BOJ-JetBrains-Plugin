package com.boj.intellij.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RunBarPanelTest {

    @Test
    fun `initial status shows waiting message`() {
        val panel = RunBarPanel(onRunAll = {})
        assertEquals("실행 대기 중", panel.getStatusText())
    }

    @Test
    fun `run all button is disabled when no command available`() {
        val panel = RunBarPanel(onRunAll = {})
        assertFalse(panel.isRunAllEnabled())
    }

    @Test
    fun `run all button is enabled after setting command`() {
        val panel = RunBarPanel(onRunAll = {})
        panel.setAvailableCommands(listOf(RunBarPanel.CommandEntry("main.py", "python3 main.py")))
        assertTrue(panel.isRunAllEnabled())
    }

    @Test
    fun `clicking run all invokes callback with selected command`() {
        var receivedCommand: String? = null
        val panel = RunBarPanel(onRunAll = { receivedCommand = it })
        panel.setAvailableCommands(listOf(RunBarPanel.CommandEntry("main.py", "python3 main.py")))
        panel.simulateRunAllClick()
        assertEquals("python3 main.py", receivedCommand)
    }

    @Test
    fun `updateStatus changes status label`() {
        val panel = RunBarPanel(onRunAll = {})
        panel.updateStatus("2/3 통과")
        assertEquals("2/3 통과", panel.getStatusText())
    }

    @Test
    fun `getSelectedCommand returns null when no commands set`() {
        val panel = RunBarPanel(onRunAll = {})
        assertEquals(null, panel.getSelectedCommand())
    }

    @Test
    fun `getSelectedCommand returns command after setting`() {
        val panel = RunBarPanel(onRunAll = {})
        panel.setAvailableCommands(listOf(RunBarPanel.CommandEntry("main.py", "python3 main.py")))
        assertEquals("python3 main.py", panel.getSelectedCommand())
    }

    @Test
    fun `setRunning disables run all button`() {
        val panel = RunBarPanel(onRunAll = {})
        panel.setAvailableCommands(listOf(RunBarPanel.CommandEntry("main.py", "python3 main.py")))
        panel.setRunning(true)
        assertFalse(panel.isRunAllEnabled())
    }

    @Test
    fun `setRunning false re-enables run all button`() {
        val panel = RunBarPanel(onRunAll = {})
        panel.setAvailableCommands(listOf(RunBarPanel.CommandEntry("main.py", "python3 main.py")))
        panel.setRunning(true)
        panel.setRunning(false)
        assertTrue(panel.isRunAllEnabled())
    }

    @Test
    fun `stop button disabled by default`() {
        val panel = RunBarPanel(onRunAll = {})
        assertFalse(panel.isStopEnabled())
    }

    @Test
    fun `stop button enabled during running`() {
        val panel = RunBarPanel(onRunAll = {})
        panel.setRunning(true)
        assertTrue(panel.isStopEnabled())
    }

    @Test
    fun `stop button disabled after running completes`() {
        val panel = RunBarPanel(onRunAll = {})
        panel.setRunning(true)
        panel.setRunning(false)
        assertFalse(panel.isStopEnabled())
    }
}
