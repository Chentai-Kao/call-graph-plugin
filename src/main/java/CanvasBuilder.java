import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressIndicatorProvider;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiMethod;
import org.jetbrains.annotations.NotNull;

import java.util.HashSet;
import java.util.Set;

class CanvasBuilder {
    private ProgressIndicator progressIndicator;
    private Set<Dependency> dependencySnapshot = new HashSet<>();

    @NotNull
    Canvas build(@NotNull CanvasConfig canvasConfig) {
        // cancel existing progress if any
        if (this.progressIndicator != null) {
            this.progressIndicator.cancel();
        }
        this.progressIndicator = ProgressIndicatorProvider.getGlobalProgressIndicator();

        // build a dependency snapshot for the entire code base
        this.dependencySnapshot = Utils.getDependencySnapshot(canvasConfig, this.dependencySnapshot);

        // visualize the viewing part as graph
        Set<PsiFile> files = Utils.getSourceCodeFiles(canvasConfig);
        Set<PsiMethod> methods = Utils.getMethodsInScope(canvasConfig, files);
        Set<Dependency> dependencyView = Utils.getDependencyView(canvasConfig, methods, this.dependencySnapshot);
        return visualizeGraph(methods, dependencyView);
    }

    @NotNull
    private Canvas visualizeGraph(
            @NotNull Set<PsiMethod> methods,
            @NotNull Set<Dependency> dependencyView) {
        Graph graph = new Graph();
        methods.forEach(graph::addNode);
        dependencyView.forEach(dependency -> {
            graph.addNode(dependency.getCaller());
            graph.addNode(dependency.getCallee());
            graph.addEdge(dependency.getCaller(), dependency.getCallee());
        });
        Utils.layout(graph);
        return renderGraphOnCanvas(graph);
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
