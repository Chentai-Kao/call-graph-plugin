import com.google.common.collect.ImmutableMap;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiModifierList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.Arc2D;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.Point2D;
import java.util.List;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

class Canvas extends JPanel {
    private final Graph graph;
    private JPanel canvasPanel;
    private final CallGraphToolWindow callGraphToolWindow;
    private Map<Shape, Node> nodeShapesMap = Collections.emptyMap();
    private Collection<Node> visibleNodes;
    private Collection<Edge> visibleEdges;
    private Node hoveredNode;
    private final Point2D defaultCameraOrigin = new Point2D.Float(0, 0);
    private final float defaultZoomRatio = 1.0f;
    private Point2D cameraOrigin = defaultCameraOrigin;
    private float xZoomRatio = defaultZoomRatio;
    private float yZoomRatio = defaultZoomRatio;
    private final int nodeRadius = 5;
    private final float regularLineWidth = 1.0f;
    private final Stroke solidLineStroke = new BasicStroke(regularLineWidth);
    private final Map<String, Color> methodAccessColorMap = ImmutableMap.<String, Color>builder()
            .put(PsiModifier.PUBLIC, Colors.green)
            .put(PsiModifier.PROTECTED, Colors.cyan)
            .put(PsiModifier.PACKAGE_LOCAL, Colors.lightOrange)
            .put(PsiModifier.PRIVATE, Colors.red)
            .build();

    Canvas(@NotNull CallGraphToolWindow callGraphToolWindow, @NotNull Graph graph) {
        super();
        this.callGraphToolWindow = callGraphToolWindow;
        this.graph = graph;
        this.visibleNodes = graph.getNodes();
        this.visibleEdges = graph.getEdges();
    }

    @Override
    public void paintComponent(Graphics graphics) {
        super.paintComponent(graphics);

        // set up the drawing panel
        Graphics2D graphics2D = (Graphics2D) graphics;
        graphics2D.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        graphics2D.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        // fill the background for entire canvas
        graphics2D.setColor(Colors.backgroundColor);
        graphics2D.fillRect(0, 0, this.getWidth(), this.getHeight());

        // draw un-highlighted and highlighted self loops
        this.visibleEdges.stream()
                .filter(edge -> edge.getSourceNode() == edge.getTargetNode())
                .forEach(edge -> drawSelfLoopEdge(graphics2D, edge, isNodeHighlighted(edge.getSourceNode())));

        // draw un-highlighted edges
        this.visibleEdges.stream()
                .filter(edge -> edge.getSourceNode() != edge.getTargetNode() &&
                        !isNodeHighlighted(edge.getSourceNode()) && !isNodeHighlighted(edge.getTargetNode()))
                .forEach(edge -> drawNonLoopEdge(graphics2D, edge, Colors.unHighlightedColor));

        // draw upstream/downstream edges
        Set<Node> highlightedNodes = this.visibleNodes.stream()
                .filter(this::isNodeHighlighted)
                .collect(Collectors.toSet());
        Set<Edge> upstreamEdges = highlightedNodes.stream()
                .flatMap(node -> node.getInEdges().values().stream())
                .collect(Collectors.toSet());
        Set<Edge> downstreamEdges = highlightedNodes.stream()
                .flatMap(node -> node.getOutEdges().values().stream())
                .collect(Collectors.toSet());
        upstreamEdges.forEach(edge -> drawNonLoopEdge(graphics2D, edge, Colors.upstreamColor));
        downstreamEdges.forEach(edge -> drawNonLoopEdge(graphics2D, edge, Colors.downstreamColor));

        // draw un-highlighted labels
        Set<Node> upstreamNodes = upstreamEdges.stream().map(Edge::getSourceNode).collect(Collectors.toSet());
        Set<Node> downstreamNodes = downstreamEdges.stream().map(Edge::getTargetNode).collect(Collectors.toSet());
        Set<Node> unHighlightedNodes = this.visibleNodes.stream()
                .filter(node ->
                        !isNodeHighlighted(node) && !upstreamNodes.contains(node) && !downstreamNodes.contains(node)
                )
                .collect(Collectors.toSet());
        unHighlightedNodes.forEach(node -> drawNodeLabels(graphics2D, node, Colors.neutralColor, false));

        // draw un-highlighted nodes (upstream/downstream nodes are excluded)
        this.nodeShapesMap = new HashMap<>();
        unHighlightedNodes.stream()
                .filter(node -> !upstreamNodes.contains(node) && !downstreamNodes.contains(node))
                .forEach(node -> drawNode(graphics2D, node, Colors.unHighlightedColor));

        // draw upstream/downstream label and nodes
        upstreamNodes.forEach(node -> drawNodeLabels(graphics2D, node, Colors.upstreamColor, false));
        downstreamNodes.forEach(node -> drawNodeLabels(graphics2D, node, Colors.downstreamColor, false));
        upstreamNodes.forEach(node -> drawNode(graphics2D, node, Colors.upstreamColor));
        downstreamNodes.forEach(node -> drawNode(graphics2D, node, Colors.downstreamColor));

        // draw highlighted node and label
        this.visibleNodes.stream()
                .filter(this::isNodeHighlighted)
                .forEach(node -> {
                    drawNode(graphics2D, node, Colors.highlightedColor);
                    drawNodeLabels(graphics2D, node, Colors.highlightedColor, true);
                });
    }

