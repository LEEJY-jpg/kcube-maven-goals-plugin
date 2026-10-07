package com.kcube.mavenview.services;

import java.io.File;
import java.util.List;

import javax.xml.parsers.DocumentBuilderFactory;

import org.eclipse.core.runtime.IStatus;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import com.kcube.mavenview.model.MavenGoal;

/**
 * pom.xml 파일 하나를 읽어 {@link MavenGoal} 트리(PROJECT → Lifecycle/Plugins → 하위 goal)로 변환한다.
 * <p>
 * 트리의 부모-자식 관계는 각 {@link MavenGoal} 이 직접 들고 있으며, 뷰의 {@code ITreeContentProvider}는 {@link #children(MavenGoal)}으로 조회한다.
 */
public final class MavenPomParser
{
	/** 유틸리티 클래스이므로 인스턴스를 만들 수 없다. */
	private MavenPomParser()
	{
	}

	/** Maven 표준 lifecycle의 phase를 실행 순서대로 나열한 목록. "Lifecycle" 노드의 자식으로 그대로 사용된다. */
	private static final String[] LIFECYCLE_PHASES = {
		"clean",
		"validate",
		"initialize",
		"generate-sources",
		"process-sources",
		"generate-resources",
		"process-resources",
		"compile",
		"process-classes",
		"generate-test-sources",
		"process-test-sources",
		"generate-test-resources",
		"process-test-resources",
		"test-compile",
		"test",
		"prepare-package",
		"package",
		"pre-integration-test",
		"integration-test",
		"post-integration-test",
		"verify",
		"install",
		"deploy"};

	/** pom.xml의 {@code <profiles><profile><id>}를 선언 순서대로 반환한다(이 pom에 직접 선언된 것만). 읽을 수 없으면 빈 목록. */
	public static List<String> profileIds(File pom)
	{
		List<String> ids = new java.util.ArrayList<>();
		try
		{
			Element profiles = directChild(newSecureDocumentBuilder().parse(pom).getDocumentElement(), "profiles");
			if (profiles != null)
			{
				for (Element profile : directChildren(profiles, "profile"))
				{
					String id = text(profile, "id");
					if (id != null && !id.isBlank() && !ids.contains(id))
						ids.add(id);
				}
			}
		}
		catch (Exception e)
		{
			PluginLog.log(IStatus.WARNING, "Cannot read profiles from " + pom, e);
		}
		return ids;
	}

	/** pom.xml을 파싱해 Lifecycle/Plugins를 자식으로 가진 PROJECT 루트 노드를 만든다. */
	public static MavenGoal parseProject(File pom) throws Exception
	{
		return parseProject(pom, null, new java.util.HashSet<>());
	}

	/**
	 * pom.xml을 파싱한다. {@code <modules>}가 있으면 하위 모듈 pom도 재귀적으로 파싱해 "Modules" 노드 아래에 PROJECT로 매단다.
	 *
	 * @param parent 상위 프로젝트 노드(최상위면 null)
	 * @param visited 이미 파싱한 pom의 정규 경로. 순환 참조로 무한 재귀하는 것을 막는다.
	 */
	private static MavenGoal parseProject(File pom, MavenGoal parent, java.util.Set<String> visited) throws Exception
		{
		visited.add(pom.getCanonicalPath());
		Document d = newSecureDocumentBuilder().parse(pom);
		Element project = d.getDocumentElement();

		PomProperties props = PomProperties.of(project, pom);
		String name = props.resolve(text(project, "name"));
		if (name == null || name.isBlank())
			name = props.resolve(text(project, "artifactId"));
		if (name == null || name.isBlank())
			name = pom.getParentFile().getName();

		MavenGoal projectRoot = parent == null ? new MavenGoal(name, pom) : new MavenGoal(name, pom, parent);

		MavenGoal lifecycle = new MavenGoal("Lifecycle", null, MavenGoal.Type.LIFECYCLE, projectRoot);
		addChild(projectRoot, lifecycle);
		for (String phase : LIFECYCLE_PHASES)
		{
			addChild(lifecycle, new MavenGoal(phase, phase, MavenGoal.Type.GOAL, lifecycle));
		}

		MavenGoal plugins = new MavenGoal("Plugins", null, MavenGoal.Type.PLUGIN, projectRoot);
		addChild(projectRoot, plugins);
		parsePlugins(plugins, declaredPlugins(project), props);

		parseModules(projectRoot, pom, project, visited, props);

		return projectRoot;
	}

