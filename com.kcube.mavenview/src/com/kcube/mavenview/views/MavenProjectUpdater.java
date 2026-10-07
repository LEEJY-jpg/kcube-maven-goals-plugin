package com.kcube.mavenview.views;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Shell;

import com.kcube.mavenview.Messages;
import com.kcube.mavenview.model.MavenGoal;
import com.kcube.mavenview.services.PluginLog;

/** 트리에서 선택한 프로젝트에 m2e의 Update Maven Project를 수행한다. */
final class MavenProjectUpdater
{
	private MavenProjectUpdater()
	{
	}

	/** 선택된 PROJECT 노드 중 워크스페이스에 실제 임포트된 프로젝트만 골라 Update Maven Project를 수행한다. */
	static void updateSelected(IStructuredSelection ss)
	{
		if (ss == null || ss.isEmpty())
		{
			notifyUser(IStatus.WARNING, "update.noSelection", null);
			return;
		}
		List<IProject> toUpdate = new ArrayList<>();
		for (Object o : ss.toArray())
		{
			if (o instanceof MavenGoal g)
			{
				File pom = g.resolvePomFile();
				IProject project = pom == null ? null : findWorkspaceProject(pom.getParentFile());
				if (project != null)
					toUpdate.add(project);
			}
		}
		if (toUpdate.isEmpty())
		{
			notifyUser(IStatus.WARNING, "update.notWorkspace", null);
			return;
		}
		scheduleUpdate(toUpdate);
	}

	/** m2e 버전에 따라 생성자 시그니처(IProject[] / Collection)가 달라 리플렉션으로 호출한다. */
	private static void scheduleUpdate(List<IProject> projects)
	{
		try
		{
			// m2e 내부(internal) 클래스라 import하면 PDE 접근 제한 오류가 나므로 이름으로 로드한다.
			Class<?> jobClass = Class.forName("org.eclipse.m2e.core.ui.internal.UpdateMavenProjectJob");
			Job job;
			try
			{
				job = (Job) jobClass.getConstructor(IProject[].class).newInstance(
					(Object) projects.toArray(new IProject[0]));
			}
			catch (NoSuchMethodException e)
			{
				job = (Job) jobClass.getConstructor(java.util.Collection.class).newInstance(projects);
			}
			job.schedule();
		}
		catch (ReflectiveOperationException e)
		{
			// m2e 내부 클래스를 리플렉션으로 호출하므로 m2e 버전이 바뀌면 여기서 실패할 수 있다.
			notifyUser(IStatus.ERROR, "update.failed", e);
		}
	}

	/** 결과를 Error Log에 남기고, 사용자 동작(버튼 클릭)의 결과이므로 대화상자로도 알린다. */
	private static void notifyUser(int severity, String messageKey, Throwable e)
	{
		String message = Messages.get(messageKey);
		PluginLog.log(severity, message, e);
		Display display = Display.getCurrent();
		if (display == null)
			return;
		Shell shell = display.getActiveShell();
		String title = Messages.get("update.title");
		if (severity == IStatus.ERROR)
			MessageDialog.openError(shell, title, message + (e != null ? "\n\n" + e : ""));
		else
			MessageDialog.openInformation(shell, title, message);
	}

	/**
	 * pom.xml이 있는 디렉터리와 위치가 일치하는 워크스페이스 IProject를 찾는다. 없으면 null.
	 */
	private static IProject findWorkspaceProject(File dir)
	{
		if (dir == null)
			return null;
		for (IProject p : ResourcesPlugin.getWorkspace().getRoot().getProjects())
		{
			IPath loc = p.getLocation();
			if (loc != null && loc.toFile().equals(dir))
				return p;
		}
		return null;
	}
}
