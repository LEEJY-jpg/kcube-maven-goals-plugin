package com.kcube.mavenview.views;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.core.runtime.IStatus;
import org.eclipse.jface.action.Action;
import org.eclipse.jface.action.MenuManager;
import org.eclipse.jface.action.Separator;
import org.eclipse.jface.resource.JFaceResources;
import org.eclipse.jface.viewers.AbstractTreeViewer;
import org.eclipse.jface.viewers.IFontProvider;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.ITreeContentProvider;
import org.eclipse.jface.viewers.LabelProvider;
import org.eclipse.jface.viewers.TreeViewer;
import org.eclipse.jface.viewers.Viewer;
import org.eclipse.jface.viewers.ViewerFilter;
import org.eclipse.jface.window.Window;
import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Font;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Menu;
import org.eclipse.swt.widgets.Text;
import org.eclipse.ui.IMemento;
import org.eclipse.ui.IViewSite;
import org.eclipse.ui.PartInitException;
import org.eclipse.ui.part.ViewPart;
import org.eclipse.ui.plugin.AbstractUIPlugin;

import com.kcube.mavenview.model.MavenGoal;
import com.kcube.mavenview.Messages;
import com.kcube.mavenview.services.GoalFilter;
import com.kcube.mavenview.services.GoalHistory;
import com.kcube.mavenview.services.GoalLabels;
import com.kcube.mavenview.services.MavenExecutor;
import com.kcube.mavenview.services.MavenPomParser;
import com.kcube.mavenview.services.PluginLog;
import com.kcube.mavenview.services.PomRegistryStore;
import com.kcube.mavenview.services.ViewPreferences;
import com.kcube.mavenview.services.PomWatcher;
import com.kcube.mavenview.services.RunOptions;

/**
 * Maven lifecycle phase/plugin goal을 트리로 보여주는 뷰 본체.
 * <p>
 * Eclipse의 "Ant" 뷰와 비슷하게, 사용자가 등록한 여러 pom.xml이 각각 트리의 최상위 노드가 되고 그 아래 Lifecycle/Plugins 하위 트리가 매달린다. 노드를 더블클릭하면
 * {@link MavenExecutor}를 통해 외부 mvn 또는 Eclipse 내장 Maven으로 실행한다. 등록된 pom.xml 목록과 실행 방식(외부/내장) 선택은 workspace preference에
 * 저장되어 Eclipse를 재시작해도 유지된다.
 */
public final class MavenGoalsView extends ViewPart
{
	public static final String ID = "com.kcube.mavenview.views.mavenGoals";
	private static final String MEMENTO_TREE = "tree";
	private static final String MEMENTO_EXPANDED = "expanded";
	private static final String MEMENTO_PATH = "path";
	private static final String MEMENTO_FILTER = "filter";
	/** 펼침 상태 경로에서 pom 경로와 노드 이름들을 구분하는 문자(경로/이름에 나올 수 없는 제어문자). */
	private static final String PATH_NODE_SEP = "\u0001";
	/** {@link #PREF_POMS}에 여러 pom.xml 경로를 이어붙일 때 쓰는 구분자. 파일 경로엔 나올 수 없는 개행문자를 사용. */
	private static final String PATH_SEP = "\n";
	/** 트리 기본 펼침 깊이: 프로젝트 루트(1) → Lifecycle/Plugins(2)까지 보이도록 펼친다. */
	private static final int TOP_LEVEL_EXPAND_DEPTH = 2;

	private TreeViewer viewer;
	/** 검색창 입력값(소문자, 앞뒤 공백 제거). 비어 있으면 필터 없이 전체를 보여준다. */
	private String filterText = "";
	/** 검색 입력 후 이 시간(ms) 동안 추가 입력이 없을 때 한 번만 필터를 적용한다(디바운스). */
	private static final int FILTER_DELAY_MS = 200;
	/** 필터 결과가 이 개수 이하일 때만 자동으로 전부 펼친다. */
	private static final int MAX_AUTO_EXPAND_NODES = 1000;
	/** 결과가 많을 때 펼치는 깊이: 프로젝트(1) → Lifecycle/Plugins(2) → 개별 플러그인(3). */
	private static final int PLUGIN_EXPAND_DEPTH = 3;
	/** 현재 검색어 기준으로 트리에 보여줄 노드 집합(identity). null이면 다시 계산해야 한다는 뜻. */
	private java.util.Set<MavenGoal> visibleNodes;
	/** 검색어에 맞는 노드를 계산하는 로직. */
	private final GoalFilter goalFilter = new GoalFilter();
	/** 예약돼 있는(아직 실행 전인) 필터 적용 작업. 새 입력이 오면 취소하고 다시 예약한다. */
	private Runnable pendingFilter;
	/** true면 외부 mvn 프로세스로, false면 Eclipse 내장 Maven(m2e)으로 goal을 실행한다. 툴바 체크박스와 연동. */
	private boolean useExternalMvn;
	/** 즐겨찾기와 최근 실행 목록. */
	private final GoalHistory history = new GoalHistory();

