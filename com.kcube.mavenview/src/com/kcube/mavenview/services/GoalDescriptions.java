package com.kcube.mavenview.services;

import java.io.File;
import java.util.regex.Matcher;

import com.kcube.mavenview.Messages;
import com.kcube.mavenview.model.MavenGoal;

/**
 * 트리 노드에 마우스를 올렸을 때 보여 줄 설명(툴팁)을 만든다. 설명 문구는 언어별 messages 파일의 {@code desc.*} 키에 있다:
 * lifecycle phase는 {@code desc.phase.<phase>}, 플러그인 goal은 {@code desc.goal.<prefix>.<goal>}(예: desc.goal.war.exploded).
 */
public final class GoalDescriptions
{
	/** 유틸리티 클래스이므로 인스턴스를 만들 수 없다. */
	private GoalDescriptions()
	{
	}

	/** 노드의 툴팁 문자열. 보여 줄 것이 없으면 null. */
	public static String describe(MavenGoal node)
	{
		switch (node.getType())
		{
			case PROJECT:
				File pom = node.getPomFile();
				return pom == null ? null : pom.getAbsolutePath();
			case LIFECYCLE:
				return Messages.find("desc.folder.lifecycle");
			case MODULES:
				return Messages.find("desc.folder.modules");
			case PLUGIN:
				return node.getArguments().length == 2 ? node.getName() : Messages.find("desc.folder.plugins");
			case GOAL:
				return Messages.find("desc.phase." + node.getGoal());
			case EXECUTION:
				return describeExecution(node.getGoal());
			default:
				return null;
		}
	}

	/** "group:artifact:goal[@id]" 실행 노드의 설명: 알려진 goal이면 그 설명, execution id가 있으면 그 안내, 끝에 실제 실행 문자열. */
	private static String describeExecution(String invocation)
	{
		Matcher m = GoalLabels.PLUGIN_GOAL.matcher(invocation);
		if (!m.matches())
			return invocation;
		StringBuilder sb = new StringBuilder();
		String desc = Messages.find("desc.goal." + MavenPomParser.pluginPrefix(m.group(1)) + "." + m.group(2));
		if (desc != null)
			sb.append(desc).append('\n');
		if (m.group(3) != null)
			sb.append(Messages.get("desc.execution", m.group(3))).append('\n');
		return sb.append("mvn ").append(invocation).toString();
	}
}
