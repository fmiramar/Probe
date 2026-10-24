/*
    ProbeDashboard.sc
    Probe — Unified Multi-Panel Audio Analysis Dashboard
    Composes Meter, Oscilloscope, Spectrum, Loudness, and Vectorscope into a single
    synchronized interface sharing measurement sources via ProbeBroker,
    with master freeze, global theme propagation, dynamic responsive grid resizing, and broker telemetry.
*/

ProbeDashboardView {
	var <server;
	var <view;
	var <meterView, <scopeView, <spectrumView, <loudnessView, <vectorscopeView;
	var <theme;
	var <targetLUFS = -23.0;
	var <isFrozen = false;
	var <isClosed = false;

	var txtTitle, lblTheme, lblTarget, lblFont;
	var themeMenu, freezeBtn, targetMenu, fontMenu, txtBrokerStatus;

	*new { |server, parent, bounds, numIns=2, numOuts=2, busIndex=0, theme=\graphite, targetLUFS=(-23.0)|
		^super.new.prInit(server, parent, bounds, numIns, numOuts, busIndex, theme, targetLUFS);
	}

	prInit { |argServer, parent, bounds, numIns, numOuts, busIndex, argTheme, argTargetLUFS|
		var rect = bounds ?? { Rect(0, 0, 960, 560) };

		server     = argServer ?? { Server.default };
		theme      = ProbeTheme.get(argTheme);
		targetLUFS = argTargetLUFS.asFloat;

		view = CompositeView(parent, rect);
		view.resize = 5;
		view.background = theme.background;
		view.onClose = { this.stop };
		view.onResize = { |v|
			var b = if (v.notNil and: { v.isClosed.not }) { v.bounds };
			if (b.notNil and: { isClosed.not }) { this.prHandleResize(b.width, b.height) };
		};

		this.prBuildUI(rect.width, rect.height, numIns, numOuts, busIndex);
		this.prStartTelemetry;
	}

	prBuildUI { |w, h, numIns, numOuts, busIndex|
		var themes = [\graphite, \phosphor, \ice, \amber, \paper, \monochrome];
		var fs = theme.fontScale ? 1.25;
		var btnH = (22 * fs).max(22).min(28).round.asInteger;
		var topH = btnH + 8;
		var panelH = ((h - topH - 20) / 2).floor;
		var meterW = (w * 0.17).clip((130 * fs).max(120), (260 * fs).max(240));
		var midW = (((w - meterW - 32) / 2).floor).max(80);
		var rightW = (w - meterW - midW - 32).max(80);

		// 1. Top Control Bar (bounds will be set in prHandleResize)
		txtTitle = StaticText(view, Rect(0, 0, 10, 10))
			.string_("PROBE DASHBOARD")
			.font_(theme.font(10, bold: true))
			.stringColor_(theme.text);

		// Global Theme Dropdown
		lblTheme = StaticText(view, Rect(0, 0, 10, 10))
			.string_("Theme:")
			.font_(theme.font(9, bold: true))
			.stringColor_(theme.subtext);

		themeMenu = PopUpMenu(view, Rect(0, 0, 10, 10))
			.items_(["Graphite", "Phosphor", "Ice", "Amber", "Paper", "Monochrome"])
			.value_(themes.indexOf(theme.name) ? 0)
			.font_(theme.font(9))
			.action_({ |m|
				this.theme = themes[m.value];
			});

		// Target LUFS Selector
		lblTarget = StaticText(view, Rect(0, 0, 10, 10))
			.string_("Target:")
			.font_(theme.font(9, bold: true))
			.stringColor_(theme.subtext);

		targetMenu = PopUpMenu(view, Rect(0, 0, 10, 10))
			.items_(["-23 LUFS (EBU)", "-14 LUFS (Web)"])
			.value_(if (targetLUFS == -14.0) { 1 } { 0 })
			.font_(theme.font(9))
			.action_({ |m|
				var t = if (m.value == 0) { -23.0 } { -14.0 };
				this.targetLUFS = t;
			});

		// Global Font Size Selector
		lblFont = StaticText(view, Rect(0, 0, 10, 10))
			.string_("Font:")
			.font_(theme.font(9, bold: true))
			.stringColor_(theme.subtext);

		fontMenu = PopUpMenu(view, Rect(0, 0, 10, 10))
			.items_(["100%", "115%", "125%", "140%", "150%"])
			.value_(this.prFontScaleToIndex(theme.fontScale))
			.font_(theme.font(9))
			.action_({ |m|
				var scales = [1.0, 1.15, 1.25, 1.4, 1.5];
				this.fontScale = scales[m.value];
			});

		// Master Freeze Button
		freezeBtn = Button(view, Rect(0, 0, 10, 10))
			.states_([["Freeze All", theme.text, theme.panelBackground], ["Unfreeze All", theme.background, theme.signal]])
			.font_(theme.font(9, bold: true))
			.action_({ this.toggleFreeze });

		// Broker Telemetry Label
		txtBrokerStatus = StaticText(view, Rect(0, 0, 10, 10))
			.string_("Broker: 0 sources | 0 subs")
			.font_(theme.font(8, mono: true))
			.stringColor_(theme.subtext);

		// 2. Embedded Panels Grid
		// Panel 1: Multi-Channel Meter (Left)
		meterView = ProbeMeterView(server, view, Rect(10, topH + 8, meterW, h - topH - 16), numIns, numOuts, theme);

		// Panel 2: Scope (Mid-Top)
		scopeView = ProbeScopeView(server, view, Rect(meterW + 18, topH + 8, midW, panelH), busIndex, 2, theme);

		// Panel 3: Spectrum (Mid-Bottom)
		spectrumView = ProbeSpectrumView(server, view, Rect(meterW + 18, topH + 16 + panelH, midW, panelH), busIndex, 2048, theme);

		// Panel 4: Loudness Meter (Right-Top)
		loudnessView = ProbeLoudnessView(server, view, Rect(meterW + midW + 26, topH + 8, rightW, panelH), \outputs, busIndex, 2, theme, targetLUFS);

		// Panel 5: Vectorscope (Right-Bottom)
		vectorscopeView = ProbeVectorscopeView(server, view, Rect(meterW + midW + 26, topH + 16 + panelH, rightW, panelH), busIndex, theme);

		// Master Keyboard Controls
		view.keyDownAction = { |v, char|
			case
				{ char === $  } { this.toggleFreeze }
				{ char === $r } { loudnessView.reset };
		};

		this.prHandleResize(w, h);
	}

	prHandleResize { |w, h|
		var fs = theme.fontScale ? 1.25;
		var btnH = (22 * fs).max(22).min(28).round.asInteger;
		var topH = btnH + 8;
		var curY = ((topH - btnH) * 0.5).round.asInteger;
		var curX = 10;

		var titleW = (145 * fs).clip(145, 195).round.asInteger;
		var lblThemeW = (32 * fs).clip(32, 42).round.asInteger;
		var themeW = (76 * fs).clip(76, 96).round.asInteger;
		var lblTargetW = (34 * fs).clip(34, 44).round.asInteger;
		var targetW = (94 * fs).clip(94, 118).round.asInteger;
		var lblFontW = (24 * fs).clip(24, 32).round.asInteger;
		var fontW = (48 * fs).clip(48, 62).round.asInteger;
		var freezeW = (72 * fs).clip(72, 90).round.asInteger;

		var panelH = ((h - topH - 20) / 2).floor.max(60);
		var meterW = (w * 0.17).clip((130 * fs).max(120), (260 * fs).max(240));
		var midW = (((w - meterW - 32) / 2).floor).max(80);
		var rightW = (w - meterW - midW - 32).max(80);

		if (isClosed.not and: { view.notNil } and: { view.isClosed.not }) {
			view.bounds = Rect(view.bounds.left, view.bounds.top, w, h);

			if (txtTitle.notNil) {
				txtTitle.bounds = Rect(curX, curY, titleW, btnH);
				curX = curX + titleW + 5;
			};
			if (lblTheme.notNil) {
				lblTheme.bounds = Rect(curX, curY, lblThemeW, btnH);
				curX = curX + lblThemeW + 2;
			};
			if (themeMenu.notNil) {
				themeMenu.bounds = Rect(curX, curY, themeW, btnH);
				curX = curX + themeW + 6;
			};
			if (lblTarget.notNil) {
				lblTarget.bounds = Rect(curX, curY, lblTargetW, btnH);
				curX = curX + lblTargetW + 2;
			};
			if (targetMenu.notNil) {
				targetMenu.bounds = Rect(curX, curY, targetW, btnH);
				curX = curX + targetW + 6;
			};
			if (lblFont.notNil) {
				lblFont.bounds = Rect(curX, curY, lblFontW, btnH);
				curX = curX + lblFontW + 2;
			};
			if (fontMenu.notNil) {
				fontMenu.bounds = Rect(curX, curY, fontW, btnH);
				curX = curX + fontW + 6;
			};
			if (freezeBtn.notNil) {
				freezeBtn.bounds = Rect(curX, curY, freezeW, btnH);
				curX = curX + freezeW + 8;
			};
			if (txtBrokerStatus.notNil) {
				txtBrokerStatus.bounds = Rect(curX, curY, (w - curX - 10).max(60), btnH);
			};

			if (meterView.notNil and: { meterView.view.notNil }) {
				meterView.view.bounds = Rect(8, topH + 6, meterW, h - topH - 14);
				meterView.prHandleResize(meterW, h - topH - 14);
			};
			if (scopeView.notNil and: { scopeView.view.notNil }) {
				scopeView.view.bounds = Rect(meterW + 14, topH + 6, midW, panelH);
				scopeView.prHandleResize(midW, panelH);
			};
			if (spectrumView.notNil and: { spectrumView.view.notNil }) {
				spectrumView.view.bounds = Rect(meterW + 14, topH + 12 + panelH, midW, panelH);
				spectrumView.prHandleResize(midW, panelH);
			};
			if (loudnessView.notNil and: { loudnessView.view.notNil }) {
				loudnessView.view.bounds = Rect(meterW + midW + 20, topH + 6, rightW, panelH);
				loudnessView.prHandleResize(rightW, panelH);
			};
			if (vectorscopeView.notNil and: { vectorscopeView.view.notNil }) {
				vectorscopeView.view.bounds = Rect(meterW + midW + 20, topH + 12 + panelH, rightW, panelH);
				vectorscopeView.prHandleResize(rightW, panelH);
			};
		};
	}

	prStartTelemetry {
		AppClock.sched(0.5, {
			if (isClosed.not and: { txtBrokerStatus.notNil }) {
				var b = Probe.broker;
				txtBrokerStatus.string = "Broker: " ++ b.sourceCount ++ " src | " ++ b.totalSubscriberCount ++ " sub";
				0.5;
			} {
				nil;
			};
		});
	}

	prFontScaleToIndex { |scale|
		var scales = [1.0, 1.15, 1.25, 1.4, 1.5];
		var idx = scales.detectIndex { |s| (s - scale).abs < 0.05 };
		^idx ? 0;
	}

	fontScale {
		^theme.fontScale;
	}

	fontScale_ { |scale|
		this.theme = theme.withFontScale(scale);
		if (fontMenu.notNil) {
			fontMenu.value = this.prFontScaleToIndex(theme.fontScale);
		};
		if (meterView.notNil)       { meterView.fontScale = scale };
		if (scopeView.notNil)       { scopeView.fontScale = scale };
		if (spectrumView.notNil)    { spectrumView.fontScale = scale };
		if (loudnessView.notNil)    { loudnessView.fontScale = scale };
		if (vectorscopeView.notNil) { vectorscopeView.fontScale = scale };
		this.prHandleResize(view.bounds.width, view.bounds.height);
	}

	theme_ { |newTheme|
		theme = ProbeTheme.get(newTheme);
		view.background = theme.background;
		if (txtTitle.notNil) {
			txtTitle.stringColor = theme.text;
			txtTitle.font = theme.font(10, bold: true);
		};
		if (lblTheme.notNil) {
			lblTheme.stringColor = theme.subtext;
			lblTheme.font = theme.font(9, bold: true);
		};
		if (themeMenu.notNil) {
			themeMenu.font = theme.font(9);
		};
		if (lblTarget.notNil) {
			lblTarget.stringColor = theme.subtext;
			lblTarget.font = theme.font(9, bold: true);
		};
		if (targetMenu.notNil) {
			targetMenu.font = theme.font(9);
		};
		if (lblFont.notNil) {
			lblFont.stringColor = theme.subtext;
			lblFont.font = theme.font(9, bold: true);
		};
		if (fontMenu.notNil) {
			fontMenu.font = theme.font(9);
		};
		if (txtBrokerStatus.notNil) {
			txtBrokerStatus.stringColor = theme.subtext;
			txtBrokerStatus.font = theme.font(8, mono: true);
		};
		if (freezeBtn.notNil) {
			freezeBtn.states = [
				["Freeze All", theme.text, theme.panelBackground],
				["Unfreeze All", theme.background, theme.signal]
			];
			freezeBtn.font = theme.font(9, bold: true);
			freezeBtn.value = if (isFrozen) { 1 } { 0 };
		};
		if (meterView.notNil)       { meterView.theme = theme };
		if (scopeView.notNil)       { scopeView.theme = theme };
		if (spectrumView.notNil)    { spectrumView.theme = theme };
		if (loudnessView.notNil)    { loudnessView.theme = theme };
		if (vectorscopeView.notNil) { vectorscopeView.theme = theme };
		this.prHandleResize(view.bounds.width, view.bounds.height);
	}

	targetLUFS_ { |val|
		targetLUFS = val.asFloat;
		loudnessView.targetLUFS = targetLUFS;
	}

	toggleFreeze {
		isFrozen = isFrozen.not;
		scopeView.toggleFreeze;
		spectrumView.toggleFreeze;
		loudnessView.toggleFreeze;
		vectorscopeView.toggleFreeze;
		freezeBtn.value = if (isFrozen) { 1 } { 0 };
	}

	stop {
		if (isClosed.not) {
			isClosed = true;
			if (meterView.notNil)       { meterView.stop };
			if (scopeView.notNil)       { scopeView.stop };
			if (spectrumView.notNil)    { spectrumView.stop };
			if (loudnessView.notNil)    { loudnessView.stop };
			if (vectorscopeView.notNil) { vectorscopeView.stop };
		};
	}
}

