import org.jetbrains.annotations.NotNull;

import java.awt.event.*;
import java.awt.geom.Point2D;

class MouseEventHandler implements MouseListener, MouseMotionListener, MouseWheelListener {
    private Canvas canvas;
    private Point2D lastMousePosition;

    MouseEventHandler(@NotNull Canvas canvas) {
        super();
        this.canvas = canvas;
    }

    public void mouseClicked(@NotNull MouseEvent event) {
        Node node = this.canvas.getNodeUnderPoint(event.getPoint());
        this.canvas.setClickedNode(node);
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
        // zoom the camera
        int scrollRotation = event.getWheelRotation(); // 1 if scroll down, -1 otherwise
        float zoomFactor = (float) Math.pow(1.25, -scrollRotation);
        float currentZoomRatio = this.canvas.getZoomRatio();
        float newZoomRatio = currentZoomRatio * zoomFactor;
        // move the view to the mouse position
        Point2D mousePosition = new Point2D.Float(event.getX(), event.getY());
        Point2D cameraOrigin = this.canvas.getCameraOrigin();
        Point2D newCameraOrigin = new Point2D.Float(
                (float) (zoomFactor * cameraOrigin.getX() + (zoomFactor - 1) * mousePosition.getX()),
                (float) (zoomFactor * cameraOrigin.getY() + (zoomFactor - 1) * mousePosition.getY())
        );
        // repaint
        this.canvas.setZoomRatio(newZoomRatio)
                .setCameraOrigin(newCameraOrigin)
                .repaint();
    }
}
