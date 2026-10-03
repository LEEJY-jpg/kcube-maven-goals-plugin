package com.kcube.mavenview.views;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.core.resources.*;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.preferences.IEclipsePreferences;
import org.eclipse.core.runtime.preferences.InstanceScope;
import org.eclipse.jface.action.*;
import org.eclipse.jface.resource.JFaceResources;
import org.eclipse.jface.viewers.*;
import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Font;
import org.eclipse.swt.dnd.DND;
import org.eclipse.swt.dnd.DropTarget;
import org.eclipse.swt.dnd.DropTargetAdapter;
import org.eclipse.swt.dnd.DropTargetEvent;
import org.eclipse.swt.dnd.FileTransfer;
import org.eclipse.swt.dnd.Transfer;
import org.eclipse.swt.events.SelectionListener;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.FileDialog;
import org.eclipse.m2e.core.ui.internal.UpdateMavenProjectJob;
import org.eclipse.ui.ISelectionListener;
import org.eclipse.ui.ISharedImages;
import org.eclipse.ui.IWorkbenchPart;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.part.ResourceTransfer;
import org.eclipse.ui.part.ViewPart;
import org.eclipse.ui.plugin.AbstractUIPlugin;

import com.kcube.mavenview.model.*;
import com.kcube.mavenview.services.*;

// Ant-view-style tree: each registered pom.xml is a top-level node with its lifecycle/plugin goals nested below.
/**
 * Maven lifecycle phase/plugin goal을 트리로 보여주는 뷰 본체.
 * <p>Eclipse의 "Ant" 뷰와 비슷하게, 사용자가 등록한 여러 pom.xml이 각각 트리의 최상위 노드가 되고
 * 그 아래 Lifecycle/Plugins 하위 트리가 매달린다. 노드를 더블클릭하면 {@link MavenExecutor}를 통해
 * 외부 mvn 또는 Eclipse 내장 Maven으로 실행한다. 등록된 pom.xml 목록과 실행 방식(외부/내장) 선택은
 * workspace preference에 저장되어 Eclipse를 재시작해도 유지된다.
 */
public final class MavenGoalsView extends ViewPart {
    public static final String ID = "com.kcube.mavenview.views.mavenGoals";
    private static final String PREFS_NODE = "com.kcube.mavenview";
    private static final String PREF_POMS = "registeredPoms";
    private static final String PREF_USE_EXTERNAL_MVN = "useExternalMvn";
    /** {@link #PREF_POMS}에 여러 pom.xml 경로를 이어붙일 때 쓰는 구분자. 파일 경로엔 나올 수 없는 개행문자를 사용. */
    private static final String PATH_SEP = "\n";
    /** 트리 기본 펼침 깊이: 프로젝트 루트(1) → Lifecycle/Plugins(2)까지 보이도록 펼친다. */
    private static final int TOP_LEVEL_EXPAND_DEPTH = 2;

    private TreeViewer viewer;
    /** true면 외부 mvn 프로세스로, false면 Eclipse 내장 Maven(m2e)으로 goal을 실행한다. 툴바 체크박스와 연동. */
    private boolean useExternalMvn;
    /** Absolute pom.xml path -> parsed project root, in registration order. */
    private final Map<String, MavenGoal> projects = new LinkedHashMap<>();
    /** Last resource selected in some OTHER part (e.g. Project Explorer), kept so "Add from
     *  Selection" still works once focus has moved to this view's own toolbar/tree. */
    private IResource lastExternalSelection;
    private final ISelectionListener externalSelectionTracker = (IWorkbenchPart part, ISelection selection) -> {
        if (part == this) return;
        if (selection instanceof IStructuredSelection ss && ss.getFirstElement() instanceof IResource r) {
            lastExternalSelection = r;
        }
    };