ProbeDashboard {
	var <window, <dashboardView;

	*new { |server, numIns=2, numOuts=2, busIndex=0, theme=\graphite, targetLUFS=(-23.0)|
		^super.new.prInit(server, numIns, numOuts, busIndex, theme, targetLUFS);
	}

	prInit { |server, numIns, numOuts, busIndex, theme, targetLUFS|
		var s = server ?? { Server.default };
		var w = 1020, h = 600;

		window = Window(s.name ++ " Unified Audio Analysis Dashboard — Probe", Rect(100, 100, w, h));
		dashboardView = ProbeDashboardView(s, window, Rect(0, 0, w, h), numIns, numOuts, busIndex, theme, targetLUFS);
		window.view.background = dashboardView.theme.background;

		window.view.onResize = { |v|
			if (v.notNil and: { v.isClosed.not } and: { v.bounds.notNil } and: { dashboardView.notNil } and: { dashboardView.isClosed.not }) {
				dashboardView.prHandleResize(v.bounds.width, v.bounds.height);
			};
		};
		window.onClose = { dashboardView.stop };
		window.front;
	}

	close {
		dashboardView.stop;
		window.close;
	}

	theme_ { |aTheme|
		dashboardView.theme = aTheme;
		window.view.background = dashboardView.theme.background;
	}

	fontScale {
		^dashboardView.fontScale;
	}

	fontScale_ { |aScale|
		dashboardView.fontScale = aScale;
		window.view.background = dashboardView.theme.background;
	}

	targetLUFS_ { |val|
		dashboardView.targetLUFS = val;
	}

	toggleFreeze {
		dashboardView.toggleFreeze;
	}
}
