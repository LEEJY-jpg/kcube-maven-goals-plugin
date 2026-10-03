# KCube Maven Goals Eclipse Plug-in

Eclipse Plug-in for displaying Maven lifecycle phases and plugin goals in an Ant View-like tree.

## Features
- Detect selected Eclipse Maven project / pom.xml
- Display Maven lifecycle phases
- Display Maven plugins and configured executions
- Double-click a lifecycle phase or plugin goal to run Maven
- Run Maven through the local `mvn` executable
- Output Maven stdout/stderr to an Eclipse Console
- Refresh from the current selection
- Eclipse 4.x / Java 17+ target (built with `--release 17`)

## Import
1. Eclipse: File > Import > Existing Projects into Workspace
2. Select this directory.
3. Run `com.kcube.mavenview` as an Eclipse Application.

For production deployment, export/install the plug-in with Eclipse Product Export or PDE Feature/Update Site packaging.