    @Override
    public void createPartControl(Composite parent) {
        viewer = new TreeViewer(parent);
        // 트리 데이터는 뷰가 직접 들고 있지 않고 projects 맵(최상위) + MavenPomParser.CHILDREN(하위)에서 가져온다.
        viewer.setContentProvider(new ITreeContentProvider() {
            @Override public Object[] getElements(Object input) {
                // 최상위 PROJECT 노드들을 이름순으로 정렬해 보여준다.
                return projects.values().stream()
                    .sorted(java.util.Comparator.comparing(MavenGoal::getName, String.CASE_INSENSITIVE_ORDER))
                    .toArray();
            }
            @Override public Object[] getChildren(Object parent) {
                return MavenPomParser.children((MavenGoal) parent).toArray();
            }
            @Override public Object getParent(Object element) { return ((MavenGoal) element).getParent(); }
            @Override public boolean hasChildren(Object element) {
                return !MavenPomParser.children((MavenGoal) element).isEmpty();
            }
            @Override public void dispose() {}
            @Override public void inputChanged(Viewer viewer, Object oldInput, Object newInput) {}
        });
        viewer.setLabelProvider(new MavenGoalLabelProvider());
        // 더블클릭: 실행 가능한 노드(goal이 설정된 노드)는 실행하고, 폴더성 노드(프로젝트/Lifecycle/Plugins/플러그인)는 접기/펼치기를 토글한다.
        viewer.addDoubleClickListener(e -> {
            if (((IStructuredSelection) e.getSelection()).getFirstElement() instanceof MavenGoal g
                && g.getGoal() == null && !MavenPomParser.children(g).isEmpty()) {
                viewer.setExpandedState(g, !viewer.getExpandedState(g));
            } else {
                runSelectedGoal();
            }
        });
        viewer.setInput(new Object()); // 콘텐츠 프로바이더가 입력값 자체는 쓰지 않으므로 더미 객체로 충분.
        hookDragAndDrop();

        // 체크박스를 실제로 그리기(createActions) 전에 저장된 값을 먼저 읽어와야 초기 상태가 맞는다.
        useExternalMvn = InstanceScope.INSTANCE.getNode(PREFS_NODE).getBoolean(PREF_USE_EXTERNAL_MVN, false);
        createActions();
        getSite().setSelectionProvider(viewer);
        getSite().getWorkbenchWindow().getSelectionService().addSelectionListener(externalSelectionTracker);

        loadRegisteredPoms();
    }

    @Override
    public void dispose() {
        getSite().getWorkbenchWindow().getSelectionService().removeSelectionListener(externalSelectionTracker);
        super.dispose();
    }

    /** Finder/Project Explorer 등에서 pom.xml 또는 프로젝트 폴더를 뷰로 끌어다 놓으면 자동 등록한다. */
    private void hookDragAndDrop() {
        DropTarget target = new DropTarget(viewer.getControl(), DND.DROP_COPY | DND.DROP_DEFAULT);
        target.setTransfer(new Transfer[]{FileTransfer.getInstance(), ResourceTransfer.getInstance()});
        target.addDropListener(new DropTargetAdapter() {
            @Override public void dragEnter(DropTargetEvent event) {
                if (event.detail == DND.DROP_DEFAULT) event.detail = DND.DROP_COPY;
            }
            @Override public void drop(DropTargetEvent event) {
                boolean changed = false;
                if (FileTransfer.getInstance().isSupportedType(event.currentDataType)) {
                    // Finder 등 OS 파일 매니저에서 드롭: 파일 경로 문자열 배열로 전달됨.
                    for (String path : (String[]) event.data) {
                        changed |= addPomOrProjectPath(new File(path));
                    }
                } else if (ResourceTransfer.getInstance().isSupportedType(event.currentDataType)) {
                    // Project/Package Explorer에서 드롭: 워크스페이스 IResource로 전달됨.
                    for (IResource resource : (IResource[]) event.data) {
                        IFile pomFile = resource instanceof IFile f && f.getName().equals("pom.xml")
                            ? f : resource.getProject().getFile("pom.xml");
                        if (pomFile.exists()) { addPom(pomFile.getLocation().toFile()); changed = true; }
                    }
                }
                if (changed) persistRegisteredPoms();
            }
        });
    }

