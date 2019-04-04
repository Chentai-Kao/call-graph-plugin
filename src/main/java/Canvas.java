import com.intellij.ui.JBColor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.Arc2D;
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
    private final int nodeRadius = 10;
    private final int nodeDiameter = 2 * nodeRadius;
    private final Stroke solidLineStroke = new BasicStroke(0.1f);
    private final Stroke dashedLineStroke = new BasicStroke(
            0.1f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_BEVEL, 10.0f, new float[] { 5.0f }, 0.0f);

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
        super.paintComponent(graphics);

        // set up the drawing panel
        Graphics2D graphics2D = (Graphics2D) graphics;
        graphics2D.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        graphics2D.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        // draw edges
        this.graph.getEdges()
                .forEach(edge -> {
                    Node sourceNode = edge.getSourceNode();
                    Node targetNode = edge.getTargetNode();
                    if (sourceNode == targetNode) {
                        // recursive function call, draw self loop
                        Point2D nodeCenter = toCameraView(new Point2D.Float(sourceNode.getX(), sourceNode.getY()));
                        Point2D loopCenter = new Point2D.Float(
                                (float) nodeCenter.getX(),
                                (float) nodeCenter.getY() - this.nodeRadius
                        );
                        drawArc(graphics2D, loopCenter);
                    } else {
                        // non recursive function call, draw line (dashed if call looks backward on the graph)
                        boolean isDashed = sourceNode.getX() > targetNode.getX();
                        drawLine(
                                graphics2D,
                                toCameraView(new Point2D.Float(sourceNode.getX(), sourceNode.getY())),
                                toCameraView(new Point2D.Float(targetNode.getX(), targetNode.getY())),
                                isDashed
                        );
                    }
                });

        // draw nodes and label
        this.nodeShapesMap = new HashMap<>();
        this.graph.getNodes()
                .forEach(node -> {
                    Point2D nodeCenter = toCameraView(new Point2D.Float(node.getX(), node.getY()));
                    // draw node
                    Ellipse2D circleShape = drawCircle(graphics2D, nodeCenter);
                    this.nodeShapesMap.put(circleShape, node);
                    // draw label
                    Point2D labelLowerLeft = new Point2D.Float(
                            (float) (nodeCenter.getX() + this.nodeDiameter),
                            (float) (nodeCenter.getY() + this.nodeRadius)
                    );
                    drawText(graphics2D, labelLowerLeft, node.getMethod().getName());
                });
    }

    @NotNull
    private Point2D toCameraView(@NotNull Point2D point) {
        Dimension canvasSize = this.canvasPanel.getSize();
        return new Point2D.Float(
                (float) (this.zoomRatio * point.getX() * canvasSize.width - this.cameraCenter.getX()),
                (float) (this.zoomRatio * point.getY() * canvasSize.height - this.cameraCenter.getY())
        );
    }

    @NotNull
    private Ellipse2D drawCircle(
            Graphics2D graphics2D,
            @NotNull Point2D center) {
        // create node shape
        Point2D upperLeft = new Point2D.Float(
                (float) center.getX() - this.nodeRadius,
                (float) center.getY() - this.nodeRadius
        );
        Ellipse2D shape = new Ellipse2D.Float(
                (float) upperLeft.getX(),
                (float) upperLeft.getY(),
                this.nodeDiameter,
                this.nodeDiameter
        );
        // decide stroke to use, and draw node shape
        graphics2D.setColor(JBColor.BLACK);
        Shape strokedShape = this.solidLineStroke.createStrokedShape(shape);
        graphics2D.draw(strokedShape);
        // fill node shape with color
        graphics2D.setColor(JBColor.WHITE);
        graphics2D.fill(shape);

        return shape;
    }

    private void drawText(
            Graphics2D graphics2D,
            @NotNull Point2D lowerLeft,
            @NotNull String text) {
        graphics2D.setColor(JBColor.BLUE);
        graphics2D.drawString(text, (float) lowerLeft.getX(), (float) lowerLeft.getY());
    }

    private void drawLine(
            Graphics2D graphics2D,
            @NotNull Point2D sourcePoint,
            @NotNull Point2D targetPoint,
            boolean isDashed) {
        Line2D shape = new Line2D.Float(sourcePoint, targetPoint);
        Stroke stroke = isDashed ? this.dashedLineStroke : this.solidLineStroke;
        Shape strokedShape = stroke.createStrokedShape(shape);
        graphics2D.setColor(JBColor.GRAY);
        graphics2D.draw(strokedShape);
    }

    private void drawArc(
            Graphics2D graphics2D,
            @NotNull Point2D center) {
        // create node shape
        Point2D upperLeft = new Point2D.Float(
                (float) center.getX() - this.nodeRadius,
                (float) center.getY() - this.nodeRadius
        );
        Arc2D shape = new Arc2D.Float(
                (float) upperLeft.getX(),
                (float) upperLeft.getY(),
                this.nodeDiameter,
                this.nodeDiameter,
                0.0f,
                360.0f,
                Arc2D.OPEN
        );
        Shape strokedShape = this.dashedLineStroke.createStrokedShape(shape);
        graphics2D.setColor(JBColor.GRAY);
        graphics2D.draw(strokedShape);
    }
}
