import org.jetbrains.annotations.NotNull;

class Edge {
    private final String id;
    private final Node sourceNode;
    private final Node targetNode;

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
