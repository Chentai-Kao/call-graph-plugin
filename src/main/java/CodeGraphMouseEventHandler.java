import org.graphstream.graph.Node;
import org.graphstream.ui.geom.Point3;
import org.graphstream.ui.graphicGraph.GraphicElement;
import org.graphstream.ui.view.util.DefaultMouseManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.event.MouseEvent;

public class CodeGraphMouseEventHandler extends DefaultMouseManager {
    private Point3 lastMousePositionPx;

    @Override
    public void mouseClicked(MouseEvent event) {
        System.out.println("clicked");
        Node node = getNodeUnderMouse(event);
        if (node != null) {
            System.out.println(String.format("clicked on node %s", node.getId()));
        }
    }

    @Override
    public void mousePressed(MouseEvent event) {
        System.out.println("pressed");
        lastMousePositionPx = new Point3(event.getX(), event.getY());
    }

    @Override
    public void mouseReleased(MouseEvent event) {
        System.out.println("released");
    }

    @Override
    public void mouseEntered(MouseEvent event) {
        System.out.println("entered");
    }

    @Override
    public void mouseExited(MouseEvent event) {
        System.out.println("exited");
    }

    @Override
    public void mouseDragged(MouseEvent event) {
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

    @Override
    public void mouseMoved(MouseEvent event) {
        System.out.println(String.format("moved %d %d", event.getX(), event.getY()));
    }

    @Nullable
    private Node getNodeUnderMouse(MouseEvent event) {
        GraphicElement element = this.view.findNodeOrSpriteAt(event.getX(), event.getY());
        return element == null ? null : this.graph.getNode(element.getId());
    }

    @NotNull
    private Point3 pxToGu(@NotNull Point3 point) {
        return this.view.getCamera().transformPxToGu(point.x, point.y);
    }
}
