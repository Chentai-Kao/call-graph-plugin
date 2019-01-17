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
import org.graphstream.ui.swingViewer.ViewPanel;
import org.graphstream.ui.view.Viewer;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.util.List;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

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
        Project[] projects = ProjectManager.getInstance().getOpenProjects();
        Optional<Project> maybeActiveProject = Arrays.stream(projects)
                .filter(project -> {
                    Window window = WindowManager.getInstance().suggestParentWindow(project);
                    return window != null && window.isActive();
                })
                .findFirst();
        if (!maybeActiveProject.isPresent()) {
            return;
        }
        Project project = maybeActiveProject.get();
        // get all source files
        VirtualFile[] sourceRoots = ProjectRootManager.getInstance(project).getContentSourceRoots();
        Arrays.stream(sourceRoots).forEach(root -> System.out.println(root.getCanonicalPath()));
        Set<PsiFile> psiFiles = Arrays.stream(sourceRoots)
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
//                        ProjectFileIndex.getInstance(project).iterateContentUnderDirectory(contentSourceRoot, contentIterator);
                    return childrenVirtualFiles.stream()
                            .map(file -> PsiManager.getInstance(project).findFile(file));
                })
                .collect(Collectors.toSet());
        psiFiles.forEach(file -> System.out.println(file.getVirtualFile().getName()));

        // get all classes from source files
        Set<PsiClass> psiClasses = psiFiles.stream()
                .flatMap(psiFile -> Arrays.stream(((PsiJavaFile)psiFile).getClasses()))
                .collect(Collectors.toSet());

        // get all methods
        Set<PsiMethod> psiMethods = psiClasses.stream()
                .flatMap(psiClass -> Arrays.stream(psiClass.getMethods()))
                .collect(Collectors.toSet());
        psiMethods.forEach(method -> System.out.println(method.getName()));

        // find usages of every method, build edge from references (caller --> callee)
        Map<PsiMethod, Set<PsiReference>> psiMethodReferencesMap = psiMethods.stream()
                .collect(Collectors.toMap(
                        method -> method,
                        method -> new HashSet<>(ReferencesSearch.search(method).findAll())
                ));
        psiMethodReferencesMap.forEach((callee, references) -> {
            System.out.println(String.format("--- %s ---", callee.getName()));
            references.forEach(reference -> {
                PsiElement callerElement = reference.getElement();
                PsiMethod callerMethod = getContainingKnownMethod(callerElement, psiMethods);
                if (callerMethod != null) {
                    System.out.println(String.format("[%s] calls [%s] by %s", callerMethod.getName(),
                            callee.getName(), reference.getCanonicalText()));
                }
            });
        });
        // list of all method dependency <callerId, calleeId>
        List<AbstractMap.SimpleEntry<String, String>> methodDependencies = psiMethodReferencesMap.entrySet()
                .stream()
                .flatMap(entry -> {
                    PsiMethod callee = entry.getKey();
                    String calleeId = getNodeHash(callee);
                    List<PsiMethod> callers = entry.getValue().stream()
                            .map(reference -> getContainingKnownMethod(reference.getElement(), psiMethods))
                            .filter(Objects::nonNull) // exclude null values (i.e. caller is out of our known methods)
                            .collect(Collectors.toList());
                    return callers.stream().map(caller -> new AbstractMap.SimpleEntry<>(getNodeHash(caller), calleeId));
                })
                .collect(Collectors.toList());

        // - build a graph from all function calls
        // - remove self cycle
        // - detect cycles by DFS
        // - for every cycle, sort nodes by (outdegree-indegree) descendant, remove edge from last node to first node
        // - now graph is DAG, topological sort all nodes
        // - set node coordinate by topological order (all on 1D line, left to right),
        //   then try to move node closer to the origin (to the leftmost) if it's not called by the previous node
        // - add back (1) self cycle edge (2) removed cycle edge
        // - plot the graph

        // draw the diagram
        Graph graph = new MultiGraph("embedded");
        Map<String, PsiMethod> nodeIdToMethodMap = psiMethodReferencesMap.keySet()
                .stream()
                .collect(Collectors.toMap(
                        this::getNodeHash,
                        psiMethod -> psiMethod
                ));
        System.out.println("--------");
        nodeIdToMethodMap.forEach((nodeId, method) -> System.out.println(String.format("[%s]: %s", nodeId, method.getName())));
        // add every method as a graph node
        nodeIdToMethodMap.keySet().forEach(graph::addNode);
        // add every reference as a graph edge
        psiMethodReferencesMap.forEach((callee, references) -> {
            String calleeId = getNodeHash(callee);
            references.forEach(reference -> {
                PsiElement callerElement = reference.getElement();
                PsiMethod caller = getContainingKnownMethod(callerElement, psiMethods);
                if (caller != null) {
                    System.out.println(String.format("Adding edge for %s -> %s", caller.getName(), callee.getName()));
                    String callerId = getNodeHash(caller);
                    String edgeId = getEdgeHash(callerId, calleeId);
                    // add an edge only if it's non-existent (may happen if multiple calls from method 1 -> method 2)
                    if (graph.getEdge(edgeId) == null) {
                        graph.addEdge(edgeId, callerId, calleeId, true);
                    }
                }
            });
        });
        System.out.println("--------");
        graph.getEdgeSet().forEach(edge -> {
            PsiMethod source = nodeIdToMethodMap.get(edge.getNode0().getId());
            PsiMethod destination = nodeIdToMethodMap.get(edge.getNode1().getId());
            System.out.println(String.format("edge [%s]: %s to %s", edge.getId(), source.getName(), destination.getName()));
        });
        // find and remove self cycle
        Set<AbstractMap.SimpleEntry<String, String>> selfCycleEdges = methodDependencies.stream()
                .filter(entry -> entry.getKey().equals(entry.getValue()))
                .collect(Collectors.toSet());
        selfCycleEdges.forEach(entry -> graph.removeEdge(getEdgeHash(entry.getKey(), entry.getValue())));
        //
        //
        //
        //
        // TODO problem: mst is too conservative, it removes many non-cycle edges
        //
        //
        //
        //
        // using custom BFS to break cycle in a greedy manner
        Set<AbstractMap.SimpleEntry<String, String>> cycleEdges = new HashSet<>();
        graph.getNodeSet().forEach(bfsRootNode -> {
            Map<String, Integer> bfsNodeDepthMap = new HashMap<>();
            int depth = 0;
            ArrayDeque<Node> queue = new ArrayDeque<>();
            queue.add(bfsRootNode);
            while (!queue.isEmpty()) {
                Set<Node> nextRoundNodes = new HashSet<>();
                final int currentDepth = depth;
                queue.forEach(caller -> {
                    String callerId = caller.getId();
                    bfsNodeDepthMap.put(callerId, currentDepth);
                    caller.getLeavingEdgeSet().forEach(edge -> {
                        Node callee = edge.getOpposite(caller);
                        String calleeId = callee.getId();
                        if (bfsNodeDepthMap.containsKey(calleeId)) {
                            // callee is visited
                            if (bfsNodeDepthMap.get(calleeId) < currentDepth) {
                                // visited and the callee is in a lower depth, a cycle is detected!
                                graph.removeEdge(edge);
                                cycleEdges.add(new AbstractMap.SimpleEntry<>(callerId, calleeId));
                            }
                        } else {
                            // callee hasn't been visited, add to next round
                            nextRoundNodes.add(callee);
                        }
                    });
                });
                queue.clear();
                queue.addAll(nextRoundNodes);
                ++depth;
            }
        });
        graph.getEdgeSet().forEach(edge -> System.out.println(String.format("acyclic %s -> %s",
                nodeIdToMethodMap.get(edge.getNode0().getId()), nodeIdToMethodMap.get(edge.getNode1().getId()))));
        System.out.println(String.format("edge count %d", graph.getEdgeCount()));

        // find and use minimum spanning tree (MST) of the graph as the DAG
