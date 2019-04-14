import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiNamedElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.ui.JBColor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.*;
import java.util.List;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

class Canvas extends JPanel {
    private Graph graph;
    private JPanel canvasPanel;
    private CallGraphToolWindow callGraphToolWindow;
    private Project project;
    private Map<Shape, Node> nodeShapesMap;
    private Node hoveredNode;
    private Node clickedNode;
    private Point2D cameraOrigin = new Point2D.Float(0, 0);
    private float zoomRatio = 1.0f;
    private final int nodeRadius = 5;
    private final int nodeDiameter = 2 * nodeRadius;
    private final float regularLineWidth = 1.0f;
    private final Stroke solidLineStroke = new BasicStroke(regularLineWidth);
    private final Color backgroundColor = new JBColor(new Color(0xFDFEFF), new Color(0x292B2D));
    private final Color unHighlightedColor = new JBColor(new Color(0xC6C8CA), new Color(0x585A5C));
    private final Color unHighlightedTextColor = new JBColor(new Color(0x626466), new Color(0x949698));
    private final Color highlightedColor = new JBColor(new Color(0x4285F4), new Color(0x4C79A4));
    private final Color upstreamColor = new JBColor(new Color(0xFBBC05), new Color(0xBE9117));
    private final Color downstreamColor = new JBColor(new Color(0x34A853), new Color(0x538863));

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
    Canvas setCallGraphToolWindow(@NotNull CallGraphToolWindow callGraphToolWindow) {
        this.callGraphToolWindow = callGraphToolWindow;
        return this;
    }

    @NotNull
    Canvas setProject(@NotNull Project project) {
        this.project = project;
        return this;
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

        // draw un-highlighted and highlighted self loops
        this.graph.getEdges()
                .stream()
                .filter(edge -> edge.getSourceNode() == edge.getTargetNode())
                .forEach(edge -> drawSelfLoopEdge(graphics2D, edge, isNodeHighlighted(edge.getSourceNode())));

        // draw un-highlighted edges
        this.graph.getEdges()
                .stream()
                .filter(edge -> edge.getSourceNode() != edge.getTargetNode() &&
                        !isNodeHighlighted(edge.getSourceNode()) && !isNodeHighlighted(edge.getTargetNode()))
                .forEach(edge -> drawNonLoopEdge(graphics2D, edge, this.unHighlightedColor));

        // draw upstream/downstream edges
        Collection<Edge> upstreamEdges = this.graph.getEdges()
                .stream()
                .filter(edge -> isNodeHighlighted(edge.getTargetNode()) && edge.getSourceNode() != edge.getTargetNode())
                .collect(Collectors.toSet());
        Collection<Edge> downstreamEdges = this.graph.getEdges()
                .stream()
                .filter(edge -> isNodeHighlighted(edge.getSourceNode()) && edge.getSourceNode() != edge.getTargetNode())
                .collect(Collectors.toSet());
        upstreamEdges.forEach(edge -> drawNonLoopEdge(graphics2D, edge, this.upstreamColor));
        downstreamEdges.forEach(edge -> drawNonLoopEdge(graphics2D, edge, this.downstreamColor));

        // draw un-highlighted labels
        Collection<Node> upstreamNodes =
                upstreamEdges.stream().map(Edge::getSourceNode).collect(Collectors.toSet());
        Collection<Node> downstreamNodes =
                downstreamEdges.stream().map(Edge::getTargetNode).collect(Collectors.toSet());
        Collection<Node> unHighlightedNodes = this.graph.getNodes()
                .stream()
                .filter(node -> !isNodeHighlighted(node) &&
                        !upstreamNodes.contains(node) && !downstreamNodes.contains(node))
                .collect(Collectors.toSet());
        unHighlightedNodes.forEach(node -> drawNodeLabel(
                        graphics2D, node, node.getMethod().getName(), this.unHighlightedTextColor));

        // draw un-highlighted nodes (upstream/downstream nodes are excluded)
        this.nodeShapesMap = new HashMap<>();
        unHighlightedNodes.stream()
                .filter(node -> !upstreamNodes.contains(node) && !downstreamNodes.contains(node))
                .forEach(node -> {
                    Shape nodeShape = drawNode(graphics2D, node, this.unHighlightedColor);
                    this.nodeShapesMap.put(nodeShape, node);
                });

        // draw upstream/downstream label and nodes
        upstreamNodes.forEach(node -> drawNodeLabel(
                graphics2D, node, node.getMethod().getName(), this.upstreamColor));
        downstreamNodes.forEach(node -> drawNodeLabel(
                graphics2D, node, node.getMethod().getName(), this.downstreamColor));
        upstreamNodes.forEach(node -> {
            Shape nodeShape = drawNode(graphics2D, node, this.upstreamColor);
            this.nodeShapesMap.put(nodeShape, node);
        });
        downstreamNodes.forEach(node -> {
            Shape nodeShape = drawNode(graphics2D, node, this.downstreamColor);
            this.nodeShapesMap.put(nodeShape, node);
        });

        // draw highlighted node and label
        this.graph.getNodes()
                .stream()
                .filter(this::isNodeHighlighted)
                .forEach(node -> {
                    Shape nodeShape = drawNode(graphics2D, node, this.highlightedColor);
                    this.nodeShapesMap.put(nodeShape, node);
                    drawNodeLabel(graphics2D, node, getFunctionSignature(node), this.highlightedColor);
                    drawNodeFilePath(graphics2D, node, getFunctionFilePath(node.getMethod()));
                });
    }

