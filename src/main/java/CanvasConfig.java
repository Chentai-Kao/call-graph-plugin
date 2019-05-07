import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiMethod;
import org.jetbrains.annotations.NotNull;

import java.util.HashSet;
import java.util.Set;

class CanvasConfig {
    public enum BuildType {
        WHOLE_PROJECT_WITH_TEST_LIMITED(
                "Whole project (test files included), limited upstream/downstream scope"),
        WHOLE_PROJECT_WITHOUT_TEST_LIMITED(
                "Whole project (test files excluded), limited upstream/downstream scope"),
        MODULE_LIMITED("Module, limited upstream/downstream scope"),
        DIRECTORY_LIMITED("Directory, limited upstream/downstream scope"),
        WHOLE_PROJECT_WITH_TEST("Whole project (test files included)"),
        WHOLE_PROJECT_WITHOUT_TEST("Whole project (test files excluded)"),
        MODULE("Module"),
        DIRECTORY("Directory"),
        UPSTREAM("Upstream"),
        DOWNSTREAM("Downstream"),
        UPSTREAM_DOWNSTREAM("Upstream & downstream");

        private final String label;

        BuildType(@NotNull String label) {
            this.label = label;
        }

        @NotNull
        public String getLabel() {
            return this.label;
        }
    }

    private BuildType buildType;
    private String selectedModuleName;
    private String selectedDirectoryPath;
    private Project project;
    private Set<PsiMethod> focusedMethods = new HashSet<>();
    private CallGraphToolWindow callGraphToolWindow;

    @NotNull
    String getSelectedModuleName() {
        return selectedModuleName;
    }

    @NotNull
    CanvasConfig setSelectedModuleName(@NotNull String selectedModuleName) {
        this.selectedModuleName = selectedModuleName;
        return this;
    }

    @NotNull
    Project getProject() {
        return this.project;
    }

    @NotNull
    CanvasConfig setProject(@NotNull Project project) {
        this.project = project;
        return this;
    }

    @NotNull
    String getSelectedDirectoryPath() {
        return this.selectedDirectoryPath;
    }

    @NotNull
    CanvasConfig setSelectedDirectoryPath(@NotNull String selectedDirectoryPath) {
        this.selectedDirectoryPath = selectedDirectoryPath;
        return this;
    }

    @NotNull
    Set<PsiMethod> getFocusedMethods() {
        return this.focusedMethods;
    }

    @NotNull
    CanvasConfig setFocusedMethods(@NotNull Set<PsiMethod> focusedMethods) {
        this.focusedMethods = focusedMethods;
        return this;
    }

    @NotNull
    BuildType getBuildType() {
        return this.buildType;
    }

    @NotNull
    CanvasConfig setBuildType(@NotNull BuildType buildType) {
        this.buildType = buildType;
        return this;
    }

    @NotNull
    CallGraphToolWindow getCallGraphToolWindow() {
        return this.callGraphToolWindow;
    }

    @NotNull
    CanvasConfig setCallGraphToolWindow(@NotNull CallGraphToolWindow callGraphToolWindow) {
        this.callGraphToolWindow = callGraphToolWindow;
        return this;
    }
}
