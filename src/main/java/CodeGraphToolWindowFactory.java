import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.*;
import com.intellij.ui.content.*;
import org.jetbrains.annotations.NotNull;

public class CodeGraphToolWindowFactory implements ToolWindowFactory {
    // Create the tool window content.
    public void createToolWindowContent(@NotNull Project project, @NotNull ToolWindow toolWindow) {
        CodeGraphToolWindow codeGraphToolWindow = new CodeGraphToolWindow(toolWindow);
        ContentFactory contentFactory = ContentFactory.SERVICE.getInstance();
        Content content = contentFactory.createContent(codeGraphToolWindow.getContent(), "", false);
        toolWindow.getContentManager().addContent(content);
    }
}
