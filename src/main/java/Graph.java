import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.stream.Collectors;

class Graph {
    private Map<String, Node> nodes = new HashMap<>();
    private Map<String, Edge> edges = new HashMap<>();
    private Set<Graph> connectedComponents;

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
            sourceNode.addOutEdge(edge);
            targetNode.addInEdge(edge);
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
    Node getNodeByMethod(@NotNull PsiMethod method) {
        String nodeId = getNodeHash(method);
        return this.nodes.get(nodeId);
    }

    @NotNull
    Set<Graph> getConnectedComponents() {
        if (this.connectedComponents == null) {
            Set<Node> visitedNodes = new HashSet<>();
            this.connectedComponents = this.getNodes()
                    .stream()
                    .map(node -> traverseBfs(node, visitedNodes))
                    .filter(component -> !component.isEmpty())
                    .map(component -> {
                        Map<String, Node> componentNodes = this.nodes.entrySet()
                                .stream()
                                .filter(entry -> component.contains(entry.getValue()))
                                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
                        Map<String, Edge> componentEdges = this.edges.entrySet()
                                .stream()
                                .filter(entry ->
                                        component.contains(entry.getValue().getSourceNode()) ||
                                                component.contains(entry.getValue().getTargetNode()))
                                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
                        return new Graph().setNodes(componentNodes).setEdges(componentEdges);
                    })
                    .collect(Collectors.toSet());
        }
        return this.connectedComponents;
    }

    @NotNull
    private Set<Node> traverseBfs(
            @NotNull Node root,
            @NotNull Set<Node> visitedNodes) {
        if (visitedNodes.contains(root)) {
            return Collections.emptySet();
        }
        Set<Node> path = new HashSet<>();
        Set<Node> queue = Collections.singleton(root);
        while (!queue.isEmpty()) {
            visitedNodes.addAll(queue);
            path.addAll(queue);
            queue = queue.stream()
                    .flatMap(node -> node.getNeighbors().stream())
                    .filter(node -> !visitedNodes.contains(node))
                    .collect(Collectors.toSet());
        }
        return path;
    }

    @NotNull
    private String getNodeHash(@NotNull PsiElement element) {
        return Integer.toString(element.hashCode());
    }

    @NotNull
    private String getEdgeHash(@NotNull String sourceNodeId, @NotNull String targetNodeId) {
        return String.format("%s-%s", sourceNodeId, targetNodeId);
    }

    @NotNull
    private Graph setNodes(@NotNull Map<String, Node> nodes) {
        this.nodes = nodes;
        return this;
    }

    @NotNull
    private Graph setEdges(@NotNull Map<String, Edge> edges) {
        this.edges = edges;
        return this;
    }
}
