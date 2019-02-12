import org.graphstream.graph.Node;
import org.graphstream.ui.geom.Point3;
import org.graphstream.ui.graphicGraph.GraphicElement;
import org.graphstream.ui.graphicGraph.GraphicGraph;
import org.graphstream.ui.swing_viewer.ViewPanel;
import org.graphstream.ui.swing_viewer.util.DefaultMouseManager;
import org.graphstream.ui.view.View;
import org.graphstream.ui.view.util.InteractiveElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.awt.event.MouseWheelListener;
import java.util.EnumSet;

public class CodeGraphMouseEventHandler extends DefaultMouseManager implements MouseWheelListener {
    private Point3 lastMousePositionPx;

    // construction
    public void init(@NotNull GraphicGraph graph, @NotNull View view) {
        super.init(graph, view);
        ((ViewPanel) this.view).addMouseWheelListener(this);
    }

    // destruction
    public void release() {
        super.release();
        ((ViewPanel) this.view).removeMouseWheelListener(this);
    }

    public void mouseClicked(@NotNull MouseEvent event) {
        Node node = getNodeUnderMouse(event);
        if (node != null) {
            System.out.println(String.format("clicked on node %s: %s", node.getId(), node.getAttribute("ui.label")));
        }
    }

    public void mousePressed(@NotNull MouseEvent event) {
        this.lastMousePositionPx = new Point3(event.getX(), event.getY());
    }

    public void mouseReleased(@NotNull MouseEvent event) {
    }

    public void mouseEntered(@NotNull MouseEvent event) {
    }

    public void mouseExited(@NotNull MouseEvent event) {
    }

    public void mouseDragged(@NotNull MouseEvent event) {
        Point3 currentMousePositionPx = new Point3(event.getX(), event.getY());
        if (!currentMousePositionPx.equals(this.lastMousePositionPx)) {
            Point3 currentCameraCenterGu = this.view.getCamera().getViewCenter();
            Point3 currentMousePositionGu = pxToGu(currentMousePositionPx);
            Point3 lastMousePositionGu = pxToGu(this.lastMousePositionPx);
            this.view.getCamera().setViewCenter(
                    currentCameraCenterGu.x - currentMousePositionGu.x + lastMousePositionGu.x,
                    currentCameraCenterGu.y - currentMousePositionGu.y + lastMousePositionGu.y,
                    0
            );
            this.lastMousePositionPx = currentMousePositionPx;
        }
    }

    public void mouseMoved(@NotNull MouseEvent event) {
    }

    public void mouseWheelMoved(@NotNull MouseWheelEvent event) {
        // zoom the camera
        int scrollRotation = event.getWheelRotation(); // 1 if scroll down, -1 otherwise
        double zoomFactor = Math.pow(1.25, scrollRotation);
        double currentZoomRatio = this.view.getCamera().getViewPercent();
        this.view.getCamera().setViewPercent(currentZoomRatio * zoomFactor);

        // move the view to the mouse position
        Point3 mousePositionPx = new Point3(event.getX(), event.getY());
        Point3 mousePositionGu = pxToGu(mousePositionPx);
        Point3 cameraCenterGu = this.view.getCamera().getViewCenter();
        this.view.getCamera().setViewCenter(
                zoomFactor * cameraCenterGu.x + (1 - zoomFactor) * mousePositionGu.x,
                zoomFactor * cameraCenterGu.y + (1 - zoomFactor) * mousePositionGu.y,
                0
        );
    }

    @Nullable
    private Node getNodeUnderMouse(@NotNull MouseEvent event) {
        GraphicElement element =
                this.view.findGraphicElementAt(EnumSet.of(InteractiveElement.NODE), event.getX(), event.getY());
        return element == null ? null : this.graph.getNode(element.getId());
    }

    @NotNull
    private Point3 pxToGu(@NotNull Point3 point) {
        return this.view.getCamera().transformPxToGu(point.x, point.y);
    }
}
