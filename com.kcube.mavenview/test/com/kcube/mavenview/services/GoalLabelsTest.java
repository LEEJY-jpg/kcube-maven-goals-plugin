package com.kcube.mavenview.services;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class GoalLabelsTest
{
	@Test
	void shortensExecutionGoal()
	{
		assertEquals("foo-plugin:go@run-it", GoalLabels.shorten("org.foo:foo-plugin:go@run-it"));
	}

	@Test
	void shortensPluginGoalWithoutExecutionId()
	{
		assertEquals(
			"compiler:compile",
			GoalLabels.shorten("org.apache.maven.plugins:maven-compiler-plugin:compile"));
	}

	@Test
	void keepsPhasesAndOptions()
	{
		assertEquals("clean install -DskipTests -Pdev", GoalLabels.shorten("clean install -DskipTests -Pdev"));
	}

	@Test
	void shortensOnlyPluginTokensInCommandLine()
	{
		assertEquals("foo-plugin:go -o", GoalLabels.shorten("org.foo:foo-plugin:go -o"));
	}

	@Test
	void keepsQuotedArgumentsReadable()
	{
		assertEquals("test \"-Dname=a b\"", GoalLabels.shorten("test \"-Dname=a b\""));
	}
}
