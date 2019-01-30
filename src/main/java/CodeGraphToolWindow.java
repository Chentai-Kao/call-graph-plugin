import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.roots.ContentIterator;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.psi.*;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.PsiTreeUtil;
import guru.nidi.graphviz.attribute.RankDir;
import guru.nidi.graphviz.engine.Format;
import guru.nidi.graphviz.engine.Graphviz;
import guru.nidi.graphviz.model.MutableNode;
import org.graphstream.graph.Graph;
import org.graphstream.graph.Node;
import org.graphstream.graph.implementations.MultiGraph;
import org.graphstream.ui.graphicGraph.GraphicElement;
import org.graphstream.ui.swingViewer.ViewPanel;
import org.graphstream.ui.view.Viewer;
import org.graphstream.ui.view.util.DefaultMouseManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseEvent;
import java.util.List;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import static guru.nidi.graphviz.model.Factory.mutGraph;
import static guru.nidi.graphviz.model.Factory.mutNode;

@SuppressWarnings("WeakerAccess")
public class CodeGraphToolWindow {
    private JButton runButton;
    private JPanel codeGraphToolWindowContent;
    private JPanel canvasPanel;

    public CodeGraphToolWindow() {
        runButton.addActionListener(e -> run());
    }

    public void run() {
        Project project = getActiveProject();
        if (project == null) {
            return;
        }
        Set<PsiFile> sourceCodeFiles = getSourceCodeFiles(project);
        System.out.println(String.format("found %d files", sourceCodeFiles.size()));
        Map<PsiMethod, Set<PsiReference>> methodReferences = getMethodReferences(sourceCodeFiles);
        Map<String, PsiMethod> nodeIdToMethodMap = generateMethodNodeIds(methodReferences);
        System.out.println(String.format("found %d methods", nodeIdToMethodMap.size()));
        Graph gsGraph = createGraph();
        System.out.println("--- building graph ---");
        buildGraph(gsGraph, nodeIdToMethodMap, methodReferences);
        System.out.println("--- getting layout from GraphViz ---");
        Map<String, AbstractMap.SimpleEntry<Integer, Integer>> nodeCoordinateMap = layoutByGraphViz(gsGraph);
        System.out.println("--- applying layout from GraphViz to set node position ---");
        applyGraphLayout(gsGraph, nodeCoordinateMap);
        System.out.println("--- rendering graph ---");
        ViewPanel viewPanel = renderGraphOnCanvas(gsGraph);
        System.out.println("--- rendered ---");
        attachEventListeners(viewPanel, gsGraph);
        System.out.println("--- attached event listeners ---");
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
        System.out.println(String.format("found %d source roots", sourceRoots.length));
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
        Graph gsGraph = new MultiGraph("embedded");
        gsGraph.addAttribute("ui.quality");
        gsGraph.addAttribute("ui.antialias");

        // set up graph styling
        // GraphStream CSS doc: http://graphstream-project.org/doc/Advanced-Concepts/GraphStream-CSS-Reference/
        gsGraph.addAttribute("ui.stylesheet",
                "" +
                        "node {" +
                        "  fill-color: #aaa;" +
                        "  text-offset: 10, 10;" +
                        "  text-color: #33f;" +
                        "  text-alignment: at-right;" +
                        "}"
        );
        return gsGraph;
    }

    @NotNull
    private Map<String, PsiMethod> generateMethodNodeIds(@NotNull Map<PsiMethod, Set<PsiReference>> methodReferences) {
        return methodReferences.keySet()
                .stream()
                .collect(Collectors.toMap(this::getNodeHash, method -> method));
    }

