package com.kcube.mavenview.services;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** 메뉴처럼 좁은 곳에 표시할 goal 문자열의 짧은 라벨을 만든다. 트리에 보이는 모양("prefix:goal@id")을 따른다. */
public final class GoalLabels
{
	/** "groupId:artifactId:goal" 또는 "groupId:artifactId:goal@executionId" 형태의 단일 토큰. */
	private static final Pattern PLUGIN_GOAL = Pattern.compile("^[^:\\s]+:([^:\\s]+):([^@\\s]+)(?:@(\\S+))?$");

	/** 유틸리티 클래스이므로 인스턴스를 만들 수 없다. */
	private GoalLabels()
	{
	}

	/**
	 * 명령행에서 플러그인 goal 토큰을 짧게 줄인다. {@code org.apache.maven.plugins:maven-antrun-plugin:run@js} → {@code antrun:run@js},
	 * {@code org.apache.maven.plugins:maven-antrun-plugin:run} → {@code antrun:run}. 트리의 execution 노드 라벨과 같은 모양이다. 그 외 토큰(phase, 옵션 등)은 그대로 둔다.
	 */
	public static String shorten(String commandLine)
	{
		StringBuilder sb = new StringBuilder();
		for (String token : RunOptions.tokenize(commandLine))
		{
			if (sb.length() > 0)
				sb.append(' ');
			Matcher m = PLUGIN_GOAL.matcher(token);
			if (m.matches())
			{
				String id = m.group(3);
				sb.append(MavenPomParser.pluginPrefix(m.group(1))).append(':').append(m.group(2));
				if (id != null)
					sb.append('@').append(id);
			}
			else
			{
				sb.append(token.contains(" ") ? '"' + token + '"' : token);
			}
		}
		return sb.toString();
	}
}
