package callgraph

import com.intellij.ide.util.EditorHelper
import com.intellij.psi.PsiMethod
import java.awt.Dimension
import java.awt.event.KeyEvent
import java.awt.event.KeyListener
import java.awt.geom.Point2D
import javax.swing.*

class CallGraphToolWindow {
    private lateinit var runButton: JButton
    private lateinit var callGraphToolWindowContent: JPanel
    private lateinit var canvasPanel: JPanel
    private lateinit var projectScopeButton: JRadioButton
    private lateinit var moduleScopeButton: JRadioButton
    private lateinit var directoryScopeButton: JRadioButton
    private lateinit var directoryScopeTextField: JTextField
    private lateinit var moduleScopeComboBox: JComboBox<String>
    private lateinit var mainTabbedPanel: JTabbedPane
    private lateinit var includeTestFilesCheckBox: JCheckBox
    private lateinit var buildTypeLabel: JLabel
    private lateinit var loadingProgressBar: JProgressBar
    private lateinit var showOnlyUpstreamButton: JButton
    private lateinit var showOnlyDownstreamButton: JButton
    private lateinit var showOnlyUpstreamDownstreamButton: JButton
    private lateinit var upstreamDownstreamScopeCheckbox: JCheckBox
    private lateinit var fitGraphToViewButton: JButton
    private lateinit var fitGraphToBestRatioButton: JButton
    private lateinit var increaseXGridButton: JButton
    private lateinit var decreaseXGridButton: JButton
    private lateinit var increaseYGridButton: JButton
    private lateinit var decreaseYGridButton: JButton
    private lateinit var statsLabel: JLabel
    private lateinit var viewSourceCodeButton: JButton
    private lateinit var viewPackageNameComboBox: JComboBox<String>
    private lateinit var viewFilePathComboBox: JComboBox<String>
    private lateinit var nodeSelectionComboBox: JComboBox<String>
    private lateinit var searchTextField: JTextField
    private lateinit var nodeColorComboBox: JComboBox<String>
    private lateinit var filterExternalCheckbox: JCheckBox
    private lateinit var filterAccessPublicCheckbox: JCheckBox
    private lateinit var filterAccessProtectedCheckbox: JCheckBox
    private lateinit var filterAccessPackageLocalCheckbox: JCheckBox
    private lateinit var filterAccessPrivateCheckbox: JCheckBox

    private val canvasBuilder = CanvasBuilder()
    private val canvas: Canvas = Canvas(this)
    private val focusedMethods = mutableSetOf<PsiMethod>()
    private val filterCheckboxes = listOf(
            this.filterExternalCheckbox,
            this.filterAccessPublicCheckbox,
            this.filterAccessProtectedCheckbox,
            this.filterAccessPackageLocalCheckbox,
            this.filterAccessPrivateCheckbox
    )

