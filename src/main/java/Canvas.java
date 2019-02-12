import com.intellij.ui.JBColor;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;

class Canvas extends JPanel {
    private Graph graph;
    private JPanel canvasPanel;

    @NotNull
    Canvas setGraph(@NotNull Graph graph) {
        this.graph = graph;
        return this;
    }

    @NotNull
    Canvas setCanvasPanel(@NotNull JPanel canvasPanel) {
        this.canvasPanel = canvasPanel;
        return this;
    }

    @Override
    public void paintComponent(Graphics graphics) {
        super.paintComponent(graphics);
        Graphics2D graphics2D = (Graphics2D) graphics;

        // draw edges
        graphics2D.setColor(JBColor.BLACK);
        Dimension canvasSize = this.canvasPanel.getSize();
        this.graph.getEdges()
                .forEach(edge -> {
                    int sourceNodeX = Math.round(edge.getSourceNode().getX() * canvasSize.width);
                    int sourceNodeY = Math.round(edge.getSourceNode().getY() * canvasSize.height);
                    int targetNodeX = Math.round(edge.getTargetNode().getX() * canvasSize.width);
                    int targetNodeY = Math.round(edge.getTargetNode().getY() * canvasSize.height);
                    graphics2D.drawLine(sourceNodeX, sourceNodeY, targetNodeX, targetNodeY);
                });

        // draw nodes and label
        int radius = 10;
        int diameter = 2 * radius;
        this.graph.getNodes()
                .forEach(node -> {
                    // draw node
                    graphics2D.setColor(JBColor.BLACK);
                    int centerX = Math.round(node.getX() * canvasSize.width);
                    int centerY = Math.round(node.getY() * canvasSize.height);
                    int upperLeftX = centerX - radius;
                    int upperLeftY = centerY - radius;
                    graphics2D.drawOval(upperLeftX, upperLeftY, diameter, diameter);
                    graphics2D.setColor(JBColor.WHITE);
                    graphics2D.fillOval(upperLeftX, upperLeftY, diameter, diameter);
                    // draw label
                    graphics2D.setColor(JBColor.BLUE);
                    int labelBottomLeftX = centerX + diameter;
                    int labelBottomLeftY = centerY + diameter;
                    graphics2D.drawString(node.getMethod().getName(), labelBottomLeftX, labelBottomLeftY);
                });
    }
}