//        Kruskal kruskal = new Kruskal();
//        kruskal.init(graph);
//        kruskal.compute();
//        Set<String> treeEdgeIds = StreamSupport.stream(kruskal.getTreeEdges().spliterator(), false)
//                .map(Element::getId)
//                .collect(Collectors.toSet());
//        Set<AbstractMap.SimpleEntry<String, String>> graphCycleEdges = graph.getEdgeSet().stream()
//                .filter(edge -> !treeEdgeIds.contains(edge.getId()))
//                .map(edge -> new AbstractMap.SimpleEntry<>(edge.getNode0().getId(), edge.getNode1().getId()))
//                .collect(Collectors.toSet());
//        graphCycleEdges.forEach(entry -> graph.removeEdge(getEdgeHash(entry.getKey(), entry.getValue())));
//        graph.getNodeSet().forEach(node -> System.out.println(String.format("after mst node %s", nodeIdToMethodMap.get(node.getId()))));
//        System.out.println(String.format("node count %d", graph.getNodeCount()));
//        graph.getEdgeSet().forEach(edge -> System.out.println(String.format("after mst %s -> %s",
//                nodeIdToMethodMap.get(edge.getNode0().getId()), nodeIdToMethodMap.get(edge.getNode1().getId()))));
//        System.out.println(String.format("edge count %d", graph.getEdgeCount()));
        // - now graph is DAG, topological sort all nodes
        CustomTopologicalSort customTopologicalSort = new CustomTopologicalSort();
        customTopologicalSort.init(graph);
        customTopologicalSort.compute();
        customTopologicalSort.getSortedNodes()
                .forEach(node -> System.out.println(String.format("sorted %s", nodeIdToMethodMap.get(node.getId()))));
        // determine node coordinates by topological sorted order
        List<Node> sortedNodes = customTopologicalSort.getSortedNodes();
        Map<String, Integer> nodeIdToPositionMap = new HashMap<>();
        sortedNodes.forEach(node -> {
            int maxCallerPosition = node.getEnteringEdgeSet().stream()
                    .map(edge -> edge.getOpposite(node).getId())
                    .filter(nodeIdToPositionMap::containsKey)
                    .map(nodeIdToPositionMap::get)
                    .mapToInt(position -> position)
                    .max()
                    .orElse(-1);
            nodeIdToPositionMap.put(node.getId(), maxCallerPosition + 1);
        });
        sortedNodes.forEach(node -> System.out.println(String.format("%s position: %d",
                nodeIdToMethodMap.get(node.getId()), nodeIdToPositionMap.get(node.getId()))));
        nodeIdToPositionMap.entrySet()
                .stream()
                .collect(Collectors.groupingBy(Map.Entry::getValue))
                .forEach((xPosition, nodeIdPositions) ->
                        IntStream.range(0, nodeIdPositions.size())
                                .forEach(index -> {
                                    String nodeId = nodeIdPositions.get(index).getKey();
                                    Node node = graph.getNode(nodeId);
                                    PsiMethod method = nodeIdToMethodMap.get(nodeId);
                                    node.setAttribute("xy", xPosition, index);
                                    node.setAttribute("ui.label", method.getName());
                                })
                );


        // find and remove cycle by dfs
