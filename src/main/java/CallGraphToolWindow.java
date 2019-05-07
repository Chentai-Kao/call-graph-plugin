import com.intellij.ide.util.EditorHelper;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiMethod;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.geom.Point2D;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@SuppressWarnings("WeakerAccess")
public class CallGraphToolWindow {
    private JButton runButton;
    private JPanel callGraphToolWindowContent;
    private JPanel canvasPanel;
    private JRadioButton projectScopeButton;
    private JRadioButton moduleScopeButton;
    private JRadioButton directoryScopeButton;
    private JTextField directoryScopeTextField;
    private JComboBox<String> moduleScopeComboBox;
    private JTabbedPane mainTabbedPanel;
    private JCheckBox includeTestFilesCheckBox;
    private JLabel buildTypeLabel;
    private JProgressBar loadingProgressBar;
    private JButton showOnlyUpstreamButton;
    private JButton showOnlyDownstreamButton;
    private JButton showOnlyUpstreamDownstreamButton;
    private JCheckBox upstreamDownstreamScopeCheckbox;
    private JButton fitGraphToViewButton;
    private JButton fitGraphToBestRatioButton;
    private JButton increaseXGridButton;
    private JButton decreaseXGridButton;
    private JButton increaseYGridButton;
    private JButton decreaseYGridButton;
    private JLabel statsLabel;
    private JButton viewSourceCodeButton;
    private JComboBox<String> viewPackageNameComboBox;
    private JComboBox<String> viewFilePathComboBox;
    private JComboBox<String> nodeSelectionComboBox;
    private JTextField searchTextField;

    private final CanvasBuilder canvasBuilder = new CanvasBuilder();
    private Canvas canvas;
    private final Set<PsiMethod> focusedMethods = new HashSet<>();

    public CallGraphToolWindow() {
        // drop-down options
        List<ComboBoxOptions> viewComboBoxOptions =
                Arrays.asList(ComboBoxOptions.VIEW_ALWAYS, ComboBoxOptions.VIEW_HOVERED, ComboBoxOptions.VIEW_NEVER);
        viewComboBoxOptions.forEach(option -> this.viewPackageNameComboBox.addItem(option.getText()));
        this.viewPackageNameComboBox.setSelectedItem(ComboBoxOptions.VIEW_HOVERED.getText());
        viewComboBoxOptions.forEach(option -> this.viewFilePathComboBox.addItem(option.getText()));
        this.viewFilePathComboBox.setSelectedItem(ComboBoxOptions.VIEW_NEVER.getText());
        List<ComboBoxOptions> nodeSelectionComboBoxOptions =
                Arrays.asList(ComboBoxOptions.NODE_SELECTION_SINGLE, ComboBoxOptions.NODE_SELECTION_MULTIPLE);
        nodeSelectionComboBoxOptions.forEach(option -> this.nodeSelectionComboBox.addItem(option.getText()));
        this.nodeSelectionComboBox.setSelectedItem(ComboBoxOptions.NODE_SELECTION_SINGLE.getText());

        // search field
        this.searchTextField.addKeyListener(new KeyListener() {
            @Override
            public void keyTyped(KeyEvent keyEvent) {
            }

            @Override
            public void keyPressed(KeyEvent keyEvent) {
            }

            @Override
            public void keyReleased(KeyEvent keyEvent) {
                canvas.repaint();
            }
        });

        // click handlers for buttons
        this.projectScopeButton.addActionListener(e -> projectScopeButtonHandler());
        this.moduleScopeButton.addActionListener(e -> moduleScopeButtonHandler());
        this.directoryScopeButton.addActionListener(e -> directoryScopeButtonHandler());
        this.runButton.addActionListener(e -> {
            this.focusedMethods.clear();
            run(getSelectedBuildType());
        });
        this.viewPackageNameComboBox.addActionListener(e -> this.canvas.repaint());
        this.viewFilePathComboBox.addActionListener(e -> this.canvas.repaint());
        this.showOnlyUpstreamButton.addActionListener(e -> run(CanvasConfig.BuildType.UPSTREAM));
        this.showOnlyDownstreamButton.addActionListener(e -> run(CanvasConfig.BuildType.DOWNSTREAM));
        this.showOnlyUpstreamDownstreamButton.addActionListener(e -> run(CanvasConfig.BuildType.UPSTREAM_DOWNSTREAM));
        this.viewSourceCodeButton.addActionListener(e -> viewSourceCodeHandler());
        this.fitGraphToViewButton.addActionListener(e -> fitGraphToViewButtonHandler());
        this.fitGraphToBestRatioButton.addActionListener(e -> fitGraphToBestRatioButtonHandler());
        this.increaseXGridButton.addActionListener(e -> gridSizeButtonHandler(true, true));
        this.decreaseXGridButton.addActionListener(e -> gridSizeButtonHandler(true, false));
        this.increaseYGridButton.addActionListener(e -> gridSizeButtonHandler(false, true));
        this.decreaseYGridButton.addActionListener(e -> gridSizeButtonHandler(false, false));
    }