	/** {@code <modules><module>}에 선언된 하위 모듈 pom을 읽어 "Modules" 노드 아래에 추가한다. 읽을 수 없는 모듈은 건너뛴다. */
	private static void parseModules(
		MavenGoal projectRoot,
		File pom,
		Element project,
		java.util.Set<String> visited,
		PomProperties props)
	{
		Element modules = directChild(project, "modules");
		if (modules == null)
			return;
		MavenGoal folder = new MavenGoal("Modules", null, MavenGoal.Type.MODULES, projectRoot);
		for (Element m : directChildren(modules, "module"))
		{
			String path = m.getTextContent() == null ? "" : props.resolve(m.getTextContent().trim());
			if (path.isEmpty())
				continue;
			File modulePom = new File(pom.getParentFile(), path);
			if (modulePom.isDirectory())
				modulePom = new File(modulePom, "pom.xml");
			try
			{
				if (modulePom.isFile() && !visited.contains(modulePom.getCanonicalPath()))
					addChild(folder, parseProject(modulePom, folder, visited));
			}
			catch (Exception e)
			{
				// 깨진 모듈 pom 하나 때문에 전체 트리를 포기하지 않는다.
				PluginLog.log(IStatus.WARNING, "Skipping unparsable module pom " + modulePom, e);
			}
		}
		if (!children(folder).isEmpty())
			addChild(projectRoot, folder);
	}

	/** parent의 직접 자식 중 이름이 tag인 첫 엘리먼트를 반환한다. 없으면 null. */
	static Element directChild(Element parent, String tag)
	{
		List<Element> found = directChildren(parent, tag);
		return found.isEmpty() ? null : found.get(0);
	}

	/** parent의 직접 자식 중 이름이 tag인 엘리먼트들을 반환한다. */
	static List<Element> directChildren(Element parent, String tag)
	{
		List<Element> result = new java.util.ArrayList<>();
		for (org.w3c.dom.Node n = parent.getFirstChild(); n != null; n = n.getNextSibling())
		{
			if (n instanceof Element e && tag.equals(e.getTagName()))
				result.add(e);
		}
		return result;
	}

	/**
	 * 실제로 빌드에 바인딩되는 플러그인 선언만 모은다: {@code <build><plugins>}와 프로파일의 {@code <build><plugins>}.
	 * {@code <pluginManagement>}(버전/설정만 정하고 실행은 하지 않음)와 {@code <reporting>}은 제외한다. 이전에는 pom 전체에서
	 * {@code <plugin>}을 찾아 그런 것들까지 실행 가능한 것처럼 보였다.
	 */
	private static List<Element> declaredPlugins(Element project)
	{
		List<Element> result = new java.util.ArrayList<>(buildPlugins(project));
		Element profiles = directChild(project, "profiles");
		if (profiles != null)
		{
			for (Element profile : directChildren(profiles, "profile"))
				result.addAll(buildPlugins(profile));
		}
		return result;
	}

	/** parent(project 또는 profile) 바로 아래 {@code <build><plugins><plugin>}들. */
	private static List<Element> buildPlugins(Element parent)
	{
		Element build = directChild(parent, "build");
		Element plugins = build == null ? null : directChild(build, "plugins");
		return plugins == null ? List.of() : directChildren(plugins, "plugin");
	}

