package com.chronos.studio

import com.intellij.diff.DiffContentFactory
import com.intellij.diff.DiffManager
import com.intellij.diff.requests.SimpleDiffRequest
import com.intellij.openapi.project.Project
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBPanel
import com.intellij.ui.components.JBTextArea
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Font
import javax.swing.JScrollPane

/**
 * Diff viewer for comparing state between two points in time.
 * 
 * Features:
 * - Side-by-side or unified diff view
 * - Syntax highlighting for JSON/data
 * - Change highlighting
 */
class DiffViewerPanel : JBPanel<DiffViewerPanel>(BorderLayout()) {
    
    private val titleLabel = JBLabel("State Diff")
    private val leftTextArea = JBTextArea().apply {
        isEditable = false
        font = Font(Font.MONOSPACED, Font.PLAIN, 12)
    }
    private val rightTextArea = JBTextArea().apply {
        isEditable = false
        font = Font(Font.MONOSPACED, Font.PLAIN, 12)
    }
    private val diffTextArea = JBTextArea().apply {
        isEditable = false
        font = Font(Font.MONOSPACED, Font.PLAIN, 12)
    }
    
    init {
        border = JBUI.Borders.empty(4)
        titleLabel.border = JBUI.Borders.empty(4)
        
        add(titleLabel, BorderLayout.NORTH)
        add(JScrollPane(diffTextArea), BorderLayout.CENTER)
    }
    
    /**
     * Shows a diff between two state values.
     */
    fun showDiff(beforeJson: String, afterJson: String) {
        val diff = computeSimpleDiff(beforeJson, afterJson)
        diffTextArea.text = diff
        titleLabel.text = "State Diff (${countChanges(diff)} changes)"
    }
    
    /**
     * Simple line-by-line diff computation.
     */
    private fun computeSimpleDiff(before: String, after: String): String {
        val beforeLines = before.lines()
        val afterLines = after.lines()
        
        val result = StringBuilder()
        
        var i = 0
        var j = 0
        
        while (i < beforeLines.size || j < afterLines.size) {
            when {
                i >= beforeLines.size -> {
                    result.appendLine("+ ${afterLines[j]}")
                    j++
                }
                j >= afterLines.size -> {
                    result.appendLine("- ${beforeLines[i]}")
                    i++
                }
                beforeLines[i] == afterLines[j] -> {
                    result.appendLine("  ${beforeLines[i]}")
                    i++
                    j++
                }
                else -> {
                    result.appendLine("- ${beforeLines[i]}")
                    result.appendLine("+ ${afterLines[j]}")
                    i++
                    j++
                }
            }
        }
        
        return result.toString()
    }
    
    private fun countChanges(diff: String): Int {
        return diff.lines().count { it.startsWith("+") || it.startsWith("-") }
    }
    
    /**
     * Clears the diff display.
     */
    fun clear() {
        diffTextArea.text = ""
        titleLabel.text = "State Diff"
    }
}
