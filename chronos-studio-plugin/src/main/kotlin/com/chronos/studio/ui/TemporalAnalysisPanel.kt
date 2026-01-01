package com.chronos.studio

import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBPanel
import com.intellij.util.ui.JBUI
import java.awt.*
import javax.swing.JScrollPane

/**
 * Temporal analysis visualization panel.
 * 
 * Shows temporal relationships between events.
 * Important: Shows correlation, NOT causation.
 */
class TemporalAnalysisPanel : JBPanel<TemporalAnalysisPanel>(BorderLayout()) {
    
    private val titleLabel = JBLabel("Temporal Analysis")
    private val disclaimer = JBLabel("⚠️ Shows temporal correlation, NOT causation")
    private val canvas = TemporalCanvas()
    
    private val events = mutableListOf<TemporalEvent>()
    
    data class TemporalEvent(
        val sequenceNumber: Long,
        val timestamp: Long,
        val sourceId: String,
        val threadName: String
    )
    
    init {
        border = JBUI.Borders.empty(4)
        preferredSize = Dimension(0, 120)
        
        val headerPanel = JBPanel<JBPanel<*>>(BorderLayout()).apply {
            add(titleLabel, BorderLayout.WEST)
            add(disclaimer, BorderLayout.EAST)
        }
        disclaimer.foreground = JBColor(Color(180, 120, 0), Color(200, 140, 0))
        
        add(headerPanel, BorderLayout.NORTH)
        add(JScrollPane(canvas), BorderLayout.CENTER)
    }
    
    /**
     * Updates the temporal analysis with new events.
     */
    fun setEvents(newEvents: List<TemporalEvent>) {
        events.clear()
        events.addAll(newEvents)
        canvas.repaint()
    }
    
    /**
     * Canvas for drawing temporal relationships.
     */
    inner class TemporalCanvas : JBPanel<TemporalCanvas>() {
        init {
            preferredSize = Dimension(800, 80)
        }
        
        override fun paintComponent(g: Graphics) {
            super.paintComponent(g)
            val g2 = g as Graphics2D
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            
            if (events.isEmpty()) {
                g2.color = foreground
                g2.drawString("No events to display", 20, 40)
                return
            }
            
            // Group events by thread
            val byThread = events.groupBy { it.threadName }
            val threads = byThread.keys.toList()
            
            val rowHeight = height / maxOf(threads.size, 1)
            val minTime = events.minOf { it.timestamp }
            val maxTime = events.maxOf { it.timestamp }
            val timeSpan = maxOf(maxTime - minTime, 1)
            
            // Draw thread lanes
            threads.forEachIndexed { index, threadName ->
                val y = index * rowHeight + rowHeight / 2
                
                // Thread label
                g2.color = foreground
                g2.drawString(threadName.takeLast(15), 5, y + 4)
                
                // Thread line
                g2.color = JBColor(Color(200, 200, 200), Color(80, 80, 80))
                g2.drawLine(100, y, width - 10, y)
                
                // Events on this thread
                val threadEvents = byThread[threadName] ?: emptyList()
                threadEvents.forEach { event ->
                    val x = 100 + ((event.timestamp - minTime) * (width - 120) / timeSpan).toInt()
                    
                    g2.color = JBColor(Color(100, 150, 220), Color(120, 170, 240))
                    g2.fillOval(x - 4, y - 4, 8, 8)
                }
            }
            
            // Draw temporal arrows between consecutive events
            if (events.size > 1) {
                val sorted = events.sortedBy { it.timestamp }
                for (i in 0 until sorted.size - 1) {
                    val e1 = sorted[i]
                    val e2 = sorted[i + 1]
                    
                    val t1Idx = threads.indexOf(e1.threadName)
                    val t2Idx = threads.indexOf(e2.threadName)
                    
                    if (t1Idx != t2Idx) {
                        val x1 = 100 + ((e1.timestamp - minTime) * (width - 120) / timeSpan).toInt()
                        val x2 = 100 + ((e2.timestamp - minTime) * (width - 120) / timeSpan).toInt()
                        val y1 = t1Idx * rowHeight + rowHeight / 2
                        val y2 = t2Idx * rowHeight + rowHeight / 2
                        
                        g2.color = JBColor(Color(150, 150, 150, 100), Color(100, 100, 100, 100))
                        g2.drawLine(x1, y1, x2, y2)
                    }
                }
            }
        }
    }
}
