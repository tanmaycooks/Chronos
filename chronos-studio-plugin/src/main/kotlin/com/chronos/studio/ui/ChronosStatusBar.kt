package com.chronos.studio

import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBPanel
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.Color
import java.awt.FlowLayout
import javax.swing.JProgressBar

/**
 * Status bar showing connection state, recording status, and determinism score.
 */
class ChronosStatusBar : JBPanel<ChronosStatusBar>(BorderLayout()) {
    
    private val connectionLabel = JBLabel("⚪ Disconnected")
    private val recordingLabel = JBLabel("⏹ Stopped")
    private val scoreLabel = JBLabel("Score: 100")
    private val scoreBar = JProgressBar(0, 100).apply {
        value = 100
        isStringPainted = true
        string = "PERFECT"
    }
    
    init {
        border = JBUI.Borders.empty(4, 8)
        
        val leftPanel = JBPanel<JBPanel<*>>(FlowLayout(FlowLayout.LEFT, 16, 0)).apply {
            add(connectionLabel)
            add(recordingLabel)
        }
        
        val rightPanel = JBPanel<JBPanel<*>>(FlowLayout(FlowLayout.RIGHT, 8, 0)).apply {
            add(scoreLabel)
            add(scoreBar)
        }
        
        add(leftPanel, BorderLayout.WEST)
        add(rightPanel, BorderLayout.EAST)
    }
    
    fun update(state: ChronosState) {
        // Connection status
        connectionLabel.text = if (state.isConnected) "🟢 Connected" else "⚪ Disconnected"
        
        // Recording status
        recordingLabel.text = if (state.isRecording) "🔴 Recording" else "⏹ Stopped"
        
        // Determinism score
        scoreLabel.text = "Score: ${state.determinismScore}"
        scoreBar.value = state.determinismScore
        scoreBar.string = state.determinismLevel
        
        // Color coding based on score
        scoreBar.foreground = when {
            state.determinismScore == 100 -> JBColor(Color(0, 180, 0), Color(0, 200, 0))
            state.determinismScore >= 80 -> JBColor(Color(0, 150, 0), Color(0, 170, 0))
            state.determinismScore >= 50 -> JBColor(Color(200, 150, 0), Color(220, 170, 0))
            else -> JBColor(Color(200, 0, 0), Color(220, 0, 0))
        }
    }
}
