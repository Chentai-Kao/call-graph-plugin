package callgraph

import com.intellij.openapi.project.Project
import com.intellij.psi.PsiMethod

data class CanvasConfig(val project: Project, val buildType: BuildType, val canvas: Canvas) {
    enum class BuildType(val label: String) {
        WHOLE_PROJECT_WITH_TEST_LIMITED("Whole project (test files included), limited upstream/downstream scope"),
        WHOLE_PROJECT_WITHOUT_TEST_LIMITED("Whole project (test files excluded), limited upstream/downstream scope"),
        MODULE_LIMITED("Module, limited upstream/downstream scope"),
        DIRECTORY_LIMITED("Directory, limited upstream/downstream scope"),
        WHOLE_PROJECT_WITH_TEST("Whole project (test files included)"),
        WHOLE_PROJECT_WITHOUT_TEST("Whole project (test files excluded)"),
        MODULE("Module"),
        DIRECTORY("Directory"),
        UPSTREAM("Upstream"),
        DOWNSTREAM("Downstream"),
        UPSTREAM_DOWNSTREAM("Upstream & downstream")
    }

    var selectedModuleName = ""
    var selectedDirectoryPath = ""
    var focusedMethods = setOf<PsiMethod>()
    var callGraphToolWindow: CallGraphToolWindow? = null
}