    init {
        // drop-down options
        val viewComboBoxOptions = listOf(
                ComboBoxOptions.VIEW_ALWAYS,
                ComboBoxOptions.VIEW_HOVERED,
                ComboBoxOptions.VIEW_NEVER
        )
        viewComboBoxOptions.forEach { this.viewPackageNameComboBox.addItem(it.text) }
        this.viewPackageNameComboBox.selectedItem = ComboBoxOptions.VIEW_HOVERED.text
        viewComboBoxOptions.forEach { this.viewFilePathComboBox.addItem(it.text) }
        this.viewFilePathComboBox.selectedItem = ComboBoxOptions.VIEW_NEVER.text
        val nodeSelectionComboBoxOptions = listOf(
                ComboBoxOptions.NODE_SELECTION_SINGLE,
                ComboBoxOptions.NODE_SELECTION_MULTIPLE
        )
        nodeSelectionComboBoxOptions.forEach { this.nodeSelectionComboBox.addItem(it.text) }
        this.nodeSelectionComboBox.selectedItem = ComboBoxOptions.NODE_SELECTION_SINGLE.text
        val nodeColorComboBoxOptions = listOf(
                ComboBoxOptions.NODE_COLOR_NONE,
                ComboBoxOptions.NODE_COLOR_ACCESS,
                ComboBoxOptions.NODE_COLOR_CLASS
        )
        nodeColorComboBoxOptions.forEach { option -> this.nodeColorComboBox.addItem(option.text) }
        this.nodeColorComboBox.selectedItem = ComboBoxOptions.NODE_COLOR_NONE.text

        // search field
        this.searchTextField.addKeyListener(object: KeyListener {
            override fun keyTyped(keyEvent: KeyEvent) {
            }

            override fun keyPressed(keyEvent: KeyEvent) {
            }

            override fun keyReleased(keyEvent: KeyEvent) {
                this@CallGraphToolWindow.canvas.repaint()
            }
        })
        this.filterCheckboxes.forEach { it.addActionListener { this.canvas.filterChangeHandler() } }

        // click handlers for buttons
        this.projectScopeButton.addActionListener { projectScopeButtonHandler() }
        this.moduleScopeButton.addActionListener { moduleScopeButtonHandler() }
        this.directoryScopeButton.addActionListener { directoryScopeButtonHandler() }
        this.runButton.addActionListener {
            this.focusedMethods.clear()
            run(getSelectedBuildType())
        }
        this.viewPackageNameComboBox.addActionListener { this.canvas.repaint() }
        this.viewFilePathComboBox.addActionListener { this.canvas.repaint() }
        this.nodeColorComboBox.addActionListener { this.canvas.repaint() }
        this.showOnlyUpstreamButton.addActionListener { run(CanvasConfig.BuildType.UPSTREAM) }
        this.showOnlyDownstreamButton.addActionListener { run(CanvasConfig.BuildType.DOWNSTREAM) }
        this.showOnlyUpstreamDownstreamButton.addActionListener { run(CanvasConfig.BuildType.UPSTREAM_DOWNSTREAM) }
        this.viewSourceCodeButton.addActionListener { viewSourceCodeHandler() }
        this.fitGraphToViewButton.addActionListener { this.canvas.fitCanvasToView() }
        this.fitGraphToBestRatioButton.addActionListener { this.canvas.fitCanvasToBestRatio() }
        this.increaseXGridButton.addActionListener { gridSizeButtonHandler(isXGrid = true, isIncrease = true) }
        this.decreaseXGridButton.addActionListener { gridSizeButtonHandler(isXGrid = true, isIncrease = false) }
        this.increaseYGridButton.addActionListener { gridSizeButtonHandler(isXGrid = false, isIncrease = true) }
        this.decreaseYGridButton.addActionListener { gridSizeButtonHandler(isXGrid = false, isIncrease = false) }

        // attach event listeners to canvas
        val mouseEventHandler = MouseEventHandler(this.canvas)
        this.canvas.addMouseListener(mouseEventHandler)
        this.canvas.addMouseMotionListener(mouseEventHandler)
        this.canvas.addMouseWheelListener(mouseEventHandler)
        this.canvas.isVisible = false
        this.canvasPanel.add(this.canvas)
    }

    fun getContent() = this.callGraphToolWindowContent

    fun isFocusedMethod(method: PsiMethod) = this.focusedMethods.contains(method)

    fun toggleFocusedMethod(method: PsiMethod): CallGraphToolWindow {
        if (this.focusedMethods.contains(method)) {
            // clicked on a selected node
            this.focusedMethods.remove(method)
        } else {
            // clicked on an un-selected node
            if (getSelectedComboBoxOption(this.nodeSelectionComboBox) == ComboBoxOptions.NODE_SELECTION_SINGLE) {
                this.focusedMethods.clear()
            }
            this.focusedMethods.add(method)
        }
        enableFocusedMethodButtons()
        return this
    }

    fun clearFocusedMethods(): CallGraphToolWindow {
        this.focusedMethods.clear()
        enableFocusedMethodButtons()
        return this
    }

    fun resetProgressBar(maximum: Int) {
        this.loadingProgressBar.isIndeterminate = false
        this.loadingProgressBar.maximum = maximum
        this.loadingProgressBar.value = 0
    }

    fun incrementProgressBar() {
        val newValue = this.loadingProgressBar.value + 1
        this.loadingProgressBar.value = newValue
        val text =
                if (this.loadingProgressBar.isIndeterminate) "$newValue functions processed"
                else "$newValue functions processed (total ${this.loadingProgressBar.maximum})"
        this.loadingProgressBar.string = text
    }

    fun isRenderFunctionPackageName(isNodeHovered: Boolean): Boolean {
        val option = getSelectedComboBoxOption(this.viewPackageNameComboBox)
        return option == ComboBoxOptions.VIEW_ALWAYS || (option == ComboBoxOptions.VIEW_HOVERED && isNodeHovered)
    }

    fun isRenderFunctionFilePath(isNodeHovered: Boolean): Boolean {
        val option = getSelectedComboBoxOption(this.viewFilePathComboBox)
        return option == ComboBoxOptions.VIEW_ALWAYS || (option == ComboBoxOptions.VIEW_HOVERED && isNodeHovered)
    }

    fun isQueried(text: String): Boolean {
        val searchQuery = this.searchTextField.text.toLowerCase()
        return searchQuery.isNotEmpty() && text.toLowerCase().contains(searchQuery)
    }

