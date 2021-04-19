package cherryup.ui

import cherryup.CherryUpProcess
import cherryup.Config
import java.awt.EventQueue

private fun createAndShowGUI() {
    val frame = MainWindow(Config.load(), CherryUpProcess::create)
    frame.isVisible = true
}


fun main() {
    EventQueue.invokeLater(::createAndShowGUI)
}