import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.module.impl.scopes.ModulesScope;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.roots.ContentIterator;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.ToolWindowManager;
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

import java.awt.*;
import java.awt.geom.Point2D;
import java.util.List;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static guru.nidi.graphviz.model.Factory.mutGraph;
import static guru.nidi.graphviz.model.Factory.mutNode;
import static java.util.Collections.max;
import static java.util.Collections.min;

class Utils {
    private static final float normalizedGridSize = 0.1f;

    @Nullable
    static Project getActiveProject() {
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
    static List<Module> getActiveModules(@NotNull Project project) {
        return Arrays.asList(ModuleManager.getInstance(project).getModules());
    }

    @NotNull
    static Map<PsiMethod, Set<PsiMethod>> getDependencyFromProject(
            @NotNull CanvasConfig canvasConfig,
            @NotNull Set<PsiMethod> allMethods) {
        canvasConfig.getCallGraphToolWindow().resetDeterminateProgressBar(allMethods.size());
        return allMethods.stream()
                .collect(Collectors.toMap(
                        method -> method,
                        method -> {
                            canvasConfig.getCallGraphToolWindow().incrementProgressBar(1);
                            SearchScope searchScope = getSearchScope(canvasConfig, method);
                            return ReferencesSearch
                                    .search(method, searchScope)
                                    .findAll()
                                    .stream()
                                    .map(reference -> getContainingKnownMethod(reference.getElement(), allMethods))
                                    .filter(Objects::nonNull)
                                    .collect(Collectors.toSet());
                        }
                ));
    }

    @NotNull
    static Map<PsiMethod, Set<PsiMethod>> getDependencyFromMethods(
            @NotNull Set<PsiMethod> methods,
            @NotNull CanvasConfig canvasConfig) {
        canvasConfig.getCallGraphToolWindow().resetIndeterminateProgressBar();
        // upstream mapping of { callee => callers }
        CanvasConfig.BuildType buildType = canvasConfig.getBuildType();
        boolean needsUpstream = buildType == CanvasConfig.BuildType.WHOLE_PROJECT_WITH_TEST ||
                buildType == CanvasConfig.BuildType.WHOLE_PROJECT_WITHOUT_TEST ||
                buildType == CanvasConfig.BuildType.MODULE ||
                buildType == CanvasConfig.BuildType.DIRECTORY ||
                buildType == CanvasConfig.BuildType.UPSTREAM ||
                buildType == CanvasConfig.BuildType.UPSTREAM_DOWNSTREAM;
        Map<PsiMethod, Set<PsiMethod>> upstreamDependency = needsUpstream ?
                getUpstreamDependency(canvasConfig, methods, new HashSet<>()) : Collections.emptyMap();
        // downstream mapping of { callee => callers }
        boolean needsDownstream = buildType == CanvasConfig.BuildType.WHOLE_PROJECT_WITH_TEST ||
                buildType == CanvasConfig.BuildType.WHOLE_PROJECT_WITHOUT_TEST ||
                buildType == CanvasConfig.BuildType.MODULE ||
                buildType == CanvasConfig.BuildType.DIRECTORY ||
                buildType == CanvasConfig.BuildType.DOWNSTREAM ||
                buildType == CanvasConfig.BuildType.UPSTREAM_DOWNSTREAM;
        Map<PsiMethod, Set<PsiMethod>> downstreamDependency = needsDownstream ?
                getDownstreamDependency(canvasConfig, methods) : Collections.emptyMap();
        return Stream
                .concat(upstreamDependency.entrySet().stream(), downstreamDependency.entrySet().stream())
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        Map.Entry::getValue,
                        (left, right) -> Stream.concat(left.stream(), right.stream()).collect(Collectors.toSet())
                ));
    }

    @NotNull
    static Set<PsiMethod> getAllMethodsFromProject(@NotNull CanvasConfig canvasConfig) {
        Set<PsiFile> allFiles = getSourceCodeRoots(canvasConfig)
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
                            .map(file -> PsiManager.getInstance(canvasConfig.getProject()).findFile(file));
                })
                .collect(Collectors.toSet());
        return allFiles.stream()
                .flatMap(psiFile -> Stream.of(((PsiJavaFile) psiFile).getClasses())) // get all classes
                .flatMap(psiClass -> Stream.of(psiClass.getMethods())) // get all methods
                .collect(Collectors.toSet());
    }

    static void layout(@NotNull Graph graph) {
        // get connected components from the graph, and render each part separately
        Set<Map<String, Point2D>> subGraphBlueprints = graph.getConnectedComponents()
                .stream()
                .map(Utils::getLayoutFromGraphViz)
                .map(Utils::normalizeBlueprintGridSize)
                .collect(Collectors.toSet());

        // merge all connected components to a single graph, then adjust node coordinates so they fit in the view
        Map<String, Point2D> mergedBlueprint = Utils.mergeNormalizedLayouts(new ArrayList<>(subGraphBlueprints));
        applyRawLayoutBlueprintToGraph(mergedBlueprint, graph);
        applyLayoutBlueprintToGraph(mergedBlueprint, graph);
    }

    static void runBackgroundTask(@NotNull Project project, @NotNull Runnable runnable) {
        ProgressManager.getInstance()
                .run(new Task.Backgroundable(project, "Call Graph") {
                         public void run(@NotNull ProgressIndicator progressIndicator) {
                             ApplicationManager
                                     .getApplication()
                                     .runReadAction(runnable);
                         }
                     }
                );
    }

    static void applyLayoutBlueprintToGraph(@NotNull Map<String, Point2D> blueprint, @NotNull Graph graph) {
        blueprint.forEach((nodeId, point) -> graph.getNode(nodeId).setPoint(point));
    }

    @NotNull
    static String getMethodPackageName(@NotNull PsiMethod psiMethod) {
        // get class name
        PsiClass psiClass = psiMethod.getContainingClass();
        String className = psiClass != null && psiClass.getQualifiedName() != null ? psiClass.getQualifiedName() : "";
        // get package name
        PsiJavaFile psiJavaFile = (PsiJavaFile) psiMethod.getContainingFile();
        if (psiJavaFile != null) {
            PsiPackageStatement psiPackageStatement = psiJavaFile.getPackageStatement();
            if (psiPackageStatement != null) {
                String packageName = psiPackageStatement.getPackageName();
                return className.startsWith(packageName) ? className : String.format("%s.%s", packageName, className);
            }
        }
        // no package, just return class name
        return className;
    }

    @NotNull
    static String getMethodFilePath(@NotNull PsiMethod method) {
        PsiFile psiFile = PsiTreeUtil.getParentOfType(method, PsiFile.class);
        if (psiFile != null) {
            VirtualFile currentFile = psiFile.getVirtualFile();
            Project project = getActiveProject();
            if (project != null) {
                VirtualFile rootFile = ProjectFileIndex.SERVICE.getInstance(project).getContentRootForFile(currentFile);
                if (rootFile != null) {
                    String relativePath = VfsUtilCore.getRelativePath(currentFile, rootFile);
                    if (relativePath != null) {
                        return relativePath;
                    }
                }
            }
        }
        return "";
    }

    @NotNull
    static String getMethodSignature(@NotNull PsiMethod method) {
        String parameterNames = Stream.of(method.getParameterList().getParameters())
                .map(PsiNamedElement::getName)
                .collect(Collectors.joining(", "));
        String parameters = parameterNames.isEmpty() ? "" : String.format("(%s)", parameterNames);
        return String.format("%s%s", method.getName(), parameters);
    }

    @NotNull
    static Map<String, Point2D> fitLayoutToViewport(@NotNull Map<String, Point2D> blueprint) {
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
        return blueprint.entrySet()
                .stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        entry -> new Point2D.Double(
                                (entry.getValue().getX() - minPoint.getX()) / xSize * bestFitSize + bestFitBaseline,
                                (entry.getValue().getY() - minPoint.getY()) / ySize * bestFitSize + bestFitBaseline
                        )
                ));
    }

    private static void applyRawLayoutBlueprintToGraph(@NotNull Map<String, Point2D> blueprint, @NotNull Graph graph) {
        blueprint.forEach((nodeId, point) -> graph.getNode(nodeId).setRawLayoutPoint(point));
    }

    @NotNull
    private static Map<String, Point2D> normalizeBlueprintGridSize(@NotNull Map<String, Point2D> blueprint) {
        if (blueprint.size() < 2) {
            return blueprint;
        }
        Point2D gridSize = getGridSize(blueprint);
        Point2D desiredGridSize = new Point2D.Float(normalizedGridSize, normalizedGridSize);
        float xFactor = gridSize.getX() == 0 ? 1.0f : (float) (desiredGridSize.getX() / gridSize.getX());
        float yFactor = gridSize.getY() == 0 ? 1.0f : (float) (desiredGridSize.getY() / gridSize.getY());
        return blueprint.entrySet()
                .stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        entry -> new Point2D.Float(
                                (float) entry.getValue().getX() * xFactor,
                                (float) entry.getValue().getY() * yFactor)
                ));
    }

    @NotNull
    private static Map<String, Point2D> mergeNormalizedLayouts(@NotNull List<Map<String, Point2D>> blueprints) {
        if (blueprints.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<Map<String, Point2D>, Float> blueprintHeights = blueprints.stream()
                .collect(Collectors.toMap(
                        blueprint -> blueprint,
                        blueprint -> {
                            Set<Float> yPoints = blueprint.values()
                                    .stream()
                                    .map(point -> (float) point.getY())
                                    .collect(Collectors.toSet());
                            // set padding to the average y grid size of the previous sub-graph (but minimum 0.1)
                            return max(yPoints) - min(yPoints) + normalizedGridSize;
                        })
                );
        List<Map<String, Point2D>> sortedBlueprints = blueprintHeights.entrySet()
                .stream()
                .sorted(Comparator.comparing(entry -> -entry.getValue()))
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());
        List<Float> sortedHeights = blueprintHeights.values()
                .stream()
                .sorted(Comparator.comparing(height -> -height))
                .collect(Collectors.toList());
        float xBaseline = 0.5f;
        float yBaseline = 0.5f;
        // put the left-most point of the first sub-graph in the view center, by using its y value as central line
        float yCentralLine = sortedBlueprints.get(0).values()
                .stream()
                .min(Comparator.comparing(Point2D::getX))
                .map(Point2D::getY)
                .orElse(0.0)
                .floatValue();
        return IntStream.range(0, sortedBlueprints.size())
                .mapToObj(index -> {
                    // calculate the y-offset of this sub-graph (by summing up all the height of previous sub-graphs)
                    float yOffset = (float) sortedHeights.subList(0, index)
                            .stream()
                            .mapToDouble(Float::doubleValue)
                            .sum();
                    // left align the graph by the left-most nodes, then centering the baseline
                    Map<String, Point2D> blueprint = sortedBlueprints.get(index);
                    float minX = (float) blueprint.values()
                            .stream()
                            .map(Point2D::getX)
                            .mapToDouble(Double::doubleValue)
                            .min()
                            .orElse(0);
                    //noinspection UnnecessaryLocalVariable
                    Map<String, Point2D> shiftedBlueprint = blueprint.entrySet()
                            .stream()
                            .collect(Collectors.toMap(
                                    Map.Entry::getKey,
                                    entry -> new Point2D.Float(
                                            (float) entry.getValue().getX() - minX + xBaseline,
                                            (float) entry.getValue().getY() + yOffset - yCentralLine + yBaseline
                                    )
                            ));
                    return shiftedBlueprint;
                })
                .reduce((blueprintA, blueprintB) ->
                        Stream.concat(blueprintA.entrySet().stream(), blueprintB.entrySet().stream())
                                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue))
                )
                .orElse(new HashMap<>());
    }

    @NotNull
    private static Map<PsiMethod, Set<PsiMethod>> getUpstreamDependency(
            @NotNull CanvasConfig canvasConfig,
            @NotNull Set<PsiMethod> methods,
            @NotNull Set<PsiMethod> seenMethods) {
        if (methods.isEmpty()) {
            return Collections.emptyMap();
        }
        canvasConfig.getCallGraphToolWindow().incrementProgressBar(methods.size());
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
        Map<PsiMethod, Set<PsiMethod>> indirectUpstream = getUpstreamDependency(canvasConfig, parents, seenMethods);
        return Stream.concat(directUpstream.entrySet().stream(), indirectUpstream.entrySet().stream())
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        Map.Entry::getValue,
                        (left, right) -> Stream.concat(left.stream(), right.stream()).collect(Collectors.toSet())
                ));
    }

    @NotNull
    private static Map<PsiMethod, Set<PsiMethod>> getDownstreamDependency(
            @NotNull CanvasConfig canvasConfig,
            @NotNull Set<PsiMethod> methods) {
        // downstream mapping of { caller => callees }
        Map<PsiMethod, Set<PsiMethod>> downstreamMethodCalleesMap =
                getDownstreamMethodCalleesMap(canvasConfig, methods, new HashSet<>());
        // reverse the key value relation of downstream mapping from { caller => callees } to { callee => callers }
        return downstreamMethodCalleesMap.entrySet()
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
    }

    @NotNull
    private static Map<PsiMethod, Set<PsiMethod>> getDownstreamMethodCalleesMap(
            @NotNull CanvasConfig canvasConfig,
            @NotNull Set<PsiMethod> methods,
            @NotNull Set<PsiMethod> seenMethods) {
        if (methods.isEmpty()) {
            return Collections.emptyMap();
        }
        canvasConfig.getCallGraphToolWindow().incrementProgressBar(methods.size());
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
        Map<PsiMethod, Set<PsiMethod>> indirectDownstream =
                getDownstreamMethodCalleesMap(canvasConfig, children, seenMethods);
        return Stream.concat(directDownstream.entrySet().stream(), indirectDownstream.entrySet().stream())
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        Map.Entry::getValue,
                        (left, right) -> Stream.concat(left.stream(), right.stream()).collect(Collectors.toSet())
                ));
    }

    @Nullable
    private static PsiMethod getContainingKnownMethod(
            @NotNull PsiElement psiElement,
            @NotNull Set<PsiMethod> knownMethods) {
        PsiMethod parent = PsiTreeUtil.getParentOfType(psiElement, PsiMethod.class);
        if (parent == null) {
            return null;
        }
        return knownMethods.contains(parent) ? parent : getContainingKnownMethod(parent, knownMethods);
    }

    @NotNull
    private static Point2D getGridSize(@NotNull Map<String, Point2D> blueprint) {
        float precisionFactor = 1000;
        Set<Long> xUniqueValues = blueprint.values()
                .stream()
                .map(point -> Math.round(precisionFactor * point.getX()))
                .collect(Collectors.toSet());
        Set<Long> yUniqueValues = blueprint.values()
                .stream()
                .map(point -> Math.round(precisionFactor * point.getY()))
                .collect(Collectors.toSet());
        return new Point2D.Float(
                getAverageElementDifference(xUniqueValues) / precisionFactor,
                getAverageElementDifference(yUniqueValues) / precisionFactor
        );
    }

    private static float getAverageElementDifference(@NotNull Set<Long> elements) {
        return elements.size() < 2 ? 0 : (max(elements) - min(elements)) / (float) (elements.size() - 1);
    }

    @NotNull
    private static SearchScope getSearchScope(@NotNull CanvasConfig canvasConfig, @NotNull PsiMethod method) {
        switch (canvasConfig.getBuildType()) {
            case WHOLE_PROJECT_WITH_TEST_LIMITED:
                return GlobalSearchScope.allScope(PsiUtilCore.getProjectInReadAction(method));
            case WHOLE_PROJECT_WITHOUT_TEST_LIMITED:
                GlobalSearchScope[] modulesScope = getActiveModules(canvasConfig.getProject())
                        .stream()
                        .map(module -> module.getModuleScope(false))
                        .toArray(GlobalSearchScope[]::new);
                return GlobalSearchScope.union(modulesScope);
            case MODULE_LIMITED:
                Set<Module> selectedModules =
                        getSelectedModules(canvasConfig.getProject(), canvasConfig.getSelectedModuleName());
                return new ModulesScope(selectedModules, canvasConfig.getProject());
            case DIRECTORY_LIMITED:
                System.out.println("(getSearchScope) Directory scope not implemented");
                break;
            default:
                break;
        }
        return GlobalSearchScope.allScope(PsiUtilCore.getProjectInReadAction(method));
    }

    @NotNull
    private static Set<Module> getSelectedModules(@NotNull Project project, @NotNull String selectedModuleName) {
        return Utils.getActiveModules(project)
                .stream()
                .filter(module -> module.getName().equals(selectedModuleName))
                .collect(Collectors.toSet());
    }

    @NotNull
    private static Set<VirtualFile> getSourceCodeRoots(@NotNull CanvasConfig canvasConfig) {
        switch (canvasConfig.getBuildType()) {
            case WHOLE_PROJECT_WITH_TEST_LIMITED:
            case WHOLE_PROJECT_WITH_TEST:
                VirtualFile[] contentRoots =
                        ProjectRootManager.getInstance(canvasConfig.getProject()).getContentRoots();
                return new HashSet<>(Arrays.asList(contentRoots));
            case WHOLE_PROJECT_WITHOUT_TEST_LIMITED:
            case WHOLE_PROJECT_WITHOUT_TEST:
                return getActiveModules(canvasConfig.getProject())
                        .stream()
                        .flatMap(module -> Stream.of(ModuleRootManager.getInstance(module).getSourceRoots(false)))
                        .collect(Collectors.toSet());
            case MODULE_LIMITED:
            case MODULE:
                return getSelectedModules(canvasConfig.getProject(), canvasConfig.getSelectedModuleName())
                        .stream()
                        .flatMap(module -> Stream.of(ModuleRootManager.getInstance(module).getSourceRoots()))
                        .collect(Collectors.toSet());
            case DIRECTORY_LIMITED:
            case DIRECTORY:
                String directoryPath = canvasConfig.getSelectedDirectoryPath();
                if (!directoryPath.isEmpty()) {
                    VirtualFile root = LocalFileSystem.getInstance().findFileByPath(directoryPath);
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
    private static Map<String, Point2D> getLayoutFromGraphViz(@NotNull Graph graph) {
        // if graph only has one node, just set its coordinate to (0.5, 0.5), no need to call GraphViz
        if (graph.getNodes().size() == 1) {
            return graph.getNodes()
                    .stream()
                    .collect(Collectors.toMap(Node::getId, entry -> new Point2D.Float(0.5f, 0.5f)));
        }
        // construct the GraphViz graph
        guru.nidi.graphviz.model.MutableGraph gvGraph = mutGraph("test")
                .setDirected(true)
                .graphAttrs()
                .add(RankDir.LEFT_TO_RIGHT);
        List<Node> sortedNodes = graph.getNodes()
                .stream()
                .sorted(Comparator.comparing(n -> n.getMethod().getName()))
                .collect(Collectors.toList());
        sortedNodes.forEach(node -> {
            MutableNode gvNode = mutNode(node.getId());
            List<Node> sortedDownstreamNodes = node.getOutEdges()
                    .values()
                    .stream()
                    .map(Edge::getTargetNode)
                    .sorted(Comparator.comparing(n -> n.getMethod().getName()))
                    .collect(Collectors.toList());
            sortedDownstreamNodes.forEach(downstreamNode -> gvNode.addLink(downstreamNode.getId()));
            gvGraph.add(gvNode);
        });

        // parse the GraphViz layout as a mapping from "node name" to "x-y coordinate (percent of full graph size)"
        // GraphViz doc: https://graphviz.gitlab.io/_pages/doc/info/output.html#d:plain
        String layoutRawText = Graphviz.fromGraph(gvGraph).render(Format.PLAIN).toString();
        String[] lines = layoutRawText.split("\n");
        return Arrays.stream(lines)
                .filter(line -> line.startsWith("node"))
                .map(line -> line.split(" "))
                .collect(Collectors.toMap(
                        parts -> parts[1], // node ID
                        parts -> new Point2D.Float(Float.parseFloat(parts[2]), Float.parseFloat(parts[3])) // (x, y)
                ));
    }

    static void runCallGraphFromAction(
            @NotNull AnActionEvent anActionEvent,
            @NotNull CanvasConfig.BuildType buildType) {
        Project project = anActionEvent.getProject();
        PsiElement psiElement = anActionEvent.getData(CommonDataKeys.PSI_ELEMENT); // get the element under editor caret
        if (project != null && psiElement instanceof PsiMethod) {
            PsiMethod focusedMethod = (PsiMethod) psiElement;
            ToolWindowManager.getInstance(project)
                    .getToolWindow("Call Graph")
                    .activate(() -> ServiceManager.getService(project, CallGraphToolWindowProjectService.class)
                            .getCallGraphToolWindow()
                            .setFocusedMethod(focusedMethod)
                            .run(buildType));
        }
    }

    static void setActionEnabledAndVisibleByContext(@NotNull AnActionEvent anActionEvent) {
        Project project = anActionEvent.getProject();
        PsiElement psiElement = anActionEvent.getData(CommonDataKeys.PSI_ELEMENT);
        boolean isEnabledAndVisible = project != null && psiElement instanceof PsiMethod;
        anActionEvent.getPresentation().setEnabledAndVisible(isEnabledAndVisible);
    }
}
