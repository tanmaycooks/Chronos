package com.chronos.studio

import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBPanel
import com.intellij.ui.treeStructure.Tree
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.Color
import javax.swing.JTree
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.DefaultTreeCellRenderer
import javax.swing.tree.DefaultTreeModel

/**
 * Tree view for displaying state snapshots.
 * 
 * Features:
 * - Expandable object hierarchy
 * - Type annotations
 * - Redacted value indicators
 * - Determinism class badges
 */
class StateTreePanel : JBPanel<StateTreePanel>(BorderLayout()) {
    
    private val rootNode = DefaultMutableTreeNode("State")
    private val treeModel = DefaultTreeModel(rootNode)
    private val tree = Tree(treeModel)
    
    init {
        border = JBUI.Borders.empty(4)
        
        val titleLabel = JBLabel("State Tree")
        titleLabel.border = JBUI.Borders.empty(4)
        
        tree.cellRenderer = StateTreeCellRenderer()
        tree.isRootVisible = false
        tree.showsRootHandles = true
        
        add(titleLabel, BorderLayout.NORTH)
        add(tree, BorderLayout.CENTER)
    }
    
    /**
     * Updates the tree with new state data.
     */
    fun update(state: ChronosState) {
        // In real implementation, this would receive actual state data
    }
    
    /**
     * Sets the displayed state from a snapshot.
     */
    fun displaySnapshot(sourceId: String, value: Any?) {
        rootNode.removeAllChildren()
        
        if (value == null) {
            rootNode.add(DefaultMutableTreeNode(StateNodeData("null", "null", null)))
        } else {
            addObjectToTree(rootNode, sourceId, value)
        }
        
        treeModel.reload()
        expandAll()
    }
    
    private fun addObjectToTree(parent: DefaultMutableTreeNode, name: String, value: Any?) {
        when (value) {
            null -> parent.add(DefaultMutableTreeNode(StateNodeData(name, "null", null)))
            is String -> parent.add(DefaultMutableTreeNode(StateNodeData(name, "\"$value\"", "String")))
            is Number -> parent.add(DefaultMutableTreeNode(StateNodeData(name, value.toString(), value::class.simpleName)))
            is Boolean -> parent.add(DefaultMutableTreeNode(StateNodeData(name, value.toString(), "Boolean")))
            is List<*> -> {
                val listNode = DefaultMutableTreeNode(StateNodeData(name, "[${value.size} items]", "List"))
                parent.add(listNode)
                value.forEachIndexed { index, item ->
                    addObjectToTree(listNode, "[$index]", item)
                }
            }
            is Map<*, *> -> {
                val mapNode = DefaultMutableTreeNode(StateNodeData(name, "{${value.size} entries}", "Map"))
                parent.add(mapNode)
                value.forEach { (k, v) ->
                    addObjectToTree(mapNode, k.toString(), v)
                }
            }
            else -> {
                val objectNode = DefaultMutableTreeNode(StateNodeData(name, "", value::class.simpleName))
                parent.add(objectNode)
                // In real implementation, would reflect on object properties
            }
        }
    }
    
    private fun expandAll() {
        var row = 0
        while (row < tree.rowCount) {
            tree.expandRow(row)
            row++
        }
    }
    
    /**
     * Data for each tree node.
     */
    data class StateNodeData(
        val name: String,
        val value: String,
        val type: String?,
        val isRedacted: Boolean = false,
        val determinismClass: String? = null
    ) {
        override fun toString(): String {
            val typeStr = if (type != null) ": $type" else ""
            val valueStr = if (value.isNotEmpty()) " = $value" else ""
            return "$name$typeStr$valueStr"
        }
    }
    
    /**
     * Custom cell renderer for state tree nodes.
     */
    class StateTreeCellRenderer : DefaultTreeCellRenderer() {
        override fun getTreeCellRendererComponent(
            tree: JTree?,
            value: Any?,
            sel: Boolean,
            expanded: Boolean,
            leaf: Boolean,
            row: Int,
            hasFocus: Boolean
        ): java.awt.Component {
            val component = super.getTreeCellRendererComponent(tree, value, sel, expanded, leaf, row, hasFocus)
            
            if (value is DefaultMutableTreeNode) {
                val data = value.userObject
                if (data is StateNodeData && data.isRedacted) {
                    foreground = JBColor(Color(200, 100, 100), Color(220, 120, 120))
                }
            }
            
            return component
        }
    }
}
