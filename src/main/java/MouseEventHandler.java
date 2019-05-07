import org.jetbrains.annotations.NotNull;

import java.awt.event.*;
import java.awt.geom.Point2D;

class MouseEventHandler implements MouseListener, MouseMotionListener, MouseWheelListener {
    private final Canvas canvas;
    private Point2D lastMousePosition;

    MouseEventHandler(@NotNull Canvas canvas) {
        super();
        this.canvas = canvas;
    }

    public void mouseClicked(@NotNull MouseEvent event) {
        Node node = this.canvas.getNodeUnderPoint(event.getPoint());
        if (node == null) {
            this.canvas.clearClickedNodes();
        } else {
            this.canvas.toggleClickedNode(node);
        }
    }

    public void mousePressed(@NotNull MouseEvent event) {
        this.lastMousePosition = new Point2D.Float(event.getX(), event.getY());
    }

    public void mouseReleased(@NotNull MouseEvent event) {
    }

    public void mouseEntered(@NotNull MouseEvent event) {
    }

    public void mouseExited(@NotNull MouseEvent event) {
    }

    public void mouseDragged(@NotNull MouseEvent event) {
        Point2D.Float currentMousePosition = new Point2D.Float(event.getX(), event.getY());
        if (!currentMousePosition.equals(this.lastMousePosition)) {
            Point2D currentCameraOrigin = this.canvas.getCameraOrigin();
            Point2D newCameraOrigin = new Point2D.Float(
                    (float) (currentCameraOrigin.getX() - currentMousePosition.getX() + this.lastMousePosition.getX()),
                    (float) (currentCameraOrigin.getY() - currentMousePosition.getY() + this.lastMousePosition.getY())
            );
            this.canvas
                    .setCameraOrigin(newCameraOrigin)
                    .repaint();
            this.lastMousePosition = currentMousePosition;
        }
    }

    public void mouseMoved(@NotNull MouseEvent event) {
        Node node = this.canvas.getNodeUnderPoint(event.getPoint());
        this.canvas.setHoveredNode(node);
    }

    public void mouseWheelMoved(@NotNull MouseWheelEvent event) {
        int scrollRotation = event.getWheelRotation(); // 1 if scroll down, -1 otherwise
        float zoomFactor = (float) Math.pow(1.25, -scrollRotation);
        Point2D mousePosition = new Point2D.Float(event.getX(), event.getY());
        this.canvas.zoomAtPoint(mousePosition, zoomFactor, zoomFactor);
    }
}