    @SuppressWarnings("WeakerAccess")
    @NotNull
    public JPanel getContent() {
        return this.callGraphToolWindowContent;
    }

    boolean isFocusedMethod(@NotNull PsiMethod method) {
        return this.focusedMethods.contains(method);
    }

    @NotNull
    CallGraphToolWindow toggleFocusedMethod(@NotNull PsiMethod method) {
        if (this.focusedMethods.contains(method)) {
            // clicked on a selected node
            this.focusedMethods.remove(method);
        } else {
            // clicked on an un-selected node
            if (getSelectedComboBoxOption(this.nodeSelectionComboBox) == ComboBoxOptions.NODE_SELECTION_SINGLE) {
                this.focusedMethods.clear();
            }
            this.focusedMethods.add(method);
        }
        enableFocusedMethodButtons();
        return this;
    }

    @NotNull
    CallGraphToolWindow clearFocusedMethods() {
        this.focusedMethods.clear();
        enableFocusedMethodButtons();
        return this;
    }

    void resetProgressBar(int maximum) {
        this.loadingProgressBar.setIndeterminate(false);
        this.loadingProgressBar.setMaximum(maximum);
        this.loadingProgressBar.setValue(0);
    }

    void incrementProgressBar() {
        int newValue = this.loadingProgressBar.getValue() + 1;
        this.loadingProgressBar.setValue(newValue);
        String text = this.loadingProgressBar.isIndeterminate() ?
                String.format("%d functions processed", newValue) :
                String.format("%d functions processed (total %d)", newValue, this.loadingProgressBar.getMaximum());
        this.loadingProgressBar.setString(text);
    }

    boolean isRenderFunctionPackageName(boolean isNodeHovered) {
        ComboBoxOptions option = getSelectedComboBoxOption(this.viewPackageNameComboBox);
        return option == ComboBoxOptions.VIEW_ALWAYS || (option == ComboBoxOptions.VIEW_HOVERED && isNodeHovered);
    }

    boolean isRenderFunctionFilePath(boolean isNodeHovered) {
        ComboBoxOptions option = getSelectedComboBoxOption(this.viewFilePathComboBox);
        return option == ComboBoxOptions.VIEW_ALWAYS || (option == ComboBoxOptions.VIEW_HOVERED && isNodeHovered);
    }

    boolean isQueried(@NotNull String text) {
        String searchQuery = this.searchTextField.getText().toLowerCase();
        return !searchQuery.isEmpty() && text.toLowerCase().contains(searchQuery);
    }

    void run(@NotNull CanvasConfig.BuildType buildType) {
        Project project = Utils.getActiveProject();
        if (project != null) {
            Utils.runBackgroundTask(project, () -> {
                // set up the config object
                String maybeModuleName = (String) this.moduleScopeComboBox.getSelectedItem();
                CanvasConfig canvasConfig = new CanvasConfig()
                        .setProject(project)
                        .setBuildType(buildType)
                        .setSelectedModuleName(maybeModuleName == null ? "" : maybeModuleName)
                        .setSelectedDirectoryPath(this.directoryScopeTextField.getText())
                        .setFocusedMethods(this.focusedMethods)
                        .setCallGraphToolWindow(this);
                // start building graph
                setupUiBeforeRun(canvasConfig);
                this.canvas = this.canvasBuilder.build(canvasConfig);
                setupUiAfterRun();
            });
        }
    }

