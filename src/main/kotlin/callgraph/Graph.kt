package callgraph

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiMethod
import java.util.*

data class Graph(
        val nodesMap: MutableMap<String, Node> = mutableMapOf(),
        val edgesMap: MutableMap<String, Edge> = mutableMapOf()) {

    val connectedComponents: Set<Graph> by lazy {
        val visitedNodes = mutableSetOf<Node>()
        this.getNodes()
                .map { traverseBfs(it, visitedNodes) }
                .filter { it.isNotEmpty() }
                .map { component ->
                    val componentNodes = this.nodesMap.filterValues { component.contains(it) }.toMutableMap()
                    val componentEdges = this.edgesMap.filterValues {
                        component.contains(it.sourceNode) || component.contains(it.targetNode)
                    }.toMutableMap()
                    Graph(componentNodes, componentEdges)
                }
                .toSet()
    }

    fun addNode(method: PsiMethod) {
        val nodeId = getNodeHash(method)
        if (!this.nodesMap.containsKey(nodeId)) {
            val node = Node(nodeId, method)
            this.nodesMap[nodeId] = node
        }
    }

    fun addEdge(sourceMethod: PsiMethod, targetMethod: PsiMethod) {
        val sourceNodeId = getNodeHash(sourceMethod)
        val targetNodeId = getNodeHash(targetMethod)
        val edgeId = getEdgeHash(sourceNodeId, targetNodeId)
        if (!this.edgesMap.containsKey(edgeId)) {
            val sourceNode = this.nodesMap.getValue(sourceNodeId)
            val targetNode = this.nodesMap.getValue(targetNodeId)
            val edge = Edge(edgeId, sourceNode, targetNode)
            this.edgesMap[edgeId] = edge
            sourceNode.addOutEdge(edge)
            targetNode.addInEdge(edge)
        }
    }

    fun getNode(nodeId: String): Node {
        return this.nodesMap.getValue(nodeId)
    }

    fun getNodes(): Collection<Node> {
        return this.nodesMap.values
    }

    fun getEdges(): Collection<Edge> {
        return this.edgesMap.values
    }

    private fun traverseBfs(root: Node, visitedNodes: MutableSet<Node>): Set<Node> {
        if (visitedNodes.contains(root)) {
            return Collections.emptySet()
        }
        val path = mutableSetOf<Node>()
        val queue = mutableSetOf(root)
        while (queue.isNotEmpty()) {
            visitedNodes.addAll(queue)
            path.addAll(queue)
            val newQueue = queue
                    .flatMap { it.getNeighbors() }
                    .filter { !visitedNodes.contains(it) }
            queue.clear()
            queue.addAll(newQueue)
        }
        return path
    }

    private fun getNodeHash(element: PsiElement): String {
        return element.hashCode().toString()
    }

    private fun getEdgeHash(sourceNodeId: String, targetNodeId: String): String {
        return "$sourceNodeId-$targetNodeId"
    }
}