    private void buildGraph(@NotNull Graph gsGraph,
                            @NotNull Map<String, PsiMethod> nodeIdToMethodMap,
                            @NotNull Map<PsiMethod, Set<PsiReference>> methodReferences) {
        // add every method as a graph node
        nodeIdToMethodMap.forEach((nodeId, method) -> {
            Node node = gsGraph.addNode(nodeId);
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
                    if (gsGraph.getEdge(edgeId) == null) {
                        gsGraph.addEdge(edgeId, callerId, calleeId, true);
                    }
                }
            });
        });
    }

    private void applyGraphLayout(@NotNull Graph gsGraph,
                                  @NotNull Map<String, AbstractMap.SimpleEntry<Integer, Integer>> nodeCoordinateMap) {
        nodeCoordinateMap.forEach((nodeId, coordinate) -> {
            int x = coordinate.getKey();
            int y = coordinate.getValue();
            gsGraph.getNode(nodeId).setAttribute("xy", x, y);
        });                System.out.println("dragged");
    }

    @NotNull
    private ViewPanel renderGraphOnCanvas(@NotNull Graph gsGraph) {
        Viewer viewer = new Viewer(gsGraph, Viewer.ThreadingModel.GRAPH_IN_GUI_THREAD);
        viewer.disableAutoLayout();
        ViewPanel viewPanel = viewer.addDefaultView(false); // false indicates "no JFrame" (no window)
        canvasPanel.removeAll();
        canvasPanel.add(viewPanel);
        canvasPanel.updateUI();
        return viewPanel;
    }

    private void attachEventListeners(@NotNull ViewPanel viewPanel, @NotNull Graph gsGraph) {
        viewPanel.setMouseManager(new DefaultMouseManager() {
            @Override
            public void mouseClicked(MouseEvent event) {
                Node node = getNodeUnderMouse(event);
                if (node != null) {
                    System.out.println(String.format("clicked on node %s", node.getId()));
                }
            }

            @Override
            public void mousePressed(MouseEvent event) {

            }

            @Override
            public void mouseReleased(MouseEvent event) {

            }

            @Override
            public void mouseEntered(MouseEvent event) {

            }

            @Override
            public void mouseExited(MouseEvent event) {

            }

            @Override
            public void mouseDragged(MouseEvent event) {

            }

            @Override
            public void mouseMoved(MouseEvent event) {

            }

            @Nullable
            private Node getNodeUnderMouse(MouseEvent event) {
                GraphicElement element = viewPanel.findNodeOrSpriteAt(event.getX(), event.getY());
                return element == null ? null : gsGraph.getNode(element.getId());
            }
        });
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

    @NotNull
    private Map<String, AbstractMap.SimpleEntry<Integer, Integer>> layoutByGraphViz(@NotNull Graph gsGraph) {
        guru.nidi.graphviz.model.MutableGraph gvGraph = mutGraph("test")
                .setDirected(true)
                .graphAttrs()
                .add(RankDir.LEFT_TO_RIGHT);

        gsGraph.getNodeSet()
                .forEach(node -> {
                    MutableNode gvNode = mutNode(node.getId());
                    StreamSupport.stream(node.getEachLeavingEdge().spliterator(), false)
                            .forEach(edge -> gvNode.addLink(edge.getTargetNode().getId()));
                    gvGraph.add(gvNode);
                });
        String layoutBlueprint = Graphviz.fromGraph(gvGraph).render(Format.PLAIN).toString();

        // parse the GraphViz layout as a mapping from "node name" to "x-y coordinate (percent of full graph size)"
        // GraphViz doc: https://graphviz.gitlab.io/_pages/doc/info/output.html#d:plain
        List<String> layoutLines = Arrays.asList(layoutBlueprint.split("\n"));
        String graphSizeLine = layoutLines.stream()
                .filter(line -> line.startsWith("graph"))
                .findFirst()
                .orElse("");
        String[] graphSizeParts = graphSizeLine.split(" ");
        float graphWidth = Float.parseFloat(graphSizeParts[2]);
        float graphHeight = Float.parseFloat(graphSizeParts[3]);
        int graphScale = 100;
        return layoutLines.stream()
                .filter(line -> line.startsWith("node"))
                .map(line -> line.split(" "))
                .collect(Collectors.toMap(
                        parts -> parts[1],
                        parts -> {
                            int x = Math.round(graphScale * Float.parseFloat(parts[2]) / graphWidth);
                            int y = Math.round(graphScale * Float.parseFloat(parts[3]) / graphHeight);
                            return new AbstractMap.SimpleEntry<>(x, y);
                        }
                ));
    }

    @NotNull
    public JPanel getContent() {
        return codeGraphToolWindowContent;
    }
}