    /** 드롭된 경로가 pom.xml이면 그대로, 프로젝트 폴더면 그 안의 pom.xml을 등록한다. */
    private boolean addPomOrProjectPath(File dropped) {
        File pomFile = dropped.isDirectory() ? new File(dropped, "pom.xml") : dropped;
        if (!"pom.xml".equals(pomFile.getName()) || !pomFile.isFile()) {
            log(IStatus.WARNING, "Not a pom.xml, ignored: " + dropped, null);
            return false;
        }
        addPom(pomFile);
        return true;
    }

    /** 툴바에 올라가는 모든 Action/위젯을 생성하고 배치한다. */
    private void createActions() {
        ISharedImages images = PlatformUI.getWorkbench().getSharedImages();

        // 더블클릭과 동일하게, 현재 선택된 goal을 실행한다. 다른 툴바 버튼들과 마찬가지로 항상 활성 상태이며,
        // 실행 불가능한 노드가 선택된 경우 runSelectedGoal()이 조용히 무시한다.
        Action run = new Action("Run") {
            @Override public void run() { runSelectedGoal(); }
        };
        run.setToolTipText("Run the selected goal");
        run.setImageDescriptor(AbstractUIPlugin.imageDescriptorFromPlugin("com.kcube.mavenview", "icons/run.png"));

        Action updateProject = new Action("Update Maven Project") {
            @Override public void run() { updateSelectedProjects(); }
        };
        updateProject.setToolTipText(
            "Update Maven Project: reload pom.xml and re-resolve dependencies (workspace projects only)");
        updateProject.setImageDescriptor(AbstractUIPlugin.imageDescriptorFromPlugin(
            "com.kcube.mavenview", "icons/update_dependencies.png"));

        Action add = new Action("Add POM File...") {
            @Override public void run() { addPomViaDialog(); }
        };
        add.setToolTipText("Register a pom.xml from disk");
        add.setImageDescriptor(images.getImageDescriptor(ISharedImages.IMG_OBJ_ADD));

        Action addFromSelection = new Action("Add from Selection") {
            @Override public void run() { addPomFromSelection(); }
        };
        addFromSelection.setToolTipText("Register the pom.xml of the currently selected project/resource");
        addFromSelection.setImageDescriptor(images.getImageDescriptor(ISharedImages.IMG_ETOOL_HOME_NAV));

        Action remove = new Action("Remove") {
            @Override public void run() { removeSelected(); }
        };
        remove.setToolTipText("Unregister the selected build file(s)");
        remove.setImageDescriptor(images.getImageDescriptor(ISharedImages.IMG_TOOL_DELETE));

        Action refresh = new Action("Refresh All") {
            @Override public void run() { refreshAll(); }
        };
        refresh.setToolTipText("Re-parse all registered pom.xml files");
        refresh.setImageDescriptor(
            AbstractUIPlugin.imageDescriptorFromPlugin("org.eclipse.ui.ide", "icons/full/elcl16/refresh_nav.png"));

        Action expandAll = new Action("Expand All") {
            @Override public void run() {
                // 자식이 있는 노드가 선택돼 있으면 그 하위만 펼치고, 아니면 트리 전체를 펼친다.
                Object selected = getSelectedContainer();
                if (selected != null) viewer.expandToLevel(selected, AbstractTreeViewer.ALL_LEVELS);
                else viewer.expandAll();
            }
        };
        expandAll.setToolTipText("Expand All");
        expandAll.setImageDescriptor(
            AbstractUIPlugin.imageDescriptorFromPlugin("org.eclipse.ui", "icons/full/elcl16/expandall.png"));

        Action collapseAll = new Action("Collapse All") {
            @Override public void run() { viewer.collapseAll(); }
        };
        collapseAll.setToolTipText("Collapse All");
        collapseAll.setImageDescriptor(
            AbstractUIPlugin.imageDescriptorFromPlugin("org.eclipse.ui", "icons/full/elcl16/collapseall.png"));

        // 네이티브 SWT 체크박스를 툴바 맨 왼쪽에 직접 꽂아넣기 위한 ControlContribution.
        // Composite로 한 번 감싸는 이유는 GridData(CENTER)로 세로 정렬을 맞추기 위함.
        ControlContribution useExternalCheckbox = new ControlContribution("useExternalMvnCheckbox") {
            @Override protected Control createControl(Composite parent) {
                Composite holder = new Composite(parent, SWT.NONE);
                GridLayout layout = new GridLayout(1, false);
                layout.marginWidth = 0;
                layout.marginHeight = 0;
                holder.setLayout(layout);

                Button checkbox = new Button(holder, SWT.CHECK);
                checkbox.setLayoutData(new GridData(SWT.LEFT, SWT.CENTER, false, true));
                checkbox.setText("mvn");
                checkbox.setToolTipText(
                    "Checked: run the external mvn on PATH. Unchecked: use Eclipse's embedded Maven (m2e).");
                checkbox.setSelection(useExternalMvn);
                checkbox.addSelectionListener(SelectionListener.widgetSelectedAdapter(e -> {
                    useExternalMvn = checkbox.getSelection();
                    IEclipsePreferences prefs = InstanceScope.INSTANCE.getNode(PREFS_NODE);
                    prefs.putBoolean(PREF_USE_EXTERNAL_MVN, useExternalMvn);
                    try {
                        prefs.flush();
                    } catch (Exception ex) {
                        log(IStatus.WARNING, "Failed to persist mvn execution mode", ex);
                    }
                }));
                return holder;
            }
        };

        IToolBarManager toolbar = getViewSite().getActionBars().getToolBarManager();
        toolbar.add(useExternalCheckbox);
        toolbar.add(new Separator());
        toolbar.add(run);
        toolbar.add(updateProject);
        toolbar.add(new Separator());
        toolbar.add(add);
        toolbar.add(addFromSelection);
        toolbar.add(remove);
        toolbar.add(refresh);
        toolbar.add(new Separator());
        toolbar.add(expandAll);
        toolbar.add(collapseAll);
    }

