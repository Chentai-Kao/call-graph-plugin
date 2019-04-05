import com.intellij.ui.JBColor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.*;
import java.util.HashMap;
import java.util.Map;

class Canvas extends JPanel {
    private Graph graph;
    private JPanel canvasPanel;
    private Map<Shape, Node> nodeShapesMap;
    private Node hoveredNode;
    private Node clickedNode;
    private Point2D cameraCenter = new Point2D.Float(0, 0);
    private float zoomRatio = 1.0f;
    private final int nodeRadius = 5;
    private final int nodeDiameter = 2 * nodeRadius;
    private final float regularLineWidth = 1.0f;
    private final Stroke solidLineStroke = new BasicStroke(regularLineWidth);
    private final Stroke dashedLineStroke = new BasicStroke(
            regularLineWidth,
            BasicStroke.CAP_BUTT,
            BasicStroke.JOIN_MITER,
            10.0f,
            new float[] { 5.0f },
            0.0f
    );
    private final JBColor backgroundColor = JBColor.WHITE;
    private final JBColor unHighlightedLineColor = JBColor.LIGHT_GRAY;
    private final JBColor highlightedLineColor = JBColor.BLACK;
    private final JBColor unHighlightedTextColor = JBColor.DARK_GRAY;
    private final JBColor highlightedTextColor = JBColor.BLACK;

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

        // fill the background for entire canvas
        graphics2D.setColor(this.backgroundColor);
        graphics2D.fillRect(0, 0, this.getWidth(), this.getHeight());

        // draw edges
        this.graph.getEdges()
                .forEach(edge -> {
                    Node sourceNode = edge.getSourceNode();
                    Node targetNode = edge.getTargetNode();
                    Point2D sourceNodeCenter = toCameraView(new Point2D.Float(sourceNode.getX(), sourceNode.getY()));
                    boolean isHighlighted = isNodeHighlighted(sourceNode) || isNodeHighlighted(targetNode);
                    if (sourceNode == targetNode) {
                        // recursive function call, draw self loop
                        drawSelfLoop(graphics2D, sourceNodeCenter, isHighlighted);
                    } else {
                        // non recursive function call, draw line (dashed if call looks backward on the graph)
                        Point2D targetNodeCenter =
                                toCameraView(new Point2D.Float(targetNode.getX(), targetNode.getY()));
                        boolean isDashed = sourceNodeCenter.getX() > targetNodeCenter.getX();
                        drawLine(graphics2D, sourceNodeCenter, targetNodeCenter, isDashed, isHighlighted);
                    }
                });

        // draw nodes and label
        this.nodeShapesMap = new HashMap<>();
        this.graph.getNodes()
                .forEach(node -> {
                    Point2D nodeCenter = toCameraView(new Point2D.Float(node.getX(), node.getY()));
                    // draw node
                    boolean isHighlighted = isNodeHighlighted(node);
                    Shape nodeShape = drawFunctionNode(graphics2D, nodeCenter, isHighlighted);
                    this.nodeShapesMap.put(nodeShape, node);
                    // draw label
                    drawFunctionName(graphics2D, nodeCenter, node.getMethod().getName(), isHighlighted);
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
    private Shape drawFunctionNode(
            Graphics2D graphics2D,
            @NotNull Point2D center,
            boolean isHighlighted) {
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
        // fill node with color
        graphics2D.setColor(JBColor.WHITE);
        graphics2D.fill(shape);
        // draw the outline
        JBColor outlineColor = isHighlighted ? this.highlightedLineColor : this.unHighlightedLineColor;
        graphics2D.setColor(outlineColor);
        Shape strokedShape = this.solidLineStroke.createStrokedShape(shape);
        graphics2D.draw(strokedShape);

        return shape;
    }

    private void drawFunctionName(
            Graphics2D graphics2D,
            @NotNull Point2D nodeCenter,
            @NotNull String text,
            boolean isHighlighted) {
        FontMetrics fontMetrics = graphics2D.getFontMetrics();
        Point2D labelLowerLeft = new Point2D.Float(
                (int) (nodeCenter.getX() + 2 * this.nodeDiameter),
                (int) (nodeCenter.getY() + 0.5 * fontMetrics.getAscent() - 0.5 * fontMetrics.getDescent())
        );
        // draw text background
        Rectangle2D textBoundingBox = fontMetrics.getStringBounds(text, graphics2D);
        graphics2D.setColor(this.backgroundColor);
        graphics2D.fillRect(
                (int) labelLowerLeft.getX(),
                (int) labelLowerLeft.getY() - fontMetrics.getAscent(),
                (int) textBoundingBox.getWidth(),
                (int) textBoundingBox.getHeight()
        );
        // draw text
        JBColor textColor = isHighlighted ? this.highlightedTextColor : this.unHighlightedTextColor;
        graphics2D.setColor(textColor);
        graphics2D.drawString(text, (float) labelLowerLeft.getX(), (float) labelLowerLeft.getY());
    }

    private void drawLine(
            Graphics2D graphics2D,
            @NotNull Point2D sourcePoint,
            @NotNull Point2D targetPoint,
            boolean isDashed,
            boolean isHighlighted) {
        Line2D shape = new Line2D.Float(sourcePoint, targetPoint);
        Stroke stroke = isDashed ? this.dashedLineStroke : this.solidLineStroke;
        Shape strokedShape = stroke.createStrokedShape(shape);
        JBColor lineColor = isHighlighted ? this.highlightedLineColor : this.unHighlightedLineColor;
        graphics2D.setColor(lineColor);
        graphics2D.draw(strokedShape);
    }

    private void drawSelfLoop(
            Graphics2D graphics2D,
            @NotNull Point2D nodeCenter,
            boolean isHighlighted) {
        // create node shape
        int selfLoopRadius = 10;
        int selfLoopDiameter = 2 * selfLoopRadius;
        Point2D loopUpperLeft = new Point2D.Float(
                (float) nodeCenter.getX() - selfLoopRadius,
                (float) nodeCenter.getY() - selfLoopDiameter
        );
        Arc2D shape = new Arc2D.Float(
                (float) loopUpperLeft.getX(),
                (float) loopUpperLeft.getY(),
                selfLoopDiameter,
                selfLoopDiameter,
                0.0f,
                360.0f,
                Arc2D.OPEN
        );
        Shape strokedShape = this.dashedLineStroke.createStrokedShape(shape);
        JBColor arcColor = isHighlighted ? this.highlightedLineColor : this.unHighlightedLineColor;
        graphics2D.setColor(arcColor);
        graphics2D.draw(strokedShape);
    }

    void setHoveredNode(@Nullable Node node) {
        if (this.hoveredNode != node) {
            this.hoveredNode = node;
            repaint();
        }
    }

    void setClickedNode(@Nullable Node node) {
        if (this.clickedNode != node) {
            this.clickedNode = node;
            repaint();
        }
    }

    private boolean isNodeHighlighted(@NotNull Node node) {
        return this.hoveredNode == node || this.clickedNode == node;
    }
}
