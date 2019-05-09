package oldcallgraph;

import org.jetbrains.annotations.NotNull;

public class CallGraphToolWindowProjectService {
    private CallGraphToolWindow callGraphToolWindow;

    CallGraphToolWindow getCallGraphToolWindow() {
        return this.callGraphToolWindow;
    }

    void setCallGraphToolWindow(@NotNull CallGraphToolWindow callGraphToolWindow) {
        this.callGraphToolWindow = callGraphToolWindow;
    }
}
