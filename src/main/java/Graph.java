import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

class Graph {
    private Map<String, Node> nodes = new HashMap<>();
    private Map<String, Edge> edges = new HashMap<>();

    void addNode(@NotNull PsiMethod method) {
        String nodeId = getNodeHash(method);
        if (!this.nodes.containsKey(nodeId)) {
            Node node = new Node(nodeId, method);
            this.nodes.put(nodeId, node);
        }
    }

    void addEdge(@NotNull PsiMethod sourceMethod, @NotNull PsiMethod targetMethod) {
        String sourceNodeId = getNodeHash(sourceMethod);
        String targetNodeId = getNodeHash(targetMethod);
        String edgeId = getEdgeHash(sourceNodeId, targetNodeId);
        if (!this.edges.containsKey(edgeId)) {
            Node sourceNode = this.nodes.get(sourceNodeId);
            Node targetNode = this.nodes.get(targetNodeId);
            Edge edge = new Edge(edgeId, sourceNode, targetNode);
            this.edges.put(edgeId, edge);
            sourceNode.addLeavingEdge(edge);
        }
    }

    @NotNull
    Node getNode(@NotNull String nodeId) {
        return this.nodes.get(nodeId);
    }

    @NotNull
    Collection<Node> getNodes() {
        return this.nodes.values();
    }

    @NotNull
    Collection<Edge> getEdges() {
        return this.edges.values();
    }

    @NotNull
    private String getNodeHash(@NotNull PsiElement element) {
        return Integer.toString(element.hashCode());
    }

    @NotNull
    private String getEdgeHash(@NotNull String sourceNodeId, @NotNull String targetNodeId) {
        return String.format("%s-%s", sourceNodeId, targetNodeId);
    }
}
