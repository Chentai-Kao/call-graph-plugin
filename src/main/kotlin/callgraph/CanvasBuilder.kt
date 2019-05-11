package callgraph

import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressIndicatorProvider
import com.intellij.psi.PsiMethod

class CanvasBuilder {
    private var progressIndicator: ProgressIndicator? = null

    fun build(canvasConfig: CanvasConfig): Canvas {
        // cancel existing progress if any
        this.progressIndicator?.cancel()
        this.progressIndicator = ProgressIndicatorProvider.getGlobalProgressIndicator()

        // build a dependency snapshot for the entire code base
        val dependencies = Utils.getDependencies(canvasConfig)

        // visualize the viewing part as graph
        val files = Utils.getSourceCodeFiles(canvasConfig)
        val methods = Utils.getMethodsInScope(canvasConfig, files)
        val dependencyView = Utils.getDependencyView(canvasConfig, methods, dependencies)
        val graph = buildGraph(methods, dependencyView)
        return renderGraphOnCanvas(canvasConfig.callGraphToolWindow!!, graph)
    }

    private fun buildGraph(methods: Set<PsiMethod>, dependencyView: Set<Dependency>): Graph {
        val graph = Graph()
        methods.forEach { graph.addNode(it) }
        dependencyView.forEach {
            graph.addNode(it.caller)
            graph.addNode(it.callee)
            graph.addEdge(it.caller, it.callee)
        }
        Utils.layout(graph)
        return graph
    }

    private fun renderGraphOnCanvas(callGraphToolWindow: CallGraphToolWindow, graph: Graph): Canvas {
        val canvas = Canvas(callGraphToolWindow, graph)
        val mouseEventHandler = MouseEventHandler(canvas)
        canvas.addMouseListener(mouseEventHandler)
        canvas.addMouseMotionListener(mouseEventHandler)
        canvas.addMouseWheelListener(mouseEventHandler)
        return canvas
    }
}
