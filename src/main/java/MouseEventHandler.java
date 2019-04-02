import javafx.geometry.Point2D;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.event.*;
import java.util.EnumSet;

public class MouseEventHandler implements MouseListener, MouseMotionListener, MouseWheelListener {
    private Point2D lastMousePositionPx;

    // construction
//    public void init(@NotNull GraphicGraph graph, @NotNull View view) {
//        super.init(graph, view);
//        ((ViewPanel) this.view).addMouseWheelListener(this);
//    }

    // destruction
//    public void release() {
//        super.release();
//        ((ViewPanel) this.view).removeMouseWheelListener(this);
//    }

    public void mouseClicked(@NotNull MouseEvent event) {
        System.out.println("clicked");
        Node node = getNodeUnderMouse(event);
        if (node != null) {
            System.out.println(String.format("clicked on node %s: %s", node.getId(), node.getLabel()));
        }
    }

    public void mousePressed(@NotNull MouseEvent event) {
        System.out.println("pressed");
        this.lastMousePositionPx = new Point2D(event.getX(), event.getY());
    }

    public void mouseReleased(@NotNull MouseEvent event) {
        System.out.println("released");
    }

    public void mouseEntered(@NotNull MouseEvent event) {
        System.out.println("entered");
    }

    public void mouseExited(@NotNull MouseEvent event) {
        System.out.println("exited");
    }

    public void mouseDragged(@NotNull MouseEvent event) {
        System.out.println("dragged");
//        Point2D currentMousePositionPx = new Point2D(event.getX(), event.getY());
//        if (!currentMousePositionPx.equals(this.lastMousePositionPx)) {
//            Point2D currentCameraCenterGu = this.view.getCamera().getViewCenter();
//            Point2D currentMousePositionGu = pxToGu(currentMousePositionPx);
//            Point2D lastMousePositionGu = pxToGu(this.lastMousePositionPx);
//            this.view.getCamera().setViewCenter(
//                    currentCameraCenterGu.getX() - currentMousePositionGu.getX() + lastMousePositionGu.getX(),
//                    currentCameraCenterGu.getY() - currentMousePositionGu.getY() + lastMousePositionGu.getY(),
//                    0
//            );
//            this.lastMousePositionPx = currentMousePositionPx;
//        }
    }

    public void mouseMoved(@NotNull MouseEvent event) {
        System.out.println("moved");
    }

    public void mouseWheelMoved(@NotNull MouseWheelEvent event) {
        System.out.println("wheel moved");
//        // zoom the camera
//        int scrollRotation = event.getWheelRotation(); // 1 if scroll down, -1 otherwise
//        double zoomFactor = Math.pow(1.25, scrollRotation);
//        double currentZoomRatio = this.view.getCamera().getViewPercent();
//        this.view.getCamera().setViewPercent(currentZoomRatio * zoomFactor);
//
//        // move the view to the mouse position
//        Point2D mousePositionPx = new Point2D(event.getX(), event.getY());
//        Point2D mousePositionGu = pxToGu(mousePositionPx);
//        Point2D cameraCenterGu = this.view.getCamera().getViewCenter();
//        this.view.getCamera().setViewCenter(
//                zoomFactor * cameraCenterGu.getX() + (1 - zoomFactor) * mousePositionGu.getX(),
//                zoomFactor * cameraCenterGu.getY() + (1 - zoomFactor) * mousePositionGu.getY(),
//                0
//        );
    }

    @Nullable
    private Node getNodeUnderMouse(@NotNull MouseEvent event) {
//        GraphicElement element =
//                this.view.findGraphicElementAt(EnumSet.of(InteractiveElement.NODE), event.getX(), event.getY());
//        return element == null ? null : this.graph.getNode(element.getId());
        return null;
    }

    @NotNull
    private Point2D pxToGu(@NotNull Point2D point) {
//        return this.view.getCamera().transformPxToGu(point.getX(), point.getY());
        return new Point2D(0, 0);
    }
}
