/*
    SCVDashboard.sc
    SCVisuals — Unified Multi-Panel Audio Analysis Dashboard
    Composes Meter, Oscilloscope, Spectrum, Loudness, and Vectorscope into a single
    synchronized interface sharing measurement sources via SCVMeasurementBroker,
    with master freeze, global theme propagation, dynamic responsive grid resizing, and broker telemetry.
*/

SCVDashboardView {
	var <server;
	var <view;
	var <meterView, <scopeView, <spectrumView, <loudnessView, <vectorscopeView;
	var <theme;
	var <targetLUFS = -23.0;
	var <isFrozen = false;
	var <isClosed = false;

	var txtTitle, lblTheme, lblTarget;
	var themeMenu, freezeBtn, targetMenu, txtBrokerStatus;

	*new { |server, parent, bounds, numIns=2, numOuts=2, busIndex=0, theme=\graphite, targetLUFS=(-23.0)|
		^super.new.prInit(server, parent, bounds, numIns, numOuts, busIndex, theme, targetLUFS);
	}

	prInit { |argServer, parent, bounds, numIns, numOuts, busIndex, argTheme, argTargetLUFS|
		var rect = bounds ?? { Rect(0, 0, 960, 560) };

		server     = argServer ?? { Server.default };
		theme      = SCVTheme.get(argTheme);
		targetLUFS = argTargetLUFS.asFloat;

		view = CompositeView(parent, rect);
		view.background = theme.background;
		view.onClose = { this.stop };
		view.onResize = { |v| this.prHandleResize(v.bounds.width, v.bounds.height) };

		this.prBuildUI(rect.width, rect.height, numIns, numOuts, busIndex);
		this.prStartTelemetry;
	}

	prBuildUI { |w, h, numIns, numOuts, busIndex|
		var themes = [\graphite, \phosphor, \ice, \amber, \paper, \monochrome];
		var topH = 32;
		var panelH = ((h - topH - 24) / 2).floor;
		var meterW = (w * 0.17).clip(120, 240);
		var midW = (((w - meterW - 36) / 2).floor).max(80);
		var rightW = (w - meterW - midW - 36).max(80);

		// 1. Top Control Bar
		txtTitle = StaticText(view, Rect(12, 6, 260, 20))
			.string_("SCVisuals AUDIO ANALYSIS DASHBOARD")
			.font_(Font.sansSerif(11).boldVariant)
			.stringColor_(theme.text);

		// Global Theme Dropdown
		lblTheme = StaticText(view, Rect(280, 6, 46, 20))
			.string_("Theme:")
			.font_(Font.sansSerif(9).boldVariant)
			.stringColor_(theme.subtext);

		themeMenu = PopUpMenu(view, Rect(330, 5, 110, 22))
			.items_(["Graphite", "Phosphor", "Ice", "Amber", "Paper", "Monochrome"])
			.value_(themes.indexOf(theme.name) ? 0)
			.font_(Font.sansSerif(9))
			.action_({ |m|
				this.theme = themes[m.value];
			});

		// Target LUFS Selector
		lblTarget = StaticText(view, Rect(455, 6, 46, 20))
			.string_("Target:")
			.font_(Font.sansSerif(9).boldVariant)
			.stringColor_(theme.subtext);

		targetMenu = PopUpMenu(view, Rect(505, 5, 130, 22))
			.items_(["-23 LUFS (Broadcast)", "-14 LUFS (Streaming)"])
			.value_(if (targetLUFS == -14.0) { 1 } { 0 })
			.font_(Font.sansSerif(9))
			.action_({ |m|
				var t = if (m.value == 0) { -23.0 } { -14.0 };
				this.targetLUFS = t;
			});

		// Master Freeze Button
		freezeBtn = Button(view, Rect(650, 5, 100, 22))
			.states_([["Freeze All", theme.text, theme.panelBackground], ["Unfreeze All", theme.background, theme.signal]])
			.font_(Font.sansSerif(9).boldVariant)
			.action_({ this.toggleFreeze });

		// Broker Telemetry Label
		txtBrokerStatus = StaticText(view, Rect(760, 6, (w - 770).max(60), 20))
			.string_("Broker: 0 sources | 0 subs")
			.font_(Font.monospace(8))
			.stringColor_(theme.subtext);

		// 2. Embedded Panels Grid
		// Panel 1: Multi-Channel Meter (Left)
		meterView = SCVMeterView(server, view, Rect(10, topH + 8, meterW, h - topH - 16), numIns, numOuts, theme);

		// Panel 2: Scope (Mid-Top)
		scopeView = SCVScopeView(server, view, Rect(meterW + 18, topH + 8, midW, panelH), busIndex, 2, theme);

		// Panel 3: Spectrum (Mid-Bottom)
		spectrumView = SCVSpectrumView(server, view, Rect(meterW + 18, topH + 16 + panelH, midW, panelH), busIndex, 2048, theme);

		// Panel 4: Loudness Meter (Right-Top)
		loudnessView = SCVLoudnessView(server, view, Rect(meterW + midW + 26, topH + 8, rightW, panelH), \outputs, busIndex, 2, theme, targetLUFS);

		// Panel 5: Vectorscope (Right-Bottom)
		vectorscopeView = SCVVectorscopeView(server, view, Rect(meterW + midW + 26, topH + 16 + panelH, rightW, panelH), busIndex, theme);

		// Master Keyboard Controls
		view.keyDownAction = { |v, char|
			case
				{ char === $  } { this.toggleFreeze }
				{ char === $r } { loudnessView.reset };
		};
	}

	prHandleResize { |w, h|
		var topH = 32;
		var panelH = ((h - topH - 24) / 2).floor.max(60);
		var meterW = (w * 0.17).clip(120, 240);
		var midW = (((w - meterW - 36) / 2).floor).max(80);
		var rightW = (w - meterW - midW - 36).max(80);

		if (isClosed.not) {
			if (txtBrokerStatus.notNil) {
				txtBrokerStatus.bounds = Rect(760, 6, (w - 770).max(60), 20);
			};
			if (meterView.notNil and: { meterView.view.notNil }) {
				meterView.view.bounds = Rect(10, topH + 8, meterW, h - topH - 16);
				meterView.prHandleResize(meterW, h - topH - 16);
			};
			if (scopeView.notNil and: { scopeView.view.notNil }) {
				scopeView.view.bounds = Rect(meterW + 18, topH + 8, midW, panelH);
				scopeView.prHandleResize(midW, panelH);
			};
			if (spectrumView.notNil and: { spectrumView.view.notNil }) {
				spectrumView.view.bounds = Rect(meterW + 18, topH + 16 + panelH, midW, panelH);
				spectrumView.prHandleResize(midW, panelH);
			};
			if (loudnessView.notNil and: { loudnessView.view.notNil }) {
				loudnessView.view.bounds = Rect(meterW + midW + 26, topH + 8, rightW, panelH);
				loudnessView.prHandleResize(rightW, panelH);
			};
			if (vectorscopeView.notNil and: { vectorscopeView.view.notNil }) {
				vectorscopeView.view.bounds = Rect(meterW + midW + 26, topH + 16 + panelH, rightW, panelH);
				vectorscopeView.prHandleResize(rightW, panelH);
			};
		};
	}

	prStartTelemetry {
		AppClock.sched(0.5, {
			if (isClosed.not and: { txtBrokerStatus.notNil }) {
				var b = SCVisuals.broker;
				txtBrokerStatus.string = "Broker: " ++ b.sourceCount ++ " sources | " ++ b.totalSubscriberCount ++ " subs";
				0.5;
			} {
				nil;
			};
		});
	}

	theme_ { |newTheme|
		theme = SCVTheme.get(newTheme);
		view.background = theme.background;
		if (txtTitle.notNil) { txtTitle.stringColor = theme.text };
		if (lblTheme.notNil) { lblTheme.stringColor = theme.subtext };
		if (lblTarget.notNil) { lblTarget.stringColor = theme.subtext };
		if (txtBrokerStatus.notNil) { txtBrokerStatus.stringColor = theme.subtext };
		if (freezeBtn.notNil) {
			freezeBtn.states = [
				["Freeze All", theme.text, theme.panelBackground],
				["Unfreeze All", theme.background, theme.signal]
			];
			freezeBtn.value = if (isFrozen) { 1 } { 0 };
		};
		if (meterView.notNil)       { meterView.theme = theme };
		if (scopeView.notNil)       { scopeView.theme = theme };
		if (spectrumView.notNil)    { spectrumView.theme = theme };
		if (loudnessView.notNil)    { loudnessView.theme = theme };
		if (vectorscopeView.notNil) { vectorscopeView.theme = theme };
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

SCVDashboard {
	var <window, <dashboardView;

	*new { |server, numIns=2, numOuts=2, busIndex=0, theme=\graphite, targetLUFS=(-23.0)|
		^super.new.prInit(server, numIns, numOuts, busIndex, theme, targetLUFS);
	}

	prInit { |server, numIns, numOuts, busIndex, theme, targetLUFS|
		var s = server ?? { Server.default };
		var w = 980, h = 580;

		window = Window(s.name ++ " Unified Audio Analysis Dashboard — SCVisuals", Rect(100, 100, w, h));
		dashboardView = SCVDashboardView(s, window, Rect(0, 0, w, h), numIns, numOuts, busIndex, theme, targetLUFS);

		window.view.onResize = { |v| dashboardView.prHandleResize(v.bounds.width, v.bounds.height) };
		window.onClose = { dashboardView.stop };
		window.front;
	}

	close {
		dashboardView.stop;
		window.close;
	}

	theme_ { |aTheme|
		dashboardView.theme = aTheme;
	}

	targetLUFS_ { |val|
		dashboardView.targetLUFS = val;
	}

	toggleFreeze {
		dashboardView.toggleFreeze;
	}
}
