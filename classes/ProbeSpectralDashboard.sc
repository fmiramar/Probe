/*
    ProbeSpectralDashboard.sc
    Probe — Advanced Spectral Analysis Multi-Panel Dashboard
    Unifies High-Resolution FFT Spectrum, 16-Band Spectral Analyzer, Descriptors HUD
    (Centroid, Flatness, Roll-off, Flux), Pitch & Voicing Tracker, 12-Tone Chromagram,
    Time-Frequency Spectrogram, 3D Waterfall, and Differential A/B Comparison into a single
    synchronized, theme-aware laboratory console.
*/

ProbeSpectralDashboardView {
	var <server;
	var <view;
	var <busIndex;
	var <theme;
	var <currentPreset = \overview;
	var <isFrozen = false;
	var <isClosed = false;

	var containerView;
	var txtTitle, presetMenu, themeMenu, fontMenu, freezeBtn, txtStatus;
	var panelOverview, panelPitch, panelTimeFreq, panelCompare;
	var specOverview, bandView, descView;
	var pitchView, chromaView;
	var gramView, fallView;
	var specCompare, compareView;
	var allSubViews;

	*new { |server, parent, bounds, busIndex=0, theme=\graphite|
		^super.new.prInit(server, parent, bounds, busIndex, theme);
	}

	prInit { |argServer, parent, bounds, argBusIndex, argTheme|
		var rect = bounds ?? { Rect(0, 0, 960, 580) };

		server      = argServer ?? { Server.default };
		busIndex    = argBusIndex.asInteger;
		theme       = SCVTheme.get(argTheme);
		allSubViews = List.new;

		view = CompositeView(parent, rect);
		view.resize = 5;
		view.background = theme.background;
		view.onClose = { this.stop };
		view.onResize = { |v|
			var b = if (v.notNil and: { v.isClosed.not }) { v.bounds };
			if (b.notNil and: { isClosed.not }) { this.prHandleResize(b.width, b.height) };
		};

		this.prBuildUI(rect.width, rect.height);
	}

	prBuildUI { |w, h|
		var themes = [\graphite, \phosphor, \ice, \amber, \paper, \monochrome];
		var fs = theme.fontScale ? 1.25;
		var btnH = (22 * fs).max(22).min(28).round.asInteger;
		var topH = btnH + 8;
		var cw = w - 16, ch = h - topH - 8;

		// 1. Top Control Bar
		txtTitle = StaticText(view, Rect(8, 4, 180, btnH))
			.string_("PROBE SPECTRAL DASHBOARD")
			.font_(theme.font(10, bold: true))
			.stringColor_(theme.text);

		presetMenu = PopUpMenu(view, Rect(192, 4, 130, btnH))
			.items_(["Overview", "Pitch & Chroma", "Time-Frequency", "Compare Live/Ref"])
			.font_(theme.font(9))
			.action_({ |m|
				var sel = switch(m.value,
					0, { \overview },
					1, { \pitch },
					2, { \timeFreq },
					3, { \compare }
				);
				this.preset = sel;
			});

		themeMenu = PopUpMenu(view, Rect(328, 4, 95, btnH))
			.items_(["Graphite", "Phosphor", "Ice", "Amber", "Paper", "Monochrome"])
			.font_(theme.font(9))
			.action_({ |m| this.theme = themes[m.value] });

		fontMenu = PopUpMenu(view, Rect(428, 4, 60, btnH))
			.items_(["100%", "125%", "150%"])
			.font_(theme.font(9))
			.value_(if (fs >= 1.45) { 2 } { if (fs >= 1.2) { 1 } { 0 } })
			.action_({ |m|
				var scales = [1.0, 1.25, 1.5];
				this.fontScale = scales[m.value];
			});

		freezeBtn = Button(view, Rect(494, 4, 85, btnH))
			.states_([
				["Freeze All", theme.text, theme.panelBackground],
				["RUNNING", theme.background, theme.signal]
			])
			.font_(theme.font(9, bold: true))
			.action_({ |b| this.toggleFreeze });

		txtStatus = StaticText(view, Rect(585, 4, (w - 590).max(80), btnH))
			.string_("Bus: " ++ busIndex ++ " | FFT: 2048 | Broker: Active")
			.font_(theme.font(9, mono: true))
			.stringColor_(theme.subtext);

		// 2. Main Preset Container
		containerView = CompositeView(view, Rect(8, topH, cw, ch));
		containerView.resize = 5;
		containerView.background = theme.background;

		// 3. Construct 4 Persistent Panels
		// A. Overview Panel
		panelOverview = CompositeView(containerView, Rect(0, 0, cw, ch));
		panelOverview.resize = 5;
		panelOverview.background = theme.background;
		{
			var halfW = ((cw - 8) * 0.5).floor;
			var topPartH = (ch * 0.65).floor;
			var botPartH = ch - topPartH - 8;
			specOverview = SCVSpectrumView(server, panelOverview, Rect(0, 0, halfW, topPartH), busIndex, 2048, theme);
			bandView     = ProbeBandAnalyzerView(server, panelOverview, Rect(halfW + 8, 0, halfW, topPartH), busIndex, theme);
			descView     = ProbeDescriptorsView(server, panelOverview, Rect(0, topPartH + 8, cw, botPartH), busIndex, theme);
			allSubViews.addAll([specOverview, bandView, descView]);
		}.value;

		// B. Pitch & Chroma Panel
		panelPitch = CompositeView(containerView, Rect(0, 0, cw, ch));
		panelPitch.resize = 5;
		panelPitch.background = theme.background;
		panelPitch.visible = false;
		{
			var halfH = ((ch - 8) * 0.5).floor;
			pitchView  = ProbePitchTrackerView(server, panelPitch, Rect(0, 0, cw, halfH), busIndex, theme);
			chromaView = ProbeChromagramView(server, panelPitch, Rect(0, halfH + 8, cw, halfH), busIndex, theme);
			allSubViews.addAll([pitchView, chromaView]);
		}.value;

		// C. Time-Frequency Panel
		panelTimeFreq = CompositeView(containerView, Rect(0, 0, cw, ch));
		panelTimeFreq.resize = 5;
		panelTimeFreq.background = theme.background;
		panelTimeFreq.visible = false;
		{
			var halfH = ((ch - 8) * 0.5).floor;
			gramView = SCVSpectrogramView(server, panelTimeFreq, Rect(0, 0, cw, halfH), busIndex, 64, theme);
			fallView = SCVWaterfallView(server, panelTimeFreq, Rect(0, halfH + 8, cw, halfH), busIndex, 64, theme);
			allSubViews.addAll([gramView, fallView]);
		}.value;

		// D. Compare Panel
		panelCompare = CompositeView(containerView, Rect(0, 0, cw, ch));
		panelCompare.resize = 5;
		panelCompare.background = theme.background;
		panelCompare.visible = false;
		{
			var halfH = ((ch - 8) * 0.5).floor;
			specCompare = SCVSpectrumView(server, panelCompare, Rect(0, 0, cw, halfH), busIndex, 2048, theme);
			compareView = ProbeSpectralCompareView(server, panelCompare, Rect(0, halfH + 8, cw, halfH), busIndex, theme);
			allSubViews.addAll([specCompare, compareView]);
		}.value;
	}

	preset_ { |newPreset|
		currentPreset = newPreset.asSymbol;
		if (containerView.notNil and: { containerView.isClosed.not }) {
			panelOverview.visible = (currentPreset === \overview);
			panelPitch.visible    = (currentPreset === \pitch);
			panelTimeFreq.visible = (currentPreset === \timeFreq);
			panelCompare.visible  = (currentPreset === \compare);
		};
	}

	prHandleResize { |w, h|
		var fs = theme.fontScale ? 1.25;
		var btnH = (22 * fs).max(22).min(28).round.asInteger;
		var topH = btnH + 8;
		var cw = w - 16, ch = h - topH - 8;

		if (isClosed.not and: { view.notNil } and: { view.isClosed.not }) {
			view.bounds = Rect(view.bounds.left, view.bounds.top, w, h);
			if (containerView.notNil and: { containerView.isClosed.not }) {
				containerView.bounds = Rect(8, topH, cw, ch);
				if (panelOverview.notNil and: { panelOverview.isClosed.not }) {
					panelOverview.bounds = Rect(0, 0, cw, ch);
				};
				if (panelPitch.notNil and: { panelPitch.isClosed.not }) {
					panelPitch.bounds = Rect(0, 0, cw, ch);
				};
				if (panelTimeFreq.notNil and: { panelTimeFreq.isClosed.not }) {
					panelTimeFreq.bounds = Rect(0, 0, cw, ch);
				};
				if (panelCompare.notNil and: { panelCompare.isClosed.not }) {
					panelCompare.bounds = Rect(0, 0, cw, ch);
				};
			};
		};
	}

	theme_ { |newTheme|
		theme = SCVTheme.get(newTheme);
		view.background = theme.background;
		if (containerView.notNil and: { containerView.isClosed.not }) {
			containerView.background = theme.background;
		};
		if (txtTitle.notNil) {
			txtTitle.stringColor = theme.text;
			txtTitle.font = theme.font(10, bold: true);
		};
		if (txtStatus.notNil) {
			txtStatus.stringColor = theme.subtext;
		};
		allSubViews.do { |sv|
			if (sv.respondsTo(\theme_)) { sv.theme = theme };
		};
	}

	fontScale {
		^theme.fontScale;
	}

	fontScale_ { |scale|
		this.theme = theme.withFontScale(scale);
		this.prHandleResize(view.bounds.width, view.bounds.height);
	}

	toggleFreeze {
		isFrozen = isFrozen.not;
		freezeBtn.value = if (isFrozen) { 1 } { 0 };
		allSubViews.do { |sv|
			if (sv.respondsTo(\toggleFreeze)) { sv.toggleFreeze };
			if (sv.respondsTo(\isFrozen_)) { sv.isFrozen = isFrozen };
		};
	}

	stop {
		if (isClosed.not) {
			isClosed = true;
			allSubViews.do { |sv|
				if (sv.respondsTo(\stop)) { sv.stop };
			};
			allSubViews.clear;
		};
	}
}

ProbeSpectralDashboard {
	var <window, <dashboardView;

	*new { |server, busIndex=0, theme=\graphite|
		^super.new.prInit(server, busIndex, theme);
	}

	prInit { |server, busIndex, theme|
		var s = server ?? { Server.default };
		var w = 980, h = 600;

		window = Window(s.name ++ " Spectral Dashboard — Probe", Rect(80, 100, w, h));
		dashboardView = ProbeSpectralDashboardView(s, window, Rect(0, 0, w, h), busIndex, theme);
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
		window.close;
	}

	preset_ { |aPreset|
		dashboardView.preset = aPreset;
	}

	theme_ { |aTheme|
		dashboardView.theme = aTheme;
		window.view.background = dashboardView.theme.background;
	}

	fontScale_ { |scale|
		dashboardView.fontScale = scale;
		window.view.background = dashboardView.theme.background;
	}

	toggleFreeze {
		dashboardView.toggleFreeze;
	}
}
