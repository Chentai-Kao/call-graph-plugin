import com.intellij.psi.PsiMethod;
import org.jetbrains.annotations.NotNull;

import java.awt.event.*;
import java.awt.geom.Point2D;
import java.util.stream.Collectors;
import java.util.stream.Stream;

class MouseEventHandler implements MouseListener, MouseMotionListener, MouseWheelListener {
    private Canvas canvas;
    private CodeGraphToolWindow codeGraphToolWindow;
    private Point2D lastMousePosition;

    // construction
    void init(@NotNull Canvas canvas, @NotNull CodeGraphToolWindow codeGraphToolWindow) {
        this.canvas = canvas;
        this.codeGraphToolWindow = codeGraphToolWindow;
    }

    public void mouseClicked(@NotNull MouseEvent event) {
        Node node = this.canvas.getNodeUnderPoint(event.getPoint());
        if (node == null) {
            this.codeGraphToolWindow.setFunctionDocCommentLabelText("");
            this.codeGraphToolWindow.setFunctionInfoLabelText("");
        } else {
            System.out.println(String.format("clicked on node %s: %s", node.getId(), node.getLabel()));
            // switch to navigate tab
            this.codeGraphToolWindow.focusNavigateTab();

            // show function signature
            PsiMethod method = node.getMethod();
            String functionReturnType = method.getReturnType() == null
                    ? "" : htmlBold(extractLastPart(method.getReturnType().getCanonicalText()));
            String functionName = method.getName();
            String functionParameters = Stream.of(method.getParameterList().getParameters())
                    .map(parameter -> String.format("%s %s",
                            htmlBold(extractLastPart(parameter.getType().getCanonicalText())),
                            parameter.getName()))
                    .collect(Collectors.joining(", "));
            String functionSignature = String.format("%s %s(%s)", functionReturnType, functionName, functionParameters);
            String functionSignatureHtml = toHtml(functionSignature);
            this.codeGraphToolWindow.setFunctionInfoLabelText(functionSignatureHtml);

            // show function doc comment
            String functionDocComment = method.getDocComment() == null ? "" : method.getDocComment().getText();
            String functionDocCommentHtml = toHtml(functionDocComment);
            this.codeGraphToolWindow.setFunctionDocCommentLabelText(functionDocCommentHtml);
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
            Point2D currentCameraCenter = this.canvas.getCameraCenter();
            Point2D newCameraCenter = new Point2D.Float(
                    (float) (currentCameraCenter.getX() - currentMousePosition.getX() + this.lastMousePosition.getX()),
                    (float) (currentCameraCenter.getY() - currentMousePosition.getY() + this.lastMousePosition.getY())
            );
            this.canvas
                    .setCameraCenter(newCameraCenter)
                    .repaint();
            this.lastMousePosition = currentMousePosition;
        }
    }

    public void mouseMoved(@NotNull MouseEvent event) {
    }

    public void mouseWheelMoved(@NotNull MouseWheelEvent event) {
        // zoom the camera
        int scrollRotation = event.getWheelRotation(); // 1 if scroll down, -1 otherwise
        float zoomFactor = (float) Math.pow(1.25, -scrollRotation);
        float currentZoomRatio = this.canvas.getZoomRatio();
        float newZoomRatio = currentZoomRatio * zoomFactor;
        // move the view to the mouse position
        Point2D mousePosition = new Point2D.Float(event.getX(), event.getY());
        Point2D cameraCenter = this.canvas.getCameraCenter();
        Point2D newCameraCenter = new Point2D.Float(
                (float) (zoomFactor * cameraCenter.getX() + (zoomFactor - 1) * mousePosition.getX()),
                (float) (zoomFactor * cameraCenter.getY() + (zoomFactor - 1) * mousePosition.getY())
        );
        // repaint
        this.canvas.setZoomRatio(newZoomRatio)
                .setCameraCenter(newCameraCenter)
                .repaint();
    }

    @NotNull
    private String extractLastPart(@NotNull String text) {
        String[] parts = text.split("\\.");
        return parts[parts.length - 1];
    }

    @NotNull
    private String htmlBold(@NotNull String text) {
        return wrapHtmlTag(text, "b");
    }

    @NotNull
    private String toHtml(@NotNull String text) {
        String multipleLines = text.replace("\n", "<br>");
        return wrapHtmlTag(multipleLines, "html");
    }

    @NotNull
    private String wrapHtmlTag(@NotNull String text, @NotNull String htmlTag) {
        return String.format("<%s>%s</%s>", htmlTag, text, htmlTag);
    }
}
