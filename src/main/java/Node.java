import com.intellij.psi.PsiMethod;
import org.jetbrains.annotations.NotNull;

import java.awt.geom.Point2D;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

class Node {
    private final String id;
    private final PsiMethod method;
    private final Map<String, Edge> outEdges = new HashMap<>();
    private final Map<String, Edge> inEdges = new HashMap<>();
    private Point2D point;
    private Point2D rawLayoutPoint;

    Node(@NotNull String nodeId, @NotNull PsiMethod method) {
        this.id = nodeId;
        this.method = method;
    }

    @SuppressWarnings("UnusedReturnValue")
    @NotNull
    Node setPoint(@NotNull Point2D point) {
        this.point = point;
        return this;
    }

    @NotNull
    Point2D getPoint() {
        return this.point;
    }

    @SuppressWarnings("UnusedReturnValue")
    @NotNull
    Node setRawLayoutPoint(@NotNull Point2D rawLayoutPoint) {
        this.rawLayoutPoint = rawLayoutPoint;
        return this;
    }

    @NotNull
    Point2D getRawLayoutPoint() {
        return this.rawLayoutPoint;
    }

    void addInEdge(@NotNull Edge edge) {
        if (!this.inEdges.containsKey(edge.getId())) {
            this.inEdges.put(edge.getId(), edge);
        }
    }

    @NotNull
    Map<String, Edge> getInEdges() {
        return this.inEdges;
    }

    void addOutEdge(@NotNull Edge edge) {
        if (!this.outEdges.containsKey(edge.getId())) {
            this.outEdges.put(edge.getId(), edge);
        }
    }

    @NotNull
    Map<String, Edge> getOutEdges() {
        return this.outEdges;
    }

    @NotNull
    Set<Node> getNeighbors() {
        Stream<Node> upstreamNodes = this.inEdges.values().stream().map(Edge::getSourceNode);
        Stream<Node> downstreamNodes = this.outEdges.values().stream().map(Edge::getTargetNode);
        return Stream.concat(upstreamNodes, downstreamNodes).collect(Collectors.toSet());
    }

    @NotNull
    PsiMethod getMethod() {
        return this.method;
    }

    @NotNull
    String getId() {
        return this.id;
    }
}
