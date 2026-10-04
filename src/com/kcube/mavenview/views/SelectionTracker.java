package com.kcube.mavenview.views;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.Adapters;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.ui.ISelectionListener;
import org.eclipse.ui.IWorkbenchPart;

/**
 * 다른 파트(예: Project/Package Explorer)에서 마지막으로 선택된 항목들이 속한 프로젝트를 기억한다. 포커스가 이 뷰의 툴바/트리로 넘어간 뒤에도
 * "Add from Selection"이 동작하도록 하기 위한 것이다. 프로젝트로 변환되지 않는 선택(예: 콘솔)은 무시해 기존 값을 유지한다.
 */
final class SelectionTracker implements ISelectionListener
{
	private final IWorkbenchPart owner;

	private List<IProject> lastProjects = List.of();

	/** @param owner 이 트래커를 쓰는 뷰. 뷰 자신의 선택은 무시한다. */
	SelectionTracker(IWorkbenchPart owner)
	{
		this.owner = owner;
	}

	/** 마지막으로 선택된 외부 프로젝트들. */
	List<IProject> lastProjects()
	{
		return lastProjects;
	}

	@Override
	public void selectionChanged(IWorkbenchPart part, ISelection selection)
	{
		if (part == owner || !(selection instanceof IStructuredSelection ss))
			return;
		Set<IProject> found = new LinkedHashSet<>();
		for (Object o : ss.toArray())
		{
			// Package Explorer는 IResource가 아닌 IJavaProject 등을 주므로 어댑터로 변환한다.
			IResource r = o instanceof IResource res ? res : Adapters.adapt(o, IResource.class);
			if (r != null && r.getProject() != null)
				found.add(r.getProject());
		}
		if (!found.isEmpty())
			lastProjects = new ArrayList<>(found);
	}
}