    @SuppressWarnings("UnusedReturnValue")
    @NotNull
    Canvas setCanvasPanel(@NotNull JPanel canvasPanel) {
        this.canvasPanel = canvasPanel;
        return this;
    }

    @SuppressWarnings("UnusedReturnValue")
    @NotNull
    Canvas setHoveredNode(@Nullable Node node) {
        if (this.hoveredNode != node) {
            this.hoveredNode = node;
            repaint();
        }
        return this;
    }

    void toggleClickedNode(@NotNull Node node) {
        this.callGraphToolWindow.toggleFocusedMethod(node.getMethod());
        repaint();
    }

    void clearClickedNodes() {
        this.callGraphToolWindow.clearFocusedMethods();
        repaint();
    }

    @NotNull
    Canvas setCameraOrigin(@NotNull Point2D cameraOrigin) {
        this.cameraOrigin = cameraOrigin;
        return this;
    }

    @NotNull
    Point2D getCameraOrigin() {
        return this.cameraOrigin;
    }

    void zoomAtPoint(@NotNull Point2D point, float xZoomFactor, float yZoomFactor) {
        this.cameraOrigin = new Point2D.Float(
                (float) (xZoomFactor * this.cameraOrigin.getX() + (xZoomFactor - 1) * point.getX()),
                (float) (yZoomFactor * this.cameraOrigin.getY() + (yZoomFactor - 1) * point.getY())
        );
        this.xZoomRatio *= xZoomFactor;
        this.yZoomRatio *= yZoomFactor;
        repaint();
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

    void fitCanvasToView() {
        Map<String, Point2D> blueprint = this.graph.getNodes()
                .stream()
                .collect(Collectors.toMap(Node::getId, Node::getRawLayoutPoint));
        Map<String, Point2D> bestFitBlueprint = Utils.fitLayoutToViewport(blueprint);
        Utils.applyLayoutBlueprintToGraph(bestFitBlueprint, this.graph);
        this.cameraOrigin = defaultCameraOrigin;
        this.xZoomRatio = defaultZoomRatio;
        this.yZoomRatio = defaultZoomRatio;
        repaint();
    }

    void fitCanvasToBestRatio() {
        // set every node coordinate to its original raw layout by GraphViz
        this.graph.getNodes().forEach(point -> point.setPoint(point.getRawLayoutPoint()));
        this.cameraOrigin = defaultCameraOrigin;
        this.xZoomRatio = defaultZoomRatio;
        this.yZoomRatio = defaultZoomRatio;
        repaint();
    }

    int getNodesCount() {
        return this.graph.getNodes().size();
    }

    void filterAccessChangeHandler() {
        this.visibleNodes = this.graph.getNodes()
                .stream()
                .filter(node -> {
                    PsiMethod method = node.getMethod();
                    if (Utils.isPublic(method)) {
                        return this.callGraphToolWindow.isFilterAccessPublicChecked();
                    } else if (Utils.isProtected(method)) {
                        return this.callGraphToolWindow.isFilterAccessProtectedChecked();
                    } else if (Utils.isPackageLocal(method)) {
                        return this.callGraphToolWindow.isFilterAccessPackageLocalChecked();
                    } else if (Utils.isPrivate(method)) {
                        return this.callGraphToolWindow.isFilterAccessPrivateChecked();
                    }
                    return true;
                })
                .collect(Collectors.toSet());
        this.visibleEdges = this.graph.getEdges()
                .stream()
                .filter(edge -> this.visibleNodes.contains(edge.getSourceNode()) &&
                        this.visibleNodes.contains(edge.getTargetNode()))
                .collect(Collectors.toSet());
        repaint();
    }

    @NotNull
    private Point2D toCameraView(@NotNull Point2D point) {
        Dimension canvasSize = this.canvasPanel.getSize();
        return new Point2D.Float(
                (float) (this.xZoomRatio * point.getX() * canvasSize.width - this.cameraOrigin.getX()),
                (float) (this.yZoomRatio * point.getY() * canvasSize.height - this.cameraOrigin.getY())
        );
    }

    private boolean isNodeHighlighted(@NotNull Node node) {
        return this.hoveredNode == node || this.callGraphToolWindow.isFocusedMethod(node.getMethod());
    }

    private void drawSelfLoopEdge(@NotNull Graphics2D graphics2D, @NotNull Edge edge, boolean isHighlighted) {
        Point2D sourceNodeCenter = toCameraView(edge.getSourceNode().getPoint());
        drawSelfLoop(graphics2D, sourceNodeCenter, isHighlighted);
    }

    private void drawNonLoopEdge(@NotNull Graphics2D graphics2D, @NotNull Edge edge, @NotNull Color color) {
        Point2D sourceNodeCenter = toCameraView(edge.getSourceNode().getPoint());
        Point2D targetNodeCenter = toCameraView(edge.getTargetNode().getPoint());
        drawLine(graphics2D, sourceNodeCenter, targetNodeCenter, color);
        drawLineArrow(graphics2D, sourceNodeCenter, targetNodeCenter, color);
    }

    private void drawNode(@NotNull Graphics2D graphics2D, @NotNull Node node, @NotNull Color outlineColor) {
        Point2D nodeCenter = toCameraView(node.getPoint());
        Color backgroundColor = getNodeBackgroundColor(node);
        Shape nodeShape = drawCircle(graphics2D, nodeCenter, this.nodeRadius, backgroundColor, outlineColor);
        this.nodeShapesMap.put(nodeShape, node);
    }

    @NotNull
    private Color getNodeBackgroundColor(@NotNull Node node) {
        if (this.callGraphToolWindow.isNodeColorByAccess()) {
            PsiModifierList psiModifierList = node.getMethod().getModifierList();
            return this.methodAccessColorMap.keySet()
                    .stream()
                    .filter(psiModifierList::hasModifierProperty)
                    .findFirst()
                    .map(this.methodAccessColorMap::get)
                    .orElse(Colors.backgroundColor);
        } else if (this.callGraphToolWindow.isNodeColorByClassName()) {
            PsiClass psiClass = node.getMethod().getContainingClass();
            if (psiClass != null) {
                int hashIndex = psiClass.hashCode() % Colors.heatMapColors.size();
                return Colors.heatMapColors.get(hashIndex);
            }
        }
        return Colors.backgroundColor;
    }

    @NotNull
    private List<AbstractMap.SimpleEntry<String, Color>> createNodeLabels(
            @NotNull Node node,
            @NotNull Color signatureColor,
            boolean isNodeHovered) {
        // draw labels in top-down order
        List<AbstractMap.SimpleEntry<String, Color>> labels = new ArrayList<>();
        // function signature
        String signature = isNodeHovered ? Utils.getMethodSignature(node.getMethod()) : node.getMethod().getName();
        labels.add(new AbstractMap.SimpleEntry<>(signature, signatureColor));
        // package name
        if (this.callGraphToolWindow.isRenderFunctionPackageName(isNodeHovered)) {
            String packageName = Utils.getMethodPackageName(node.getMethod());
            labels.add(new AbstractMap.SimpleEntry<>(packageName, Colors.unHighlightedColor));
        }
        // file path
        if (this.callGraphToolWindow.isRenderFunctionFilePath(isNodeHovered)) {
            String filePath = Utils.getMethodFilePath(node.getMethod());
            labels.add(new AbstractMap.SimpleEntry<>(filePath, Colors.unHighlightedColor));
        }
        return labels;
    }

    private void drawNodeLabels(
            @NotNull Graphics2D graphics2D,
            @NotNull Node node,
            @NotNull Color labelColor,
            boolean isNodeHovered) {
        // create labels
        List<AbstractMap.SimpleEntry<String, Color>> labels = createNodeLabels(node, labelColor, isNodeHovered);
        // fill background to overall bounding box
        int padding = 2; // 1 px padding in the text bounding box
        FontMetrics fontMetrics = graphics2D.getFontMetrics();
        double singleLabelHeight = fontMetrics.getAscent() + fontMetrics.getDescent();
        double boundingBoxWidth = labels.stream()
                .mapToDouble(label -> fontMetrics.getStringBounds(label.getKey(), graphics2D).getWidth())
                .max()
                .orElse(0.0);
        double boundingBoxHeight = labels.size() * singleLabelHeight;
        Point2D nodeCenter = toCameraView(node.getPoint());
        int nodeDiameter = 2 * nodeRadius;
        Point2D boundingBoxLowerLeft = new Point2D.Float(
                (float) nodeCenter.getX() + 2 * nodeDiameter - padding,
                (float) (nodeCenter.getY() + 0.5f * singleLabelHeight + padding)
        );
        Point2D boundingBoxUpperLeft = new Point2D.Float(
                (float) boundingBoxLowerLeft.getX(),
                (float) (boundingBoxLowerLeft.getY() - 2 * padding - boundingBoxHeight)
        );
        Point2D boundingBoxUpperRight = new Point2D.Float(
                (float) (boundingBoxUpperLeft.getX() + 2 * padding + boundingBoxWidth),
                (float) boundingBoxUpperLeft.getY()
        );
        Point2D boundingBoxLowerRight = new Point2D.Float(
                (float) boundingBoxUpperRight.getX(),
                (float) boundingBoxLowerLeft.getY()
        );
        Color textBackgroundColor = this.callGraphToolWindow.isQueried(node.getMethod().getName()) ?
                Colors.highlightedBackgroundColor : Colors.backgroundColor;
        graphics2D.setColor(textBackgroundColor);
        graphics2D.fillRect(
                (int) boundingBoxUpperLeft.getX() + 1,
                (int) boundingBoxUpperLeft.getY() + 1,
                (int) (boundingBoxUpperRight.getX() - boundingBoxUpperLeft.getX() - 1),
                (int) (boundingBoxLowerLeft.getY() - boundingBoxUpperLeft.getY() - 1)
        );
        // draw border if the node is hovered
        if (isNodeHovered) {
            drawLine(graphics2D, boundingBoxLowerLeft, boundingBoxUpperLeft, Colors.unHighlightedColor);
            drawLine(graphics2D, boundingBoxUpperLeft, boundingBoxUpperRight, Colors.unHighlightedColor);
            drawLine(graphics2D, boundingBoxUpperRight, boundingBoxLowerRight, Colors.unHighlightedColor);
            drawLine(graphics2D, boundingBoxLowerRight, boundingBoxLowerLeft, Colors.unHighlightedColor);
        }
        // draw text
        IntStream.range(0, labels.size())
                .forEach(index -> {
                    Point2D labelCenterLeft = new Point2D.Float(
                            (float) boundingBoxLowerLeft.getX() + padding,
                            (float) (nodeCenter.getY() - index * singleLabelHeight)
                    );
                    AbstractMap.SimpleEntry<String, Color> label = labels.get(index);
                    drawText(graphics2D, labelCenterLeft, label.getKey(), label.getValue());
                });
    }

    @NotNull
    private Shape drawCircle(
            Graphics2D graphics2D,
            @NotNull Point2D circleCenter,
            int radius,
            @NotNull Color backgroundColor,
            @NotNull Color outlineColor) {
        // create node shape
        Point2D upperLeft = new Point2D.Float(
                (float) circleCenter.getX() - radius,
                (float) circleCenter.getY() - radius
        );
        int diameter = 2 * radius;
        Ellipse2D shape = new Ellipse2D.Float(
                (float) upperLeft.getX(),
                (float) upperLeft.getY(),
                diameter,
                diameter
        );
        // fill node with color
        graphics2D.setColor(backgroundColor);
        graphics2D.fill(shape);
        // draw the outline
        graphics2D.setColor(outlineColor);
        Shape strokedShape = this.solidLineStroke.createStrokedShape(shape);
        graphics2D.draw(strokedShape);
        return shape;
    }

    private void drawText(
            Graphics2D graphics2D,
            @NotNull Point2D textCenterLeft,
            @NotNull String text,
            @NotNull Color textColor) {
        FontMetrics fontMetrics = graphics2D.getFontMetrics();
        Point2D textLowerLeft = new Point2D.Float(
                (int) textCenterLeft.getX(),
                (int) (textCenterLeft.getY() + 0.5 * (fontMetrics.getAscent() - fontMetrics.getDescent()))
        );
        graphics2D.setColor(textColor);
        graphics2D.drawString(text, (float) textLowerLeft.getX(), (float) textLowerLeft.getY());
    }

    private void drawLine(
            Graphics2D graphics2D,
            @NotNull Point2D sourcePoint,
            @NotNull Point2D targetPoint,
            @NotNull Color lineColor) {
        Line2D shape = new Line2D.Float(sourcePoint, targetPoint);
        Shape strokedShape = this.solidLineStroke.createStrokedShape(shape);
        graphics2D.setColor(lineColor);
        graphics2D.draw(strokedShape);
    }

    private void drawSelfLoop(@NotNull Graphics2D graphics2D, @NotNull Point2D nodeCenter, boolean isHighlighted) {
        // draw circle shape
        int selfLoopRadius = 10;
        int selfLoopDiameter = 2 * selfLoopRadius;
        Point2D loopUpperLeft = new Point2D.Float(
                (float) nodeCenter.getX() - selfLoopRadius,
                (float) nodeCenter.getY() - selfLoopDiameter
        );
        Arc2D upstreamHalfArc = new Arc2D.Float(
                (float) loopUpperLeft.getX(),
                (float) loopUpperLeft.getY(),
                selfLoopDiameter,
                selfLoopDiameter,
                90.0f,
                180.0f,
                Arc2D.OPEN
        );
        Arc2D downstreamHalfArc = new Arc2D.Float(
                (float) loopUpperLeft.getX(),
                (float) loopUpperLeft.getY(),
                selfLoopDiameter,
                selfLoopDiameter,
                270.0f,
                180.0f,
                Arc2D.OPEN
        );
        Shape strokedUpstreamHalfShape = this.solidLineStroke.createStrokedShape(upstreamHalfArc);
        Shape strokedDownstreamHalfShape = this.solidLineStroke.createStrokedShape(downstreamHalfArc);
        Color upstreamHalfLoopColor = isHighlighted ? Colors.upstreamColor : Colors.unHighlightedColor;
        Color downstreamHalfLoopColor = isHighlighted ? Colors.downstreamColor : Colors.unHighlightedColor;
        graphics2D.setColor(upstreamHalfLoopColor);
        graphics2D.draw(strokedUpstreamHalfShape);
        graphics2D.setColor(downstreamHalfLoopColor);
        graphics2D.draw(strokedDownstreamHalfShape);
        // draw arrow
        Point2D arrowCenter = new Point2D.Double(nodeCenter.getX(), nodeCenter.getY() - selfLoopDiameter);
        drawArrow(graphics2D, arrowCenter, Math.PI, downstreamHalfLoopColor);
    }

    private void drawLineArrow(
            @NotNull Graphics2D graphics2D,
            @NotNull Point2D sourcePoint,
            @NotNull Point2D targetPoint,
            @NotNull Color arrowColor) {
        double dx = targetPoint.getX() - sourcePoint.getX();
        double dy = targetPoint.getY() - sourcePoint.getY();
        double angle = Math.atan2(dy, dx);
        Point2D arrowCenter = new Point2D.Double(
                0.5 * (sourcePoint.getX() + targetPoint.getX()),
                0.5 * (sourcePoint.getY() + targetPoint.getY())
        );
        drawArrow(graphics2D, arrowCenter, angle, arrowColor);
    }

    private void drawArrow(
            @NotNull Graphics2D graphics2D,
            @NotNull Point2D center,
            double angle,
            @NotNull Color arrowColor) {
        long arrowSize = 5;
        Point2D midPoint = new Point2D.Double(
                center.getX() + arrowSize * Math.cos(angle),
                center.getY() + arrowSize * Math.sin(angle)
        );
        double upperTipAngle = angle + Math.PI * 2 / 3;
        Point2D upperTipPoint = new Point2D.Double(
                center.getX() + arrowSize * Math.cos(upperTipAngle),
                center.getY() + arrowSize * Math.sin(upperTipAngle)
        );
        double lowerTipAngle = angle - Math.PI * 2 / 3;
        Point2D lowerTipPoint = new Point2D.Double(
                center.getX() + arrowSize * Math.cos(lowerTipAngle),
                center.getY() + arrowSize * Math.sin(lowerTipAngle)
        );
        List<Point2D> points = Arrays.asList(midPoint, upperTipPoint, lowerTipPoint, midPoint);
        int[] xPoints = points.stream()
                .mapToInt(point -> (int) Math.round(point.getX()))
                .toArray();
        int[] yPoints = points.stream()
                .mapToInt(point -> (int) Math.round(point.getY()))
                .toArray();
        graphics2D.setColor(arrowColor);
        graphics2D.fillPolygon(xPoints, yPoints, xPoints.length);
    }
}
