import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentFactory;
import org.jetbrains.annotations.NotNull;

@SuppressWarnings({"WeakerAccess", "RedundantSuppression"})
public class CallGraphToolWindowFactory implements com.intellij.openapi.wm.ToolWindowFactory {
    // Create the tool window content.
    public void createToolWindowContent(@NotNull Project project, @NotNull ToolWindow toolWindow) {
        CallGraphToolWindow callGraphToolWindow = new CallGraphToolWindow();

        // register the call graph tool window as a project service, so it can be accessed by editor menu actions.
        CallGraphToolWindowProjectService callGraphToolWindowProjectService =
                ServiceManager.getService(project, CallGraphToolWindowProjectService.class);
        callGraphToolWindowProjectService.setCallGraphToolWindow(callGraphToolWindow);

        // register the tool window content
        Content content = ContentFactory.SERVICE.getInstance()
                .createContent(callGraphToolWindow.getContent(), "", false);
        toolWindow.getContentManager().addContent(content);
    }
}
