package callgraph

import com.intellij.psi.PsiMethod
import com.intellij.psi.SmartPsiElementPointer

data class Dependency(val caller: SmartPsiElementPointer<PsiMethod>, val callee: SmartPsiElementPointer<PsiMethod>)