    fun isNodeColorByAccess() = getSelectedComboBoxOption(this.nodeColorComboBox) == ComboBoxOptions.NODE_COLOR_ACCESS

    fun isNodeColorByClassName() = getSelectedComboBoxOption(this.nodeColorComboBox) == ComboBoxOptions.NODE_COLOR_CLASS

    fun isFilterExternalChecked() = this.filterExternalCheckbox.isSelected

    fun isFilterAccessPublicChecked() = this.filterAccessPublicCheckbox.isSelected

    fun isFilterAccessProtectedChecked() = this.filterAccessProtectedCheckbox.isSelected

    fun isFilterAccessPackageLocalChecked() = this.filterAccessPackageLocalCheckbox.isSelected

    fun isFilterAccessPrivateChecked() = this.filterAccessPrivateCheckbox.isSelected

    fun isLegendNeeded() = getSelectedComboBoxOption(this.nodeColorComboBox) != ComboBoxOptions.NODE_COLOR_NONE

    fun getCanvasSize(): Dimension = this.canvasPanel.size

    fun run(buildType: CanvasConfig.BuildType) {
        val project = Utils.getActiveProject()
        if (project != null) {
            Utils.runBackgroundTask(project, Runnable {
                // set up the config object
                val canvasConfig = CanvasConfig(
                        project,
                        buildType,
                        this.canvas,
                        this@CallGraphToolWindow.moduleScopeComboBox.selectedItem as String? ?: "",
                        this@CallGraphToolWindow.directoryScopeTextField.text,
                        this@CallGraphToolWindow.focusedMethods,
                        this@CallGraphToolWindow
                )
                // start building graph
                setupUiBeforeRun(buildType)
                this@CallGraphToolWindow.canvasBuilder.build(canvasConfig)
                setupUiAfterRun()
            })
        }
    }

    private fun getSelectedComboBoxOption(comboBox: JComboBox<String>): ComboBoxOptions {
        val selectedText = comboBox.selectedItem as String?
        return if (selectedText == null) ComboBoxOptions.DUMMY else ComboBoxOptions.fromText(selectedText)
    }

    private fun disableAllSecondaryOptions() {
        this.includeTestFilesCheckBox.isEnabled = false
        this.moduleScopeComboBox.isEnabled = false
        this.directoryScopeTextField.isEnabled = false
    }

    private fun projectScopeButtonHandler() {
        disableAllSecondaryOptions()
        this.includeTestFilesCheckBox.isEnabled = true
    }

    private fun moduleScopeButtonHandler() {
        val project = Utils.getActiveProject()
        if (project != null) {
            // set up modules drop-down
            this.moduleScopeComboBox.removeAllItems()
            Utils.getActiveModules(project)
                    .forEach { this.moduleScopeComboBox.addItem(it.name) }
            disableAllSecondaryOptions()
            this.moduleScopeComboBox.isEnabled = true
        }
    }

    private fun directoryScopeButtonHandler() {
        val project = Utils.getActiveProject()
        if (project != null) {
            // set up directory option text field
            disableAllSecondaryOptions()
            this.directoryScopeTextField.text = project.basePath
            this.directoryScopeTextField.isEnabled = true
        }
    }

    private fun gridSizeButtonHandler(isXGrid: Boolean, isIncrease: Boolean) {
        val zoomFactor = if (isIncrease) 1.25f else 1 / 1.25f
        val xZoomFactor = if (isXGrid) zoomFactor else 1f
        val yZoomFactor = if (isXGrid) 1f else zoomFactor
        val zoomCenter = Point2D.Float(
                0.5f * this.canvasPanel.width.toFloat(),
                0.5f * this.canvasPanel.height.toFloat()
        )
        this.canvas.zoomAtPoint(zoomCenter, xZoomFactor, yZoomFactor)
    }

    private fun viewSourceCodeHandler() {
        this.focusedMethods.forEach { EditorHelper.openInEditor(it) }
    }

