import com.intellij.openapi.application.ApplicationManager;
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

class Utils {
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
                            canvasConfig.getCallGraphToolWindow().incrementDeterminateProgressBar();
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
                getUpstreamDependency(methods, new HashSet<>()) : Collections.emptyMap();
        // downstream mapping of { callee => callers }
        boolean needsDownstream = buildType == CanvasConfig.BuildType.WHOLE_PROJECT_WITH_TEST ||
                buildType == CanvasConfig.BuildType.WHOLE_PROJECT_WITHOUT_TEST ||
                buildType == CanvasConfig.BuildType.MODULE ||
                buildType == CanvasConfig.BuildType.DIRECTORY ||
                buildType == CanvasConfig.BuildType.DOWNSTREAM ||
                buildType == CanvasConfig.BuildType.UPSTREAM_DOWNSTREAM;
        Map<PsiMethod, Set<PsiMethod>> downstreamDependency = needsDownstream ?
                getDownstreamDependency(methods) : Collections.emptyMap();
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
        guru.nidi.graphviz.model.MutableGraph gvGraph = mutGraph("test")
                .setDirected(true)
                .graphAttrs()
                .add(RankDir.LEFT_TO_RIGHT);

        Collection<Node> sortedNodes = getSortedNodes(graph.getNodes());
        sortedNodes.forEach(node -> {
            MutableNode gvNode = mutNode(node.getId());
            Set<Node> neighbors = node.getLeavingEdges()
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
        normalizedBlueprint.forEach((nodeId, point) -> graph.getNode(nodeId).setPoint(point));
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
                return String.format("%s.%s", psiPackageStatement.getPackageName(), className);
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
    private static Map<PsiMethod, Set<PsiMethod>> getUpstreamDependency(
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
        Map<PsiMethod, Set<PsiMethod>> indirectUpstream = getUpstreamDependency(parents, seenMethods);
        return Stream.concat(directUpstream.entrySet().stream(), indirectUpstream.entrySet().stream())
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        Map.Entry::getValue,
                        (left, right) -> Stream.concat(left.stream(), right.stream()).collect(Collectors.toSet())
                ));
    }

    @NotNull
    private static Map<PsiMethod, Set<PsiMethod>> getDownstreamDependency(@NotNull Set<PsiMethod> methods) {
        // downstream mapping of { caller => callees }
        Map<PsiMethod, Set<PsiMethod>> downstreamMethodCalleesMap =
                getDownstreamMethodCalleesMap(methods, new HashSet<>());
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
    private static Map<String, Point2D> normalizeBlueprintGridSize(@NotNull Map<String, Point2D> blueprint) {
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

    private static float getAverageElementDifference(@NotNull Set<Long> elements) {
        List<Long> sortedValues = elements.stream()
                .sorted()
                .collect(Collectors.toList());
        int totalDifference = IntStream.range(0, sortedValues.size() - 1)
                .map(index -> (int) (sortedValues.get(index + 1) - sortedValues.get(index)))
                .sum();
        return (float) totalDifference / elements.size();
    }

    @NotNull
    private static Collection<Node> getSortedNodes(@NotNull Collection<Node> nodes) {
        List<Node> sortedNodes = new ArrayList<>(nodes);
        sortedNodes.sort(Comparator.comparing(node -> node.getMethod().getName()));
        return sortedNodes;
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
}
