import com.intellij.psi.PsiMethod;
import org.jetbrains.annotations.NotNull;

import java.awt.geom.Point2D;
import java.util.*;

class Node {
    private final String id;
    private final PsiMethod method;
    private final Map<String, Edge> leavingEdges = new HashMap<>();
    private float x;
    private float y;

    Node(@NotNull String nodeId, @NotNull PsiMethod method) {
        this.id = nodeId;
        this.method = method;
    }

    void setCoordinate(float x, float y) {
        this.x = x;
        this.y = y;
    }

    @NotNull
    Point2D getPoint() {
        return new Point2D.Float(this.x, this.y);
    }

    void addLeavingEdge(@NotNull Edge edge) {
        if (!this.leavingEdges.containsKey(edge.getId())) {
            this.leavingEdges.put(edge.getId(), edge);
        }
    }

    @NotNull
    Map<String, Edge> getLeavingEdges() {
        return this.leavingEdges;
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
