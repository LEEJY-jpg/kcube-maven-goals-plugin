package com.kcube.mavenview.services;

import java.io.File;
import java.util.List;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.*;

import com.kcube.mavenview.model.MavenGoal;

/**
 * pom.xml 파일 하나를 읽어 {@link MavenGoal} 트리(PROJECT → Lifecycle/Plugins → 하위 goal)로 변환한다.
 * <p>트리의 부모-자식 관계는 {@link MavenGoal} 자체가 아니라 이 클래스가 들고 있는
 * {@link #CHILDREN} 맵에 별도로 저장된다. 뷰의 {@code ITreeContentProvider}는
 * {@link #children(MavenGoal)}을 통해 이 맵을 조회한다.
 */
public final class MavenPomParser {
    private MavenPomParser() {}

    /** Maven 표준 lifecycle의 phase를 실행 순서대로 나열한 목록. "Lifecycle" 노드의 자식으로 그대로 사용된다. */
    private static final String[] LIFECYCLE_PHASES = {"clean","validate","initialize","generate-sources",
        "process-sources","generate-resources","process-resources","compile","process-classes",
        "generate-test-sources","process-test-sources","generate-test-resources",
        "process-test-resources","test-compile","test","prepare-package","package",
        "pre-integration-test","integration-test","post-integration-test","verify",
        "install","deploy"};

    /** Parses a pom.xml into a PROJECT root node with Lifecycle/Plugins children. */
    public static MavenGoal parseProject(File pom) throws Exception {
        DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();
        f.setNamespaceAware(false);
        try {
            f.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        } catch (Exception ignored) {}
        Document d = f.newDocumentBuilder().parse(pom);
        Element project = d.getDocumentElement();

        String name = text(project, "name");
        if (name == null || name.isBlank()) name = text(project, "artifactId");
        if (name == null || name.isBlank()) name = pom.getParentFile().getName();

        MavenGoal projectRoot = new MavenGoal(name, pom);

        MavenGoal lifecycle = new MavenGoal("Lifecycle", null, MavenGoal.Type.LIFECYCLE, projectRoot);
        addChild(projectRoot, lifecycle);
        for (String phase : LIFECYCLE_PHASES) {
            addChild(lifecycle, new MavenGoal(phase, phase, MavenGoal.Type.GOAL, lifecycle));
        }

        MavenGoal plugins = new MavenGoal("Plugins", null, MavenGoal.Type.PLUGIN, projectRoot);
        addChild(projectRoot, plugins);
        parsePlugins(plugins, project.getElementsByTagName("plugin"));

        return projectRoot;
    }

    /** pom.xml의 모든 {@code <plugin>} 선언을 읽어 "Plugins" 노드 아래에 플러그인별 하위 트리를 만든다. */
    private static void parsePlugins(MavenGoal plugins, NodeList nodes) {
        for (int i = 0; i < nodes.getLength(); i++) {
            Element p = (Element) nodes.item(i);
            String artifact = text(p, "artifactId");
            if (artifact == null) continue; // artifactId 없는(= 유효하지 않은) plugin 선언은 건너뜀
            String group = text(p, "groupId");
            if (group == null) group = "org.apache.maven.plugins"; // groupId 생략 시 Maven 기본 플러그인으로 간주

            MavenGoal plugin = new MavenGoal(group + ":" + artifact, null,
                MavenGoal.Type.PLUGIN, plugins, group, artifact);
            addChild(plugins, plugin);

            // 플러그인에 바인딩된 execution들을 실행 가능한 goal 노드로 펼친다.
            NodeList executions = p.getElementsByTagName("execution");
            for (int j = 0; j < executions.getLength(); j++) {
                Element e = (Element) executions.item(j);
                String id = text(e, "id");
                NodeList goals = e.getElementsByTagName("goal");
                for (int k = 0; k < goals.getLength(); k++) {
                    String g = goals.item(k).getTextContent().trim();
                    // "plugin:goal@executionId" (Maven 3.3.1+) runs THIS execution's own
                    // <configuration> — plain "plugin:goal" would run the plugin's default-cli
                    // invocation instead, ignoring the execution block entirely.
                    String invocation = group + ":" + artifact + ":" + g
                        + (id == null || id.isBlank() ? "" : "@" + id);
                    addChild(plugin, new MavenGoal(
                        (id == null ? "" : id + ":") + g,
                        invocation,
                        MavenGoal.Type.EXECUTION, plugin));
                }
            }
        }
    }

    /** 주어진 엘리먼트 바로 아래의 {@code <tag>} 자식 텍스트를 읽는다. 없으면 null. */
    private static String text(Element parent, String tag) {
        NodeList n = parent.getElementsByTagName(tag);
        if (n.getLength() == 0) return null;
        String s = n.item(0).getTextContent();
        return s == null ? null : s.trim();
    }

    private static void addChild(MavenGoal parent, MavenGoal child) {
        CHILDREN.computeIfAbsent(parent, k -> new java.util.ArrayList<>()).add(child);
    }

    /** 노드(identity 기준) → 자식 목록. 트리 구조 자체는 MavenGoal이 아니라 여기서 관리한다. */
    private static final java.util.Map<MavenGoal, java.util.List<MavenGoal>> CHILDREN =
        new java.util.IdentityHashMap<>();

    /** 뷰의 ITreeContentProvider가 호출하는 조회용 메서드. 자식이 없으면 빈 리스트. */
    public static List<MavenGoal> children(MavenGoal goal) {
        return CHILDREN.getOrDefault(goal, java.util.List.of());
    }

    /** Recursively drops a parsed tree (e.g. a replaced or removed project root) from the children map. */
    public static void dispose(MavenGoal node) {
        List<MavenGoal> kids = CHILDREN.remove(node);
        if (kids != null) {
            for (MavenGoal k : kids) dispose(k);
        }
    }
}
