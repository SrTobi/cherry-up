package cherryup.ui

import java.awt.Color
import java.awt.Component
import java.awt.Rectangle
import javax.swing.JLabel
import javax.swing.JList
import javax.swing.ListCellRenderer
import javax.swing.border.EmptyBorder

class OutputListCellRenderer: JLabel(), ListCellRenderer<Step> {
    private val nonSectionBorder = EmptyBorder(0, 20, 0, 0)

    init {
        isOpaque = true;
    }

    override fun getListCellRendererComponent(
        list: JList<out Step>,
        value: Step,
        index: Int,
        isSelected: Boolean,
        cellHasFocus: Boolean
    ): Component {
        componentOrientation = list.componentOrientation
        text = value.text()
        border = if (value.isSection()) null else nonSectionBorder
        background = when(value.progress()) {
            StepProgress.None -> list.background
            StepProgress.Processing -> Color.YELLOW.darker()
            StepProgress.Stopped -> Color.RED.darker()
            StepProgress.Done -> Color.GREEN.darker()
        }

        return this
    }

    override fun validate() {}
    override fun invalidate() {}
    override fun repaint() {}
    override fun revalidate() {}
    override fun repaint(tm: Long, x: Int, y: Int, width: Int, height: Int) {}
    override fun repaint(r: Rectangle?) {}
    override fun firePropertyChange(propertyName: String?, oldValue: Byte, newValue: Byte) {}
    override fun firePropertyChange(propertyName: String?, oldValue: Char, newValue: Char) {}
    override fun firePropertyChange(propertyName: String?, oldValue: Short, newValue: Short) {}
    override fun firePropertyChange(propertyName: String?, oldValue: Int, newValue: Int) {}
    override fun firePropertyChange(propertyName: String?, oldValue: Long, newValue: Long) {}
    override fun firePropertyChange(propertyName: String?, oldValue: Float, newValue: Float) {}
    override fun firePropertyChange(propertyName: String?, oldValue: Double, newValue: Double) {}
    override fun firePropertyChange(propertyName: String?, oldValue: Boolean, newValue: Boolean) {}
}