    @NotNull
    private ComboBoxOptions getSelectedComboBoxOption(@NotNull JComboBox<String> comboBox) {
        String selectedText = (String) comboBox.getSelectedItem();
        if (selectedText == null) {
            return ComboBoxOptions.DUMMY;
        }
        return ComboBoxOptions.findByText(selectedText);
    }

    private void disableAllSecondaryOptions() {
        this.includeTestFilesCheckBox.setEnabled(false);
        this.moduleScopeComboBox.setEnabled(false);
        this.directoryScopeTextField.setEnabled(false);
    }

    private void projectScopeButtonHandler() {
        disableAllSecondaryOptions();
        this.includeTestFilesCheckBox.setEnabled(true);
    }

    private void moduleScopeButtonHandler() {
        Project project = Utils.getActiveProject();
        if (project != null) {
            // set up modules drop-down
            this.moduleScopeComboBox.removeAllItems();
            Utils.getActiveModules(project)
                    .forEach(module -> this.moduleScopeComboBox.addItem(module.getName()));
            disableAllSecondaryOptions();
            this.moduleScopeComboBox.setEnabled(true);
        }
    }

    private void directoryScopeButtonHandler() {
        Project project = Utils.getActiveProject();
        if (project != null) {
            // set up directory option text field
            this.directoryScopeTextField.setText(project.getBasePath());
            disableAllSecondaryOptions();
            this.directoryScopeTextField.setEnabled(true);
        }
    }

    private void fitGraphToViewButtonHandler() {
        if (this.canvas != null) {
            this.canvas.fitCanvasToView();
        }
    }

    private void fitGraphToBestRatioButtonHandler() {
        if (this.canvas != null) {
            this.canvas.fitCanvasToBestRatio();
        }
    }

    private void gridSizeButtonHandler(boolean isXGrid, boolean isIncrease) {
        float zoomFactor = isIncrease ? 1.25f : 1 / 1.25f;
        float xZoomFactor = isXGrid ? zoomFactor : 1;
        float yZoomFactor = isXGrid ? 1 : zoomFactor;
        Point2D zoomCenter = new Point2D.Float(
                0.5f * this.canvasPanel.getWidth(),
                0.5f * this.canvasPanel.getHeight()
        );
        this.canvas.zoomAtPoint(zoomCenter, xZoomFactor, yZoomFactor);
    }

    private void viewSourceCodeHandler() {
        this.focusedMethods.forEach(EditorHelper::openInEditor);
    }

    private void setupUiBeforeRun(@NotNull CanvasConfig canvasConfig) {
        // focus on the 'graph tab
        this.mainTabbedPanel.getComponentAt(1).setEnabled(true);
        this.mainTabbedPanel.setSelectedIndex(1);
        // stats label
        this.statsLabel.setText("...");
        // build-type label
        String buildTypeText = canvasConfig.getBuildType().getLabel();
        switch (canvasConfig.getBuildType()) {
            case WHOLE_PROJECT_WITH_TEST_LIMITED:
            case WHOLE_PROJECT_WITH_TEST:
            case WHOLE_PROJECT_WITHOUT_TEST_LIMITED:
            case WHOLE_PROJECT_WITHOUT_TEST:
                this.buildTypeLabel.setText(buildTypeText);
                break;
            case MODULE_LIMITED:
            case MODULE:
                String moduleName = (String) this.moduleScopeComboBox.getSelectedItem();
                this.buildTypeLabel.setText(String.format("<html>Module <b>%s</b></html>", moduleName));
                break;
            case DIRECTORY_LIMITED:
            case DIRECTORY:
                String path = this.directoryScopeTextField.getText();
                this.buildTypeLabel.setText(String.format("<html>Directory <b>%s</b></html>", path));
                break;
            case UPSTREAM:
            case DOWNSTREAM:
            case UPSTREAM_DOWNSTREAM:
                String functionNames = this.focusedMethods.stream()
                        .map(PsiMethod::getName)
                        .collect(Collectors.joining(", "));
                this.buildTypeLabel.setText(String.format("<html>%s of function <b>%s</b></html>",
                        buildTypeText, functionNames));
            default:
                break;
        }
        // disable some checkboxes and buttons
        this.viewPackageNameComboBox.setEnabled(false);
        this.viewFilePathComboBox.setEnabled(false);
        this.nodeSelectionComboBox.setEnabled(false);
        this.fitGraphToBestRatioButton.setEnabled(false);
        this.fitGraphToViewButton.setEnabled(false);
        this.increaseXGridButton.setEnabled(false);
        this.decreaseXGridButton.setEnabled(false);
        this.increaseYGridButton.setEnabled(false);
        this.decreaseYGridButton.setEnabled(false);
        this.viewSourceCodeButton.setEnabled(false);
        this.showOnlyUpstreamButton.setEnabled(false);
        this.showOnlyDownstreamButton.setEnabled(false);
        this.showOnlyUpstreamDownstreamButton.setEnabled(false);
        this.searchTextField.setEnabled(false);
        // progress bar
        this.loadingProgressBar.setVisible(true);
        // clear the canvas panel, ready for new graph
        this.canvasPanel.removeAll();
    }

