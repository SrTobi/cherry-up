package cherryup

import cherryup.ui.MainWindow
import java.awt.EventQueue
import java.nio.file.Paths

private fun createAndShowGUI() {
    val frame = MainWindow(Config.load()) { branchFlow, config ->
        val ultimatePath = Paths.get(config.startDir)
        CherryUpProcess.create(
            mapOf("ultimate" to ultimatePath, "community" to ultimatePath.resolve("community")),
            branchFlow,
            config.authorFilter,
            config
        )
    }
    frame.isVisible = true
}


fun main() {
    EventQueue.invokeLater(::createAndShowGUI)
}