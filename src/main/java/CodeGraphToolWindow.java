import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.roots.ContentIterator;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.psi.*;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.PsiTreeUtil;
import jdk.internal.jline.internal.Nullable;
import org.graphstream.graph.Graph;
import org.graphstream.graph.Node;
import org.graphstream.graph.implementations.MultiGraph;
import org.graphstream.ui.layout.Layout;
import org.graphstream.ui.layout.springbox.implementations.SpringBox;
import org.graphstream.ui.swingViewer.ViewPanel;
import org.graphstream.ui.view.Viewer;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.util.List;
import java.util.*;
import java.util.stream.Collectors;

@SuppressWarnings("WeakerAccess")
public class CodeGraphToolWindow {
    private JButton refreshToolWindowButton;
    private JButton hideToolWindowButton;
    private JButton runButton;
    private JLabel currentDate;
    private JLabel currentTime;
    private JLabel timeZone;
    private JPanel codeGraphToolWindowContent;
    private JPanel canvasPanel;

    public CodeGraphToolWindow(ToolWindow toolWindow) {
        hideToolWindowButton.addActionListener(e -> toolWindow.hide(null));
        refreshToolWindowButton.addActionListener(e -> currentDateTime());
        runButton.addActionListener(e -> run());

        this.currentDateTime();
    }

    public void run() {
        Project project = getActiveProject();
        if (project == null) {
            return;
        }
        Set<PsiFile> sourceCodeFiles = getSourceCodeFiles(project);
        Map<PsiMethod, Set<PsiReference>> methodReferences = getMethodReferences(sourceCodeFiles);
        Map<String, PsiMethod> nodeIdToMethodMap = generateMethodNodeIds(methodReferences);
        Graph graph = createGraph();
        Layout layout = createLayoutOnGraph(graph);
        buildGraph(graph, nodeIdToMethodMap, methodReferences);
        optimizeGraphLayout(layout);
        renderGraphOnCanvas(graph);
    }

    @Nullable
    private Project getActiveProject() {
        Project[] projects = ProjectManager.getInstance().getOpenProjects();
        Optional<Project> maybeActiveProject = Arrays.stream(projects)
                .filter(project -> {
                    Window window = WindowManager.getInstance().suggestParentWindow(project);
                    return window != null && window.isActive();
                })
                .findFirst();
        return maybeActiveProject.orElse(null);
    }

    @NotNull
    private Set<PsiFile> getSourceCodeFiles(Project project) {
        VirtualFile[] sourceRoots = ProjectRootManager.getInstance(project).getContentSourceRoots();
        return Arrays.stream(sourceRoots)
                .flatMap(contentSourceRoot -> {
                    List<VirtualFile> childrenVirtualFiles = new ArrayList<>();
                    ContentIterator contentIterator = child -> {
                        if (child.isValid() && !child.isDirectory()) {
                            String extension = Optional.ofNullable(child.getExtension()).orElse("");
                            if (extension.equals("java")) {
                                childrenVirtualFiles.add(child);
                            }
                        }
                        return true;
                    };
                    VfsUtilCore.iterateChildrenRecursively(contentSourceRoot, null, contentIterator);
                    return childrenVirtualFiles.stream()
                            .map(file -> PsiManager.getInstance(project).findFile(file));
                })
                .collect(Collectors.toSet());
    }

    @NotNull
    private Map<PsiMethod, Set<PsiReference>> getMethodReferences(Set<PsiFile> sourceCodeFiles) {
        Set<PsiMethod> sourceCodeMethods = sourceCodeFiles.stream()
                .flatMap(psiFile -> Arrays.stream(((PsiJavaFile)psiFile).getClasses())) // get all classes
                .flatMap(psiClass -> Arrays.stream(psiClass.getMethods())) // get all methods
                .collect(Collectors.toSet());
        return sourceCodeMethods.stream()
                .collect(Collectors.toMap(
                        method -> method,
                        method -> new HashSet<>(ReferencesSearch.search(method).findAll())
                ));
    }

    @NotNull
    private Graph createGraph() {
        // set system to use gs-ui renderer, which is better than the default one
        //noinspection SpellCheckingInspection
        System.setProperty("org.graphstream.ui.renderer", "org.graphstream.ui.j2dviewer.J2DGraphRenderer");

        // create graph
        Graph graph = new MultiGraph("embedded");
        graph.addAttribute("ui.quality");
        graph.addAttribute("ui.antialias");
        graph.addAttribute("ui.stylesheet",
                "" +
                        "node {" +
                        "  fill-color: #aaa;" +
                        "  text-background-mode: plain;" +
                        "  text-padding: 1;" +
                        "  text-alignment: at-right;" +
                        "}"
        );
        return graph;
    }

