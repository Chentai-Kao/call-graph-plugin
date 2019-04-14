import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.module.impl.scopes.ModulesScope;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressIndicatorProvider;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.roots.ContentIterator;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.SearchScope;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtilCore;
import guru.nidi.graphviz.attribute.RankDir;
import guru.nidi.graphviz.engine.Format;
import guru.nidi.graphviz.engine.Graphviz;
import guru.nidi.graphviz.model.MutableNode;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.Point2D;
import java.util.List;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static guru.nidi.graphviz.model.Factory.mutGraph;
import static guru.nidi.graphviz.model.Factory.mutNode;

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
    private JLabel buildOptionLabel;
    private JProgressBar loadingProgressBar;
    private JButton showOnlyUpstreamButton;
    private JButton showOnlyDownstreamButton;
    private JButton showOnlyUpstreamDownstreamButton;
    private JCheckBox upstreamDownstreamScopeCheckbox;
    private JCheckBox viewPackageNameCheckBox;
    private JCheckBox viewFilePathCheckBox;

    private Canvas canvas;
    private ProgressIndicator progressIndicator;
    private Node clickedNode;
    private enum BuildOption {
        WHOLE_PROJECT_WITH_TEST_LIMITED("Whole project (test files included), limited up/down-stream scope"),
        WHOLE_PROJECT_WITHOUT_TEST_LIMITED("Whole project (test files excluded), limited up/down-stream scope"),
        MODULE_LIMITED("Module, limited up/down-stream scope"),
        DIRECTORY_LIMITED("Directory, limited up/down-stream scope"),
        WHOLE_PROJECT_WITH_TEST("Whole project (test files included)"),
        WHOLE_PROJECT_WITHOUT_TEST("Whole project (test files excluded)"),
        MODULE("Module"),
        DIRECTORY("Directory"),
        UPSTREAM("Only upstream"),
        DOWNSTREAM("Only downstream"),
        UPSTREAM_DOWNSTREAM("Only upstream & downstream");

        private final String label;

        BuildOption(@NotNull String label) {
            this.label = label;
        }

        @NotNull
        public String getLabel() {
            return this.label;
        }
    }

    public CallGraphToolWindow() {
        // click handlers for buttons
        this.runButton.addActionListener(e -> run(getSelectedBuildOption()));
        this.projectScopeButton.addActionListener(e -> projectScopeButtonHandler());
        this.moduleScopeButton.addActionListener(e -> moduleScopeButtonHandler());
        this.directoryScopeButton.addActionListener(e -> directoryScopeButtonHandler());
        this.showOnlyUpstreamButton.addActionListener(e -> run(BuildOption.UPSTREAM));
        this.showOnlyDownstreamButton.addActionListener(e -> run(BuildOption.DOWNSTREAM));
        this.showOnlyUpstreamDownstreamButton.addActionListener(e -> run(BuildOption.UPSTREAM_DOWNSTREAM));
    }

    void disableAllSecondaryOptions() {
        this.includeTestFilesCheckBox.setEnabled(false);
        this.moduleScopeComboBox.setEnabled(false);
        this.directoryScopeTextField.setEnabled(false);
    }

    void projectScopeButtonHandler() {
        disableAllSecondaryOptions();
        this.includeTestFilesCheckBox.setEnabled(true);
    }

    void moduleScopeButtonHandler() {
        Project project = getActiveProject();
        if (project != null) {
            // set up modules drop-down
            this.moduleScopeComboBox.removeAllItems();
            getActiveModules(project)
                    .forEach(module -> this.moduleScopeComboBox.addItem(module.getName()));
            disableAllSecondaryOptions();
            this.moduleScopeComboBox.setEnabled(true);
        }
    }

    void directoryScopeButtonHandler() {
        Project project = getActiveProject();
        if (project != null) {
            // set up directory option text field
            this.directoryScopeTextField.setText(project.getBasePath());
            disableAllSecondaryOptions();
            this.directoryScopeTextField.setEnabled(true);
        }
    }

    public void run(@NotNull BuildOption buildOption) {
        Project project = getActiveProject();
        if (project == null) {
            return;
        }
        // cancel existing progress if any
        if (this.progressIndicator != null) {
            this.progressIndicator.cancel();
        }
        ProgressManager.getInstance().run(
                new Task.Backgroundable(project, "Call Graph") {
                    public void run(@NotNull ProgressIndicator progressIndicator) {
                        ApplicationManager.getApplication().runReadAction(() -> {
                            switch (buildOption) {
                                case WHOLE_PROJECT_WITH_TEST_LIMITED:
                                case WHOLE_PROJECT_WITHOUT_TEST_LIMITED:
                                case MODULE_LIMITED:
                                case DIRECTORY_LIMITED:
                                    showGraphForEntireProjectWithLimitedScope(project, buildOption);
                                    break;
                                case WHOLE_PROJECT_WITH_TEST:
                                case WHOLE_PROJECT_WITHOUT_TEST:
                                case MODULE:
                                case DIRECTORY:
                                    showGraphForEntireProject(project, buildOption);
                                    break;
                                case UPSTREAM:
                                    // fall through
                                case DOWNSTREAM:
                                    // fall through
                                case UPSTREAM_DOWNSTREAM:
                                    showGraphForSingleMethod(project, buildOption);
                                    break;
                                default:
                                    break;
                            }
                        });
                    }
                }
        );
    }

    public void showGraphForEntireProjectWithLimitedScope(
            @NotNull Project project,
            @NotNull BuildOption buildOption) {
        prepareStart();
        Set<PsiMethod> allMethods = getAllMethodsForEntireProject(project, buildOption);
        Map<PsiMethod, Set<PsiMethod>> methodCallersMap =
                getMethodCallersMapForEntireProject(project, buildOption, allMethods);
        visualizeCallGraph(project, methodCallersMap);
        prepareEnd();
    }

    public void showGraphForEntireProject(@NotNull Project project, @NotNull BuildOption buildOption) {
        prepareStart();
        Set<PsiMethod> allMethods = getAllMethodsForEntireProject(project, buildOption);
        Map<PsiMethod, Set<PsiMethod>> methodCallersMap = getMethodCallersMapForMethods(allMethods, buildOption);
        visualizeCallGraph(project, methodCallersMap);
        prepareEnd();
    }

    public void showGraphForSingleMethod(@NotNull Project project, @NotNull BuildOption buildOption) {
        prepareStart();
        Set<PsiMethod> seedMethods = Stream.of(this.clickedNode.getMethod()).collect(Collectors.toSet());
        Map<PsiMethod, Set<PsiMethod>> methodCallersMap = getMethodCallersMapForMethods(seedMethods, buildOption);
        visualizeCallGraph(project, methodCallersMap);
        prepareEnd();
    }

    private void visualizeCallGraph(
            @NotNull Project project,
            @NotNull Map<PsiMethod, Set<PsiMethod>> methodCallersMap) {
        Graph graph = buildGraph(methodCallersMap);
        layoutByGraphViz(graph);
        renderGraphOnCanvas(graph, project);
        attachEventListeners(this.canvas);
    }

    private void prepareStart() {
        this.progressIndicator = ProgressIndicatorProvider.getGlobalProgressIndicator();
        this.mainTabbedPanel.getComponentAt(1).setEnabled(true); // enable the graph tab
        this.mainTabbedPanel.setSelectedIndex(1); // focus on the graph tab
        BuildOption buildOption = getSelectedBuildOption();
        switch (buildOption) {
            case WHOLE_PROJECT_WITH_TEST_LIMITED:
            case WHOLE_PROJECT_WITH_TEST:
            case WHOLE_PROJECT_WITHOUT_TEST_LIMITED:
            case WHOLE_PROJECT_WITHOUT_TEST:
                this.buildOptionLabel.setText(buildOption.getLabel());
                break;
            case MODULE_LIMITED:
            case MODULE:
                String moduleName = (String) this.moduleScopeComboBox.getSelectedItem();
                this.buildOptionLabel.setText(String.format("%s [%s]", buildOption.getLabel(), moduleName));
                break;
            case DIRECTORY_LIMITED:
            case DIRECTORY:
                String path = this.directoryScopeTextField.getText();
                this.buildOptionLabel.setText(String.format("%s [%s]", buildOption.getLabel(), path));
                break;
            case UPSTREAM:
            case DOWNSTREAM:
            case UPSTREAM_DOWNSTREAM:
                this.buildOptionLabel.setText(String.format("%s of function [%s]",
                        buildOption.getLabel(), this.clickedNode.getMethod().getName()));
            default:
                break;
        }
        this.loadingProgressBar.setVisible(true);
        this.showOnlyUpstreamButton.setEnabled(false);
        this.showOnlyDownstreamButton.setEnabled(false);
        this.showOnlyUpstreamDownstreamButton.setEnabled(false);
        this.viewPackageNameCheckBox.setEnabled(false);
        this.viewFilePathCheckBox.setEnabled(false);
        this.canvasPanel.removeAll();
    }

    private void prepareEnd() {
        this.loadingProgressBar.setVisible(false);
        this.viewPackageNameCheckBox.setEnabled(true);
        this.viewPackageNameCheckBox.setSelected(true);
        this.viewFilePathCheckBox.setEnabled(true);
        this.viewFilePathCheckBox.setSelected(false);
    }

    @Nullable
    private Project getActiveProject() {
        Project[] projects = ProjectManager.getInstance().getOpenProjects();
        return Stream.of(projects)
                .filter(project -> {
                    Window window = WindowManager.getInstance().suggestParentWindow(project);
                    return window != null && window.isActive();
                })
                .findFirst()
                .orElse(null);
    }

    @NotNull
    private List<Module> getActiveModules(@NotNull Project project) {
        return Arrays.asList(ModuleManager.getInstance(project).getModules());
    }

    @NotNull
    private Set<VirtualFile> getSourceCodeRoots(@NotNull Project project, @NotNull BuildOption buildOption) {
        switch (buildOption) {
            case WHOLE_PROJECT_WITH_TEST_LIMITED:
            case WHOLE_PROJECT_WITH_TEST:
                VirtualFile[] contentRoots = ProjectRootManager.getInstance(project).getContentRoots();
                return new HashSet<>(Arrays.asList(contentRoots));
            case WHOLE_PROJECT_WITHOUT_TEST_LIMITED:
            case WHOLE_PROJECT_WITHOUT_TEST:
                return getActiveModules(project)
                        .stream()
                        .flatMap(module -> Stream.of(ModuleRootManager.getInstance(module).getSourceRoots(false)))
                        .collect(Collectors.toSet());
            case MODULE_LIMITED:
            case MODULE:
                return getSelectedModules(project)
                        .stream()
                        .flatMap(module -> Stream.of(ModuleRootManager.getInstance(module).getSourceRoots()))
                        .collect(Collectors.toSet());
            case DIRECTORY_LIMITED:
            case DIRECTORY:
                String path = this.directoryScopeTextField.getText();
                if (!path.isEmpty()) {
                    VirtualFile root = LocalFileSystem.getInstance().findFileByPath(path);
                    if (root != null) {
                        return Collections.singleton(root);
                    }
                }
                return Collections.emptySet();
            default:
                break;
        }
        return Collections.emptySet();
    }

    @NotNull
    private Map<PsiMethod, Set<PsiMethod>> getMethodCallersMapForMethods(
            @NotNull Set<PsiMethod> methods,
            @NotNull BuildOption buildOption) {
        resetIndeterminateProgressBar();
        // upstream mapping of { callee => callers }
        boolean needsUpstream = buildOption == BuildOption.WHOLE_PROJECT_WITH_TEST ||
                buildOption == BuildOption.WHOLE_PROJECT_WITHOUT_TEST ||
                buildOption == BuildOption.MODULE ||
                buildOption == BuildOption.DIRECTORY ||
                buildOption == BuildOption.UPSTREAM ||
                buildOption == BuildOption.UPSTREAM_DOWNSTREAM;
        Map<PsiMethod, Set<PsiMethod>> upstreamMethodCallersMap = needsUpstream ?
                getUpstreamMethodCallersMap(methods, new HashSet<>()) :
                Collections.emptyMap();
        // downstream mapping of { caller => callees }
        boolean needsDownstream = buildOption == BuildOption.WHOLE_PROJECT_WITH_TEST ||
                buildOption == BuildOption.WHOLE_PROJECT_WITHOUT_TEST ||
                buildOption == BuildOption.MODULE ||
                buildOption == BuildOption.DIRECTORY ||
                buildOption == BuildOption.DOWNSTREAM ||
                buildOption == BuildOption.UPSTREAM_DOWNSTREAM;
        Map<PsiMethod, Set<PsiMethod>> downstreamMethodCalleesMap = needsDownstream ?
                getDownstreamMethodCalleesMap(methods, new HashSet<>()) :
                Collections.emptyMap();
        // reverse the key value relation of downstream mapping from { caller => callees } to { callee => callers }
        Map<PsiMethod, Set<PsiMethod>> downstreamMethodCallersMap = downstreamMethodCalleesMap.entrySet()
                .stream()
                .flatMap(entry -> {
                    PsiMethod caller = entry.getKey();
                    Set<PsiMethod> callees = entry.getValue();
                    return callees.stream().map(callee ->
                            new AbstractMap.SimpleEntry<>(callee, new HashSet<>(Collections.singletonList(caller))));
                })
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        Map.Entry::getValue,
                        (left, right) -> Stream.concat(left.stream(), right.stream()).collect(Collectors.toSet())
                ));
        return Stream
                .concat(upstreamMethodCallersMap.entrySet().stream(), downstreamMethodCallersMap.entrySet().stream())
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        Map.Entry::getValue,
                        (left, right) -> Stream.concat(left.stream(), right.stream()).collect(Collectors.toSet())
                ));
    }

    @NotNull
    private Map<PsiMethod, Set<PsiMethod>> getUpstreamMethodCallersMap(
            @NotNull Set<PsiMethod> methods,
            @NotNull Set<PsiMethod> seenMethods) {
        if (methods.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<PsiMethod, Set<PsiMethod>> directUpstream = methods.stream()
                .collect(Collectors.toMap(
                        method -> method,
                        method -> {
                            SearchScope searchScope =
                                    GlobalSearchScope.allScope(PsiUtilCore.getProjectInReadAction(method));
                            Collection<PsiReference> references =
                                    ReferencesSearch.search(method, searchScope).findAll();
                            return references.stream()
                                    .map(reference ->
                                            PsiTreeUtil.getParentOfType(reference.getElement(), PsiMethod.class)
                                    )
                                    .filter(Objects::nonNull)
                                    .collect(Collectors.toSet());
                        }
                ));
        seenMethods.addAll(methods);
        Set<PsiMethod> parents = directUpstream.values()
                .stream()
                .flatMap(Collection::stream)
                .filter(parent -> !seenMethods.contains(parent))
                .collect(Collectors.toSet());
        Map<PsiMethod, Set<PsiMethod>> indirectUpstream = getUpstreamMethodCallersMap(parents, seenMethods);
        return Stream.concat(directUpstream.entrySet().stream(), indirectUpstream.entrySet().stream())
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        Map.Entry::getValue,
                        (left, right) -> Stream.concat(left.stream(), right.stream()).collect(Collectors.toSet())
                ));
    }

    @NotNull
    private Map<PsiMethod, Set<PsiMethod>> getDownstreamMethodCalleesMap(
            @NotNull Set<PsiMethod> methods,
            @NotNull Set<PsiMethod> seenMethods) {
        if (methods.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<PsiMethod, Set<PsiMethod>> directDownstream = methods.stream()
                .collect(Collectors.toMap(
                        method -> method,
                        method -> {
                            Collection<PsiIdentifier> identifiers =
                                    PsiTreeUtil.findChildrenOfType(method, PsiIdentifier.class);
                            return identifiers.stream()
                                    .map(PsiElement::getContext)
                                    .filter(Objects::nonNull)
                                    .flatMap(context -> Arrays.stream(context.getReferences()))
                                    .map(PsiReference::resolve)
                                    .filter(psiElement -> psiElement instanceof PsiMethod)
                                    .map(psiElement -> (PsiMethod) psiElement)
                                    .collect(Collectors.toSet());
                        }
                ));
        seenMethods.addAll(methods);
        Set<PsiMethod> children = directDownstream.values()
                .stream()
                .flatMap(Collection::stream)
                .filter(child -> !seenMethods.contains(child))
                .collect(Collectors.toSet());
        Map<PsiMethod, Set<PsiMethod>> indirectDownstream = getDownstreamMethodCalleesMap(children, seenMethods);
        return Stream.concat(directDownstream.entrySet().stream(), indirectDownstream.entrySet().stream())
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        Map.Entry::getValue,
                        (left, right) -> Stream.concat(left.stream(), right.stream()).collect(Collectors.toSet())
                ));
    }

    @NotNull
    private Set<PsiMethod> getAllMethodsForEntireProject(@NotNull Project project, @NotNull BuildOption buildOption) {
        Set<PsiFile> allFiles = getSourceCodeRoots(project, buildOption)
                .stream()
                .flatMap(contentSourceRoot -> {
                    List<VirtualFile> childrenVirtualFiles = new ArrayList<>();
                    ContentIterator contentIterator = child -> {
                        if (child.isValid() && !child.isDirectory()) {
                            String extension = Optional.ofNullable(child.getExtension()).orElse("");
                            if (extension.equals("java")) {
                                childrenVirtualFiles.add(child);
                            }
                        }
                        return true;
                    };
                    VfsUtilCore.iterateChildrenRecursively(contentSourceRoot, null, contentIterator);
                    return childrenVirtualFiles.stream()
                            .map(file -> PsiManager.getInstance(project).findFile(file));
                })
                .collect(Collectors.toSet());
        return allFiles.stream()
                .flatMap(psiFile -> Stream.of(((PsiJavaFile)psiFile).getClasses())) // get all classes
                .flatMap(psiClass -> Stream.of(psiClass.getMethods())) // get all methods
                .collect(Collectors.toSet());
    }

    @NotNull
    private Map<PsiMethod, Set<PsiMethod>> getMethodCallersMapForEntireProject(
            @NotNull Project project,
            @NotNull BuildOption buildOption,
            @NotNull Set<PsiMethod> allMethods) {
        resetDeterminateProgressBar(allMethods.size());
        return allMethods.stream()
                .collect(Collectors.toMap(
                        method -> method,
                        method -> {
                            SearchScope searchScope = getSearchScope(project, method, buildOption);
                            Collection<PsiReference> references =
                                    ReferencesSearch.search(method, searchScope).findAll();
                            incrementDeterminateProgressBar();
                            return references.stream()
                                    .map(reference -> getContainingKnownMethod(reference.getElement(), allMethods))
                                    .filter(Objects::nonNull)
                                    .collect(Collectors.toSet());
                        }
                ));
    }

    private Graph buildGraph(@NotNull Map<PsiMethod, Set<PsiMethod>> methodCallersMap) {
        Graph graph = new Graph();
        methodCallersMap.forEach((callee, callers) -> {
            graph.addNode(callee);
            callers.forEach(caller -> {
                graph.addNode(caller);
                graph.addEdge(caller, callee);
            });
        });
        return graph;
    }

    private void renderGraphOnCanvas(@NotNull Graph graph, @NotNull Project project) {
        this.canvas = new Canvas()
                .setGraph(graph)
                .setCanvasPanel(this.canvasPanel)
                .setProject(project)
                .setCallGraphToolWindow(this);
        this.canvasPanel.add(this.canvas);
        this.canvasPanel.updateUI();
    }

    private void attachEventListeners(@NotNull Canvas canvas) {
        MouseEventHandler mouseEventHandler = new MouseEventHandler();
        mouseEventHandler.init(canvas);
        canvas.addMouseListener(mouseEventHandler);
        canvas.addMouseMotionListener(mouseEventHandler);
        canvas.addMouseWheelListener(mouseEventHandler);
    }

    @Nullable
    private PsiMethod getContainingKnownMethod(@NotNull PsiElement psiElement, @NotNull Set<PsiMethod> knownMethods) {
        PsiMethod parent = PsiTreeUtil.getParentOfType(psiElement, PsiMethod.class);
        if (parent == null) {
            return null;
        }
        return knownMethods.contains(parent) ? parent : getContainingKnownMethod(parent, knownMethods);
    }

    private void layoutByGraphViz(@NotNull Graph graph) {
        guru.nidi.graphviz.model.MutableGraph gvGraph = mutGraph("test")
                .setDirected(true)
                .graphAttrs()
                .add(RankDir.LEFT_TO_RIGHT);

        Collection<Node> sortedNodes = getSortedNodes(graph.getNodes());
        sortedNodes.forEach(node -> {
            MutableNode gvNode = mutNode(node.getId());
            Collection<Node> neighbors = node.getLeavingEdges()
                    .values()
                    .stream()
                    .map(Edge::getTargetNode)
                    .collect(Collectors.toSet());
            Collection<Node> sortedNeighbors = getSortedNodes(neighbors);
            sortedNeighbors.forEach(neighborNode -> gvNode.addLink(neighborNode.getId()));
            gvGraph.add(gvNode);
        });
        String layoutRawText = Graphviz.fromGraph(gvGraph).render(Format.PLAIN).toString();

        // parse the GraphViz layout as a mapping from "node name" to "x-y coordinate (percent of full graph size)"
        // GraphViz doc: https://graphviz.gitlab.io/_pages/doc/info/output.html#d:plain
        List<String> layoutLines = Arrays.asList(layoutRawText.split("\n"));
        Map<String, Point2D> blueprint = layoutLines.stream()
                .filter(line -> line.startsWith("node"))
                .map(line -> line.split(" "))
                .collect(Collectors.toMap(
                        parts -> parts[1],
                        parts -> new Point2D.Float(Float.parseFloat(parts[2]), Float.parseFloat(parts[3]))
                ));

        // adjust node coordinates so they fit in the view
        Point2D maxPoint = blueprint.values()
                .stream()
                .reduce((pointA, pointB) -> new Point2D.Double(
                        Math.max(pointA.getX(), pointB.getX()), Math.max(pointA.getY(), pointB.getY())))
                .orElse(new Point2D.Double(1, 1));
        Point2D minPoint = blueprint.values()
                .stream()
                .reduce((pointA, pointB) -> new Point2D.Double(
                        Math.min(pointA.getX(), pointB.getX()), Math.min(pointA.getY(), pointB.getY())))
                .orElse(new Point2D.Double(0, 0));
        double xSize = maxPoint.getX() - minPoint.getX();
        double ySize = maxPoint.getY() - minPoint.getY();
        double bestFitBaseline = 0.1; // make the best fit window between 0.1 - 0.9 of the viewport
        double bestFitSize = 1 - 2 * bestFitBaseline;
        Map<String, Point2D> viewportBlueprint = blueprint.entrySet()
                .stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        entry -> new Point2D.Double(
                                (entry.getValue().getX() - minPoint.getX()) / xSize * bestFitSize + bestFitBaseline,
                                (entry.getValue().getY() - minPoint.getY()) / ySize * bestFitSize + bestFitBaseline
                        )
                ));

        //  normalize grid size on x and y axis
        Map<String, Point2D> normalizedBlueprint = normalizeBlueprintGridSize(viewportBlueprint);
        normalizedBlueprint.forEach((nodeId, point) ->
                graph.getNode(nodeId).setCoordinate((float) point.getX(), (float) point.getY())
        );
    }

    @NotNull
    private Map<String, Point2D> normalizeBlueprintGridSize(@NotNull Map<String, Point2D> blueprint) {
        float precisionFactor = 1000;
        if (blueprint.size() < 2) {
            return blueprint;
        }
        Set<Long> uniqueValuesX = blueprint.values()
                .stream()
                .map(point -> Math.round(precisionFactor * point.getX()))
                .collect(Collectors.toSet());
        Set<Long> uniqueValuesY = blueprint.values()
                .stream()
                .map(point -> Math.round(precisionFactor * point.getY()))
                .collect(Collectors.toSet());
        // to achieve equal xy grid size, only allow the graph to shrink (not grow) to keep it within the viewport
        float xyRatio = 0.3f * getAverageElementDifference(uniqueValuesX) / getAverageElementDifference(uniqueValuesY);
        float xFactor = Math.min(1, 1 / xyRatio);
        float yFactor = Math.min(1, xyRatio);
        float centralLine = 0.5f;
        return blueprint.entrySet()
                .stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        entry -> new Point2D.Float(
                                (float) (entry.getValue().getX() - centralLine) * xFactor + centralLine,
                                (float) (entry.getValue().getY() - centralLine) * yFactor + centralLine
                        )
                ));
    }

    private float getAverageElementDifference(@NotNull Set<Long> elements) {
        List<Long> uniqueValues = elements.stream()
                .sorted()
                .collect(Collectors.toList());
        int cumulativeDifference = IntStream.range(0, uniqueValues.size() - 1)
                .map(index -> (int) (uniqueValues.get(index + 1) - uniqueValues.get(index)))
                .sum();
        return (float) cumulativeDifference / elements.size();
    }

    @NotNull
    private Collection<Node> getSortedNodes(@NotNull Collection<Node> nodes) {
        List<Node> sortedNodes = new ArrayList<>(nodes);
        sortedNodes.sort(Comparator.comparing(node -> node.getMethod().getName()));
        return sortedNodes;
    }

    @NotNull
    public JPanel getContent() {
        return this.callGraphToolWindowContent;
    }

    @NotNull
    private Set<Module> getSelectedModules(@NotNull Project project) {
        String selectedModuleName = (String) this.moduleScopeComboBox.getSelectedItem();
        return getActiveModules(project)
                .stream()
                .filter(module -> module.getName().equals(selectedModuleName))
                .collect(Collectors.toSet());
    }

    @NotNull
    private SearchScope getSearchScope(
            @NotNull Project project,
            @NotNull PsiMethod method,
            @NotNull BuildOption buildOption) {
        switch (buildOption) {
            case WHOLE_PROJECT_WITH_TEST_LIMITED:
                return GlobalSearchScope.allScope(PsiUtilCore.getProjectInReadAction(method));
            case WHOLE_PROJECT_WITHOUT_TEST_LIMITED:
                GlobalSearchScope[] modulesScope = getActiveModules(project)
                        .stream()
                        .map(module -> module.getModuleScope(false))
                        .toArray(GlobalSearchScope[]::new);
                return GlobalSearchScope.union(modulesScope);
            case MODULE_LIMITED:
                Set<Module> selectedModules = getSelectedModules(project);
                return new ModulesScope(selectedModules, project);
            case DIRECTORY_LIMITED:
                System.out.println("(getSearchScope) Directory scope not implemented");
                break;
            default:
                break;
        }
        return GlobalSearchScope.allScope(PsiUtilCore.getProjectInReadAction(method));
    }

    @NotNull
    private BuildOption getSelectedBuildOption() {
        boolean isLimitedScope = this.upstreamDownstreamScopeCheckbox.isSelected();
        if (this.projectScopeButton.isSelected()) {
            if (this.includeTestFilesCheckBox.isSelected()) {
                return isLimitedScope ?
                        BuildOption.WHOLE_PROJECT_WITH_TEST_LIMITED : BuildOption.WHOLE_PROJECT_WITH_TEST;
            }
            return isLimitedScope ?
                    BuildOption.WHOLE_PROJECT_WITHOUT_TEST_LIMITED : BuildOption.WHOLE_PROJECT_WITHOUT_TEST;
        } else if (this.moduleScopeButton.isSelected()) {
            return isLimitedScope ? BuildOption.MODULE_LIMITED : BuildOption.MODULE;
        } else if (this.directoryScopeButton.isSelected()) {
            return isLimitedScope ? BuildOption.DIRECTORY_LIMITED : BuildOption.DIRECTORY;
        }
        return BuildOption.WHOLE_PROJECT_WITH_TEST;
    }

    public void setClickedNode(@Nullable Node node) {
        this.clickedNode = node;
        boolean isEnabled = node != null;
        this.showOnlyUpstreamButton.setEnabled(isEnabled);
        this.showOnlyDownstreamButton.setEnabled(isEnabled);
        this.showOnlyUpstreamDownstreamButton.setEnabled(isEnabled);
    }

    private void resetIndeterminateProgressBar() {
        this.loadingProgressBar.setIndeterminate(true);
        this.loadingProgressBar.setStringPainted(false);
    }

    private void resetDeterminateProgressBar(int maximum) {
        this.loadingProgressBar.setIndeterminate(false);
        this.loadingProgressBar.setMaximum(maximum);
        this.loadingProgressBar.setValue(0);
        this.loadingProgressBar.setStringPainted(true);
    }

    private void incrementDeterminateProgressBar() {
        int newValue = this.loadingProgressBar.getValue() + 1;
        this.loadingProgressBar.setValue(newValue);
        this.loadingProgressBar.setString(String.format("%d / %d", newValue, this.loadingProgressBar.getMaximum()));
    }

    boolean isRenderFunctionPackageName() {
        return this.viewPackageNameCheckBox.isSelected();
    }

    boolean isRenderFunctionFilePath() {
        return this.viewFilePathCheckBox.isSelected();
    }
}