    private void setupUiAfterRun() {
        // show the rendered canvas
        this.canvas.setCanvasPanel(this.canvasPanel);
        this.canvasPanel.add(this.canvas);
        this.canvasPanel.updateUI();
        // stats label
        this.statsLabel.setText(String.format("%d methods", this.canvas.getNodesCount()));
        // hide progress bar
        this.loadingProgressBar.setVisible(false);
        // enable some checkboxes and buttons
        this.viewPackageNameComboBox.setEnabled(true);
        this.viewFilePathComboBox.setEnabled(true);
        this.nodeSelectionComboBox.setEnabled(true);
        this.fitGraphToBestRatioButton.setEnabled(true);
        this.fitGraphToViewButton.setEnabled(true);
        this.increaseXGridButton.setEnabled(true);
        this.decreaseXGridButton.setEnabled(true);
        this.increaseYGridButton.setEnabled(true);
        this.decreaseYGridButton.setEnabled(true);
        this.searchTextField.setEnabled(true);
        enableFocusedMethodButtons();
    }

    @NotNull
    private CanvasConfig.BuildType getSelectedBuildType() {
        boolean isLimitedScope = this.upstreamDownstreamScopeCheckbox.isSelected();
        if (this.projectScopeButton.isSelected()) {
            if (this.includeTestFilesCheckBox.isSelected()) {
                return isLimitedScope ?
                        CanvasConfig.BuildType.WHOLE_PROJECT_WITH_TEST_LIMITED :
                        CanvasConfig.BuildType.WHOLE_PROJECT_WITH_TEST;
            }
            return isLimitedScope ?
                    CanvasConfig.BuildType.WHOLE_PROJECT_WITHOUT_TEST_LIMITED :
                    CanvasConfig.BuildType.WHOLE_PROJECT_WITHOUT_TEST;
        } else if (this.moduleScopeButton.isSelected()) {
            return isLimitedScope ? CanvasConfig.BuildType.MODULE_LIMITED : CanvasConfig.BuildType.MODULE;
        } else if (this.directoryScopeButton.isSelected()) {
            return isLimitedScope ? CanvasConfig.BuildType.DIRECTORY_LIMITED : CanvasConfig.BuildType.DIRECTORY;
        }
        return CanvasConfig.BuildType.WHOLE_PROJECT_WITH_TEST;
    }

    private void enableFocusedMethodButtons() {
        boolean isEnabled = !this.focusedMethods.isEmpty();
        this.showOnlyUpstreamButton.setEnabled(isEnabled);
        this.showOnlyDownstreamButton.setEnabled(isEnabled);
        this.showOnlyUpstreamDownstreamButton.setEnabled(isEnabled);
        this.viewSourceCodeButton.setEnabled(isEnabled);
    }
}