	/** 등록된 pom.xml이 외부에서 수정됐는지 주기적으로 확인한다. */
	private final PomWatcher pomWatcher = new PomWatcher();

	private static final int POM_WATCH_INTERVAL_MS = 2000;

	private Runnable pomWatchTask;
	/** 뷰를 다시 열 때 복원할 저장 상태(펼침/검색어). 없으면 null. */
	private IMemento savedState;
	private Text filterBox;
	/** 툴바의 즐겨찾기(별) 버튼. 선택에 따라 아이콘이 바뀐다. */
	private Action favoritesAction;
	/** pom.xml 절대 경로 -> 파싱된 프로젝트 루트. 등록한 순서를 유지한다. */
	private final Map<String, MavenGoal> projects = new LinkedHashMap<>();
	/** "Add from Selection"용으로 다른 파트의 선택을 추적한다. */
	private final SelectionTracker selectionTracker = new SelectionTracker(this);

	/** 이전 세션에서 저장한 뷰 상태(펼침 노드, 검색어)를 보관해 둔다. */
	@Override
	public void init(IViewSite site, IMemento memento) throws PartInitException
	{
		super.init(site, memento);
		savedState = memento;
	}

	/** 워크벤치가 종료되거나 뷰가 닫힐 때 트리의 펼침 상태와 검색어를 저장한다. */
	@Override
	public void saveState(IMemento memento)
	{
		if (viewer == null || viewer.getControl().isDisposed())
			return;
		IMemento tree = memento.createChild(MEMENTO_TREE);
		tree.putString(MEMENTO_FILTER, filterBox.getText());
		for (Object o : viewer.getExpandedElements())
		{
			if (o instanceof MavenGoal g)
			{
				String path = nodePath(g);
				if (path != null)
					tree.createChild(MEMENTO_EXPANDED).putString(MEMENTO_PATH, path);
			}
		}
	}

	/** 노드를 식별하는 문자열(pom 경로 + 루트부터의 이름 체인)을 만든다. */
	private static String nodePath(MavenGoal g)
	{
		File pom = g.resolvePomFile();
		if (pom == null)
			return null;
		java.util.LinkedList<String> names = new java.util.LinkedList<>();
		for (MavenGoal n = g; n != null && n.getType() != MavenGoal.Type.PROJECT; n = n.getParent())
			names.addFirst(n.getName());
		return key(pom) + PATH_NODE_SEP + String.join(PATH_NODE_SEP, names);
	}

	/** 저장된 상태가 있으면 검색어와 펼침 상태를 복원한다. 없으면 기본 펼침 상태를 유지한다. */
	private void restoreState()
	{
		IMemento tree = savedState == null ? null : savedState.getChild(MEMENTO_TREE);
		savedState = null;
		if (tree == null)
			return;
		String filter = tree.getString(MEMENTO_FILTER);
		if (filter != null && !filter.isBlank())
		{
			filterBox.setText(filter);
			if (pendingFilter != null)
				filterBox.getDisplay().timerExec(-1, pendingFilter);
			pendingFilter = null;
			applyFilter(filter);
		}
		java.util.Set<String> wanted = new java.util.HashSet<>();
		for (IMemento e : tree.getChildren(MEMENTO_EXPANDED))
		{
			String path = e.getString(MEMENTO_PATH);
			if (path != null)
				wanted.add(path);
		}
		java.util.List<MavenGoal> toExpand = new ArrayList<>();
		for (MavenGoal root : projects.values())
			collectExpanded(root, wanted, toExpand);
		viewer.getControl().setRedraw(false);
		try
		{
			viewer.collapseAll();
			viewer.setExpandedElements(toExpand.toArray());
		}
		finally
		{
			viewer.getControl().setRedraw(true);
		}
	}

	/** 저장된 경로와 일치하는 노드를 재귀적으로 찾아 모은다. */
	private static void collectExpanded(MavenGoal node, java.util.Set<String> wanted, java.util.List<MavenGoal> out)
	{
		String path = nodePath(node);
		if (node.getType() == MavenGoal.Type.PROJECT)
			path = key(node.getPomFile()) + PATH_NODE_SEP;
		if (path != null && wanted.contains(path))
			out.add(node);
		for (MavenGoal child : MavenPomParser.children(node))
			collectExpanded(child, wanted, out);
	}

