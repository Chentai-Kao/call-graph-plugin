import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import org.jetbrains.annotations.NotNull;

public class ViewDownstreamAction extends AnAction {
    @Override
    public void actionPerformed(@NotNull AnActionEvent anActionEvent) {
        Utils.runCallGraphFromAction(anActionEvent, CanvasConfig.BuildType.DOWNSTREAM);
    }

    @Override
    public void update(@NotNull AnActionEvent anActionEvent) {
        Utils.setActionEnabledAndVisibleByContext(anActionEvent);
    }
}
