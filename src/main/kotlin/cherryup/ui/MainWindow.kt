package cherryup.ui

import cherryup.BranchFlow
import cherryup.Config
import java.awt.*
import java.lang.Exception
import javax.swing.*
import javax.swing.SwingUtilities.invokeLater
import kotlin.concurrent.thread

class MainWindow(config: Config,
                 private val createModel: (String, List<BranchFlow>) -> UiModel?): JFrame() {
    private val pathInput: JTextField = JTextField(config.startDir)
    private val branchFlowInput: JTextField = JTextField(config.branchFlow)
    private val output: JList<Step> = JList()
    private val errorOutput: JLabel = JLabel("")
    private val updateButton: JButton = JButton("Update")
    private val nextStepButton: JButton = JButton("Run")

    private var model: UiModel? = null

    init {
        createUI()
        instantiateModel(config.startDir, config.branchFlow, showErrors = false)
    }

    private fun instantiateModel(startDir: String = pathInput.text,
                                 branchFlowString: String = branchFlowInput.text,
                                 showErrors: Boolean = true) {
        Config.save(Config(startDir, branchFlowString))

        val oldModel = model
        if (oldModel != null) {
            oldModel.close()
            oldModel.update = { }
        }
        errorOutput.isVisible = false
        nextStepButton.isEnabled = false
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
            nextStepButton.isEnabled = true
            newModel.update()
        }
    }

    override fun dispose() {
        model?.close()
        super.dispose()
    }

    private fun parseBranchFlow(value: String): List<BranchFlow> {
        return value
            .split("->")
            .map { it.trim() }
            .windowed(2)
            .map { BranchFlow(it[0], it[1]) }
    }

    private fun updateFromModel() {
        val model = this.model
        require(model != null)

        val listModel = model.listModel()
        if (listModel != output.model) {
            output.model = listModel
        }
    }

    private fun proceed() {
        val model = this.model
        if (model != null) {
            thread {
                try {
                    updateButton.isEnabled = false
                    nextStepButton.isEnabled = false
                    model.proceed()
                } catch (e: Exception) {
                    errorOutput.text = e.message
                    errorOutput.isVisible = true
                } finally {
                    updateButton.isEnabled = true
                    nextStepButton.isEnabled = true
                }
            }
        }
    }

    private fun createUI() {
        title = "CherryUp"

        defaultCloseOperation = EXIT_ON_CLOSE
        setSize(800, 600)
        setLocationRelativeTo(null)

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

        add(updateButton, newConstraint().apply {
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
        nextStepButton.addActionListener { proceed() }
        updateButton.addActionListener { instantiateModel() }
    }
}