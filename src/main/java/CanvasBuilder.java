import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressIndicatorProvider;
import com.intellij.psi.PsiMethod;
import org.jetbrains.annotations.NotNull;

import java.util.AbstractMap;
import java.util.Set;

class CanvasBuilder {
    private ProgressIndicator progressIndicator;

    @NotNull
    Canvas build(@NotNull CanvasConfig canvasConfig) {
        // cancel existing progress if any
        if (this.progressIndicator != null) {
            this.progressIndicator.cancel();
        }
        this.progressIndicator = ProgressIndicatorProvider.getGlobalProgressIndicator();

        // build a dependency snapshot for the entire code base
        Set<AbstractMap.SimpleEntry<PsiMethod, PsiMethod>> dependencySnapshot =
                Utils.getDependencySnapshot(canvasConfig);

        // visualize the viewing part as graph
        Set<PsiMethod> methods = Utils.getMethodsInScope(canvasConfig);
        Set<AbstractMap.SimpleEntry<PsiMethod, PsiMethod>> dependencyView =
                Utils.getDependencyView(canvasConfig, methods, dependencySnapshot);
        return visualizeGraph(methods, dependencyView);
    }

    @NotNull
    private Canvas visualizeGraph(
            @NotNull Set<PsiMethod> methods,
            @NotNull Set<AbstractMap.SimpleEntry<PsiMethod, PsiMethod>> dependencyView) {
        Graph graph = new Graph();
        methods.forEach(graph::addNode);
        dependencyView.forEach(pair -> {
            PsiMethod caller = pair.getKey();
            PsiMethod callee = pair.getValue();
            graph.addNode(caller);
            graph.addNode(callee);
            graph.addEdge(caller, callee);
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
