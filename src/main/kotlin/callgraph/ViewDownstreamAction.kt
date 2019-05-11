package callgraph

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent

class ViewDownstreamAction: AnAction() {
    override fun actionPerformed(anActionEvent: AnActionEvent) {
        Utils.runCallGraphFromAction(anActionEvent, CanvasConfig.BuildType.DOWNSTREAM)
    }

    override fun update(anActionEvent: AnActionEvent) {
        Utils.setActionEnabledAndVisibleByContext(anActionEvent)
    }
}
