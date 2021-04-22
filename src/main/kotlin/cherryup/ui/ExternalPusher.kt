package cherryup.ui

import java.io.ByteArrayInputStream
import java.io.File
import java.io.InputStream
import java.nio.charset.StandardCharsets
import javax.swing.SwingUtilities

/*
class ExternalPusher: (File) -> Boolean, AutoCloseable {
    private val window: PushDialog? = null

    override fun invoke(file: File): Boolean {
        var result = true
        SwingUtilities.invokeAndWait {
            val process = Runtime.getRuntime()
                .exec(arrayOf("git", "push"), arrayOf(), file)

            val stream: InputStream = "exampleString".byteInputStream()
            val window = PushDialog(stream)
            window.isVisible = true

            process.waitFor()

            while (stream.)

            window.isVisible = false
        }
        return result
    }

    override fun close() {
        window?.dispose()
    }
}*/