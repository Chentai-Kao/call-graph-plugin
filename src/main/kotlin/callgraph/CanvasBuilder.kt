package callgraph

import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressIndicatorProvider
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiMethod

class CanvasBuilder {
    private var progressIndicator: ProgressIndicator? = null
    private var fileModifiedTimeCache = mapOf<PsiFile, Long>()
    private var dependenciesCache = emptySet<Dependency>()

    fun build(canvasConfig: CanvasConfig) {
        // cancel existing progress if any
        this.progressIndicator?.cancel()
        this.progressIndicator = ProgressIndicatorProvider.getGlobalProgressIndicator()

        // build a dependency snapshot for the entire code base
        val dependencies = getDependencies(canvasConfig, this.dependenciesCache, this.fileModifiedTimeCache)

        // visualize the viewing part as graph
        val sourceCodeRoots = Utils.getSourceCodeRoots(canvasConfig)
        val files = Utils.getSourceCodeFiles(canvasConfig.project, sourceCodeRoots)
        val methods = Utils.getMethodsInScope(canvasConfig, files)
        val dependencyView = Utils.getDependencyView(canvasConfig, methods, dependencies)
        val graph = buildGraph(methods, dependencyView)
        canvasConfig.canvas.reset(graph)
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

    private fun getDependencies(
            canvasConfig: CanvasConfig,
            dependenciesCache: Set<Dependency>,
            fileModifiedTimeCache: Map<PsiFile, Long>
    ): Set<Dependency> {
        val allFiles = Utils.getAllSourceCodeFiles(canvasConfig.project)
        val newFiles = allFiles.filter { !fileModifiedTimeCache.containsKey(it) }
        val changedFiles = allFiles
                .filter { fileModifiedTimeCache.containsKey(it) && fileModifiedTimeCache[it] != it.modificationStamp }
                .toSet()
        val validDependencies = dependenciesCache
                .filter {
                    !changedFiles.contains(it.caller.containingFile) && !changedFiles.contains(it.callee.containingFile)
                }
                .toSet()
        val invalidFiles = dependenciesCache
                .filter { !validDependencies.contains(it) }
                .flatMap { listOf(it.caller.containingFile, it.callee.containingFile) }
                .toSet()
        val filesToParse = newFiles.union(invalidFiles)
        val methodsToParse = Utils.getMethodsFromFiles(filesToParse)

        // parse method dependencies
        canvasConfig.callGraphToolWindow.resetProgressBar(methodsToParse.size)
        val newDependencies = methodsToParse
                .flatMap {
                    canvasConfig.callGraphToolWindow.incrementProgressBar()
                    Utils.getDependenciesFromMethod(it)
                }
                .toSet()
        val dependencies = validDependencies.union(newDependencies)

        // cache the dependencies for next use
        this.dependenciesCache = dependencies
        this.fileModifiedTimeCache = allFiles.associateBy({ it }, { it.modificationStamp })

        return dependencies
    }
}