    /** 선택된 PROJECT 노드 중 워크스페이스에 실제 임포트된 프로젝트만 골라 Update Maven Project를 수행한다. */
    private void updateSelectedProjects() {
        if (!(viewer.getSelection() instanceof IStructuredSelection ss) || ss.isEmpty()) {
            log(IStatus.WARNING, "Select a registered project to update", null);
            return;
        }
        List<IProject> toUpdate = new ArrayList<>();
        for (Object o : ss.toArray()) {
            if (o instanceof MavenGoal g) {
                File pom = g.resolvePomFile();
                IProject project = pom == null ? null : findWorkspaceProject(pom.getParentFile());
                if (project != null) toUpdate.add(project);
            }
        }
        if (toUpdate.isEmpty()) {
            log(IStatus.WARNING, "Selected pom.xml is not part of a workspace project; nothing to update", null);
            return;
        }
        scheduleUpdate(toUpdate);
    }

    /** m2e 버전에 따라 생성자 시그니처(IProject[] / Collection)가 달라 리플렉션으로 호출한다. */
    private void scheduleUpdate(List<IProject> projects) {
        try {
            Job job;
            try {
                job = (Job) UpdateMavenProjectJob.class.getConstructor(IProject[].class)
                        .newInstance((Object) projects.toArray(new IProject[0]));
            } catch (NoSuchMethodException e) {
                job = (Job) UpdateMavenProjectJob.class.getConstructor(java.util.Collection.class)
                        .newInstance(projects);
            }
            job.schedule();
        } catch (ReflectiveOperationException e) {
            log(IStatus.ERROR, "Failed to start Update Maven Project", e);
        }
    }

