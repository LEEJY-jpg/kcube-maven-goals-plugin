package com.kcube.mavenview.services;

import java.util.Collection;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

import com.kcube.mavenview.model.MavenGoal;

/**
 * 검색어에 해당하는 트리 노드를 계산하는 순수 로직(UI 의존성 없음).
 * <p>
 * 노드는 자신이 일치하거나, 조상 중 하나가 일치하거나, 후손 중 하나가 일치하면 보인다. PROJECT 노드의 이름은 매칭 대상이 아니다.
 */
public final class GoalFilter
{
	/** 노드별 소문자 검색 키(이름 + goal + 인자). 노드는 불변이므로 한 번만 만들어 재사용한다. */
	private final Map<MavenGoal, String> searchKeys = new WeakHashMap<>();

	/**
	 * 보여줄 노드 집합(identity 기준)을 계산한다.
	 *
	 * @param roots 최상위 PROJECT 노드들
	 * @param filterText 소문자로 정규화되고 앞뒤 공백이 제거된 검색어
	 */
	public Set<MavenGoal> visibleNodes(Collection<MavenGoal> roots, String filterText)
	{
		Set<MavenGoal> result = Collections.newSetFromMap(new IdentityHashMap<>());
		for (MavenGoal root : roots)
			collectVisible(root, false, filterText, result);
		return result;
	}

	/** 트리를 한 번만 순회하며 보여줄 노드를 result에 모은다. 이 노드가 보이면 true를 반환한다. */
	private boolean collectVisible(MavenGoal node, boolean ancestorMatched, String filterText, Set<MavenGoal> result)
	{
		boolean matchedHere = ancestorMatched || selfMatches(node, filterText);
		boolean descendantMatched = false;
		for (MavenGoal child : MavenPomParser.children(node))
		{
			descendantMatched |= collectVisible(child, matchedHere, filterText, result);
		}
		boolean visible = matchedHere || descendantMatched;
		if (visible)
			result.add(node);
		return visible;
	}

	/** 표시 이름, 실행 문자열(goal), 플러그인 groupId/artifactId 중 하나라도 검색어를 포함하는지 본다. */
	private boolean selfMatches(MavenGoal g, String filterText)
	{
		if (g.getType() == MavenGoal.Type.PROJECT || g.getType() == MavenGoal.Type.MODULES)
			return false;
		return searchKeys.computeIfAbsent(g, GoalFilter::buildSearchKey).contains(filterText);
	}

	/** 노드의 이름, goal, 인자를 줄바꿈으로 이어 붙인 소문자 검색 키를 만든다. 검색어에는 줄바꿈이 없으므로 필드 경계를 넘는 오탐은 없다. */
	static String buildSearchKey(MavenGoal g)
	{
		StringBuilder key = new StringBuilder(g.getName());
		if (g.getGoal() != null)
			key.append('\n').append(g.getGoal());
		for (String arg : g.getArguments())
		{
			if (arg != null)
				key.append('\n').append(arg);
		}
		return key.toString().toLowerCase();
	}
}
