package cherryup.ui

import cherryup.CherryUpProcess
import cherryup.Config
import java.awt.EventQueue
import java.nio.file.Paths

private fun createAndShowGUI() {
    val frame = MainWindow(Config.load()) { ultimatePathString, branchFlow ->
        val ultimatePath = Paths.get(ultimatePathString)
        CherryUpProcess.create(mapOf("ultimate" to ultimatePath, "community" to ultimatePath.resolve("community")), branchFlow)
    }
    frame.isVisible = true
}


fun main() {
    EventQueue.invokeLater(::createAndShowGUI)
}