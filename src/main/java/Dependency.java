import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiInvalidElementAccessException;
import com.intellij.psi.PsiMethod;
import org.jetbrains.annotations.NotNull;

import java.util.Set;

@SuppressWarnings("StatefulEp")
class Dependency {
    private final PsiMethod caller;
    private final PsiMethod callee;
    private final long callerFileModificationStamp;
    private final long calleeFileModificationStamp;

    Dependency(@NotNull PsiMethod caller, @NotNull PsiMethod callee) {
        this.caller = caller;
        this.callee = callee;
        this.callerFileModificationStamp = caller.getContainingFile().getModificationStamp();
        this.calleeFileModificationStamp = callee.getContainingFile().getModificationStamp();
    }

    @NotNull
    PsiMethod getCaller() {
        return this.caller;
    }

    @NotNull
    PsiMethod getCallee() {
        return this.callee;
    }

    @NotNull
    PsiFile getCallerFile() {
        return this.caller.getContainingFile();
    }

    @NotNull
    PsiFile getCalleeFile() {
        return this.callee.getContainingFile();
    }

    boolean isValid(@NotNull Set<PsiFile> files) {
        try {
            PsiFile callerFile = this.caller.getContainingFile();
            PsiFile calleeFile = this.callee.getContainingFile();
            System.out.printf("isValid ? %s (%d <> %d) -> %s (%d <> %d), %b (%b %b %b %b)\n", caller.getName(), callerFile.getModificationStamp(), this.callerFileModificationStamp, callee.getName(), calleeFile.getModificationStamp(), this.calleeFileModificationStamp, files.contains(callerFile) && files.contains(calleeFile) && callerFile.getModificationStamp() == this.callerFileModificationStamp && calleeFile.getModificationStamp() == this.calleeFileModificationStamp, files.contains(callerFile), files.contains(calleeFile), callerFile.getModificationStamp() == this.callerFileModificationStamp, calleeFile.getModificationStamp() == this.calleeFileModificationStamp);
            return files.contains(callerFile) &&
                    files.contains(calleeFile) &&
                    callerFile.getModificationStamp() == this.callerFileModificationStamp &&
                    calleeFile.getModificationStamp() == this.calleeFileModificationStamp;
        } catch (PsiInvalidElementAccessException ex) {
            // invalid if caller or callee has vanished
            return false;
        }
    }
}