    /** pom.xml이 있는 디렉터리와 위치가 일치하는 워크스페이스 IProject를 찾는다. 없으면 null. */
    private static IProject findWorkspaceProject(File dir) {
        if (dir == null) return null;
        for (IProject p : ResourcesPlugin.getWorkspace().getRoot().getProjects()) {
            IPath loc = p.getLocation();
            if (loc != null && loc.toFile().equals(dir)) return p;
        }
        return null;
    }

    /** 현재 트리에서 선택된 노드가 실행 가능한 goal이면 실행한다. 더블클릭과 Run 툴바 버튼이 공유하는 로직. */
    private void runSelectedGoal() {
        if (viewer.getSelection() instanceof IStructuredSelection ss
            && ss.getFirstElement() instanceof MavenGoal g && g.getGoal() != null) {
            File pom = g.resolvePomFile();
            if (pom != null) MavenExecutor.run(pom, g, useExternalMvn);
        }
    }

    /** Expand All 범위를 선택 노드 하위로 한정하기 위해, 자식이 있는 노드가 선택돼 있으면 그 노드를 반환한다. */
    private Object getSelectedContainer() {
        if (viewer.getSelection() instanceof IStructuredSelection ss && !ss.isEmpty()
            && ss.getFirstElement() instanceof MavenGoal g && !MavenPomParser.children(g).isEmpty()) {
            return g;
        }
        return null;
    }

    /** 파일 다이얼로그로 디스크에서 pom.xml 파일(복수 선택 가능)을 골라 등록한다. */
    private void addPomViaDialog() {
        FileDialog dialog = new FileDialog(viewer.getControl().getShell(), SWT.OPEN | SWT.MULTI);
        dialog.setText("Select pom.xml file(s)");
        dialog.setFilterNames(new String[]{"Maven POM (pom.xml)", "All files"});
        dialog.setFilterExtensions(new String[]{"pom.xml;*.xml", "*.*"});
        IPath workspaceLocation = ResourcesPlugin.getWorkspace().getRoot().getLocation();
        if (workspaceLocation != null) dialog.setFilterPath(workspaceLocation.toOSString());

        if (dialog.open() == null) return;
        String dir = dialog.getFilterPath();
        for (String fileName : dialog.getFileNames()) {
            addPom(new File(dir, fileName));
        }
        persistRegisteredPoms();
    }

    /** externalSelectionTracker가 캐시해 둔, 다른 뷰에서 마지막으로 선택된 리소스의 pom.xml을 등록한다. */
    private void addPomFromSelection() {
        IResource r = lastExternalSelection;
        IProject project = r == null ? null : (r instanceof IProject p ? p : r.getProject());
        if (project != null) {
            IFile file = project.getFile("pom.xml");
            if (file.exists()) {
                addPom(file.getLocation().toFile());
                persistRegisteredPoms();
                return;
            }
        }
        log(IStatus.WARNING, "No pom.xml found for the last selected project/resource", null);
    }

    /** 선택된 PROJECT 노드들을 등록 목록과 트리에서 제거한다. */
    private void removeSelected() {
        if (viewer.getSelection() instanceof IStructuredSelection ss) {
            boolean changed = false;
            for (Object o : ss.toArray()) {
                if (o instanceof MavenGoal g && g.getType() == MavenGoal.Type.PROJECT) {
                    String key = key(g.resolvePomFile());
                    if (projects.remove(key) != null) {
                        MavenPomParser.dispose(g);
                        changed = true;
                    }
                }
            }
            if (changed) {
                persistRegisteredPoms();
                viewer.refresh();
            }
        }
    }

