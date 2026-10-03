package com.kcube.mavenview.handlers;

import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.ui.IViewPart;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.handlers.HandlerUtil;

import com.kcube.mavenview.views.MavenGoalsView;

/**
 * plugin.xml에 등록된 커맨드(단축키)를 통해 Maven Goals 뷰 전체 새로고침을 트리거하는 핸들러.
 */
public final class RefreshHandler extends AbstractHandler
{
	/** Refresh 커맨드 실행 시 호출된다. Maven Goals 뷰가 열려 있으면 등록된 모든 pom.xml을 다시 파싱한다. */
	@Override
	public Object execute(ExecutionEvent event) throws ExecutionException
	{
		IWorkbenchPage page = HandlerUtil.getActiveWorkbenchWindow(event).getActivePage();
		if (page != null)
		{
			// 뷰가 열려 있지 않으면 아무 동작도 하지 않는다(새로 열면서까지 새로고침하지 않음).
			IViewPart part = page.findView(MavenGoalsView.ID);
			if (part instanceof MavenGoalsView view)
				view.refreshAll();
		}
		return null;
	}
}
