package callgraph

import com.intellij.psi.PsiModifier
import java.awt.*
import java.awt.geom.Arc2D
import java.awt.geom.Ellipse2D
import java.awt.geom.Line2D
import java.awt.geom.Point2D
import javax.swing.JPanel

class Canvas(private val callGraphToolWindow: CallGraphToolWindow): JPanel() {
    private val defaultCameraOrigin = Point2D.Float(0f, 0f)
    val cameraOrigin = Point2D.Float(defaultCameraOrigin.x, defaultCameraOrigin.y)
    private val defaultZoomRatio = 1f
    private val zoomRatio = Point2D.Float(defaultZoomRatio, defaultZoomRatio)
    private val nodeRadius = 5f
    private val regularLineWidth = 1f
    private val solidLineStroke = BasicStroke(regularLineWidth)
    private val visibleNodes = mutableSetOf<Node>()
    private val visibleEdges = mutableSetOf<Edge>()
    private val nodeShapesMap = mutableMapOf<Shape, Node>()
    private val methodAccessColorMap = mapOf(
            PsiModifier.PUBLIC to Colors.GREEN.color,
            PsiModifier.PROTECTED to Colors.LIGHT_ORANGE.color,
            PsiModifier.PACKAGE_LOCAL to Colors.BLUE.color,
            PsiModifier.PRIVATE to Colors.RED.color
    )
    private val methodAccessLabelMap = mapOf(
            PsiModifier.PUBLIC to "public",
            PsiModifier.PROTECTED to "protected",
            PsiModifier.PACKAGE_LOCAL to "package local",
            PsiModifier.PRIVATE to "private"
    )
    private val heatMapColors = listOf(
            Colors.DEEP_BLUE.color,
            Colors.BLUE.color,
            Colors.LIGHT_BLUE.color,
            Colors.CYAN.color,
            Colors.GREEN.color,
            Colors.LIGHT_GREEN.color,
            Colors.YELLOW.color,
            Colors.LIGHT_ORANGE.color,
            Colors.ORANGE.color,
            Colors.RED.color
    )

    private var graph = Graph()
    private var hoveredNode: Node? = null

    override fun paintComponent(graphics: Graphics) {
        super.paintComponent(graphics)

        // set up the drawing panel
        val graphics2D = graphics as Graphics2D
        graphics2D.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        graphics2D.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)

        // fill the background for entire canvas
        graphics2D.color = Colors.BACKGROUND_COLOR.color
        graphics2D.fillRect(0, 0, this.width, this.height)

        // draw un-highlighted and highlighted self loops
        this.visibleEdges
                .filter { it.sourceNode === it.targetNode }
                .forEach { drawSelfLoopEdge(graphics2D, it, isNodeHighlighted(it.sourceNode)) }

        // draw un-highlighted edgesMap
        this.visibleEdges
                .filter { it.sourceNode !== it.targetNode &&
                        !isNodeHighlighted(it.sourceNode) && !isNodeHighlighted(it.targetNode) }
                .forEach { drawNonLoopEdge(graphics2D, it, Colors.UN_HIGHLIGHTED_COLOR.color) }

        // draw upstream/downstream edgesMap
        val highlightedNodes = this.visibleNodes.filter { isNodeHighlighted(it) }.toSet()
        val filteredUpstreamEdges = highlightedNodes.flatMap { it.inEdges.values }.toSet()
                .filter { isNodeVisibleBasedOnVisibilityFilters(it.sourceNode) }
        val filteredDownstreamEdges = highlightedNodes.flatMap { it.outEdges.values }.toSet()
                .filter { isNodeVisibleBasedOnVisibilityFilters(it.sourceNode) }
        filteredUpstreamEdges.forEach { drawNonLoopEdge(graphics2D, it, Colors.UPSTREAM_COLOR.color) }
        filteredDownstreamEdges.forEach { drawNonLoopEdge(graphics2D, it, Colors.DOWNSTREAM_COLOR.color) }

        // draw un-highlighted labels
        val filteredUpstreamNodes = filteredUpstreamEdges.map { it.sourceNode }.toSet()
        val filteredDownstreamNodes = filteredDownstreamEdges.map { it.targetNode }.toSet()
        val unHighlightedNodes = this.visibleNodes
                .filter { !isNodeHighlighted(it) && !filteredUpstreamNodes.contains(it)
                        && !filteredDownstreamNodes.contains(it) }
                .toSet()
        unHighlightedNodes.forEach { drawNodeLabels(graphics2D, it, Colors.NEUTRAL_COLOR.color, false) }

