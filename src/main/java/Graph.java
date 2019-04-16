import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

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
    Set<Graph> getConnectedComponents() {
        if (this.connectedComponents == null) {
            Set<Node> visitedNodes = new HashSet<>();
            this.connectedComponents = this.getNodes()
                    .stream()
                    .map(node -> dfsFromNodes(new HashSet<>(Collections.singletonList(node)), visitedNodes))
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
    private Set<Node> dfsFromNodes(@NotNull Set<Node> roots, @NotNull Set<Node> visitedNodes) {
        return roots.stream()
                .flatMap(root -> {
                    if (visitedNodes.contains(root)) {
                        return Stream.empty();
                    }
                    visitedNodes.add(root);
                    Set<Node> subTree = dfsFromNodes(root.getNeighbors(), visitedNodes);
                    subTree.add(root);
                    return subTree.stream();
                })
                .collect(Collectors.toSet());
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
