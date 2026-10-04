package com.kcube.mavenview.views;

import java.io.File;
import java.util.function.Consumer;
import java.util.function.Predicate;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IResource;
import org.eclipse.swt.dnd.DND;
import org.eclipse.swt.dnd.DropTarget;
import org.eclipse.swt.dnd.DropTargetAdapter;
import org.eclipse.swt.dnd.DropTargetEvent;
import org.eclipse.swt.dnd.FileTransfer;
import org.eclipse.swt.dnd.Transfer;
import org.eclipse.swt.widgets.Control;
import org.eclipse.ui.part.ResourceTransfer;

/** Finder/Project Explorer 등에서 pom.xml 또는 프로젝트 폴더를 끌어다 놓으면 등록 콜백을 호출하는 드롭 대상 설정. */
final class PomDropSupport
{
	private PomDropSupport()
	{
	}

	/**
	 * @param control 드롭을 받을 컨트롤
	 * @param register pom.xml을 등록하는 콜백. 등록했으면 true
	 * @param onChanged 하나라도 등록된 뒤 한 번 호출된다(목록 저장용)
	 * @param warn pom.xml이 아닌 항목을 무시할 때 호출되는 경고 출력
	 */
	static void install(Control control, Predicate<File> register, Runnable onChanged, Consumer<String> warn)
	{
		DropTarget target = new DropTarget(control, DND.DROP_COPY | DND.DROP_DEFAULT);
		target.setTransfer(new Transfer[] {FileTransfer.getInstance(), ResourceTransfer.getInstance()});
		target.addDropListener(new DropTargetAdapter()
		{
			/** 드래그 중인 항목이 들어올 때 기본 동작을 복사(COPY)로 지정한다. */
			@Override
			public void dragEnter(DropTargetEvent event)
			{
				if (event.detail == DND.DROP_DEFAULT)
					event.detail = DND.DROP_COPY;
			}

			/** 드롭된 파일/리소스에서 pom.xml을 찾아 등록하고, 등록이 있었으면 콜백으로 알린다. */
			@Override
			public void drop(DropTargetEvent event)
			{
				boolean changed = false;
				if (FileTransfer.getInstance().isSupportedType(event.currentDataType))
				{
					// Finder 등 OS 파일 매니저에서 드롭: 파일 경로 문자열 배열로 전달됨.
					for (String path : (String[]) event.data)
						changed |= registerPath(new File(path), register, warn);
				}
				else if (ResourceTransfer.getInstance().isSupportedType(event.currentDataType))
				{
					// Project/Package Explorer에서 드롭: 워크스페이스 IResource로 전달됨.
					for (IResource resource : (IResource[]) event.data)
					{
						IFile pomFile = resource instanceof IFile f && f.getName().equals("pom.xml")
							? f
							: resource.getProject().getFile("pom.xml");
						if (pomFile.exists())
							changed |= register.test(pomFile.getLocation().toFile());
					}
				}
				if (changed)
					onChanged.run();
			}
		});
	}

	/** 드롭된 경로가 pom.xml이면 그대로, 프로젝트 폴더면 그 안의 pom.xml을 등록한다. */
	private static boolean registerPath(File dropped, Predicate<File> register, Consumer<String> warn)
	{
		File pomFile = dropped.isDirectory() ? new File(dropped, "pom.xml") : dropped;
		if (!"pom.xml".equals(pomFile.getName()) || !pomFile.isFile())
		{
			warn.accept("Not a pom.xml, ignored: " + dropped);
			return false;
		}
		return register.test(pomFile);
	}
}