	/** 뷰의 UI를 구성한다. 검색창과 트리를 만들고, 콘텐츠/라벨 프로바이더·필터·드래그앤드롭·툴바를 설정한 뒤 저장된 pom.xml 목록을 불러온다. */
	@Override
	public void createPartControl(Composite parent)
	{
		// 위: 검색창, 아래: 트리.
		GridLayout rootLayout = new GridLayout(1, false);
		rootLayout.marginWidth = 0;
		rootLayout.marginHeight = 0;
		rootLayout.verticalSpacing = 0;
		parent.setLayout(rootLayout);
		filterBox = new Text(parent, SWT.SEARCH | SWT.ICON_SEARCH | SWT.ICON_CANCEL);
		filterBox.setMessage(Messages.get("filter.message"));
		filterBox.setToolTipText(Messages.get("filter.tooltip"));
		filterBox.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));

		viewer = new TreeViewer(parent);
		viewer.getControl().setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
		// 트리 데이터는 뷰가 직접 들고 있지 않고 projects 맵(최상위) + MavenPomParser.CHILDREN(하위)에서 가져온다.
		viewer.setContentProvider(new ITreeContentProvider()
		{
			/** 최상위 PROJECT 노드들을 이름순으로 반환한다. */
			@Override
			public Object[] getElements(Object input)
			{
				// 최상위 PROJECT 노드들을 이름순으로 정렬해 보여준다.
				return projects.values().stream().sorted(
					java.util.Comparator.comparing(MavenGoal::getName, String.CASE_INSENSITIVE_ORDER)).toArray();
			}

			/** 노드의 자식(Lifecycle/Plugins/goal 등) 목록을 반환한다. */
			@Override
			public Object[] getChildren(Object parent)
			{
				return MavenPomParser.children((MavenGoal) parent).toArray();
			}

			/** 노드의 부모를 반환한다. */
			@Override
			public Object getParent(Object element)
			{
				return ((MavenGoal) element).getParent();
			}

			/** 노드가 자식을 갖고 있는지 반환한다. */
			@Override
			public boolean hasChildren(Object element)
			{
				return !MavenPomParser.children((MavenGoal) element).isEmpty();
			}

			/** 해제할 리소스가 없어 아무 동작도 하지 않는다. */
			@Override
			public void dispose()
			{
			}

			/** 입력값을 쓰지 않으므로 아무 동작도 하지 않는다. */
			@Override
			public void inputChanged(Viewer viewer, Object oldInput, Object newInput)
			{
			}
		});
		viewer.setLabelProvider(new MavenGoalLabelProvider(this::isFavorite));
		// 더블클릭: 실행 가능한 노드(goal이 설정된 노드)는 실행하고, 폴더성 노드(프로젝트/Lifecycle/Plugins/플러그인)는 접기/펼치기를 토글한다.
		viewer.addDoubleClickListener(e -> {
			if (((IStructuredSelection) e.getSelection()).getFirstElement() instanceof MavenGoal g
				&& g.getGoal() == null
				&& !MavenPomParser.children(g).isEmpty())
			{
				viewer.setExpandedState(g, !viewer.getExpandedState(g));
			}
			else
			{
				runSelectedGoal();
			}
		});
		viewer.addFilter(new ViewerFilter()
		{
			/** 검색어가 비어 있으면 모두 통과시키고, 아니면 검색어와 일치하는 노드(와 그 경로)만 통과시킨다. */
			@Override
			public boolean select(Viewer v, Object parentElement, Object element)
			{
				return filterText.isEmpty() || visibleNodes().contains(element);
			}
		});
		filterBox.addModifyListener(e -> scheduleFilter(filterBox));
		filterBox.addListener(SWT.KeyDown, e -> {
			if (e.keyCode == SWT.ESC)
				filterBox.setText("");
		});
		viewer.setInput(new Object()); // 콘텐츠 프로바이더가 입력값 자체는 쓰지 않으므로 더미 객체로 충분.
		hookDragAndDrop();

		// 체크박스를 실제로 그리기(createActions) 전에 저장된 값을 먼저 읽어와야 초기 상태가 맞는다.
		useExternalMvn = ViewPreferences.useExternalMvn();
		createActions();
		getSite().setSelectionProvider(viewer);
		viewer.addSelectionChangedListener(e -> updateFavoritesIcon());
		getSite().getWorkbenchWindow().getSelectionService().addSelectionListener(selectionTracker);

		loadGoalLists();
		hookContextMenu();
		loadRegisteredPoms();
		pruneMissingPoms();
		restoreState();
		startPomWatch();
	}

	/** 뷰가 보이는 동안 2초마다 등록된 pom의 수정 여부를 확인해, 바뀐 프로젝트만 다시 파싱한다. */
	private void startPomWatch()
	{
		pomWatchTask = new Runnable()
		{
			@Override
			public void run()
			{
				if (viewer == null || viewer.getControl().isDisposed())
					return;
				try
				{
					if (getSite().getPage().isPartVisible(MavenGoalsView.this))
						reloadChangedPoms();
				}
				catch (RuntimeException e)
				{
					log(IStatus.WARNING, "Failed to check pom changes", e);
				}
				viewer.getControl().getDisplay().timerExec(POM_WATCH_INTERVAL_MS, this);
			}
		};
		viewer.getControl().getDisplay().timerExec(POM_WATCH_INTERVAL_MS, pomWatchTask);
	}

	/** 파일이 바뀐 프로젝트만 다시 파싱한다. 펼침 상태는 유지한다. */
	private void reloadChangedPoms()
	{
		List<String> changed = pomWatcher.changed(projects);
		if (changed.isEmpty())
			return;
		reparseKeepingExpansion(changed);
	}

	/** 지정한 프로젝트들을 다시 파싱하고 트리를 갱신하되, 갱신 전에 펼쳐져 있던 노드는 다시 펼친다. */
	private void reparseKeepingExpansion(List<String> keys)
	{
		java.util.Set<String> expanded = new java.util.HashSet<>();
		for (Object o : viewer.getExpandedElements())
		{
			if (o instanceof MavenGoal g)
			{
				String path = g.getType() == MavenGoal.Type.PROJECT ? key(g.getPomFile()) + PATH_NODE_SEP : nodePath(g);
				if (path != null)
					expanded.add(path);
			}
		}
		for (String k : keys)
			reparse(new File(k));
		viewer.getControl().setRedraw(false);
		try
		{
			viewer.refresh();
			java.util.List<MavenGoal> toExpand = new ArrayList<>();
			for (MavenGoal root : projects.values())
				collectExpanded(root, expanded, toExpand);
			viewer.setExpandedElements(toExpand.toArray());
		}
		finally
		{
			viewer.getControl().setRedraw(true);
		}
	}

	/** 뷰가 닫힐 때 외부 선택 추적 리스너를 해제한다. */
	@Override
	public void dispose()
	{
		getSite().getWorkbenchWindow().getSelectionService().removeSelectionListener(selectionTracker);
		if (pomWatchTask != null && viewer != null && !viewer.getControl().isDisposed())
			viewer.getControl().getDisplay().timerExec(-1, pomWatchTask);
		if (pendingFilter != null && viewer != null && !viewer.getControl().isDisposed())
			viewer.getControl().getDisplay().timerExec(-1, pendingFilter);
		super.dispose();
	}

	/** 입력이 잠시 멈출 때까지 기다렸다가 필터를 적용한다. 키를 누를 때마다 트리를 다시 그리지 않도록 한다. */
	private void scheduleFilter(Text filterBox)
	{
		org.eclipse.swt.widgets.Display display = filterBox.getDisplay();
		if (pendingFilter != null)
			display.timerExec(-1, pendingFilter);
		pendingFilter = () -> {
			pendingFilter = null;
			if (!filterBox.isDisposed())
				applyFilter(filterBox.getText());
		};
		display.timerExec(FILTER_DELAY_MS, pendingFilter);
	}

	/** 검색어를 갱신하고 트리를 다시 그린다. 필터 중에는 결과를 모두 펼치고, 해제하면 기본 펼침 깊이로 되돌린다. */
	private void applyFilter(String text)
	{
		String newText = text.trim().toLowerCase();
		if (newText.equals(filterText))
			return;
		filterText = newText;
		visibleNodes = null;
		// 갱신 도중 중간 상태가 그려지지 않게 해 refresh/expand 비용을 줄인다.
		viewer.getControl().setRedraw(false);
		try
		{
			viewer.refresh();
			if (filterText.isEmpty())
			{
				collapseToLevel2();
			}
			else if (visibleNodes().size() <= MAX_AUTO_EXPAND_NODES)
			{
				viewer.expandAll();
			}
			else
			{
				// 결과가 매우 많으면 전부 펼칠 때 SWT 항목을 수천 개 만들어 느려지므로 플러그인 단계까지만 펼친다.
				viewer.expandToLevel(PLUGIN_EXPAND_DEPTH);
			}
		}
		finally
		{
			viewer.getControl().setRedraw(true);
		}
	}

	/** 모든 노드를 접은 뒤 프로젝트와 2단계 노드까지만 다시 펼친다. 2단계 노드는 접힌 상태로 남지 않는다. */
	private void collapseToLevel2()
	{
		viewer.collapseAll();
		viewer.expandToLevel(TOP_LEVEL_EXPAND_DEPTH);
	}

	/** 현재 검색어에 대해 보여줄 노드 집합을 반환한다. 필요할 때 한 번만 계산해 캐시한다. */
	private java.util.Set<MavenGoal> visibleNodes()
	{
		if (visibleNodes == null)
			visibleNodes = goalFilter.visibleNodes(projects.values(), filterText);
		return visibleNodes;
	}

	/** Finder/Project Explorer 등에서 pom.xml 또는 프로젝트 폴더를 뷰로 끌어다 놓으면 자동 등록한다. */
	private void hookDragAndDrop()
	{
		PomDropSupport.install(viewer.getControl(), pomFile -> {
			addPom(pomFile);
			return true;
		}, this::persistRegisteredPoms, (msg) -> log(IStatus.WARNING, msg, null));
	}

	/** 툴바를 구성한다. 각 버튼의 동작은 이 뷰의 메서드로 연결한다. */
	private void createActions()
	{
		ViewToolbar.build(getViewSite().getActionBars().getToolBarManager(), new ViewToolbar.Handler()
		{
			@Override
			public void run()
			{
				runSelectedGoal();
			}

			@Override
			public void update()
			{
				MavenProjectUpdater.updateSelected((IStructuredSelection) viewer.getSelection());
			}

			@Override
			public void add()
			{
				pomAdder().addViaDialog();
			}

			@Override
			public void addFromSelection()
			{
				pomAdder().addFromSelection();
			}

			@Override
			public void remove()
			{
				removeSelected();
			}

			@Override
			public void refresh()
			{
				refreshAll();
			}

			@Override
			public void expandAll()
			{
				// 자식이 있는 노드가 선택돼 있으면 그 하위만 펼치고, 아니면 트리 전체를 펼친다.
				Object selected = getSelectedContainer();
				if (selected != null)
					viewer.expandToLevel(selected, AbstractTreeViewer.ALL_LEVELS);
				else
					viewer.expandAll();
			}

			@Override
			public void collapseAll()
			{
				collapseToLevel2();
			}

			@Override
			public boolean useExternalMvn()
			{
				return useExternalMvn;
			}

			@Override
			public void setUseExternalMvn(boolean value)
			{
				useExternalMvn = value;
				ViewPreferences.setUseExternalMvn(value);
			}
		}, createFavoritesAction());
	}

	/**
	 * 현재 트리에서 선택된 노드가 실행 가능한 goal이면 실행한다. 더블클릭과 Run 툴바 버튼이 공유하는 로직.
	 */
	private void runSelectedGoal()
	{
		if (viewer.getSelection() instanceof IStructuredSelection ss
			&& ss.getFirstElement() instanceof MavenGoal g
			&& g.getGoal() != null)
		{
			File pom = g.resolvePomFile();
			if (pom != null)
			{
				addRecent(entry(pom, g));
				MavenExecutor.run(pom, g, useExternalMvn);
			}
		}
	}

	/** 즐겨찾기/최근 실행 항목 문자열("pom경로\tgoal")을 만든다. */
	private static String entry(File pom, MavenGoal g)
	{
		return GoalHistory.entry(key(pom), MavenExecutor.goalString(g));
	}

	/** 메뉴에 표시할 라벨("프로젝트 : goal")을 만든다. */
	private String entryLabel(String entry)
	{
		String pomPath = GoalHistory.pomOf(entry);
		MavenGoal project = projects.get(pomPath);
		String name = project != null ? project.getName() : new File(pomPath).getParentFile().getName();
		return name + " : " + GoalLabels.shorten(GoalHistory.goalOf(entry));
	}

	/** 노드가 즐겨찾기에 등록돼 있는지 확인한다. */
	private boolean isFavorite(MavenGoal g)
	{
		File pom = g.resolvePomFile();
		return g.getGoal() != null && pom != null && history.isFavorite(entry(pom, g));
	}

	/** 즐겨찾기 등록/해제를 토글하고 저장한다. */
	private void toggleFavorite(MavenGoal g)
	{
		File pom = g.resolvePomFile();
		if (pom == null || g.getGoal() == null)
			return;
		history.toggleFavorite(entry(pom, g));
		saveGoalLists();
		viewer.refresh();
		updateFavoritesIcon();
	}

	/** 최근 실행 목록 맨 앞에 추가하고(중복 제거, 최대 10개 유지) 저장한다. */
	private void addRecent(String entry)
	{
		history.addRecent(entry);
		saveGoalLists();
	}

	/** 즐겨찾기/최근 실행 목록을 preference에서 읽는다(최근 실행은 최대 개수까지만). */
	private void loadGoalLists()
	{
		ViewPreferences.loadHistory(history);
	}

	/** 즐겨찾기/최근 실행 목록을 preference에 저장한다. */
	private void saveGoalLists()
	{
		ViewPreferences.saveHistory(history);
	}

	/**
	 * 삭제된 pom.xml을 정리한다. 파일이 없어진 등록 프로젝트는 트리와 등록 목록에서 빼고, 그 pom의 즐겨찾기와 최근 실행 항목도 함께 지운다.
	 * 뷰가 활성화될 때, 새로고침할 때, 실행 목록을 열 때 호출한다.
	 */
	private void pruneMissingPoms()
	{
		boolean projectsChanged = false;
		for (String path : new ArrayList<>(projects.keySet()))
		{
			if (!new File(path).isFile())
			{
				MavenGoal old = projects.remove(path);
				pomWatcher.forget(path);
				if (old != null)
					MavenPomParser.dispose(old);
				projectsChanged = true;
			}
		}
		boolean historyChanged = history.prune(path -> new File(path).isFile());
		if (projectsChanged)
		{
			visibleNodes = null;
			persistRegisteredPoms();
		}
		if (historyChanged)
			saveGoalLists();
		if (projectsChanged || historyChanged)
		{
			viewer.refresh();
			updateFavoritesIcon();
		}
	}

	/** 항목을 현재 실행 방식(외부 mvn/내장)으로 실행하고 최근 목록에 올린다. */
	private void runEntry(String entry)
	{
		File pom = new File(GoalHistory.pomOf(entry));
		if (!pom.isFile())
		{
			pruneMissingPoms();
			return;
		}
		addRecent(entry);
		MavenExecutor.run(pom, GoalHistory.goalOf(entry), useExternalMvn);
	}

	/** 대화상자로 goal과 옵션을 정해 실행한다. PROJECT 노드는 기본 goal로 "clean install"을 제안한다. 사용한 옵션은 다음 실행을 위해 저장한다. */
	private void runWithOptions(MavenGoal g)
	{
		File pom = g.resolvePomFile();
		if (pom == null)
			return;
		RunOptions last = ViewPreferences.loadRunOptions();
		String initialGoals = g.getType() == MavenGoal.Type.PROJECT ? "clean install" : MavenExecutor.goalString(g);
		RunOptionsDialog dialog = new RunOptionsDialog(viewer.getControl().getShell(), g.getName(), initialGoals, last);
		if (dialog.open() != Window.OK)
			return;
		RunOptions chosen = dialog.getOptions();
		ViewPreferences.saveRunOptions(chosen);
		String commandLine = dialog.getCommandLine();
		if (commandLine.isEmpty())
			return;
		addRecent(GoalHistory.entry(key(pom), commandLine));
		MavenExecutor.run(pom, commandLine, useExternalMvn);
	}

	/** 트리 우클릭 메뉴: 실행 가능한 노드에 대해 Run과 즐겨찾기 추가/해제를 제공한다. */
	private void hookContextMenu()
	{
		MenuManager manager = new MenuManager();
		manager.setRemoveAllWhenShown(true);
		manager.addMenuListener(m -> {
			if (!(viewer.getSelection() instanceof IStructuredSelection ss) || !(ss.getFirstElement() instanceof MavenGoal g))
				return;
			if (g.getGoal() != null)
			{
				m.add(new Action(Messages.get("menu.run"))
				{
					@Override
					public void run()
					{
						runSelectedGoal();
					}
				});
			}
			if (g.getGoal() != null || g.getType() == MavenGoal.Type.PROJECT)
			{
				m.add(new Action(Messages.get("menu.runWithOptions"))
				{
					@Override
					public void run()
					{
						runWithOptions(g);
					}
				});
			}
			if (g.getGoal() != null)
			{
				m.add(new Action(isFavorite(g) ? Messages.get("favorites.remove") : Messages.get("favorites.add"))
				{
					@Override
					public void run()
					{
						toggleFavorite(g);
					}
				});
			}
		});
		viewer.getControl().setMenu(manager.createContextMenu(viewer.getControl()));
	}

	/** 선택한 goal이 즐겨찾기면 채워진 별, 아니면 빈 별 아이콘을 툴바 버튼에 표시한다. */
	private void updateFavoritesIcon()
	{
		if (favoritesAction == null)
			return;
		boolean on = viewer.getSelection() instanceof IStructuredSelection ss
			&& ss.getFirstElement() instanceof MavenGoal g
			&& isFavorite(g);
		favoritesAction.setImageDescriptor(
			AbstractUIPlugin.imageDescriptorFromPlugin(
				"com.kcube.mavenview",
				on ? "icons/favorite_on.png" : "icons/favorite.png"));
	}

	/** 툴바의 즐겨찾기/최근 실행 버튼을 만든다. 별을 누르면 목록 메뉴가 바로 열리고, 항목을 고르면 실행한다. */
	private Action createFavoritesAction()
	{
		Action action = new Action(Messages.get("favorites.action"))
		{
			@Override
			public void run()
			{
				showGoalMenu();
			}
		};
		favoritesAction = action;
		action.setToolTipText(Messages.get("favorites.tooltip"));
		updateFavoritesIcon();
		return action;
	}

	/** 마우스 위치(눌린 별 버튼 근처)에 즐겨찾기/최근 실행 메뉴를 띄운다. */
	private void showGoalMenu()
	{
		Control control = viewer.getControl();
		MenuManager manager = new MenuManager();
		fillGoalMenu(manager);
		Menu menu = manager.createContextMenu(control);
		// 메뉴가 닫히면 정리한다(선택한 항목의 run()이 먼저 실행된 뒤에 해제되도록 비동기로).
		menu.addListener(SWT.Hide, e -> control.getDisplay().asyncExec(() -> {
			if (!menu.isDisposed())
				menu.dispose();
			manager.dispose();
		}));
		Point p = control.getDisplay().getCursorLocation();
		menu.setLocation(p.x, p.y);
		menu.setVisible(true);
	}

	/** 드롭다운 메뉴 내용을 채운다: 즐겨찾기, 구분선, 최근 실행, 최근 목록 지우기. */
	private void fillGoalMenu(MenuManager manager)
	{
		pruneMissingPoms();
		// 선택한 goal이 있으면 메뉴 맨 위에서 즐겨찾기에 추가/해제할 수 있다.
		if (viewer.getSelection() instanceof IStructuredSelection ss
			&& ss.getFirstElement() instanceof MavenGoal g
			&& g.getGoal() != null)
		{
			manager.add(new Action(Messages.get(isFavorite(g) ? "favorites.remove" : "favorites.add"))
			{
				@Override
				public void run()
				{
					toggleFavorite(g);
				}
			});
			manager.add(new Separator());
		}
		if (history.favorites().isEmpty() && history.recents().isEmpty())
		{
			Action empty = new Action(Messages.get("favorites.empty"))
			{
			};
			empty.setEnabled(false);
			manager.add(empty);
			return;
		}
		for (String e : history.favorites())
		{
			manager.add(new Action("\u2605 " + entryLabel(e))
			{
				@Override
				public void run()
				{
					runEntry(e);
				}
			});
		}
		if (!history.favorites().isEmpty() && !history.recents().isEmpty())
			manager.add(new Separator());
		for (String e : history.recents())
		{
			manager.add(new Action(entryLabel(e))
			{
				@Override
				public void run()
				{
					runEntry(e);
				}
			});
		}
		if (!history.recents().isEmpty())
		{
			manager.add(new Separator());
			manager.add(new Action(Messages.get("favorites.clearRecent"))
			{
				@Override
				public void run()
				{
					history.clearRecents();
					saveGoalLists();
				}
			});
		}
	}

	/** Expand All 범위를 선택 노드 하위로 한정하기 위해, 자식이 있는 노드가 선택돼 있으면 그 노드를 반환한다. */
	private Object getSelectedContainer()
	{
		if (viewer.getSelection() instanceof IStructuredSelection ss
			&& !ss.isEmpty()
			&& ss.getFirstElement() instanceof MavenGoal g
			&& !MavenPomParser.children(g).isEmpty())
		{
			return g;
		}
		return null;
	}

	/** pom.xml 추가 대화상자 흐름(파일 선택, 선택한 프로젝트에서 자동 탐색)을 담당하는 도우미. */
	private PomAdder pomAdder()
	{
		return new PomAdder(
			viewer.getControl().getShell(),
			selectionTracker,
			pomFile -> projects.containsKey(key(pomFile)),
			this::addPom,
			this::persistRegisteredPoms);
	}

	/**
	 * 선택된 PROJECT 노드들을 등록 목록과 트리에서 제거한다.
	 */
	private void removeSelected()
	{
		visibleNodes = null;
		if (viewer.getSelection() instanceof IStructuredSelection ss)
		{
			boolean changed = false;
			for (Object o : ss.toArray())
			{
				if (o instanceof MavenGoal g && g.getType() == MavenGoal.Type.PROJECT)
				{
					String key = key(g.resolvePomFile());
					if (projects.remove(key) != null)
					{
						pomWatcher.forget(key);
						MavenPomParser.dispose(g);
						changed = true;
					}
				}
			}
			if (changed)
			{
				persistRegisteredPoms();
				viewer.refresh();
			}
		}
	}

	/**
	 * 등록된 모든 pom.xml을 다시 파싱해 트리를 최신 상태로 갱신한다(단축키/새로고침 버튼용).
	 */
	public void refreshAll()
	{
		pruneMissingPoms();
		reparseKeepingExpansion(new java.util.ArrayList<>(projects.keySet()));
	}

	/** 새 pom.xml 하나를 파싱해 등록하고, 트리를 갱신한 뒤 Lifecycle/Plugins까지 펼쳐 보여준다. */
	private void addPom(File pomFile)
	{
		reparse(pomFile);
		viewer.refresh();
		viewer.expandToLevel(TOP_LEVEL_EXPAND_DEPTH);
	}

	/** pom.xml을 파싱해 projects 맵에 (교체) 등록하고, 기존에 같은 경로로 파싱된 트리가 있었다면 메모리에서 정리한다. */
	private void reparse(File pomFile)
	{
		visibleNodes = null;
		String key = key(pomFile);
		try
		{
			MavenGoal newRoot = MavenPomParser.parseProject(pomFile);
			MavenGoal old = projects.put(key, newRoot);
			pomWatcher.remember(key, newRoot);
			if (old != null)
				MavenPomParser.dispose(old);
		}
		catch (Exception e)
		{
			log(IStatus.ERROR, "Failed to parse " + pomFile, e);
		}
	}

	/** projects 맵의 키로 쓰는, pom.xml의 정규화된 절대 경로. */
	private static String key(File pomFile)
	{
		return pomFile.getAbsolutePath();
	}

	/** 현재 등록된 pom.xml 경로 목록을 workspace preference에 저장한다. */
	private void persistRegisteredPoms()
	{
		try
		{
			PomRegistryStore.save(projects.keySet());
		}
		catch (Exception e)
		{
			log(IStatus.WARNING, "Failed to persist registered pom.xml list", e);
		}
	}

	/** 뷰가 열릴 때 preference에 저장돼 있던 pom.xml 목록을 읽어 다시 등록한다(파일이 사라졌으면 경고만 남김). */
	private void loadRegisteredPoms()
	{
		for (String path : PomRegistryStore.load())
		{
			File pomFile = new File(path);
			if (pomFile.isFile())
				reparse(pomFile);
			else
				log(IStatus.WARNING, "Previously registered pom.xml no longer exists: " + path, null);
		}
		viewer.refresh();
		viewer.expandToLevel(TOP_LEVEL_EXPAND_DEPTH);
	}

	/** Eclipse Error Log에 메시지를 남긴다(상태/예외 모두 여기로 모아서 기록). */
	private void log(int severity, String message, Throwable e)
	{
		PluginLog.log(severity, message, e);
	}

	/** 뷰가 활성화되면 포커스를 트리에 준다. */
	@Override
	public void setFocus()
	{
		pruneMissingPoms();
		viewer.getControl().setFocus();
	}

	// 플러그인 항목은 getArguments()에 [groupId, artifactId]를 담고 있다. artifactId만
	// (굵게) 표시해야 긴 org.apache.maven.plugins:... 형태의 id도 읽기 쉽다.
	private static final class MavenGoalLabelProvider extends LabelProvider implements IFontProvider
	{
		private final Font boldFont = JFaceResources.getFontRegistry().getBold(JFaceResources.DEFAULT_FONT);
		/** 즐겨찾기 여부 판정기. 즐겨찾기 노드에는 별 표시를 붙인다. */
		private final java.util.function.Predicate<MavenGoal> favorite;

		/** @param favorite 노드가 즐겨찾기인지 알려주는 함수 */
		MavenGoalLabelProvider(java.util.function.Predicate<MavenGoal> favorite)
		{
			this.favorite = favorite;
		}

		/** 플러그인 노드는 artifactId만, 그 외 노드는 이름을 그대로 표시한다. */
		@Override
		public String getText(Object element)
		{
			if (element instanceof MavenGoal g && isPluginEntry(g))
				return g.getArguments()[1];
			if (element instanceof MavenGoal g && favorite.test(g))
				return "\u2605 " + g.getName();
			return element.toString();
		}

		/** 플러그인 노드는 굵은 글꼴을, 그 외 노드는 기본 글꼴(null)을 사용한다. */
		@Override
		public Font getFont(Object element)
		{
			return element instanceof MavenGoal g && isPluginEntry(g) ? boldFont : null;
		}

		/** 노드가 [groupId, artifactId]를 가진 개별 플러그인 항목인지 확인한다. */
		private static boolean isPluginEntry(MavenGoal g)
		{
			return g.getType() == MavenGoal.Type.PLUGIN && g.getArguments().length == 2;
		}
	}
}
