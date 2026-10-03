package com.kcube.mavenview.model;

import java.io.File;

/**
 * 하나의 트리 노드를 나타내는 불변(immutable) 모델.
 * <p>
 * Maven Goals 뷰의 트리는 등록된 pom.xml마다 하나의 PROJECT 루트 노드를 갖고, 그 아래로 Lifecycle phase, Plugin, Plugin의 execution-goal이 자식으로
 * 매달리는 구조다. 각 노드는 {@link #parent}를 통해 자신의 조상을 거슬러 올라갈 수 있고, 실제 자식 목록은 이 클래스가 아니라 {@code MavenPomParser}가 별도의 맵으로 관리한다
 */
public final class MavenGoal
{
	/**
	 * 트리 노드의 종류. PROJECT=등록된 pom.xml 하나, LIFECYCLE="Lifecycle" 폴더, PLUGIN="Plugins" 폴더 또는 개별 플러그인, GOAL=lifecycle
	 * phase, EXECUTION=plugin execution의 goal.
	 */
	public enum Type
	{
		PROJECT, LIFECYCLE, PLUGIN, GOAL, EXECUTION
	}

	/** 트리에 표시되는 이름(라벨). */
	private final String name;
	/** 더블클릭 시 Maven에 전달할 실행 문자열(예: "compile", "group:artifact:goal@executionId"). 실행 불가능한 폴더 노드는 null. */
	private final String goal;
	private final Type type;
	/** 부모 노드. PROJECT 루트는 null. */
	private final MavenGoal parent;
	/** 플러그인 노드의 경우 [groupId, artifactId]처럼 라벨 표시용 부가 정보를 담는다. */
	private final String[] arguments;
	/** 이 노드가 PROJECT 루트일 때만 채워지는, 자신이 파싱된 pom.xml 파일. */
	private final File pomFile;

	/** 일반 트리 노드(Lifecycle/Plugin/Goal/Execution)를 생성한다. */
	public MavenGoal(String name, String goal, Type type, MavenGoal parent, String... arguments)
	{
		this(name, goal, type, parent, null, arguments);
	}

	/** PROJECT 루트 노드를 생성한다. 파싱된 pom.xml 파일 자체를 함께 보관한다. */
	public MavenGoal(String name, File pomFile)
	{
		this(name, null, Type.PROJECT, null, pomFile);
	}

	/** 모든 public 생성자가 공통으로 사용하는 내부 생성자. 필드를 초기화하고 arguments는 방어적으로 복사해 보관한다. */
	private MavenGoal(String name, String goal, Type type, MavenGoal parent, File pomFile, String... arguments)
	{
		this.name = name;
		this.goal = goal;
		this.type = type;
		this.parent = parent;
		this.pomFile = pomFile;
		this.arguments = arguments == null ? new String[0] : arguments.clone();
	}

	/** 트리에 표시되는 이름(라벨)을 반환한다. */
	public String getName()
	{
		return name;
	}

	/** 실행할 Maven goal 문자열을 반환한다. 실행할 수 없는 폴더 노드는 null이다. */
	public String getGoal()
	{
		return goal;
	}

	/** 노드의 종류를 반환한다. */
	public Type getType()
	{
		return type;
	}

	/** 부모 노드를 반환한다. PROJECT 루트는 null이다. */
	public MavenGoal getParent()
	{
		return parent;
	}

	/** 라벨 표시용 부가 정보([groupId, artifactId] 등)의 복사본을 반환한다. */
	public String[] getArguments()
	{
		return arguments.clone();
	}

	/** 이 노드가 PROJECT 루트일 때 파싱된 pom.xml 파일을 반환한다. 그 외 노드는 null이다. */
	public File getPomFile()
	{
		return pomFile;
	}

	/** 부모 체인을 거슬러 올라가며 가장 가까운 PROJECT 루트의 pom.xml을 찾아 반환한다. */
	public File resolvePomFile()
	{
		for (MavenGoal n = this; n != null; n = n.parent)
		{
			if (n.pomFile != null)
				return n.pomFile;
		}
		return null;
	}

	/** 트리 라벨로 쓰이도록 노드 이름을 반환한다. */
	@Override
	public String toString()
	{
		return name;
	}
}
