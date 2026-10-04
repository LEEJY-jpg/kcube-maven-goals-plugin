package com.kcube.mavenview.services;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import org.junit.jupiter.api.Test;

class RunOptionsTest
{
	@Test
	void noneKeepsGoalsOnly()
	{
		assertEquals("clean install", RunOptions.NONE.toCommandLine("  clean install "));
	}

	@Test
	void appendsAllOptionsInOrder()
	{
		RunOptions o = new RunOptions(true, true, true, "dev, local", "-Dfoo=bar -T 4");
		assertEquals("package -DskipTests -o -U -Pdev,local -Dfoo=bar -T 4", o.toCommandLine("package"));
	}

	@Test
	void blankProfilesAndExtraAreIgnored()
	{
		assertEquals("test", new RunOptions(false, false, false, "  ", " ").toCommandLine("test"));
		assertEquals("test", new RunOptions(false, false, false, null, null).toCommandLine("test"));
	}

	@Test
	void tokenizeSplitsOnWhitespace()
	{
		assertEquals(List.of("clean", "install", "-DskipTests"), RunOptions.tokenize("  clean   install\t-DskipTests "));
	}

	@Test
	void tokenizeHonorsQuotes()
	{
		assertEquals(List.of("-Dname=hello world", "-Pdev"), RunOptions.tokenize("\"-Dname=hello world\" -Pdev"));
		assertEquals(List.of("a b", "c"), RunOptions.tokenize("'a b' c"));
		assertEquals(List.of(""), RunOptions.tokenize("\"\""));
	}

	@Test
	void tokenizeHandlesEmptyAndNull()
	{
		assertEquals(List.of(), RunOptions.tokenize(""));
		assertEquals(List.of(), RunOptions.tokenize(null));
	}

	@Test
	void executionGoalIsSingleToken()
	{
		assertEquals(List.of("org.foo:foo-plugin:go@run-it"), RunOptions.tokenize("org.foo:foo-plugin:go@run-it"));
	}
}
