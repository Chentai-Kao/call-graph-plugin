import org.graphstream.graph.Node;
import org.graphstream.ui.geom.Point3;
import org.graphstream.ui.graphicGraph.GraphicElement;
import org.graphstream.ui.view.util.DefaultMouseManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.event.MouseEvent;

public class CodeGraphMouseEventHandler extends DefaultMouseManager {
    private JPanel canvasPanel;
    private Point3 preDragCameraCenter;
    private Point3 preDragMousePosition;
    private Point3 lastMousePosition;

    CodeGraphMouseEventHandler(@NotNull JPanel canvasPanel) {
        this.canvasPanel = canvasPanel;
    }

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
        preDragCameraCenter = guToPx(this.view.getCamera().getViewCenter());
        preDragMousePosition = new Point3(event.getX(), event.getY());
        lastMousePosition = null;
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
        this.view.getCamera().setAutoFitView(false);
        Point3 currentMousePosition = new Point3(event.getX(), event.getY());
        boolean isMouseMoved = lastMousePosition == null ||
                (currentMousePosition.x != lastMousePosition.x || currentMousePosition.y != lastMousePosition.y);
        if (isMouseMoved) {
            System.out.println(String.format("dragged %d %d", event.getX(), event.getY()));
            Point3 postDragCameraCenter = new Point3(
                    preDragCameraCenter.x - currentMousePosition.x + preDragMousePosition.x,
                    preDragCameraCenter.y - currentMousePosition.y + preDragMousePosition.y
            );
            Point3 postDragCameraCenterGu = pxToGu(postDragCameraCenter);
            this.view.getCamera().setViewCenter(postDragCameraCenterGu.x, postDragCameraCenterGu.y, 0);
            System.out.println(String.format("mouse pre  %.2f %.2f", preDragMousePosition.x, preDragMousePosition.y));
            System.out.println(String.format("mouse post %.2f %.2f", currentMousePosition.x, currentMousePosition.y));
            System.out.println(String.format("center pre %.2f %.2f", preDragCameraCenter.x, preDragCameraCenter.y));
            System.out.println(String.format("center new %.2f %.2f", postDragCameraCenter.x, postDragCameraCenter.y));
            lastMousePosition = currentMousePosition;
        }
        canvasPanel.repaint();
        canvasPanel.revalidate();
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

    @NotNull
    private Point3 guToPx(@NotNull Point3 point) {
        return this.view.getCamera().transformGuToPx(point.x, point.y, 0);
    }
}
