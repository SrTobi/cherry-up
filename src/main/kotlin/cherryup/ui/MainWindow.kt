package cherryup.ui

import cherryup.BranchFlow
import cherryup.BranchTransition
import cherryup.Config
import com.github.srtobi.cherry_up.BuildConfig
import java.awt.*
import java.awt.EventQueue.isDispatchThread
import java.awt.event.AdjustmentEvent
import java.awt.event.AdjustmentListener
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import java.lang.Exception
import javax.swing.*
import javax.swing.SwingUtilities.invokeLater
import kotlin.concurrent.thread
import kotlin.system.exitProcess

class MainWindow(config: Config,
                 private val createModel: (BranchFlow, Config) -> UiModel?): JFrame() {
    private val pathInput: JTextField = JTextField(config.startDir)
    private val branchFlowInput: JTextField = JTextField(config.branchFlow)
    private val authorFilterInput: JTextField = JTextField(config.authorFilter)
    private val output: JList<Step> = JList()
    private val errorOutput: JLabel = JLabel("")
    private val versionLabel: JLabel = JLabel(BuildConfig.VersionBanner)
    private val reloadAndAbortButton: JButton = JButton("Reload")
    private val nextStepButton: JButton = JButton("Run")

    private var model: UiModel? = null
        set(value) {
            field?.close()
            field = value
            didRunModel = false
            updateButtons()
        }
    private var running: Boolean = false
        set(value) {
            didRunModel = didRunModel || value
            field = value
            updateButtons()
        }
    private var didRunModel: Boolean = false
    private var done: Boolean = false
        set(value) {
            field = value
            updateButtons()
        }

    init {
        createUI()
        instantiateModel(config, showErrors = false)
    }

    private fun gatherConfig(): Config = Config(
        startDir = pathInput.text,
        branchFlow = branchFlowInput.text,
        authorFilter = authorFilterInput.text
    )

    private fun instantiateModel(config: Config = gatherConfig(),
                                 showErrors: Boolean = true) {
        assert(isDispatchThread())
        Config.save(config)

        val oldModel = model
        if (oldModel != null) {
            oldModel.update = { }
        }
        done = false
        model = null
        output.model = DefaultListModel()

        val branchFlow = BranchTransition.parseFlow(config.branchFlow)

        if (branchFlow.isEmpty()) {
            if (showErrors) {
                JOptionPane.showMessageDialog(this, "Configure branch flow in the following format:\nbranch1 -> branch2 -> branch3", "Wrong branch flow", JOptionPane.ERROR_MESSAGE)
            }
            return
        }

        val newModel = try {
            createModel(branchFlow, config)
        } catch(e: Exception) {
            if (showErrors) {
                JOptionPane.showMessageDialog(this, e.message, "Error while opening Git", JOptionPane.ERROR_MESSAGE)
            }
            null
        }
        if (newModel != null) {
            newModel.update = {
                invokeLater {
                    updateFromModel()
                }
            }
            model = newModel
            newModel.update()
        }
    }

    override fun dispose() {
        model = null
        super.dispose()
    }

    private fun updateFromModel() {
        val model = this.model
        require(model != null)

        val listModel = model.listModel()
        if (listModel != output.model) {
            output.model = listModel
        }
        repaint()
    }

    private fun runModel(stop: Boolean = false) {
        assert(isDispatchThread())
        val model = this.model
        if (model != null) {
            if (!didRunModel && model.config != gatherConfig()) {
                val options = arrayOf("Run", "Reload", "Cancel")
                val selected = JOptionPane.showOptionDialog(
                    this,
                    "Configuration has changed. Really Run?",
                    "Run? Consider reload",
                    JOptionPane.YES_NO_CANCEL_OPTION,
                    JOptionPane.QUESTION_MESSAGE,
                    null,
                    options,
                    1
                )
                when (selected) {
                    0 -> {}
                    1 -> return instantiateModel()
                    2 -> return
                }
            }

            errorOutput.isVisible = false
            running = true
            updateButtons()
            thread {
                var suceeded = false
                try {
                    suceeded =
                        if (stop) model.stop()
                        else model.proceed()
                } catch (e: Exception) {
                    invokeLater {
                        errorOutput.text = "<html>${e.message?.replace("\n", "<br />")}"
                        errorOutput.isVisible = true
                    }
                } finally {
                    invokeLater {
                        running = false
                        done = suceeded
                    }
                }
            }
        }
    }

    private fun updateButtons() {
        assert(isDispatchThread())
        reloadAndAbortButton.isEnabled = !running
        nextStepButton.isEnabled = !running && !done

        if (model != null && !done && didRunModel) {
            reloadAndAbortButton.text = "Abort"
        } else {
            reloadAndAbortButton.text = "Reload"
        }

        nextStepButton.text =
            if (didRunModel) "Continue"
            else "Run"
    }

    private fun createUI() {
        title = "CherryUp"

        setSize(800, 600)
        setLocationRelativeTo(null)

        defaultCloseOperation = DO_NOTHING_ON_CLOSE
        addWindowListener(object: WindowAdapter() {
            override fun windowClosing(e: WindowEvent?) {
                val close =
                    if (model != null && !done && didRunModel) {
                        val selected = JOptionPane.showConfirmDialog(
                            this@MainWindow,
                            "Process not finished. Consider aborting first.\nReally Exit?",
                            "Exit?",
                            JOptionPane.YES_NO_OPTION
                        )
                        selected == JOptionPane.YES_OPTION
                    } else true

                if (close) {
                    model?.close()
                    exitProcess(0)
                }
            }
        })

        BuildConfig.VersionBanner

        contentPane.layout = GridBagLayout()

        fun newConstraint() = GridBagConstraints().apply {
            insets = Insets(10, 10, 0, 10)
        }

        ////////////////// Top Inputs //////////////////
        add(JLabel("Path"), newConstraint().apply {
            gridx = 0
            gridy = 0
            fill = GridBagConstraints.HORIZONTAL
        })

        add(pathInput, newConstraint().apply {
            gridx = 1
            gridy = 0
            gridwidth = 3
            fill = GridBagConstraints.HORIZONTAL
        })

        add(JLabel("Branch Flow"), newConstraint().apply {
            gridx = 0
            gridy = 1
            fill = GridBagConstraints.HORIZONTAL
        })

        add(branchFlowInput, newConstraint().apply {
            gridx = 1
            gridy = 1
            gridwidth = 3
            fill = GridBagConstraints.HORIZONTAL
            weightx = 1.0
        })

        add(JLabel("Author Filter"), newConstraint().apply {
            gridx = 0
            gridy = 2
            fill = GridBagConstraints.HORIZONTAL
        })

        add(authorFilterInput, newConstraint().apply {
            gridx = 1
            gridy = 2
            gridwidth = 3
            fill = GridBagConstraints.HORIZONTAL
            weightx = 1.0
        })

        ////////////////// Output //////////////////
        val scrollPane = JScrollPane(output)
        add(scrollPane, newConstraint().apply {
            gridx = 0
            gridy = 3
            gridwidth = 4
            fill = GridBagConstraints.BOTH
            weightx = 1.0
            weighty = 1.0
        })
        output.cellRenderer = OutputListCellRenderer()

        ////////////////// Output //////////////////
        add(errorOutput, newConstraint().apply {
            gridx = 0
            gridy = 4
            gridwidth = 4
            fill = GridBagConstraints.HORIZONTAL
            weightx = 1.0
        })
        errorOutput.foreground = Color.RED
        errorOutput.background = output.background
        errorOutput.isVisible = false

        ////////////////// Buttons //////////////////
        add(versionLabel, newConstraint().apply {
            gridx = 0
            gridy = 5
        })

        add(Panel(), newConstraint().apply {
            gridx = 1
            gridy = 5
            fill = GridBagConstraints.HORIZONTAL
            weightx = 1.0
        })

        add(reloadAndAbortButton, newConstraint().apply {
            gridx = 2
            gridy = 5
            insets = Insets(10, 10, 10, 4)
        })

        add(nextStepButton, newConstraint().apply {
            gridx = 3
            gridy = 5
            insets = Insets(10, 4, 10, 10)
        })
        nextStepButton.isEnabled = false
        nextStepButton.addActionListener { runModel() }
        reloadAndAbortButton.addActionListener {
            val model = this.model
            if (model != null && !done && didRunModel) {
                val selected = JOptionPane.showConfirmDialog(this, "Really abort?", "Abort?", JOptionPane.YES_NO_OPTION)
                if (selected == JOptionPane.YES_OPTION) {
                    runModel(stop = true)
                }
            } else {
                instantiateModel()
            }
        }
    }
}