    /** 등록된 모든 pom.xml을 다시 파싱해 트리를 최신 상태로 갱신한다(단축키/새로고침 버튼용). */
    public void refreshAll() {
        for (String path : new java.util.ArrayList<>(projects.keySet())) {
            reparse(new File(path));
        }
        viewer.refresh();
    }

    /** 새 pom.xml 하나를 파싱해 등록하고, 트리를 갱신한 뒤 Lifecycle/Plugins까지 펼쳐 보여준다. */
    private void addPom(File pomFile) {
        reparse(pomFile);
        viewer.refresh();
        viewer.expandToLevel(TOP_LEVEL_EXPAND_DEPTH);
    }

    /** pom.xml을 파싱해 projects 맵에 (교체) 등록하고, 기존에 같은 경로로 파싱된 트리가 있었다면 메모리에서 정리한다. */
    private void reparse(File pomFile) {
        String key = key(pomFile);
        try {
            MavenGoal newRoot = MavenPomParser.parseProject(pomFile);
            MavenGoal old = projects.put(key, newRoot);
            if (old != null) MavenPomParser.dispose(old);
        } catch (Exception e) {
            log(IStatus.ERROR, "Failed to parse " + pomFile, e);
        }
    }

    /** projects 맵의 키로 쓰는, pom.xml의 정규화된 절대 경로. */
    private static String key(File pomFile) {
        return pomFile.getAbsolutePath();
    }

    /** 현재 등록된 pom.xml 경로 목록을 workspace preference에 저장한다. */
    private void persistRegisteredPoms() {
        IEclipsePreferences prefs = InstanceScope.INSTANCE.getNode(PREFS_NODE);
        prefs.put(PREF_POMS, String.join(PATH_SEP, projects.keySet()));
        try {
            prefs.flush();
        } catch (Exception e) {
            log(IStatus.WARNING, "Failed to persist registered pom.xml list", e);
        }
    }

    /** 뷰가 열릴 때 preference에 저장돼 있던 pom.xml 목록을 읽어 다시 등록한다(파일이 사라졌으면 경고만 남김). */
    private void loadRegisteredPoms() {
        IEclipsePreferences prefs = InstanceScope.INSTANCE.getNode(PREFS_NODE);
        String stored = prefs.get(PREF_POMS, "");
        if (stored.isBlank()) return;
        for (String path : stored.split(PATH_SEP)) {
            if (path.isBlank()) continue;
            File pomFile = new File(path);
            if (pomFile.isFile()) reparse(pomFile);
            else log(IStatus.WARNING, "Previously registered pom.xml no longer exists: " + path, null);
        }
        viewer.refresh();
        viewer.expandToLevel(TOP_LEVEL_EXPAND_DEPTH);
    }

    /** Eclipse Error Log에 메시지를 남긴다(상태/예외 모두 여기로 모아서 기록). */
    private void log(int severity, String message, Throwable e) {
        org.eclipse.core.runtime.Platform.getLog(getClass())
            .log(new Status(severity, "com.kcube.mavenview", message, e));
    }

    @Override public void setFocus() { viewer.getControl().setFocus(); }

    // Plugin entries carry [groupId, artifactId] in getArguments(); showing only the
    // artifactId (and bolding it) keeps the long org.apache.maven.plugins:... ids readable.
    private static final class MavenGoalLabelProvider extends LabelProvider implements IFontProvider {
        private final Font boldFont = JFaceResources.getFontRegistry().getBold(JFaceResources.DEFAULT_FONT);

        @Override public String getText(Object element) {
            if (element instanceof MavenGoal g && isPluginEntry(g)) return g.getArguments()[1];
            return element.toString();
        }

        @Override public Font getFont(Object element) {
            return element instanceof MavenGoal g && isPluginEntry(g) ? boldFont : null;
        }

        private static boolean isPluginEntry(MavenGoal g) {
            return g.getType() == MavenGoal.Type.PLUGIN && g.getArguments().length == 2;
        }
    }
}
