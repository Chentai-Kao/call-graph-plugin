package callgraph

import java.awt.event.*
import java.awt.geom.Point2D

class MouseEventHandler(private val canvas: Canvas): MouseListener, MouseMotionListener, MouseWheelListener {
    private val lastMousePosition = Point2D.Float()

    override fun mouseClicked(event: MouseEvent) {
        val node = this.canvas.getNodeUnderPoint(event.point)
        if (node == null) {
            this.canvas.clearClickedNodes()
        } else {
            this.canvas.toggleClickedNode(node)
        }
    }

    override fun mousePressed(event: MouseEvent) {
        this.lastMousePosition.setLocation(event.x.toFloat(), event.y.toFloat())
    }

    override fun mouseReleased(event: MouseEvent) {
    }

    override fun mouseEntered(event: MouseEvent) {
    }

    override fun mouseExited(event: MouseEvent) {
    }

    override fun mouseDragged(event: MouseEvent) {
        val currentMousePosition = Point2D.Float(event.x.toFloat(), event.y.toFloat())
        if (currentMousePosition != this.lastMousePosition) {
            val currentCameraOrigin = this.canvas.cameraOrigin
            val newCameraOrigin = Point2D.Float(
                    currentCameraOrigin.x - currentMousePosition.x + this.lastMousePosition.x,
                    currentCameraOrigin.y - currentMousePosition.y + this.lastMousePosition.y
            )
            this.canvas.cameraOrigin.setLocation(newCameraOrigin)
            this.canvas.repaint()
            this.lastMousePosition.setLocation(currentMousePosition)
        }
    }

    override fun mouseMoved(event: MouseEvent) {
        val node = this.canvas.getNodeUnderPoint(event.point)
        this.canvas.setHoveredNode(node)
    }

    override fun mouseWheelMoved(event: MouseWheelEvent) {
        val scrollRotation = event.wheelRotation // 1 if scroll down, -1 otherwise
        val zoomFactor = Math.pow(1.25, -scrollRotation.toDouble()).toFloat()
        val mousePosition = Point2D.Float(event.x.toFloat(), event.y.toFloat())
        this.canvas.zoomAtPoint(mousePosition, zoomFactor, zoomFactor)
    }
}
