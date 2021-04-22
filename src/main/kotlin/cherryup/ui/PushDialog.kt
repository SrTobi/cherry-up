package cherryup.ui

import java.awt.BorderLayout
import java.io.InputStream
import javax.swing.JFrame
import javax.swing.JTextArea
import javax.swing.Timer

/*
class PushDialog(inputStream: InputStream): JFrame() {
    private val output: JTextArea = JTextArea()

    private val timer = Timer(100) {
        for (stream in inputStreams) {
            val bufferedStream = inputStream.bufferedReader()

            while (bufferedStream.ready()) {
                output.append(bufferedStream.read().toChar().toString())
            }
        }
    }

    private var inputStreams: List<InputStream> = listOf()
        set(value) {
            if (value.isEmpty()) timer.stop()
            else timer.start()
            field = value
        }

    init {
        createUI()
        timer.isRepeats = true
        timer.start()
    }

    private fun createUI() {
        title = "CherryUp - Push"
        setSize(600, 400)
        setLocationRelativeTo(null)

        defaultCloseOperation = DISPOSE_ON_CLOSE

        layout = BorderLayout();
        add(output, BorderLayout.CENTER)
    }

    override fun dispose() {
        timer.stop()
        super.dispose()
    }
}*/