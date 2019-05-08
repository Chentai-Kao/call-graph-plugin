import com.intellij.ui.JBColor;

import java.awt.*;
import java.util.Arrays;
import java.util.List;

@SuppressWarnings("WeakerAccess")
class Colors {
    static final Color backgroundColor = new JBColor(new Color(0xFDFEFF), new Color(0x292B2D));
    static final Color unHighlightedColor = new JBColor(new Color(0xC6C8CA), new Color(0x585A5C));
    static final Color neutralColor = new JBColor(new Color(0x626466), new Color(0x949698));
    static final Color highlightedColor = new JBColor(new Color(0x4285F4), new Color(0x589DEF));
    static final Color highlightedBackgroundColor = new JBColor(new Color(0xFFFF00), new Color(0xFFFF00));
    static final Color upstreamColor = new JBColor(new Color(0xFBBC05), new Color(0xBE9117));
    static final Color downstreamColor = new JBColor(new Color(0x34A853), new Color(0x538863));


    static final Color deepBlue = new JBColor(new Color(0x0000FF), new Color(0x0000FF));
    static final Color blue = new JBColor(new Color(0x0088FF), new Color(0x0088FF));
    static final Color lightBlue = new JBColor(new Color(0x00FFFF), new Color(0x00FFFF));
    static final Color cyan = new JBColor(new Color(0x00FF88), new Color(0x00FF88));
    static final Color green = new JBColor(new Color(0x00FF00), new Color(0x00FF00));
    static final Color lightGreen = new JBColor(new Color(0x88FF00), new Color(0x88FF00));
    static final Color yellow = new JBColor(new Color(0xFFFF00), new Color(0xFFFF00));
    static final Color lightOrange = new JBColor(new Color(0xFFAA00), new Color(0xFFAA00));
    static final Color orange = new JBColor(new Color(0xFF6600), new Color(0xFF6600));
    static final Color red = new JBColor(new Color(0xFF0000), new Color(0xFF0000));

    static final List<Color> heatMapColors = Arrays.asList(
            deepBlue,
            blue,
            lightBlue,
            cyan,
            green,
            lightGreen,
            yellow,
            lightOrange,
            orange,
            red
    );
}