        // draw un-highlighted nodesMap (upstream/downstream nodesMap are excluded)
        this.nodeShapesMap.clear()
        unHighlightedNodes
                .filter { !filteredUpstreamNodes.contains(it) && !filteredDownstreamNodes.contains(it) }
                .forEach { drawNode(graphics2D, it, Colors.UN_HIGHLIGHTED_COLOR.color) }

        // draw upstream/downstream label and nodesMap
        filteredUpstreamNodes.forEach { drawNodeLabels(graphics2D, it, Colors.UPSTREAM_COLOR.color, false) }
        filteredDownstreamNodes.forEach { drawNodeLabels(graphics2D, it, Colors.DOWNSTREAM_COLOR.color, false) }
        filteredUpstreamNodes.forEach { drawNode(graphics2D, it, Colors.UPSTREAM_COLOR.color) }
        filteredDownstreamNodes.forEach { drawNode(graphics2D, it, Colors.DOWNSTREAM_COLOR.color) }

        // draw highlighted node and label
        this.visibleNodes
                .filter { isNodeHighlighted(it) }
                .forEach {
                    drawNode(graphics2D, it, Colors.HIGHLIGHTED_COLOR.color)
                    drawNodeLabels(graphics2D, it, Colors.HIGHLIGHTED_COLOR.color, true)
                }

