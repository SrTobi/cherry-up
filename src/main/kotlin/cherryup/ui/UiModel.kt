package cherryup.ui

import cherryup.Config
import javax.swing.ListModel

enum class StepProgress {
    None,
    Processing,
    Done,
    Stopped,
}

interface Step {
    fun text(): String
    fun isSection(): Boolean
    fun progress(): StepProgress
    fun actionText(): String = "Proceed"
}

abstract class UiModel: AutoCloseable {
    var update: () -> Unit = { }

    abstract val config: Config

    abstract fun proceed(): Boolean
    abstract fun stop(): Boolean

    abstract fun listModel(): ListModel<Step>
}