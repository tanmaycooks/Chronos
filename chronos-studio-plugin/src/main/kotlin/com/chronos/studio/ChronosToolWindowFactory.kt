package com.chronos.studio

import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.content.ContentFactory

/**
 * Factory for creating the Chronos Debugger Tool Window.
 * 
 * This is the main entry point for the Android Studio UI.
 */
class ChronosToolWindowFactory : ToolWindowFactory, DumbAware {
    
    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val chronosPanel = ChronosMainPanel(project)
        val contentFactory = ContentFactory.getInstance()
        val content = contentFactory.createContent(chronosPanel, "", false)
        toolWindow.contentManager.addContent(content)
    }
    
    override fun shouldBeAvailable(project: Project): Boolean {
        // Only available for Android projects
        return true // Simplified - would check for Android facet in real implementation
    }
}
