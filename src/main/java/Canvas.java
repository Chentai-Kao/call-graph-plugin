import com.intellij.ui.JBColor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.Point2D;
import java.util.HashMap;
import java.util.Map;

class Canvas extends JPanel {
    private Graph graph;
    private JPanel canvasPanel;
    private Map<Ellipse2D, Node> nodeShapesMap;
    private Point2D cameraCenter = new Point2D.Float(0, 0);
    private float zoomRatio = 1.0f;

    @NotNull
    Canvas setGraph(@NotNull Graph graph) {
        this.graph = graph;
        return this;
    }

    @NotNull
    Canvas setCanvasPanel(@NotNull JPanel canvasPanel) {
        this.canvasPanel = canvasPanel;
        return this;
    }

    @NotNull
    Canvas setCameraCenter(@NotNull Point2D cameraCenter) {
        this.cameraCenter = cameraCenter;
        return this;
    }

    @NotNull
    Point2D getCameraCenter() {
        return this.cameraCenter;
    }

    @NotNull
    Canvas setZoomRatio(float zoomRatio) {
        this.zoomRatio = zoomRatio;
        return this;
    }

    float getZoomRatio() {
        return this.zoomRatio;
    }

    @Nullable
    Node getNodeUnderPoint(@NotNull Point2D point) {
        return this.nodeShapesMap.entrySet()
                .stream()
                .filter(entry -> entry.getKey().contains(point.getX(), point.getY()))
                .map(Map.Entry::getValue)
                .findFirst()
                .orElse(null);
    }

    @Override
    public void paintComponent(Graphics graphics) {
        System.out.println("-- paintComponent() --");
        super.paintComponent(graphics);
        Graphics2D graphics2D = (Graphics2D) graphics;

        // draw edges
        graphics2D.setColor(JBColor.BLACK);
        this.graph.getEdges()
                .forEach(edge -> {
                    Point2D sourcePoint = toCameraCoordinate(
                            new Point2D.Float(edge.getSourceNode().getX(), edge.getSourceNode().getY())
                    );
                    Point2D targetPoint = toCameraCoordinate(
                            new Point2D.Float(edge.getTargetNode().getX(), edge.getTargetNode().getY())
                    );
                    Line2D line = new Line2D.Float(sourcePoint, targetPoint);
                    graphics2D.draw(line);
                });

        // draw nodes and label
        int radius = 10;
        int diameter = 2 * radius;
        this.nodeShapesMap = new HashMap<>();
        this.graph.getNodes()
                .forEach(node -> {
                    // create node shape
                    Point2D nodeCenter = toCameraCoordinate(new Point2D.Float(node.getX(), node.getY()));
                    Point2D nodeUpperLeft = new Point2D.Float(
                            (float) nodeCenter.getX() - radius,
                            (float) nodeCenter.getY() - radius
                    );
                    Ellipse2D nodeShape = new Ellipse2D.Float(
                            (float) nodeUpperLeft.getX(),
                            (float) nodeUpperLeft.getY(),
                            diameter,
                            diameter
                    );
                    this.nodeShapesMap.put(nodeShape, node);
                    // draw node shape
                    graphics2D.setColor(JBColor.BLACK);
                    graphics2D.draw(nodeShape);
                    // fill node shape with color
                    graphics2D.setColor(JBColor.WHITE);
                    graphics2D.fill(nodeShape);
                    // draw label
                    graphics2D.setColor(JBColor.BLUE);
                    Point2D labelBottomLeft = new Point2D.Float(
                            (float) (nodeCenter.getX() + 1.5 * radius),
                            (float) (nodeCenter.getY() + 1.5 * radius)
                    );
                    graphics2D.drawString(
                            node.getMethod().getName(),
                            (float) labelBottomLeft.getX(),
                            (float) labelBottomLeft.getY()
                    );
                });
    }

    @NotNull
    private Point2D toCameraCoordinate(@NotNull Point2D point) {
        Dimension canvasSize = this.canvasPanel.getSize();
        return new Point2D.Float(
                (float) (this.zoomRatio * point.getX() * canvasSize.width - this.cameraCenter.getX()),
                (float) (this.zoomRatio * point.getY() * canvasSize.height - this.cameraCenter.getY())
        );
    }
}
