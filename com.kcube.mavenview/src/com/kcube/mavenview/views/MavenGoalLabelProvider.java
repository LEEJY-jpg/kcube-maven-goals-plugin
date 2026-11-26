package com.kcube.mavenview.views;

import java.util.function.Predicate;
import java.util.function.UnaryOperator;

import org.eclipse.jface.resource.JFaceResources;
import org.eclipse.jface.viewers.ColumnLabelProvider;
import org.eclipse.swt.graphics.Font;

import com.kcube.mavenview.Messages;
import com.kcube.mavenview.model.MavenGoal;
import com.kcube.mavenview.services.GoalDescriptions;
import com.kcube.mavenview.services.GoalHistory;

/**
 * 트리 노드의 표시 텍스트/글꼴/툴팁. 플러그인 항목은 getArguments()에 [groupId, artifactId]를 담고 있고,
 * 긴 org.apache.maven.plugins:... 형태의 id도 읽기 쉽도록 artifactId만 (굵게) 표시한다. 툴팁은 뷰에서
 * ColumnViewerToolTipSupport 를 켜야 표시된다.
 */
final class MavenGoalLabelProvider extends ColumnLabelProvider
{
	private final Font boldFont = JFaceResources.getFontRegistry().getBold(JFaceResources.DEFAULT_FONT);
	/** 즐겨찾기 여부 판정기. 즐겨찾기 노드에는 별 표시를 붙인다. */
	private final Predicate<MavenGoal> favorite;

	/** 즐겨찾기 항목 문자열을 표시 라벨로 바꾸는 함수. */
	private final UnaryOperator<String> entryLabel;

	/**
	 * @param favorite 노드가 즐겨찾기인지 알려주는 함수
	 * @param entryLabel 즐겨찾기 항목의 표시 라벨을 만드는 함수
	 */
	MavenGoalLabelProvider(Predicate<MavenGoal> favorite, UnaryOperator<String> entryLabel)
	{
		this.favorite = favorite;
		this.entryLabel = entryLabel;
	}

	/** 플러그인 노드는 artifactId만, 그 외 노드는 이름을 그대로 표시한다. */
	@Override
	public String getText(Object element)
	{
		if (element == FavoriteNode.Folder.INSTANCE)
			return "★ " + Messages.get("favorites.node");
		if (element instanceof FavoriteNode f)
			return entryLabel.apply(f.entry());
		if (element instanceof MavenGoal g && isPluginEntry(g))
			return g.getArguments()[1];
		if (element instanceof MavenGoal g && favorite.test(g))
			return "★ " + g.getName();
		return element.toString();
	}

	/** 플러그인 노드는 굵은 글꼴을, 그 외 노드는 기본 글꼴(null)을 사용한다. */
	@Override
	public Font getFont(Object element)
	{
		return element instanceof MavenGoal g && isPluginEntry(g) ? boldFont : null;
	}

	/** 마우스를 올렸을 때 보여 줄 노드 설명(phase/goal의 기능, 실행 문자열 등). */
	@Override
	public String getToolTipText(Object element)
	{
		if (element instanceof FavoriteNode f)
			return GoalHistory.goalOf(f.entry()) + "\n" + GoalHistory.pomOf(f.entry());
		return element instanceof MavenGoal g ? GoalDescriptions.describe(g) : null;
	}

	/** 노드가 [groupId, artifactId]를 가진 개별 플러그인 항목인지 확인한다. */
	private static boolean isPluginEntry(MavenGoal g)
	{
		return g.getType() == MavenGoal.Type.PLUGIN && g.getArguments().length == 2;
	}
}
