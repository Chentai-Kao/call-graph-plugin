import com.intellij.psi.PsiMethod;
import org.jetbrains.annotations.NotNull;

import java.util.*;

class Node {
    private String id;
    private PsiMethod method;
    private Map<String, Edge> leavingEdges = new HashMap<>();
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

    float getX() {
        return this.x;
    }

    float getY() {
        return this.y;
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
