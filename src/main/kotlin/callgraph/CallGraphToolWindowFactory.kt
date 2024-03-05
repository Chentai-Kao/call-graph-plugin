package callgraph

import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.ui.content.ContentFactory

class CallGraphToolWindowFactory: com.intellij.openapi.wm.ToolWindowFactory {
    // Create the tool window content.
    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val callGraphToolWindow = CallGraphToolWindow()

        // register the call graph tool window as a project service, so it can be accessed by editor menu actions.
        val callGraphToolWindowProjectService = project.service<CallGraphToolWindowProjectService>()
        callGraphToolWindowProjectService.callGraphToolWindow = callGraphToolWindow

        // register the tool window content
        val content = ContentFactory.getInstance().createContent(callGraphToolWindow.getContent(), "", false)
        toolWindow.contentManager.addContent(content)
    }
}
