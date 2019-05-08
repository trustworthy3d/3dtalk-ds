package org.area515.resinprinter.job;

public enum JobStatus {
	// FIXME: 2017/11/1 zyd add for connecting status -s
	Connecting,
	// FIXME: 2017/11/1 zyd add for connecting status -e
	Ready,
	// FIXME: 2017/10/31 zyd add for error status -s
	ErrorScreen,
	ErrorControlBoard,
	// FIXME: 2017/10/31 zyd add for error status -e
	Printing,
	Failed,
	Completed,
	Cancelled,
	Cancelling,
	Deleted,
	Paused,
	// FIXME: 2017/9/25 zyd add for the reason of paused -s
	PausedUnconformableMaterial,
	PausedDoorOpened,
	PausedLedOverTemperature,
	PausedGrooveOutOfMaterial,
	PausedBottleOutOfMaterial,
	// FIXME: 2017/9/25 zyd add for the reason of paused -e
	PausedOutOfPrintMaterial,
	PausedWithWarning;

	public boolean isPrintInProgress() {
		return this == JobStatus.Paused ||
				this == JobStatus.Printing ||
				// FIXME: 2017/9/25 zyd add for the reason of paused -s
				this == JobStatus.PausedUnconformableMaterial ||
				this == JobStatus.PausedDoorOpened ||
				this == JobStatus.PausedLedOverTemperature ||
				this == JobStatus.PausedGrooveOutOfMaterial ||
				this == JobStatus.PausedBottleOutOfMaterial ||
				// FIXME: 2017/9/25 zyd add for the reason of paused -e
				this == JobStatus.PausedOutOfPrintMaterial ||
				this == JobStatus.PausedWithWarning ||
				this == JobStatus.Cancelling;
	}
	
	public boolean isPrintActive() {
		return this == JobStatus.Paused ||
				this == JobStatus.Printing ||
				// FIXME: 2017/9/25 zyd add for the reason of paused -s
				this == JobStatus.PausedUnconformableMaterial ||
				this == JobStatus.PausedDoorOpened ||
				this == JobStatus.PausedLedOverTemperature ||
				this == JobStatus.PausedGrooveOutOfMaterial ||
				this == JobStatus.PausedBottleOutOfMaterial ||
				// FIXME: 2017/9/25 zyd add for the reason of paused -e
				this == JobStatus.PausedOutOfPrintMaterial ||
				this == JobStatus.PausedWithWarning;
	}
	
	public boolean isPaused() {
		return this == JobStatus.Paused ||
				// FIXME: 2017/9/25 zyd add for the reason of paused -s
				this == JobStatus.PausedUnconformableMaterial ||
				this == JobStatus.PausedDoorOpened ||
				this == JobStatus.PausedLedOverTemperature ||
				this == JobStatus.PausedGrooveOutOfMaterial ||
				this == JobStatus.PausedBottleOutOfMaterial ||
				// FIXME: 2017/9/25 zyd add for the reason of paused -e
				this == JobStatus.PausedOutOfPrintMaterial ||
				this == JobStatus.PausedWithWarning;
	}

	// FIXME: 2017/10/31 zyd add for error status -s
	public boolean isNotReady() {
		return this == JobStatus.ErrorScreen ||
				this == JobStatus.ErrorControlBoard ||
				this == JobStatus.Connecting;
	}

	public boolean isError() {
		return this == JobStatus.ErrorScreen ||
				this == JobStatus.ErrorControlBoard;
	}

	public String getStateString() {
		String string = "";
		if (this == JobStatus.Ready)
			string = "Ready";
		else if (this == JobStatus.ErrorScreen)
			string = "Error(Screen)";
		else if (this == JobStatus.ErrorControlBoard)
			string = "Error(Control board)";
		else if (this == JobStatus.Connecting)
			string = "Connecting";
		else if (this == JobStatus.Printing)
			string = "Printing";
		else if (this == JobStatus.Failed)
			string = "Failed";
		else if (this == JobStatus.Completed)
			string = "Completed";
		else if (this == JobStatus.Cancelled)
			string = "Cancelled";
		else if (this == JobStatus.Cancelling)
			string = "Cancelling";
		else if (this == JobStatus.Deleted)
			string = "Deleted";
		else if (this == JobStatus.Paused)
			string = "Paused";
		else if (this == JobStatus.PausedUnconformableMaterial)
			string = "Paused";
		else if (this == JobStatus.PausedDoorOpened)
			string = "Paused(Door opened)";
		else if (this == JobStatus.PausedLedOverTemperature)
			string = "Paused(Led over temp.)";
		else if (this == JobStatus.PausedGrooveOutOfMaterial)
			string = "Paused";
		else if (this == JobStatus.PausedBottleOutOfMaterial)
			string = "Paused";
		return string;
	}

	public String getStateStringCN() {
		String string = "";
		if (this == JobStatus.Ready)
			string = new String(new char[] {0x5C31, 0x7EEA});//就绪
		else if (this == JobStatus.ErrorScreen)
			string = new String(new char[] {0x5C4F, 0x5E55, 0x9519, 0x8BEF});//屏幕错误
		else if (this == JobStatus.ErrorControlBoard)
			string = new String(new char[] {0x63A7, 0x5236, 0x7248, 0x9519, 0x8BEF});//控制板错误
		else if (this == JobStatus.Connecting)
			string = new String(new char[] {0x6B63, 0x5728, 0x8FDE, 0x63A5});//正在连接
		else if (this == JobStatus.Printing)
			string = new String(new char[] {0x6253, 0x5370, 0x4E2D});//打印中
		else if (this == JobStatus.Failed)
			string = new String(new char[] {0x5931, 0x8D25});//失败
		else if (this == JobStatus.Completed)
			string = new String(new char[] {0x5B8C, 0x6210});//完成
		else if (this == JobStatus.Cancelled)
			string = new String(new char[] {0x5DF2, 0x53D6, 0x6D88});//已取消
		else if (this == JobStatus.Cancelling)
			string = new String(new char[] {0x6B63, 0x5728, 0x53D6, 0x6D88});//正在取消
		else if (this == JobStatus.Deleted)
			string = new String(new char[] {0x5DF2, 0x5220, 0x9664});//已删除
		else if (this == JobStatus.Paused)
			string = new String(new char[] {0x5DF2, 0x6682, 0x505C});//已暂停
		else if (this == JobStatus.PausedUnconformableMaterial)
			string = new String(new char[] {0x6811, 0x8102, 0x7C7B, 0x578B, 0x4E0D, 0x7B26});//树脂类型不符
		else if (this == JobStatus.PausedDoorOpened)
			string = new String(new char[] {0x8231, 0x95E8, 0x6253, 0x5F00});//舱门打开
		else if (this == JobStatus.PausedLedOverTemperature)
			string = new String(new char[] {0x706F, 0x677F, 0x6E29, 0x5EA6, 0x8FC7, 0x9AD8});//灯板温度过高
		else if (this == JobStatus.PausedGrooveOutOfMaterial)
			string = new String(new char[] {0x6599, 0x69FD, 0x7F3A, 0x6599});//料槽缺料
		else if (this == JobStatus.PausedBottleOutOfMaterial)
			string = new String(new char[] {0x6599, 0x74F6, 0x7F3A, 0x6599});//料瓶缺料
		return string;
	}
	// FIXME: 2017/10/31 zyd add for error status -e
}