    private fun setupUiBeforeRun(buildType: CanvasConfig.BuildType) {
        // focus on the 'graph tab
        this.mainTabbedPanel.getComponentAt(1).isEnabled = true
        this.mainTabbedPanel.selectedIndex = 1
        // stats label
        this.statsLabel.text = "..."
        // build-type label
        when (buildType) {
            CanvasConfig.BuildType.WHOLE_PROJECT_WITH_TEST_LIMITED,
            CanvasConfig.BuildType.WHOLE_PROJECT_WITH_TEST,
            CanvasConfig.BuildType.WHOLE_PROJECT_WITHOUT_TEST_LIMITED,
            CanvasConfig.BuildType.WHOLE_PROJECT_WITHOUT_TEST -> this.buildTypeLabel.text = buildType.label
            CanvasConfig.BuildType.MODULE_LIMITED,
            CanvasConfig.BuildType.MODULE -> {
                val moduleName = this.moduleScopeComboBox.selectedItem as String
                this.buildTypeLabel.text = "<html>Module <b>$moduleName</b></html>"
            }
            CanvasConfig.BuildType.DIRECTORY_LIMITED,
            CanvasConfig.BuildType.DIRECTORY -> {
                val path = this.directoryScopeTextField.text
                this.buildTypeLabel.text = "<html>Directory <b>$path</b></html>"
            }
            CanvasConfig.BuildType.UPSTREAM,
            CanvasConfig.BuildType.DOWNSTREAM,
            CanvasConfig.BuildType.UPSTREAM_DOWNSTREAM -> {
                val functionNames = this.focusedMethods.joinToString { it.name }
                this.buildTypeLabel.text = "<html>${buildType.label} of function <b>$functionNames</b></html>"
            }
        }
        // disable some checkboxes and buttons
        listOf(
                this.viewPackageNameComboBox,
                this.viewFilePathComboBox,
                this.nodeSelectionComboBox,
                this.nodeColorComboBox,
                this.fitGraphToBestRatioButton,
                this.fitGraphToViewButton,
                this.increaseXGridButton,
                this.decreaseXGridButton,
                this.increaseYGridButton,
                this.decreaseYGridButton,
                this.viewSourceCodeButton,
                this.showOnlyUpstreamButton,
                this.showOnlyDownstreamButton,
                this.showOnlyUpstreamDownstreamButton,
                this.searchTextField
        ).forEach { (it as JComponent).isEnabled = false }
        // filter-related checkboxes
        this.filterCheckboxes.forEach {
            it.isEnabled = false
            it.isSelected = true
        }
        // progress bar
        this.loadingProgressBar.isVisible = true
        // clear the canvas panel, ready for new graph
        this.canvas.isVisible = false
    }

    private fun setupUiAfterRun() {
        // hide progress bar
        this.loadingProgressBar.isVisible = false
        // show the rendered canvas
        this.canvas.isVisible = true
        this.canvasPanel.updateUI()
        // stats label
        this.statsLabel.text = "${this.canvas.getNodesCount()} methods"
        // enable some checkboxes and buttons
        enableFocusedMethodButtons()
        listOf(
                this.viewPackageNameComboBox,
                this.viewFilePathComboBox,
                this.nodeSelectionComboBox,
                this.nodeColorComboBox,
                this.fitGraphToBestRatioButton,
                this.fitGraphToViewButton,
                this.increaseXGridButton,
                this.decreaseXGridButton,
                this.increaseYGridButton,
                this.decreaseYGridButton,
                this.searchTextField
        ).forEach { (it as JComponent).isEnabled = true }
        // filter-related checkboxes
        this.filterCheckboxes.forEach { it.isEnabled = true }
    }

    private fun getSelectedBuildType(): CanvasConfig.BuildType {
        val isLimitedScope = this.upstreamDownstreamScopeCheckbox.isSelected
        return when {
            this.projectScopeButton.isSelected -> {
                if (this.includeTestFilesCheckBox.isSelected) {
                    if (isLimitedScope) CanvasConfig.BuildType.WHOLE_PROJECT_WITH_TEST_LIMITED
                    else CanvasConfig.BuildType.WHOLE_PROJECT_WITH_TEST
                }
                if (isLimitedScope) CanvasConfig.BuildType.WHOLE_PROJECT_WITHOUT_TEST_LIMITED
                else CanvasConfig.BuildType.WHOLE_PROJECT_WITHOUT_TEST
            }
            this.moduleScopeButton.isSelected ->
                if (isLimitedScope) CanvasConfig.BuildType.MODULE_LIMITED
                else CanvasConfig.BuildType.MODULE
            this.directoryScopeButton.isSelected ->
                if (isLimitedScope) CanvasConfig.BuildType.DIRECTORY_LIMITED
                else CanvasConfig.BuildType.DIRECTORY
            else -> CanvasConfig.BuildType.WHOLE_PROJECT_WITH_TEST
        }
    }

    private fun enableFocusedMethodButtons() {
        listOf(
                this.showOnlyUpstreamButton,
                this.showOnlyDownstreamButton,
                this.showOnlyUpstreamDownstreamButton,
                this.viewSourceCodeButton
        ).forEach { it.isEnabled = this.focusedMethods.isNotEmpty() }
    }
}
