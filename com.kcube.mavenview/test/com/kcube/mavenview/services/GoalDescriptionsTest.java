package com.kcube.mavenview.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;

import org.junit.jupiter.api.Test;

import com.kcube.mavenview.Messages;
import com.kcube.mavenview.model.MavenGoal;

class GoalDescriptionsTest
{
	private final MavenGoal root = new MavenGoal("app", new File("pom.xml"));

	@Test
	void describesLifecyclePhase()
	{
		MavenGoal compile = new MavenGoal("compile", "compile", MavenGoal.Type.GOAL, root);
		assertEquals(Messages.get("desc.phase.compile"), GoalDescriptions.describe(compile));
		assertNull(GoalDescriptions.describe(new MavenGoal("odd", "odd", MavenGoal.Type.GOAL, root)), "모르는 phase는 툴팁 없음");
	}

	@Test
	void describesKnownPluginGoalWithInvocation()
	{
		MavenGoal g = new MavenGoal(
			"war:exploded",
			"org.apache.maven.plugins:maven-war-plugin:exploded",
			MavenGoal.Type.EXECUTION,
			root);
		assertEquals(
			Messages.get("desc.goal.war.exploded") + "\nmvn org.apache.maven.plugins:maven-war-plugin:exploded",
			GoalDescriptions.describe(g));
	}

	@Test
	void describesExecutionIdAndUnknownGoal()
	{
		MavenGoal g = new MavenGoal("foo:go@run-it", "org.foo:foo-plugin:go@run-it", MavenGoal.Type.EXECUTION, root);
		String text = GoalDescriptions.describe(g);
		assertTrue(text.startsWith(Messages.get("desc.execution", "run-it")), text);
		assertTrue(text.endsWith("\nmvn org.foo:foo-plugin:go@run-it"), text);
	}

	@Test
	void describesFoldersAndProject()
	{
		assertNotNull(GoalDescriptions.describe(new MavenGoal("Lifecycle", null, MavenGoal.Type.LIFECYCLE, root)));
		assertEquals(new File("pom.xml").getAbsolutePath(), GoalDescriptions.describe(root));
		MavenGoal plugin = new MavenGoal("g:a", null, MavenGoal.Type.PLUGIN, root, "g", "a");
		assertEquals("g:a", GoalDescriptions.describe(plugin));
	}

	@Test
	void everyKnownGoalHasDescription()
	{
		KnownGoals.all().forEach((artifact, goals) -> {
			for (String goal : goals)
				assertNotNull(Messages.find("desc.goal." + MavenPomParser.pluginPrefix(artifact) + "." + goal), artifact + ":" + goal);
		});
	}
}
