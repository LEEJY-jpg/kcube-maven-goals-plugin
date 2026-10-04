package com.kcube.mavenview.views;

import org.eclipse.jface.dialogs.Dialog;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Text;

import com.kcube.mavenview.services.RunOptions;

/** goal과 실행 옵션(-DskipTests, 프로파일, 추가 인자 등)을 고른 뒤 실행하는 "Run with Options" 대화상자. */
final class RunOptionsDialog extends Dialog
{
	private final String projectName;
	private final String initialGoals;
	private final RunOptions initialOptions;

	private Text goalsText;
	private Button skipTests;
	private Button offline;
	private Button updateSnapshots;
	private Text profilesText;
	private Text extraText;

	private String goals = "";
	private RunOptions options = RunOptions.NONE;

	/**
	 * @param projectName 대화상자 제목에 표시할 프로젝트 이름
	 * @param initialGoals Goals 입력란의 초기값
	 * @param initialOptions 직전에 사용한 옵션(체크박스/입력란 초기값)
	 */
	RunOptionsDialog(Shell parent, String projectName, String initialGoals, RunOptions initialOptions)
	{
		super(parent);
		this.projectName = projectName;
		this.initialGoals = initialGoals;
		this.initialOptions = initialOptions;
	}

	/** 대화상자 제목을 설정한다. */
	@Override
	protected void configureShell(Shell shell)
	{
		super.configureShell(shell);
		shell.setText("Run with Options - " + projectName);
	}

	/** 입력 컨트롤들을 만든다. */
	@Override
	protected Control createDialogArea(Composite parent)
	{
		Composite area = (Composite) super.createDialogArea(parent);
		Composite c = new Composite(area, SWT.NONE);
		c.setLayout(new GridLayout(2, false));
		c.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));

		goalsText = labeledText(c, "Goals:", initialGoals);
		profilesText = labeledText(c, "Profiles (-P):", initialOptions.profiles());
		profilesText.setToolTipText("Comma separated, e.g. dev,local");
		extraText = labeledText(c, "Additional args:", initialOptions.extraArgs());
		extraText.setToolTipText("e.g. -Dfoo=bar -T 4");

		skipTests = check(c, "Skip tests (-DskipTests)", initialOptions.skipTests());
		offline = check(c, "Offline (-o)", initialOptions.offline());
		updateSnapshots = check(c, "Update snapshots (-U)", initialOptions.updateSnapshots());
		return area;
	}

	/** 라벨과 한 줄 입력란 한 쌍을 만든다. */
	private static Text labeledText(Composite parent, String label, String value)
	{
		new Label(parent, SWT.NONE).setText(label);
		Text t = new Text(parent, SWT.BORDER);
		GridData gd = new GridData(SWT.FILL, SWT.CENTER, true, false);
		gd.widthHint = 360;
		t.setLayoutData(gd);
		t.setText(value == null ? "" : value);
		return t;
	}

	/** 두 칸을 차지하는 체크박스를 만든다. */
	private static Button check(Composite parent, String label, boolean selected)
	{
		Button b = new Button(parent, SWT.CHECK);
		b.setText(label);
		b.setSelection(selected);
		b.setLayoutData(new GridData(SWT.LEFT, SWT.CENTER, false, false, 2, 1));
		return b;
	}

	/** 확인 시 입력값을 읽어 둔다(대화상자가 닫힌 뒤에는 컨트롤에 접근할 수 없으므로). */
	@Override
	protected void okPressed()
	{
		goals = goalsText.getText().trim();
		options = new RunOptions(
			skipTests.getSelection(),
			offline.getSelection(),
			updateSnapshots.getSelection(),
			profilesText.getText().trim(),
			extraText.getText().trim());
		super.okPressed();
	}

	/** 사용자가 입력한 옵션을 반환한다. */
	RunOptions getOptions()
	{
		return options;
	}

	/** 옵션까지 합쳐진 최종 명령행(goals 뒤에 옵션)을 반환한다. goals가 비어 있으면 빈 문자열. */
	String getCommandLine()
	{
		return goals.isEmpty() ? "" : options.toCommandLine(goals);
	}
}
