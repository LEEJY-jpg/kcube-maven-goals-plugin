package com.kcube.mavenview.views;

import org.eclipse.jface.action.Action;
import org.eclipse.jface.action.ControlContribution;
import org.eclipse.jface.action.IAction;
import org.eclipse.jface.action.IToolBarManager;
import org.eclipse.jface.action.Separator;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionListener;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.ui.ISharedImages;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.plugin.AbstractUIPlugin;

import com.kcube.mavenview.Messages;
import com.kcube.mavenview.services.MavenExecutor;

/** 뷰 툴바의 버튼/체크박스 정의와 배치. 실제 동작은 {@link Handler}를 통해 뷰가 처리한다. */
final class ViewToolbar
{
	/** 툴바 버튼이 호출하는 뷰 쪽 동작. */
	interface Handler
	{
		void run();

		void update();

		void add();

		void addFromSelection();

		void remove();

		void refresh();

		void expandAll();

		void collapseAll();

		boolean useExternalMvn();

		void setUseExternalMvn(boolean value);
	}

	private ViewToolbar()
	{
	}

	/**
	 * 툴바에 올라가는 모든 Action/위젯을 생성하고 배치한다.
	 *
	 * @param toolbar 뷰의 툴바 매니저
	 * @param h 버튼 동작을 처리하는 뷰 쪽 핸들러
	 * @param favorites 뷰가 관리하는 즐겨찾기(별) 드롭다운 액션
	 */
	static void build(IToolBarManager toolbar, Handler h, IAction favorites)
	{
		ISharedImages images = PlatformUI.getWorkbench().getSharedImages();

		// 더블클릭과 동일하게, 현재 선택된 goal을 실행한다. 다른 툴바 버튼들과 마찬가지로 항상 활성 상태이며,
		// 실행 불가능한 노드가 선택된 경우 runSelectedGoal()이 조용히 무시한다.
		Action run = new Action(Messages.get("action.run"))
		{
			/** 선택된 goal을 실행한다(더블클릭과 동일). */
			@Override
			public void run()
			{
				h.run();
			}
		};
		run.setToolTipText(Messages.get("action.run.tooltip"));
		run.setImageDescriptor(AbstractUIPlugin.imageDescriptorFromPlugin("com.kcube.mavenview", "icons/run.png"));

		// 실행 중인 외부 mvn 빌드를 모두 중단한다(내장 Maven은 Eclipse 콘솔의 Terminate 버튼 사용).
		Action stop = new Action(Messages.get("action.stop"))
		{
			/** 실행 중인 외부 mvn 프로세스를 중단한다. */
			@Override
			public void run()
			{
				MavenExecutor.stopAll();
			}
		};
		stop.setToolTipText(Messages.get("action.stop.tooltip"));
		stop.setImageDescriptor(images.getImageDescriptor(ISharedImages.IMG_ELCL_STOP));

		Action updateProject = new Action(Messages.get("action.update"))
		{
			/** 선택된 프로젝트에 대해 Update Maven Project를 수행한다. */
			@Override
			public void run()
			{
				h.update();
			}
		};
		updateProject.setToolTipText(Messages.get("action.update.tooltip"));
		updateProject.setImageDescriptor(
			AbstractUIPlugin.imageDescriptorFromPlugin("com.kcube.mavenview", "icons/update_dependencies.png"));

		Action add = new Action(Messages.get("action.add"))
		{
			/** 파일 다이얼로그로 pom.xml을 골라 등록한다. */
			@Override
			public void run()
			{
				h.add();
			}
		};
		add.setToolTipText(Messages.get("action.add.tooltip"));
		add.setImageDescriptor(images.getImageDescriptor(ISharedImages.IMG_OBJ_ADD));

		Action addFromSelection = new Action(Messages.get("action.addSelection"))
		{
			/** 다른 뷰에서 마지막으로 선택한 리소스의 pom.xml을 등록한다. */
			@Override
			public void run()
			{
				h.addFromSelection();
			}
		};
		addFromSelection.setToolTipText(Messages.get("action.addSelection.tooltip"));
		addFromSelection.setImageDescriptor(
			AbstractUIPlugin.imageDescriptorFromPlugin("com.kcube.mavenview", "icons/add_project.png"));

		Action remove = new Action(Messages.get("action.remove"))
		{
			/** 선택된 프로젝트를 등록 목록에서 제거한다. */
			@Override
			public void run()
			{
				h.remove();
			}
		};
		remove.setToolTipText(Messages.get("action.remove.tooltip"));
		remove.setImageDescriptor(images.getImageDescriptor(ISharedImages.IMG_TOOL_DELETE));

		Action refresh = new Action(Messages.get("action.refresh"))
		{
			/** 등록된 모든 pom.xml을 다시 파싱한다. */
			@Override
			public void run()
			{
				h.refresh();
			}
		};
		refresh.setToolTipText(Messages.get("action.refresh.tooltip"));
		refresh.setImageDescriptor(
			AbstractUIPlugin.imageDescriptorFromPlugin("org.eclipse.ui.ide", "icons/full/elcl16/refresh_nav.png"));

		Action expandAll = new Action(Messages.get("action.expandAll"))
		{
			/** 선택된 노드 하위(없으면 트리 전체)를 모두 펼친다. */
			@Override
			public void run()
			{
				h.expandAll();
			}
		};
		expandAll.setToolTipText(Messages.get("action.expandAll"));
		expandAll.setImageDescriptor(
			AbstractUIPlugin.imageDescriptorFromPlugin("org.eclipse.ui", "icons/full/elcl16/expandall.png"));

		Action collapseAll = new Action(Messages.get("action.collapseAll"))
		{
			/** 3단계 이하를 접는다. 프로젝트와 2단계 노드(Lifecycle/Plugins/Modules)는 펼친 상태로 둔다. */
			@Override
			public void run()
			{
				h.collapseAll();
			}
		};
		collapseAll.setToolTipText(Messages.get("action.collapseAll"));
		collapseAll.setImageDescriptor(
			AbstractUIPlugin.imageDescriptorFromPlugin("org.eclipse.ui", "icons/full/elcl16/collapseall.png"));

		// 네이티브 SWT 체크박스를 툴바 맨 왼쪽에 직접 꽂아넣기 위한 ControlContribution.
		// Composite로 한 번 감싸는 이유는 GridData(CENTER)로 세로 정렬을 맞추기 위함.
		ControlContribution useExternalCheckbox = new ControlContribution("useExternalMvnCheckbox")
		{
			/** "mvn" 체크박스를 담은 컨트롤을 만든다. 상태가 바뀌면 실행 방식을 preference에 저장한다. */
			@Override
			protected Control createControl(Composite parent)
			{
				Composite holder = new Composite(parent, SWT.NONE);
				GridLayout layout = new GridLayout(1, false);
				layout.marginWidth = 0;
				layout.marginHeight = 0;
				holder.setLayout(layout);

				Button checkbox = new Button(holder, SWT.CHECK);
				checkbox.setLayoutData(new GridData(SWT.LEFT, SWT.CENTER, false, true));
				checkbox.setText("mvn");
				checkbox.setToolTipText(Messages.get("action.mvn.tooltip"));
				checkbox.setSelection(h.useExternalMvn());
				checkbox.addSelectionListener(SelectionListener.widgetSelectedAdapter(e -> {
					h.setUseExternalMvn(checkbox.getSelection());
				}));
				return holder;
			}
		};

		toolbar.add(useExternalCheckbox);
		toolbar.add(new Separator());
		toolbar.add(run);
		toolbar.add(stop);
		toolbar.add(favorites);
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
}
