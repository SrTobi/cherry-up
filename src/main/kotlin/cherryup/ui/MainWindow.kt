package cherryup.ui

import cherryup.BranchTransition
import cherryup.Config
import java.awt.*
import java.awt.EventQueue.isDispatchThread
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import java.lang.Exception
import javax.swing.*
import javax.swing.SwingUtilities.invokeLater
import kotlin.concurrent.thread
import kotlin.system.exitProcess

class MainWindow(config: Config,
                 private val createModel: (String, List<BranchTransition>) -> UiModel?): JFrame() {
    private val pathInput: JTextField = JTextField(config.startDir)
    private val branchFlowInput: JTextField = JTextField(config.branchFlow)
    private val output: JList<Step> = JList()
    private val errorOutput: JLabel = JLabel("")
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
        instantiateModel(config.startDir, config.branchFlow, showErrors = false)
    }

    private fun instantiateModel(startDir: String = pathInput.text,
                                 branchFlowString: String = branchFlowInput.text,
                                 showErrors: Boolean = true) {
        assert(isDispatchThread())
        Config.save(Config(startDir, branchFlowString))

        val oldModel = model
        if (oldModel != null) {
            oldModel.update = { }
        }
        done = false
        model = null
        output.model = DefaultListModel()

        val branchFlow = parseBranchFlow(branchFlowString)

        if (branchFlow.isEmpty()) {
            if (showErrors) {
                JOptionPane.showMessageDialog(this, "Configure branch flow in the following format:\nbranch1 -> branch2 -> branch3", "Wrong branch flow", JOptionPane.ERROR_MESSAGE)
            }
            return
        }

        val newModel = try {
            createModel(startDir, branchFlow)
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

    private fun parseBranchFlow(value: String): List<BranchTransition> {
        return value
            .split(',')
            .flatMap { part ->
                part.split("->")
                    .map { it.trim() }
                    .windowed(2)
                    .map { BranchTransition(it[0], it[1]) }
            }
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
                        errorOutput.text = e.message
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

        if (model != null && !done) {
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

        ////////////////// Output //////////////////
        add(output, newConstraint().apply {
            gridx = 0
            gridy = 2
            gridwidth = 4
            fill = GridBagConstraints.BOTH
            weightx = 1.0
            weighty = 1.0
        })
        output.cellRenderer = OutputListCellRenderer()

        ////////////////// Output //////////////////
        add(errorOutput, newConstraint().apply {
            gridx = 0
            gridy = 3
            gridwidth = 4
            fill = GridBagConstraints.HORIZONTAL
            weightx = 1.0
        })
        errorOutput.foreground = Color.RED
        errorOutput.background = output.background
        errorOutput.isVisible = false

        ////////////////// Buttons //////////////////
        add(Panel(), newConstraint().apply {
            gridx = 1
            gridy = 4
            fill = GridBagConstraints.HORIZONTAL
            weightx = 1.0
        })

        add(reloadAndAbortButton, newConstraint().apply {
            gridx = 2
            gridy = 4
            insets = Insets(10, 10, 10, 4)
        })

        add(nextStepButton, newConstraint().apply {
            gridx = 3
            gridy = 4
            insets = Insets(10, 4, 10, 10)
        })
        nextStepButton.isEnabled = false
        nextStepButton.addActionListener { runModel() }
        reloadAndAbortButton.addActionListener {
            val model = this.model
            if (model != null && !done) {
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