    private void drawNodeLabel(
            @NotNull Graphics2D graphics2D,
            @NotNull Node node,
            @NotNull String label,
            @NotNull Color labelColor) {
        Point2D nodeCenter = toCameraView(node.getPoint());
        Point2D labelCenterLeft = new Point2D.Float(
                (int) (nodeCenter.getX() + 2 * this.nodeDiameter),
                (int) nodeCenter.getY()
        );
        drawText(graphics2D, labelCenterLeft, label, labelColor);
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

    @NotNull
    private Shape drawNode(@NotNull Graphics2D graphics2D, @NotNull Node node, @NotNull Color color) {
        Point2D nodeCenter = toCameraView(node.getPoint());
        return drawCircle(graphics2D, nodeCenter, this.nodeRadius, color);
    }

    private void drawNodeFilePath(@NotNull Graphics2D graphics2D, @NotNull Node node, @NotNull String filePath) {
        FontMetrics fontMetrics = graphics2D.getFontMetrics();
        Point2D nodeCenter = toCameraView(node.getPoint());
        Point2D filePathCenterLeft = new Point2D.Float(
                (float) (nodeCenter.getX() + 2 * this.nodeDiameter),
                (float) (nodeCenter.getY() - fontMetrics.getAscent() - fontMetrics.getDescent())
        );
        drawText(graphics2D, filePathCenterLeft, filePath, this.unHighlightedTextColor);
    }

    @NotNull
    private String getFunctionFilePath(@NotNull PsiElement psiElement) {
        PsiFile psiFile = PsiTreeUtil.getParentOfType(psiElement, PsiFile.class);
        if (psiFile != null) {
            VirtualFile currentFile = psiFile.getVirtualFile();
            VirtualFile rootFile =
                    ProjectFileIndex.SERVICE.getInstance(this.project).getContentRootForFile(currentFile);
            if (rootFile != null) {
                String relativePath = VfsUtilCore.getRelativePath(currentFile, rootFile);
                if (relativePath != null) {
                    return relativePath;
                }
            }
        }
        return "";
    }

    @NotNull
    private String getFunctionSignature(@NotNull Node node) {
        PsiMethod method = node.getMethod();
        String name = method.getName();
        String parameterNames = Stream.of(method.getParameterList().getParameters())
                .map(PsiNamedElement::getName)
                .collect(Collectors.joining(", "));
        String parameters = parameterNames.isEmpty() ? "" : String.format("(%s)", parameterNames);
        return String.format("%s%s", name, parameters);
    }

    @NotNull
    private Point2D toCameraView(@NotNull Point2D point) {
        Dimension canvasSize = this.canvasPanel.getSize();
        return new Point2D.Float(
                (float) (this.zoomRatio * point.getX() * canvasSize.width - this.cameraOrigin.getX()),
                (float) (this.zoomRatio * point.getY() * canvasSize.height - this.cameraOrigin.getY())
        );
    }

    @NotNull
    private Shape drawCircle(
            Graphics2D graphics2D,
            @NotNull Point2D circleCenter,
            int radius,
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
        graphics2D.setColor(this.backgroundColor);
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
                (int) (textCenterLeft.getY() + 0.5 * fontMetrics.getAscent() - 0.5 * fontMetrics.getDescent())
        );
        // draw text background
        Rectangle2D textBoundingBox = fontMetrics.getStringBounds(text, graphics2D);
        graphics2D.setColor(this.backgroundColor);
        graphics2D.fillRect(
                (int) textLowerLeft.getX(),
                (int) textLowerLeft.getY() - fontMetrics.getAscent(),
                (int) textBoundingBox.getWidth(),
                (int) textBoundingBox.getHeight()
        );
        // draw text
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
        Color upstreamHalfLoopColor = isHighlighted ? this.upstreamColor : this.unHighlightedColor;
        Color downstreamHalfLoopColor = isHighlighted ? this.downstreamColor : this.unHighlightedColor;
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
        this.callGraphToolWindow.setClickedNode(node);
    }

    private boolean isNodeHighlighted(@NotNull Node node) {
        return this.hoveredNode == node || this.clickedNode == node;
    }
}
