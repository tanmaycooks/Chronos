package com.chronos.studio

import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBPanel
import com.intellij.util.ui.JBUI
import java.awt.*
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.JSlider
import javax.swing.event.ChangeListener

/**
 * Timeline scrubber for navigating through recorded events.
 * 
 * Features:
 * - Visual timeline with event markers
 * - Gap indicators
 * - Checkpoint markers
 * - Draggable position indicator
 */
class TimelineScrubberPanel : JBPanel<TimelineScrubberPanel>(BorderLayout()) {
    
    private val timelineSlider = JSlider(0, 100, 0)
    private val positionLabel = JBLabel("0 / 0")
    private val timestampLabel = JBLabel("00:00.000")
    private val markers = mutableListOf<TimelineMarker>()
    
    data class TimelineMarker(
        val position: Int,        // 0-100 percentage
        val type: MarkerType,
        val tooltip: String
    )
    
    enum class MarkerType {
        SNAPSHOT,
        CHECKPOINT,
        GAP,
        DIVERGENCE
    }
    
    private val listeners = mutableListOf<(Long) -> Unit>()
    
    init {
        border = JBUI.Borders.empty(8)
        preferredSize = Dimension(0, 60)
        
        // Timeline slider
        timelineSlider.majorTickSpacing = 10
        timelineSlider.paintTicks = true
        timelineSlider.addChangeListener(ChangeListener {
            val sequence = (timelineSlider.value.toLong() * getCurrentMax()) / 100
            listeners.forEach { it(sequence) }
        })
        
        // Control panel
        val controlPanel = JBPanel<JBPanel<*>>(FlowLayout(FlowLayout.RIGHT, 8, 0)).apply {
            add(JBLabel("Position:"))
            add(positionLabel)
            add(JBLabel("Time:"))
            add(timestampLabel)
        }
        
        add(MarkerOverlay(), BorderLayout.CENTER)
        add(controlPanel, BorderLayout.EAST)
    }
    
    /**
     * Overlay panel that draws markers on top of the slider.
     */
    inner class MarkerOverlay : JBPanel<MarkerOverlay>() {
        init {
            isOpaque = false
            layout = BorderLayout()
            add(timelineSlider, BorderLayout.CENTER)
        }
        
        override fun paintComponent(g: Graphics) {
            super.paintComponent(g)
            val g2 = g as Graphics2D
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            
            val sliderWidth = width - 20  // Account for slider margins
            val sliderX = 10
            
            markers.forEach { marker ->
                val x = sliderX + (marker.position * sliderWidth / 100)
                val color = when (marker.type) {
                    MarkerType.SNAPSHOT -> JBColor(Color(100, 100, 200), Color(120, 120, 220))
                    MarkerType.CHECKPOINT -> JBColor(Color(0, 180, 0), Color(0, 200, 0))
                    MarkerType.GAP -> JBColor(Color(200, 150, 0), Color(220, 170, 0))
                    MarkerType.DIVERGENCE -> JBColor(Color(200, 0, 0), Color(220, 0, 0))
                }
                
                g2.color = color
                g2.fillOval(x - 3, 5, 6, 6)
            }
        }
    }
    
    fun update(state: ChronosState) {
        positionLabel.text = "${state.currentSequence} / ${state.totalSequence}"
        if (state.totalSequence > 0) {
            timelineSlider.value = ((state.currentSequence * 100) / state.totalSequence).toInt()
        }
    }
    
    fun setMarkers(newMarkers: List<TimelineMarker>) {
        markers.clear()
        markers.addAll(newMarkers)
        repaint()
    }
    
    fun addPositionListener(listener: (Long) -> Unit) {
        listeners.add(listener)
    }
    
    private fun getCurrentMax(): Long = 1000 // Placeholder
}
