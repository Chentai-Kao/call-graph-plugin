import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.util.PsiTreeUtil;
import org.apache.commons.lang.StringEscapeUtils;
import org.jetbrains.annotations.NotNull;

import java.awt.event.*;
import java.awt.geom.Point2D;
import java.util.stream.Collectors;
import java.util.stream.Stream;

class MouseEventHandler implements MouseListener, MouseMotionListener, MouseWheelListener {
    private Canvas canvas;
    private CodeGraphToolWindow codeGraphToolWindow;
    private Project project;
    private Point2D lastMousePosition;

    void init(@NotNull Canvas canvas, @NotNull CodeGraphToolWindow codeGraphToolWindow, @NotNull Project project) {
        this.canvas = canvas;
        this.codeGraphToolWindow = codeGraphToolWindow;
        this.project = project;
    }

    public void mouseClicked(@NotNull MouseEvent event) {
        Node node = this.canvas.getNodeUnderPoint(event.getPoint());
        if (node == null) {
            this.codeGraphToolWindow.setFunctionDocCommentLabelText("");
            this.codeGraphToolWindow.setFunctionSignatureLabelText("");
        } else {
            System.out.println(String.format("clicked on node %s: %s", node.getId(), node.getLabel()));
            // switch to navigate tab
            this.codeGraphToolWindow.focusNavigateTab();

            // function file path
            PsiMethod method = node.getMethod();
            String functionFilePath = getFunctionFilePath(method);
            this.codeGraphToolWindow.setFunctionFilePathLabelText(functionFilePath);

            // show function signature
            String functionReturnType = method.getReturnType() == null
                    ? "" : htmlBold(escapeHtml(extractLastPart(method.getReturnType().getCanonicalText())));
            String functionName = method.getName();
            String functionParameters = Stream.of(method.getParameterList().getParameters())
                    .map(parameter -> String.format("%s %s",
                            htmlBold(escapeHtml(extractLastPart(parameter.getType().getCanonicalText()))),
                            parameter.getName()))
                    .collect(Collectors.joining(", "));
            String functionSignature = String.format("%s %s(%s)", functionReturnType, functionName, functionParameters);
            String functionSignatureHtml = toHtml(functionSignature);
            this.codeGraphToolWindow.setFunctionSignatureLabelText(functionSignatureHtml);

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
    private String escapeHtml(@NotNull String text) {
        return StringEscapeUtils.escapeHtml(text);
    }

    @NotNull
    private String wrapHtmlTag(@NotNull String text, @NotNull String htmlTag) {
        return String.format("<%s>%s</%s>", htmlTag, text, htmlTag);
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
}
