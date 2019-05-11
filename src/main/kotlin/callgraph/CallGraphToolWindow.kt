package callgraph

import com.intellij.ide.util.EditorHelper
import com.intellij.psi.PsiMethod
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
    private lateinit var filterAccessPublicCheckbox: JCheckBox
    private lateinit var filterAccessProtectedCheckbox: JCheckBox
    private lateinit var filterAccessPackageLocalCheckbox: JCheckBox
    private lateinit var filterAccessPrivateCheckbox: JCheckBox

    private val canvasBuilder = CanvasBuilder()
    private val canvas: Canvas = Canvas(this)
    private val focusedMethods = mutableSetOf<PsiMethod>()
    private val filterAccessCheckboxes = listOf(
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
        this.filterAccessCheckboxes.forEach { it.addActionListener { this.canvas.filterAccessChangeHandler() } }

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
        this.fitGraphToViewButton.addActionListener { fitGraphToViewButtonHandler() }
        this.fitGraphToBestRatioButton.addActionListener { fitGraphToBestRatioButtonHandler() }
        this.increaseXGridButton.addActionListener { gridSizeButtonHandler(isXGrid = true, isIncrease = true) }
        this.decreaseXGridButton.addActionListener { gridSizeButtonHandler(isXGrid = true, isIncrease = false) }
        this.increaseYGridButton.addActionListener { gridSizeButtonHandler(isXGrid = false, isIncrease = true) }
        this.decreaseYGridButton.addActionListener { gridSizeButtonHandler(isXGrid = false, isIncrease = false) }

        // attach event listeners to canvas
        val mouseEventHandler = MouseEventHandler(this.canvas)
        this.canvas.addMouseListener(mouseEventHandler)
        this.canvas.addMouseMotionListener(mouseEventHandler)
        this.canvas.addMouseWheelListener(mouseEventHandler)
    }

    fun getContent(): JPanel {
        return this.callGraphToolWindowContent
    }

    fun isFocusedMethod(method: PsiMethod): Boolean {
        return this.focusedMethods.contains(method)
    }

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

    fun isNodeColorByAccess(): Boolean {
        return getSelectedComboBoxOption(this.nodeColorComboBox) == ComboBoxOptions.NODE_COLOR_ACCESS
    }

    fun isNodeColorByClassName(): Boolean {
        return getSelectedComboBoxOption(this.nodeColorComboBox) == ComboBoxOptions.NODE_COLOR_CLASS
    }

    fun isFilterAccessPublicChecked(): Boolean {
        return this.filterAccessPublicCheckbox.isSelected
    }

    fun isFilterAccessProtectedChecked(): Boolean {
        return this.filterAccessProtectedCheckbox.isSelected
    }

    fun isFilterAccessPackageLocalChecked(): Boolean {
        return this.filterAccessPackageLocalCheckbox.isSelected
    }

    fun isFilterAccessPrivateChecked(): Boolean {
        return this.filterAccessPrivateCheckbox.isSelected
    }

    fun run(buildType: CanvasConfig.BuildType) {
        val project = Utils.getActiveProject()
        if (project != null) {
            Utils.runBackgroundTask(project, Runnable {
                // set up the config object
                val canvasConfig = CanvasConfig(project, buildType, this.canvas)
                canvasConfig.selectedModuleName =
                        this@CallGraphToolWindow.moduleScopeComboBox.selectedItem as String? ?: ""
                canvasConfig.selectedDirectoryPath = this@CallGraphToolWindow.directoryScopeTextField.text
                canvasConfig.focusedMethods = this@CallGraphToolWindow.focusedMethods
                canvasConfig.callGraphToolWindow = this@CallGraphToolWindow
                // start building graph
                setupUiBeforeRun(canvasConfig)
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

    private fun fitGraphToViewButtonHandler() = this.canvas.fitCanvasToView()

    private fun fitGraphToBestRatioButtonHandler() = this.canvas.fitCanvasToBestRatio()

    private fun gridSizeButtonHandler(isXGrid: Boolean, isIncrease: Boolean) {
        val zoomFactor = if (isIncrease) 1.25f else 1 / 1.25f
        val xZoomFactor = if (isXGrid) zoomFactor else 1.0f
        val yZoomFactor = if (isXGrid) 1.0f else zoomFactor
        val zoomCenter = Point2D.Float(
                0.5f * this.canvasPanel.width.toFloat(),
                0.5f * this.canvasPanel.height.toFloat()
        )
        this.canvas.zoomAtPoint(zoomCenter, xZoomFactor, yZoomFactor)
    }

    private fun viewSourceCodeHandler() {
        this.focusedMethods.forEach { EditorHelper.openInEditor(it) }
    }

    private fun setupUiBeforeRun(canvasConfig: CanvasConfig) {
        // focus on the 'graph tab
        this.mainTabbedPanel.getComponentAt(1).isEnabled = true
        this.mainTabbedPanel.selectedIndex = 1
        // stats label
        this.statsLabel.text = "..."
        // build-type label
        val buildTypeText = canvasConfig.buildType.label
        when (canvasConfig.buildType) {
            CanvasConfig.BuildType.WHOLE_PROJECT_WITH_TEST_LIMITED,
            CanvasConfig.BuildType.WHOLE_PROJECT_WITH_TEST,
            CanvasConfig.BuildType.WHOLE_PROJECT_WITHOUT_TEST_LIMITED,
            CanvasConfig.BuildType.WHOLE_PROJECT_WITHOUT_TEST -> this.buildTypeLabel.text = buildTypeText
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
                this.buildTypeLabel.text = "<html>$buildTypeText of function <b>$functionNames</b></html>"
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
        this.filterAccessCheckboxes.forEach {
            it.isEnabled = false
            it.isSelected = true
        }
        // progress bar
        this.loadingProgressBar.isVisible = true
        // clear the canvas panel, ready for new graph
        this.canvasPanel.removeAll()
    }

    private fun setupUiAfterRun() {
        // show the rendered canvas
        this.canvas.canvasPanel = this.canvasPanel
        this.canvasPanel.add(this.canvas)
        this.canvasPanel.updateUI()
        // stats label
        this.statsLabel.text = "${this.canvas.getNodesCount()} methods"
        // hide progress bar
        this.loadingProgressBar.isVisible = false
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
        this.filterAccessCheckboxes.forEach { it.isEnabled = true }
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
        val isEnabled = this.focusedMethods.isNotEmpty()
        this.showOnlyUpstreamButton.isEnabled = isEnabled
        this.showOnlyDownstreamButton.isEnabled = isEnabled
        this.showOnlyUpstreamDownstreamButton.isEnabled = isEnabled
        this.viewSourceCodeButton.isEnabled = isEnabled
    }
}
