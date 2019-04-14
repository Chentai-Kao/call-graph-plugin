import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressIndicatorProvider;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiMethod;
import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

class CanvasBuilder {
    private ProgressIndicator progressIndicator;

    Canvas run(@NotNull CanvasConfig canvasConfig) {
        // cancel existing progress if any
        if (this.progressIndicator != null) {
            this.progressIndicator.cancel();
        }
        this.progressIndicator = ProgressIndicatorProvider.getGlobalProgressIndicator();
        switch (canvasConfig.getBuildType()) {
            case WHOLE_PROJECT_WITH_TEST_LIMITED:
            case WHOLE_PROJECT_WITHOUT_TEST_LIMITED:
            case MODULE_LIMITED:
            case DIRECTORY_LIMITED:
                return showGraphForEntireProjectWithLimitedScope(canvasConfig);
            case WHOLE_PROJECT_WITH_TEST:
            case WHOLE_PROJECT_WITHOUT_TEST:
            case MODULE:
            case DIRECTORY:
                return showGraphForEntireProject(canvasConfig);
            case UPSTREAM:
                // fall through
            case DOWNSTREAM:
                // fall through
            case UPSTREAM_DOWNSTREAM:
                return showGraphForSingleMethod(canvasConfig);
            default:
                break;
        }
        // should not reach here
        return new Canvas();
    }

    private Canvas showGraphForEntireProjectWithLimitedScope(@NotNull CanvasConfig canvasConfig) {
        Set<PsiMethod> allMethods = Utils.getAllMethodsForEntireProject(canvasConfig);
        Map<PsiMethod, Set<PsiMethod>> methodCallersMap =
                Utils.getMethodCallersMapForEntireProject(canvasConfig, allMethods);
        return visualizeCallGraph(canvasConfig.getProject(), methodCallersMap);
    }

    private Canvas showGraphForEntireProject(@NotNull CanvasConfig canvasConfig) {
        Set<PsiMethod> allMethods = Utils.getAllMethodsForEntireProject(canvasConfig);
        Map<PsiMethod, Set<PsiMethod>> methodCallersMap = Utils.getMethodCallersMapForMethods(allMethods, canvasConfig);
        return visualizeCallGraph(canvasConfig.getProject(), methodCallersMap);
    }

    private Canvas showGraphForSingleMethod(@NotNull CanvasConfig canvasConfig) {
        Set<PsiMethod> seeds = Stream.of(canvasConfig.getFocusedNode().getMethod()).collect(Collectors.toSet());
        Map<PsiMethod, Set<PsiMethod>> methodCallersMap = Utils.getMethodCallersMapForMethods(seeds, canvasConfig);
        return visualizeCallGraph(canvasConfig.getProject(), methodCallersMap);
    }

    private Canvas visualizeCallGraph(
            @NotNull Project project,
            @NotNull Map<PsiMethod, Set<PsiMethod>> methodCallersMap) {
        Graph graph = buildGraph(methodCallersMap);
        Utils.layoutByGraphViz(graph);
        return renderGraphOnCanvas(graph, project);
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

    private Canvas renderGraphOnCanvas(@NotNull Graph graph, @NotNull Project project) {
        Canvas canvas = new Canvas()
                .setGraph(graph)
                .setProject(project);
        MouseEventHandler mouseEventHandler = new MouseEventHandler(canvas);
        canvas.addMouseListener(mouseEventHandler);
        canvas.addMouseMotionListener(mouseEventHandler);
        canvas.addMouseWheelListener(mouseEventHandler);
        return canvas;
    }
}
