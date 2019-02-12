import org.jetbrains.annotations.NotNull;

class Edge {
    private String id;
    private Node sourceNode;
    private Node targetNode;

    Edge(@NotNull String edgeId, @NotNull Node sourceNode, @NotNull Node targetNode) {
        this.id = edgeId;
        this.sourceNode = sourceNode;
        this.targetNode = targetNode;
    }

    @NotNull
    String getId() {
        return this.id;
    }

    @NotNull
    Node getSourceNode() {
        return this.sourceNode;
    }

    @NotNull
    Node getTargetNode() {
        return this.targetNode;
    }
}
