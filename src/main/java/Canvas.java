import javax.swing.*;
import java.awt.*;

class Canvas extends JPanel {
    @Override
    public void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2d = (Graphics2D) g;
        g2d.drawLine(10, 10, 500, 500);
        g2d.translate(500, 500);
        g2d.drawLine(20, 20, 520, 520);
    }
}