//        findAndRemoveCycle(graph);




//        Node nodeA = graph.addNode("A" );
//        nodeA.setAttribute("xy", 0, 0);
//        Node nodeB = graph.addNode("B" );
//        nodeB.setAttribute("xy", 1, 0);
//        Node nodeC = graph.addNode("C" );
//        nodeC.setAttribute("xy", 0, 1);
//        graph.addEdge("AB", "A", "B");
//        graph.addEdge("BC", "B", "C");
//        graph.addEdge("CA", "C", "A");





        Viewer viewer = new Viewer(graph, Viewer.ThreadingModel.GRAPH_IN_ANOTHER_THREAD);
        viewer.disableAutoLayout();
        ViewPanel viewPanel = viewer.addDefaultView(false); // false indicates "no JFrame"
        canvasPanel.add(viewPanel);

//        TarjanStronglyConnectedComponents tscc = new TarjanStronglyConnectedComponents();
//        tscc.init(graph);
//        tscc.compute();
//        for (Node n : graph.getEachNode()) {
//            n.setAttribute("ui.label", (Object)n.getAttribute(tscc.getSCCIndexAttribute()));
//        }

//        sourceRoots.forEach(root -> System.out.println(String.format("%s, %s", root.getVirtualFile().getCanonicalPath(), root.getFileType())));

//        Collection<String> javaMethodNames = JavaMethodNameIndex.getInstance().getAllKeys(project);
//        List<PsiMethod> javaMethods = javaMethodNames.stream()
//                .flatMap(methodName -> JavaMethodNameIndex.getInstance().get(methodName, project, GlobalSearchScope.projectScope(project)).stream())
//                .collect(Collectors.toList());
//        javaMethodNames.forEach(System.out::println);
//        ProjectRootManager.getInstance(project).getFileIndex();
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
