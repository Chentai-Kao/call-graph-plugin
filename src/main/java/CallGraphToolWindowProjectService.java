import org.jetbrains.annotations.NotNull;

class CallGraphToolWindowProjectService {
    private CallGraphToolWindow callGraphToolWindow;

    CallGraphToolWindow getCallGraphToolWindow() {
        return this.callGraphToolWindow;
    }

    void setCallGraphToolWindow(@NotNull CallGraphToolWindow callGraphToolWindow) {
        this.callGraphToolWindow = callGraphToolWindow;
    }
}