    @NotNull
    private Layout createLayoutOnGraph(@NotNull Graph graph) {
        Layout layout = new SpringBox(); // use SpringBox or LinLog layout
        layout.setForce(0.5);
        graph.addSink(layout);
        layout.addAttributeSink(graph);
        return layout;
    }

    @NotNull
    private Map<String, PsiMethod> generateMethodNodeIds(@NotNull Map<PsiMethod, Set<PsiReference>> methodReferences) {
        return methodReferences.keySet()
                .stream()
                .collect(Collectors.toMap(this::getNodeHash, method -> method));
    }

    private void buildGraph(@NotNull Graph graph,
                            @NotNull Map<String, PsiMethod> nodeIdToMethodMap,
                            @NotNull Map<PsiMethod, Set<PsiReference>> methodReferences) {
        // add every method as a graph node
        nodeIdToMethodMap.forEach((nodeId, method) -> {
            Node node = graph.addNode(nodeId);
            node.setAttribute("ui.label", method.getName());
        });

        // add every reference as a graph edge
        Set<PsiMethod> knownMethods = methodReferences.keySet();
        methodReferences.forEach((callee, references) -> {
            String calleeId = getNodeHash(callee);
            references.forEach(reference -> {
                PsiElement callerElement = reference.getElement();
                PsiMethod caller = getContainingKnownMethod(callerElement, knownMethods);
                if (caller != null) {
                    String callerId = getNodeHash(caller);
                    String edgeId = getEdgeHash(callerId, calleeId);
                    // avoid adding duplicated edge (may happen if multiple calls exist from method 1 -> method 2)
                    if (graph.getEdge(edgeId) == null) {
                        graph.addEdge(edgeId, callerId, calleeId, true);
                    }
                }
            });
        });
    }

    private void optimizeGraphLayout(@NotNull Layout layout) {
        while (layout.getStabilization() < 0.9) {
            layout.compute();
        }
    }

    private void renderGraphOnCanvas(@NotNull Graph graph) {
        Viewer viewer = new Viewer(graph, Viewer.ThreadingModel.GRAPH_IN_ANOTHER_THREAD);
        viewer.disableAutoLayout();
        ViewPanel viewPanel = viewer.addDefaultView(false); // false indicates "no JFrame" (no window)
        canvasPanel.add(viewPanel);
    }

    @Nullable
    private PsiMethod getContainingKnownMethod(@NotNull PsiElement psiElement, @NotNull Set<PsiMethod> knownMethods) {
        PsiMethod parent = PsiTreeUtil.getParentOfType(psiElement, PsiMethod.class);
        if (parent == null) {
            return null;
        }
        return knownMethods.contains(parent) ? parent : getContainingKnownMethod(parent, knownMethods);
    }

    @NotNull
    private String getNodeHash(@NotNull PsiElement element) {
        return Integer.toString(element.hashCode());
    }

    @NotNull
    private String getEdgeHash(@NotNull String nodeId1, @NotNull String nodeId2) {
        return String.format("%s-%s", nodeId1, nodeId2);
    }

    public void currentDateTime() {
        // Get current date and time
        Calendar instance = Calendar.getInstance();
        currentDate.setText(instance.get(Calendar.DAY_OF_MONTH) + "/"
                + (instance.get(Calendar.MONTH) + 1) + "/" +
                instance.get(Calendar.YEAR));
        currentDate.setIcon(new ImageIcon(getClass().getResource("/icons/Calendar-icon.png")));
        int min = instance.get(Calendar.MINUTE);
        String strMin;
        if (min < 10) {
            strMin = "0" + min;
        } else {
            strMin = String.valueOf(min);
        }
        currentTime.setText(instance.get(Calendar.HOUR_OF_DAY) + ":" + strMin);
        currentTime.setIcon(new ImageIcon(getClass().getResource("/icons/Time-icon.png")));
        // Get time zone
        long gmt_Offset = instance.get(Calendar.ZONE_OFFSET); // offset from GMT in milliseconds
        String str_gmt_Offset = String.valueOf(gmt_Offset / 3600000);
        str_gmt_Offset = (gmt_Offset > 0) ? "GMT + " + str_gmt_Offset : "GMT - " + str_gmt_Offset;
        timeZone.setText(str_gmt_Offset);
        timeZone.setIcon(new ImageIcon(getClass().getResource("/icons/Time-zone-icon.png")));
    }

    public JPanel getContent() {
        return codeGraphToolWindowContent;
    }
}
