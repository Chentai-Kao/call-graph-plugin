import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressIndicatorProvider;
import com.intellij.psi.PsiMethod;
import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

class CanvasBuilder {
    private ProgressIndicator progressIndicator;

    @NotNull
    Canvas build(@NotNull CanvasConfig canvasConfig) {
        // cancel existing progress if any
        if (this.progressIndicator != null) {
            this.progressIndicator.cancel();
        }
        this.progressIndicator = ProgressIndicatorProvider.getGlobalProgressIndicator();
        // build the new graph
        switch (canvasConfig.getBuildType()) {
            case WHOLE_PROJECT_WITH_TEST_LIMITED:
            case WHOLE_PROJECT_WITHOUT_TEST_LIMITED:
            case MODULE_LIMITED:
            case DIRECTORY_LIMITED:
                return buildWholeProjectLimitedScope(canvasConfig);
            case WHOLE_PROJECT_WITH_TEST:
            case WHOLE_PROJECT_WITHOUT_TEST:
            case MODULE:
            case DIRECTORY:
                return buildWholeProject(canvasConfig);
            case UPSTREAM:
                // fall through
            case DOWNSTREAM:
                // fall through
            case UPSTREAM_DOWNSTREAM:
                return buildSingleMethod(canvasConfig);
            default:
                break;
        }
        // should not reach here
        throw new RuntimeException();
    }

    @NotNull
    private Canvas buildWholeProjectLimitedScope(@NotNull CanvasConfig canvasConfig) {
        Set<PsiMethod> allMethods = Utils.getAllMethodsFromProject(canvasConfig);
        Map<PsiMethod, Set<PsiMethod>> methodCallersMap = Utils.getDependencyFromProject(canvasConfig, allMethods);
        return visualizeGraph(methodCallersMap);
    }

    @NotNull
    private Canvas buildWholeProject(@NotNull CanvasConfig canvasConfig) {
        Set<PsiMethod> allMethods = Utils.getAllMethodsFromProject(canvasConfig);
        Map<PsiMethod, Set<PsiMethod>> methodCallersMap = Utils.getDependencyFromMethods(allMethods, canvasConfig);
        return visualizeGraph(methodCallersMap);
    }

    @NotNull
    private Canvas buildSingleMethod(@NotNull CanvasConfig canvasConfig) {
        Set<PsiMethod> seedMethods = Stream.of(canvasConfig.getFocusedNode().getMethod()).collect(Collectors.toSet());
        Map<PsiMethod, Set<PsiMethod>> methodCallersMap = Utils.getDependencyFromMethods(seedMethods, canvasConfig);
        return visualizeGraph(methodCallersMap);
    }

    @NotNull
    private Canvas visualizeGraph(@NotNull Map<PsiMethod, Set<PsiMethod>> methodCallersMap) {
        Graph graph = buildGraph(methodCallersMap);
        Utils.layout(graph);
        return renderGraphOnCanvas(graph);
    }

    @NotNull
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

    @NotNull
    private Canvas renderGraphOnCanvas(@NotNull Graph graph) {
        Canvas canvas = new Canvas(graph);
        MouseEventHandler mouseEventHandler = new MouseEventHandler(canvas);
        canvas.addMouseListener(mouseEventHandler);
        canvas.addMouseMotionListener(mouseEventHandler);
        canvas.addMouseWheelListener(mouseEventHandler);
        return canvas;
    }
}