        // draw legend
        if (this.callGraphToolWindow.isLegendNeeded()) {
            val legend = if (this.callGraphToolWindow.isNodeColorByAccess()) {
                listOf(PsiModifier.PUBLIC, PsiModifier.PROTECTED, PsiModifier.PACKAGE_LOCAL, PsiModifier.PRIVATE)
                        .map { this.methodAccessLabelMap.getValue(it) to this.methodAccessColorMap.getValue(it) }
            } else {
                emptyList()
            }
            drawLegend(graphics2D, legend)
        }
    }

    fun reset(graph: Graph) {
        this.graph = graph
        this.visibleNodes.clear()
        this.visibleNodes.addAll(graph.getNodes())
        this.visibleEdges.clear()
        this.visibleEdges.addAll(graph.getEdges())
        this.nodeShapesMap.clear()
        this.hoveredNode = null
        this.cameraOrigin.setLocation(defaultCameraOrigin)
        this.zoomRatio.setLocation(this.defaultZoomRatio, this.defaultZoomRatio)
    }

    fun setHoveredNode(node: Node?): Canvas {
        if (this.hoveredNode !== node) {
            this.hoveredNode = node
            repaint()
        }
        return this
    }

    fun toggleClickedNode(node: Node) {
        this.callGraphToolWindow.toggleFocusedMethod(node.method)
        repaint()
    }

    fun clearClickedNodes() {
        this.callGraphToolWindow.clearFocusedMethods()
        repaint()
    }

    fun zoomAtPoint(point: Point2D.Float, xZoomFactor: Float, yZoomFactor: Float) {
        this.cameraOrigin.setLocation(
                xZoomFactor * this.cameraOrigin.x + (xZoomFactor - 1) * point.x,
                yZoomFactor * this.cameraOrigin.y + (yZoomFactor - 1) * point.y
        )
        this.zoomRatio.x *= xZoomFactor
        this.zoomRatio.y *= yZoomFactor
        repaint()
    }

    fun getNodeUnderPoint(point: Point2D): Node? {
        return this.nodeShapesMap
                .filter { (shape, _) -> shape.contains(point.x, point.y) }
                .values
                .firstOrNull()
    }

    fun fitCanvasToView() {
        val blueprint = this.graph.getNodes().associateBy({ it.id }, { it.rawLayoutPoint })
        val bestFitBlueprint = Utils.fitLayoutToViewport(blueprint)
        Utils.applyLayoutBlueprintToGraph(bestFitBlueprint, this.graph)
        this.cameraOrigin.setLocation(defaultCameraOrigin)
        this.zoomRatio.setLocation(defaultZoomRatio, defaultZoomRatio)
        repaint()
    }

    fun fitCanvasToBestRatio() {
        // set every node coordinate to its original raw layout by GraphViz
        this.graph.getNodes().forEach { it.point.setLocation(it.rawLayoutPoint) }
        this.cameraOrigin.setLocation(defaultCameraOrigin)
        this.zoomRatio.setLocation(defaultZoomRatio, defaultZoomRatio)
        repaint()
    }

    fun getNodesCount() = this.graph.getNodes().size

    fun filterChangeHandler() {
        this.visibleNodes.clear()
        this.visibleNodes.addAll(this.graph.getNodes().filter(this::isNodeVisibleBasedOnVisibilityFilters))
        this.visibleEdges.clear()
        this.visibleEdges.addAll(graph.getEdges()
                .filter { this.visibleNodes.contains(it.sourceNode) && this.visibleNodes.contains(it.targetNode) })
        repaint()
    }

    private fun isNodeVisibleBasedOnVisibilityFilters(node: Node): Boolean {
        val method = node.method
        val isVisibleAccessLevel = when {
            Utils.isPublic(method) -> this.callGraphToolWindow.isFilterAccessPublicChecked()
            Utils.isProtected(method) -> this.callGraphToolWindow.isFilterAccessProtectedChecked()
            Utils.isPackageLocal(method) -> this.callGraphToolWindow.isFilterAccessPackageLocalChecked()
            Utils.isPrivate(method) -> this.callGraphToolWindow.isFilterAccessPrivateChecked()
            else -> true
        }
        val isExternalMethod = Utils.getSourceRoot(method.containingFile.virtualFile) == null
        val isVisibleExternal = !isExternalMethod || this.callGraphToolWindow.isFilterExternalChecked()

        return isVisibleAccessLevel && isVisibleExternal
    }

    private fun toCameraView(point: Point2D.Float): Point2D.Float {
        val canvasSize = this.callGraphToolWindow.getCanvasSize()
        return Point2D.Float(
                this.zoomRatio.x * point.x * canvasSize.width - this.cameraOrigin.x,
                this.zoomRatio.y * point.y * canvasSize.height - this.cameraOrigin.y
        )
    }

    private fun isNodeHighlighted(node: Node): Boolean {
        return this.hoveredNode === node || this.callGraphToolWindow.isFocusedMethod(node.method)
    }

    private fun drawLegend(graphics2D: Graphics2D, labels: List<Pair<String, Color>>) {
        val singleLabelHeight = graphics2D.fontMetrics.ascent + graphics2D.fontMetrics.descent
        val boundingBoxLowerLeft = Point2D.Float(
                0f,
                labels.size * singleLabelHeight.toFloat()
        )
        drawLabels(graphics2D, boundingBoxLowerLeft, labels, Colors.BACKGROUND_COLOR.color,
                Colors.UN_HIGHLIGHTED_COLOR.color, 1)
    }

    private fun drawSelfLoopEdge(graphics2D: Graphics2D, edge: Edge, isHighlighted: Boolean) {
        val sourceNodeCenter = toCameraView(edge.sourceNode.point)
        drawSelfLoop(graphics2D, sourceNodeCenter, isHighlighted)
    }

    private fun drawNonLoopEdge(graphics2D: Graphics2D, edge: Edge, color: Color) {
        val sourceNodeCenter = toCameraView(edge.sourceNode.point)
        val targetNodeCenter = toCameraView(edge.targetNode.point)
        drawLine(graphics2D, sourceNodeCenter, targetNodeCenter, color)
        drawLineArrow(graphics2D, sourceNodeCenter, targetNodeCenter, color)
    }

    private fun drawNode(graphics2D: Graphics2D, node: Node, outlineColor: Color) {
        val nodeCenter = toCameraView(node.point)
        val backgroundColor = getNodeBackgroundColor(node)
        val nodeShape = drawCircle(graphics2D, nodeCenter, backgroundColor, outlineColor)
        this.nodeShapesMap[nodeShape] = node
    }

    private fun getNodeBackgroundColor(node: Node): Color {
        if (this.callGraphToolWindow.isNodeColorByAccess()) {
            return this.methodAccessColorMap.entries
                    .firstOrNull { (accessLevel, _) -> node.method.modifierList.hasModifierProperty(accessLevel) }
                    ?.value
                    ?: Colors.BACKGROUND_COLOR.color
        } else if (this.callGraphToolWindow.isNodeColorByClassName()) {
            val psiClass = node.method.containingClass
            if (psiClass != null) {
                val hashIndex = psiClass.hashCode() % this.heatMapColors.size
                return this.heatMapColors[hashIndex]
            }
        }
        return Colors.BACKGROUND_COLOR.color
    }

    private fun createNodeLabels(node: Node, signatureColor: Color, isNodeHovered: Boolean): List<Pair<String, Color>> {
        // draw labels in top-down order
        val labels = mutableListOf<Pair<String, Color>>()
        // file path
        if (this.callGraphToolWindow.isRenderFunctionFilePath(isNodeHovered)) {
            labels.add(node.filePath to Colors.UN_HIGHLIGHTED_COLOR.color)
        }
        // package name
        if (this.callGraphToolWindow.isRenderFunctionPackageName(isNodeHovered)) {
            labels.add(node.packageName to Colors.UN_HIGHLIGHTED_COLOR.color)
        }
        // function signature
        val signature = if (isNodeHovered) node.signature else node.method.name
        labels.add(signature to signatureColor)
        return labels
    }

    private fun drawNodeLabels(graphics2D: Graphics2D, node: Node, labelColor: Color, isNodeHovered: Boolean) {
        // create labels
        val labels = createNodeLabels(node, labelColor, isNodeHovered)
        val fontMetrics = graphics2D.fontMetrics
        val halfLabelHeight = 0.5f * (fontMetrics.ascent + fontMetrics.descent)
        val nodeCenter = toCameraView(node.point)
        val boundingBoxLowerLeft = Point2D.Float(
                nodeCenter.x + 4 * nodeRadius,
                nodeCenter.y + halfLabelHeight
        )
        val backgroundColor =
                if (this.callGraphToolWindow.isQueried(node.method.name)) Colors.HIGHLIGHTED_BACKGROUND_COLOR.color
                else Colors.BACKGROUND_COLOR.color
        val borderColor = if (isNodeHovered) Colors.UN_HIGHLIGHTED_COLOR.color else Colors.BACKGROUND_COLOR.color
        drawLabels(graphics2D, boundingBoxLowerLeft, labels, backgroundColor, borderColor, 2)
    }

    private fun drawLabels(
            graphics2D: Graphics2D,
            boundingBoxLowerLeft: Point2D.Float,
            labels: List<Pair<String, Color>>,
            backgroundColor: Color,
            borderColor: Color,
            padding: Int
    ) {
        val fontMetrics = graphics2D.fontMetrics
        val singleLabelHeight = fontMetrics.ascent + fontMetrics.descent
        val boundingBoxWidth = labels
                .map { (text, _) -> fontMetrics.getStringBounds(text, graphics2D).width.toInt() }
                .max()
                ?: 0
        val boundingBoxHeight = labels.size * singleLabelHeight
        val boundingBoxUpperLeft = Point2D.Float(
                boundingBoxLowerLeft.x,
                boundingBoxLowerLeft.y - 2 * padding - boundingBoxHeight
        )
        val boundingBoxUpperRight = Point2D.Float(
                boundingBoxUpperLeft.x + 2 * padding + boundingBoxWidth,
                boundingBoxUpperLeft.y
        )
        val boundingBoxLowerRight = Point2D.Float(
                boundingBoxUpperRight.x,
                boundingBoxLowerLeft.y
        )
        // fill background to overall bounding box
        graphics2D.color = backgroundColor
        graphics2D.fillRect(
                (boundingBoxUpperLeft.x + 1).toInt(),
                (boundingBoxUpperLeft.y + 1).toInt(),
                2 * padding + boundingBoxWidth,
                2 * padding + boundingBoxHeight
        )
        // draw border if the node is hovered
        drawLine(graphics2D, boundingBoxLowerLeft, boundingBoxUpperLeft, borderColor)
        drawLine(graphics2D, boundingBoxUpperLeft, boundingBoxUpperRight, borderColor)
        drawLine(graphics2D, boundingBoxUpperRight, boundingBoxLowerRight, borderColor)
        drawLine(graphics2D, boundingBoxLowerRight, boundingBoxLowerLeft, borderColor)
        // draw text
        labels.reversed().mapIndexed { index, (text, color) ->
            val labelLowerLeft = Point2D.Float(
                    boundingBoxLowerLeft.x + padding,
                    boundingBoxLowerLeft.y - padding - fontMetrics.descent - index * singleLabelHeight
            )
            drawText(graphics2D, labelLowerLeft, text, color)
        }
    }

    private fun drawCircle(
            graphics2D: Graphics2D,
            circleCenter: Point2D.Float,
            backgroundColor: Color,
            outlineColor: Color): Shape {
        // create node shape
        val upperLeft = Point2D.Float(
                circleCenter.x - this.nodeRadius,
                circleCenter.y - this.nodeRadius
        )
        val diameter = 2 * this.nodeRadius
        val shape = Ellipse2D.Float(
                upperLeft.x,
                upperLeft.y,
                diameter,
                diameter
        )
        // fill node with color
        graphics2D.color = backgroundColor
        graphics2D.fill(shape)
        // draw the outline
        graphics2D.color = outlineColor
        val strokedShape = this.solidLineStroke.createStrokedShape(shape)
        graphics2D.draw(strokedShape)
        return shape
    }

    private fun drawText(graphics2D: Graphics2D, textLowerLeft: Point2D.Float, text: String, textColor: Color) {
        graphics2D.color = textColor
        graphics2D.drawString(text, textLowerLeft.x, textLowerLeft.y)
    }

    private fun drawLine(
            graphics2D: Graphics2D,
            sourcePoint: Point2D.Float,
            targetPoint: Point2D.Float,
            lineColor: Color) {
        val shape = Line2D.Float(sourcePoint, targetPoint)
        val strokedShape = this.solidLineStroke.createStrokedShape(shape)
        graphics2D.color = lineColor
        graphics2D.draw(strokedShape)
    }

    private fun drawSelfLoop(graphics2D: Graphics2D, nodeCenter: Point2D.Float, isHighlighted: Boolean) {
        // draw circle shape
        val selfLoopRadius = 10f
        val selfLoopDiameter = 2 * selfLoopRadius
        val loopUpperLeft = Point2D.Float(
                nodeCenter.x - selfLoopRadius,
                nodeCenter.y - selfLoopDiameter
        )
        val upstreamHalfArc = Arc2D.Float(
                loopUpperLeft.x,
                loopUpperLeft.y,
                selfLoopDiameter,
                selfLoopDiameter,
                90f,
                180f,
                Arc2D.OPEN
        )
        val downstreamHalfArc = Arc2D.Float(
                loopUpperLeft.x,
                loopUpperLeft.y,
                selfLoopDiameter,
                selfLoopDiameter,
                270f,
                180f,
                Arc2D.OPEN
        )
        val strokedUpstreamHalfShape = this.solidLineStroke.createStrokedShape(upstreamHalfArc)
        val strokedDownstreamHalfShape = this.solidLineStroke.createStrokedShape(downstreamHalfArc)
        val upstreamHalfLoopColor =
                if (isHighlighted) Colors.UPSTREAM_COLOR.color
                else Colors.UN_HIGHLIGHTED_COLOR.color
        val downstreamHalfLoopColor =
                if (isHighlighted) Colors.DOWNSTREAM_COLOR.color
                else Colors.UN_HIGHLIGHTED_COLOR.color
        graphics2D.color = upstreamHalfLoopColor
        graphics2D.draw(strokedUpstreamHalfShape)
        graphics2D.color = downstreamHalfLoopColor
        graphics2D.draw(strokedDownstreamHalfShape)
        // draw arrow
        val arrowCenter = Point2D.Float(nodeCenter.x, nodeCenter.y - selfLoopDiameter)
        drawArrow(graphics2D, arrowCenter, Math.PI, downstreamHalfLoopColor)
    }

    private fun drawLineArrow(
            graphics2D: Graphics2D,
            sourcePoint: Point2D.Float,
            targetPoint: Point2D.Float,
            arrowColor: Color
    ) {
        val angle = Math.atan2((targetPoint.y - sourcePoint.y).toDouble(), (targetPoint.x - sourcePoint.x).toDouble())
        val arrowCenter = Point2D.Float(
                0.5f * (sourcePoint.x + targetPoint.x),
                0.5f * (sourcePoint.y + targetPoint.y)
        )
        drawArrow(graphics2D, arrowCenter, angle, arrowColor)
    }

    private fun drawArrow(
            graphics2D: Graphics2D,
            center: Point2D.Float,
            angle: Double,
            arrowColor: Color
    ) {
        val arrowSize = 5f
        val midPoint = Point2D.Float(
                center.x + arrowSize * Math.cos(angle).toFloat(),
                center.y + arrowSize * Math.sin(angle).toFloat()
        )
        val upperTipAngle = angle + Math.PI * 2 / 3
        val upperTipPoint = Point2D.Float(
                center.x + arrowSize * Math.cos(upperTipAngle).toFloat(),
                center.y + arrowSize * Math.sin(upperTipAngle).toFloat()
        )
        val lowerTipAngle = angle - Math.PI * 2 / 3
        val lowerTipPoint = Point2D.Float(
                center.x + arrowSize * Math.cos(lowerTipAngle).toFloat(),
                center.y + arrowSize * Math.sin(lowerTipAngle).toFloat()
        )
        val points = listOf(midPoint, upperTipPoint, lowerTipPoint, midPoint)
        val xPoints = points.map { Math.round(it.x) }
        val yPoints = points.map { Math.round(it.y) }
        graphics2D.color = arrowColor
        graphics2D.fillPolygon(xPoints.toIntArray(), yPoints.toIntArray(), xPoints.size)
    }
}
