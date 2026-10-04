package com.kcube.mavenview.services;

import java.util.ArrayList;
import java.util.List;

/**
 * "Run with Options..."에서 고른 Maven 실행 옵션. goal 문자열 뒤에 붙일 명령행 조각으로 변환한다.
 *
 * @param skipTests {@code -DskipTests}
 * @param offline {@code -o}
 * @param updateSnapshots {@code -U}
 * @param profiles 쉼표로 구분한 프로파일 목록({@code -P}). 비어 있으면 생략
 * @param extraArgs 그대로 덧붙일 추가 인자(예: {@code -Dfoo=bar -T 4})
 */
public record RunOptions(boolean skipTests, boolean offline, boolean updateSnapshots, String profiles, String extraArgs)
{
	/** 아무 옵션도 켜지 않은 기본값. */
	public static final RunOptions NONE = new RunOptions(false, false, false, "", "");

	/** goals 뒤에 옵션을 이어 붙인 명령행 문자열(예: "clean install -DskipTests -Pdev")을 만든다. */
	public String toCommandLine(String goals)
	{
		StringBuilder sb = new StringBuilder(goals == null ? "" : goals.trim());
		if (skipTests)
			sb.append(" -DskipTests");
		if (offline)
			sb.append(" -o");
		if (updateSnapshots)
			sb.append(" -U");
		if (profiles != null && !profiles.isBlank())
			sb.append(" -P").append(profiles.replaceAll("\\s+", ""));
		if (extraArgs != null && !extraArgs.isBlank())
			sb.append(' ').append(extraArgs.trim());
		return sb.toString().trim();
	}

	/** 명령행 문자열을 공백 기준으로 나눈다. 작은따옴표/큰따옴표로 감싼 부분은 하나의 인자로 취급하고 따옴표는 제거한다. */
	public static List<String> tokenize(String commandLine)
	{
		List<String> tokens = new ArrayList<>();
		if (commandLine == null)
			return tokens;
		StringBuilder cur = new StringBuilder();
		boolean inToken = false;
		char quote = 0;
		for (int i = 0; i < commandLine.length(); i++)
		{
			char c = commandLine.charAt(i);
			if (quote != 0)
			{
				if (c == quote)
					quote = 0;
				else
					cur.append(c);
			}
			else if (c == '"' || c == '\'')
			{
				quote = c;
				inToken = true;
			}
			else if (Character.isWhitespace(c))
			{
				if (inToken)
				{
					tokens.add(cur.toString());
					cur.setLength(0);
					inToken = false;
				}
			}
			else
			{
				cur.append(c);
				inToken = true;
			}
		}
		if (inToken)
			tokens.add(cur.toString());
		return tokens;
	}
}
