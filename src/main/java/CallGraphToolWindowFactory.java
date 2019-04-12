import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.*;
import com.intellij.ui.content.*;
import org.jetbrains.annotations.NotNull;

@SuppressWarnings({"WeakerAccess", "RedundantSuppression"})
public class CallGraphToolWindowFactory implements com.intellij.openapi.wm.ToolWindowFactory {
    // Create the tool window content.
    public void createToolWindowContent(@NotNull Project project, @NotNull ToolWindow toolWindow) {
        CallGraphToolWindow callGraphToolWindow = new CallGraphToolWindow();
        ContentFactory contentFactory = ContentFactory.SERVICE.getInstance();
        Content content = contentFactory.createContent(callGraphToolWindow.getContent(), "", false);
        toolWindow.getContentManager().addContent(content);
    }
}
