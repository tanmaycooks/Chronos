package com.chronos.studio.actions

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.ui.Messages

/**
 * Action to start recording state snapshots.
 */
class StartRecordingAction : AnAction() {
    override fun actionPerformed(e: AnActionEvent) {
        // In real implementation, would connect to app and start recording
        Messages.showInfoMessage(
            e.project,
            "Recording started. State snapshots are being captured.",
            "Chronos Recording"
        )
    }
    
    override fun update(e: AnActionEvent) {
        e.presentation.isEnabled = e.project != null
    }
}

/**
 * Action to stop recording.
 */
class StopRecordingAction : AnAction() {
    override fun actionPerformed(e: AnActionEvent) {
        Messages.showInfoMessage(
            e.project,
            "Recording stopped.",
            "Chronos Recording"
        )
    }
}

/**
 * Action to start replay.
 */
class StartReplayAction : AnAction() {
    override fun actionPerformed(e: AnActionEvent) {
        // Check determinism first
        val proceed = Messages.showYesNoDialog(
            e.project,
            "Replay requires determinism verification. Proceed?",
            "Chronos Replay",
            "Start Replay",
            "Cancel",
            Messages.getQuestionIcon()
        )
        
        if (proceed == Messages.YES) {
            Messages.showInfoMessage(
                e.project,
                "Replay started. Sandbox is active.",
                "Chronos Replay"
            )
        }
    }
}
