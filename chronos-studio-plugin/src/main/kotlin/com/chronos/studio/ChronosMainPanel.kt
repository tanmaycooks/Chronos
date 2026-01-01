package com.chronos.studio

import com.intellij.openapi.project.Project
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBPanel
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import javax.swing.*

/**
 * Main panel for the Chronos Debugger Tool Window.
 * 
 * Layout:
 * ┌─────────────────────────────────────────────────────┐
 * │ [Status Bar: Determinism Score | Recording Status] │
 * ├─────────────────────────────────────────────────────┤
 * │ [Timeline Scrubber]                                 │
 * ├─────────────────────────────────────────────────────┤
 * │ [State Tree]          │ [Diff Viewer]              │
 * ├─────────────────────────────────────────────────────┤
 * │ [Temporal Analysis Panel]                           │
 * └─────────────────────────────────────────────────────┘
 */
class ChronosMainPanel(private val project: Project) : JBPanel<ChronosMainPanel>(BorderLayout()) {
    
    private val statusBar = ChronosStatusBar()
    private val timelineScrubber = TimelineScrubberPanel()
    private val stateTreePanel = StateTreePanel()
    private val diffViewerPanel = DiffViewerPanel()
    private val temporalAnalysisPanel = TemporalAnalysisPanel()
    
    init {
        border = JBUI.Borders.empty(4)
        
        // Top: Status bar
        add(statusBar, BorderLayout.NORTH)
        
        // Center: Main content with splitter
        val centerPanel = JBPanel<JBPanel<*>>(BorderLayout())
        
        // Timeline at top of center
        centerPanel.add(timelineScrubber, BorderLayout.NORTH)
        
        // State tree and diff viewer in the middle
        val middleSplitter = JSplitPane(
            JSplitPane.HORIZONTAL_SPLIT,
            JBScrollPane(stateTreePanel),
            JBScrollPane(diffViewerPanel)
        )
        middleSplitter.dividerLocation = 300
        centerPanel.add(middleSplitter, BorderLayout.CENTER)
        
        // Temporal analysis at bottom
        centerPanel.add(temporalAnalysisPanel, BorderLayout.SOUTH)
        
        add(centerPanel, BorderLayout.CENTER)
    }
    
    fun updateState(state: ChronosState) {
        statusBar.update(state)
        timelineScrubber.update(state)
        stateTreePanel.update(state)
    }
}

/**
 * Represents the current state of the Chronos debugger.
 */
data class ChronosState(
    val isConnected: Boolean = false,
    val isRecording: Boolean = false,
    val determinismScore: Int = 100,
    val determinismLevel: String = "PERFECT",
    val eventCount: Int = 0,
    val currentSequence: Long = 0,
    val totalSequence: Long = 0
)
