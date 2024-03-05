package callgraph

import com.intellij.openapi.components.Service

// Project service holds a reference to the tool window, which is accessible by an action (editor menu)
@Service(Service.Level.PROJECT)
class CallGraphToolWindowProjectService {
    lateinit var callGraphToolWindow: CallGraphToolWindow
}
