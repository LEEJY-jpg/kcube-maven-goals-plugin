package com.kcube.mavenview.views;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Predicate;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.IPath;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.viewers.ArrayContentProvider;
import org.eclipse.jface.viewers.LabelProvider;
import org.eclipse.jface.window.Window;
import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.FileDialog;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.ui.dialogs.ListSelectionDialog;

import com.kcube.mavenview.Messages;
import com.kcube.mavenview.services.PomScanner;

/** "Add POM File..." / "Add from Selection" 대화상자 흐름. 실제 등록과 저장은 뷰가 넘겨준 콜백이 처리한다. */
final class PomAdder
{
	private final Shell shell;
	private final SelectionTracker tracker;
	private final Predicate<File> isRegistered;
	private final Consumer<File> register;
	private final Runnable onChanged;

	/**
	 * @param shell 대화상자의 부모 셸
	 * @param tracker 다른 파트에서 마지막으로 선택된 프로젝트
	 * @param isRegistered 이미 등록된 pom인지 판별
	 * @param register pom 하나를 등록(파싱 + 트리 갱신)
	 * @param onChanged 등록이 끝난 뒤 한 번 호출(목록 저장)
	 */
	PomAdder(Shell shell, SelectionTracker tracker, Predicate<File> isRegistered, Consumer<File> register,
		Runnable onChanged)
	{
		this.shell = shell;
		this.tracker = tracker;
		this.isRegistered = isRegistered;
		this.register = register;
		this.onChanged = onChanged;
	}

	/**
	 * 파일 다이얼로그로 디스크에서 pom.xml 파일(복수 선택 가능)을 골라 등록한다.
	 */
	void addViaDialog()
	{
		FileDialog dialog = new FileDialog(shell, SWT.OPEN | SWT.MULTI);
		dialog.setText(Messages.get("fileDialog.title"));
		dialog.setFilterNames(new String[] {Messages.get("fileDialog.pomFilter"), Messages.get("fileDialog.allFiles")});
		dialog.setFilterExtensions(new String[] {"pom.xml;*.xml", "*.*"});
		IPath workspaceLocation = ResourcesPlugin.getWorkspace().getRoot().getLocation();
		if (workspaceLocation != null)
			dialog.setFilterPath(workspaceLocation.toOSString());

		if (dialog.open() == null)
			return;
		String dir = dialog.getFilterPath();
		for (String fileName : dialog.getFileNames())
		{
			register.accept(new File(dir, fileName));
		}
		onChanged.run();
	}

	/**
	 * 선택한 프로젝트(복수 가능)에서 pom.xml을 자동으로 찾아 등록한다. 선택이 없으면 프로젝트를 고르게 한다. 후보가 하나면 바로 등록하고,
	 * 여러 개면 체크 목록에서 고르게 한다. 이미 등록된 pom은 후보에서 뺀다.
	 */
	void addFromSelection()
	{
		List<IProject> targets = new ArrayList<>();
		for (IProject p : tracker.lastProjects())
		{
			if (p.exists() && p.getLocation() != null)
				targets.add(p);
		}
		if (targets.isEmpty())
		{
			targets = chooseWorkspaceProjects(shell);
			if (targets.isEmpty())
				return;
		}

		Map<File, String> candidates = new LinkedHashMap<>();
		int alreadyRegistered = 0;
		for (IProject p : targets)
		{
			File dir = p.getLocation().toFile();
			for (File pom : PomScanner.find(dir))
			{
				if (isRegistered.test(pom))
					alreadyRegistered++;
				else
					candidates.put(pom, p.getName() + "  \u2014  " + dir.toPath().relativize(pom.toPath()));
			}
		}
		String title = Messages.get("scan.title");
		if (candidates.isEmpty())
		{
			MessageDialog.openInformation(
				shell,
				title,
				Messages.get(alreadyRegistered > 0 ? "scan.allRegistered" : "scan.none"));
			return;
		}

		List<File> chosen = new ArrayList<>(candidates.keySet());
		if (candidates.size() > 1)
		{
			ListSelectionDialog dialog = new ListSelectionDialog(
				shell,
				chosen,
				ArrayContentProvider.getInstance(),
				new LabelProvider()
				{
					@Override
					public String getText(Object element)
					{
						return candidates.get(element);
					}
				},
				Messages.get("scan.message"));
			dialog.setTitle(title);
			dialog.setInitialElementSelections(chosen);
			if (dialog.open() != Window.OK)
				return;
			chosen = new ArrayList<>();
			for (Object o : dialog.getResult())
				chosen.add((File) o);
		}
		for (File pom : chosen)
			register.accept(pom);
		if (!chosen.isEmpty())
			onChanged.run();
	}

	/** 선택된 프로젝트가 없을 때, 루트에 pom.xml이 있는 열린 워크스페이스 프로젝트를 체크 목록으로 보여주고 고르게 한다. */
	private List<IProject> chooseWorkspaceProjects(Shell shell)
	{
		List<IProject> mavenProjects = new ArrayList<>();
		for (IProject p : ResourcesPlugin.getWorkspace().getRoot().getProjects())
		{
			if (p.isOpen() && p.getLocation() != null && new File(p.getLocation().toFile(), "pom.xml").isFile())
				mavenProjects.add(p);
		}
		if (mavenProjects.isEmpty())
		{
			MessageDialog.openInformation(shell, Messages.get("scan.title"), Messages.get("scan.none"));
			return List.of();
		}
		ListSelectionDialog dialog = new ListSelectionDialog(
			shell,
			mavenProjects,
			ArrayContentProvider.getInstance(),
			new LabelProvider()
			{
				@Override
				public String getText(Object element)
				{
					return ((IProject) element).getName();
				}
			},
			Messages.get("scan.chooseProjects"));
		dialog.setTitle(Messages.get("scan.title"));
		if (dialog.open() != Window.OK)
			return List.of();
		List<IProject> chosen = new ArrayList<>();
		for (Object o : dialog.getResult())
			chosen.add((IProject) o);
		return chosen;
	}
}