	/**
	 * 플러그인 선언들을 읽어 "Plugins" 노드 아래에 플러그인별 하위 트리를 만든다. 같은 플러그인이 기본 빌드와 프로파일에 함께 선언돼 있으면
	 * 노드는 하나로 합치고 execution만 더한다.
	 */
	private static void parsePlugins(MavenGoal plugins, List<Element> declarations, PomProperties props)
	{
		java.util.Map<String, MavenGoal> byId = new java.util.HashMap<>();
		for (Element p : declarations)
		{
			String artifact = props.resolve(text(p, "artifactId"));
			if (artifact == null)
				continue; // artifactId 없는(= 유효하지 않은) plugin 선언은 건너뜀
			String group = props.resolve(text(p, "groupId"));
			if (group == null)
				group = "org.apache.maven.plugins"; // groupId 생략 시 Maven 기본 플러그인으로 간주

			MavenGoal plugin = byId.get(group + ":" + artifact);
			if (plugin == null)
			{
				plugin = new MavenGoal(group + ":" + artifact, null, MavenGoal.Type.PLUGIN, plugins, group, artifact);
				addChild(plugins, plugin);
				byId.put(group + ":" + artifact, plugin);
			}

			// 플러그인에 바인딩된 execution들을 실행 가능한 goal 노드로 펼친다.
			NodeList executions = p.getElementsByTagName("execution");
			for (int j = 0; j < executions.getLength(); j++)
			{
				Element e = (Element) executions.item(j);
				String id = props.resolve(text(e, "id"));
				NodeList goals = e.getElementsByTagName("goal");
			for (int k = 0; k < goals.getLength(); k++)
				{
				String raw = goals.item(k).getTextContent();
				if (raw == null || raw.isBlank())
					continue;
				String g = props.resolve(raw.trim());
					// "plugin:goal@executionId"(Maven 3.3.1+)는 해당 execution 자신의
					// <configuration>으로 실행한다. 그냥 "plugin:goal"로 실행하면 플러그인의 default-cli
					// 실행이 돌아가 execution 블록은 완전히 무시된다.
					String invocation = group + ":" + artifact + ":" + g + (id == null || id.isBlank() ? "" : "@" + id);
					boolean duplicate = false;
					for (MavenGoal existing : plugin.getChildren())
						duplicate |= invocation.equals(existing.getGoal());
					if (!duplicate)
						addChild(
							plugin,
							new MavenGoal(
								pluginPrefix(artifact) + ":" + g + (id == null || id.isBlank() ? "" : "@" + id),
								invocation,
								MavenGoal.Type.EXECUTION,
								plugin));
				}
			}
		}
	}

	/** artifactId에서 CLI용 플러그인 접두사를 구한다. maven-X-plugin, X-maven-plugin은 X, 그 외는 artifactId 그대로. */
	static String pluginPrefix(String artifactId)
	{
		if (artifactId.startsWith("maven-") && artifactId.endsWith("-plugin") && artifactId.length() > 13)
			return artifactId.substring(6, artifactId.length() - 7);
		if (artifactId.endsWith("-maven-plugin") && artifactId.length() > 13)
			return artifactId.substring(0, artifactId.length() - 13);
		return artifactId;
	}

	/** 주어진 엘리먼트 바로 아래의 {@code <tag>} 자식 텍스트를 읽는다. 없으면 null. (손자 이후의 같은 이름 태그는 무시한다.) */
	static String text(Element parent, String tag)
	{
		Element e = directChild(parent, tag);
		if (e == null)
			return null;
		String s = e.getTextContent();
		return s == null ? null : s.trim();
	}

	/** parent의 자식 목록에 child를 추가한다. */
	private static void addChild(MavenGoal parent, MavenGoal child)
	{
		parent.addChild(child);
	}

	/** 뷰의 ITreeContentProvider가 호출하는 조회용 메서드. 자식이 없으면 빈 리스트. */
	public static List<MavenGoal> children(MavenGoal goal)
	{
		return goal.getChildren();
	}

	/**
	 * XXE를 방지한 문서 빌더를 만든다. DOCTYPE 선언 자체를 금지하고, 지원되는 경우 외부 엔티티 로딩을 꺼낸다.
	 * 일부 파서에서 특정 feature가 unsupported이면 무시하고, 나머지 조치만 유지한다.
	 */
	public static javax.xml.parsers.DocumentBuilder newSecureDocumentBuilder() throws Exception
	{
		DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();
		f.setNamespaceAware(false);
		setFeature(f, "http://apache.org/xml/features/disallow-doctype-decl", true);
		setFeature(f, "http://xml.org/sax/features/external-general-entities", false);
		setFeature(f, "http://xml.org/sax/features/external-parameter-entities", false);
		setFeature(f, "http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
		return f.newDocumentBuilder();
	}

	/** feature가 현재 파서에서 지원되면 설정하고, 안 되면 조용히 무시한다. */
	private static void setFeature(DocumentBuilderFactory f, String feature, boolean value)
	{
		try
		{
			f.setFeature(feature, value);
		}
		catch (Exception ignored)
		{
			// unsupported feature는 무시. disallow-doctype-decl이 핵심 차단이다.
		}
	}
}
