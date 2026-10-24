/*
    ProbeSpectralDashboard.sc
    Probe — Advanced Spectral Analysis Multi-Panel Dashboard
    Unifies High-Resolution FFT Spectrum, 16-Band Spectral Analyzer, Descriptors HUD
    (Centroid, Flatness, Roll-off, Flux), Pitch & Voicing Tracker, 12-Tone Chromagram,
    Time-Frequency Spectrogram, 3D Waterfall, and Differential A/B Comparison into a single
    synchronized, theme-aware laboratory console.
*/

PB_SpectralDashboardView {
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
	var <allSubViews;

	*new { |server, parent, bounds, busIndex=0, theme=\graphite|
		^super.new.prInit(server, parent, bounds, busIndex, theme);
	}

	prInit { |argServer, parent, bounds, argBusIndex, argTheme|
		var rect = bounds ?? { Rect(0, 0, 960, 580) };

		server      = argServer ?? { Server.default };
		busIndex    = argBusIndex.asInteger;
		theme       = ProbeTheme.get(argTheme);
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
		this.prStartTelemetry;

		ServerQuit.add(this, server);
		ServerTree.add(this, server);
	}

	prBuildUI { |w, h|
		var themes = [\graphite, \phosphor, \ice, \amber, \paper, \monochrome, \puredata];
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
			.items_(["Graphite", "Phosphor", "Ice", "Amber", "Paper", "Monochrome", "Pure Data"])
			.value_(themes.indexOf(theme.name) ? 0)
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
			specOverview = PB_SpectrumView(server, panelOverview, Rect(0, 0, halfW, topPartH), busIndex, 2048, theme);
			bandView     = PB_BandAnalyzerView(server, panelOverview, Rect(halfW + 8, 0, halfW, topPartH), busIndex, theme);
			descView     = PB_DescriptorsView(server, panelOverview, Rect(0, topPartH + 8, cw, botPartH), busIndex, theme);
			allSubViews.addAll([specOverview, bandView, descView]);
		}.value;

		// B. Pitch & Chroma Panel
		panelPitch = CompositeView(containerView, Rect(0, 0, cw, ch));
		panelPitch.resize = 5;
		panelPitch.background = theme.background;
		panelPitch.visible = false;
		{
			var halfH = ((ch - 8) * 0.5).floor;
			pitchView  = PB_PitchTrackerView(server, panelPitch, Rect(0, 0, cw, halfH), busIndex, theme);
			chromaView = PB_ChromagramView(server, panelPitch, Rect(0, halfH + 8, cw, halfH), busIndex, theme);
			allSubViews.addAll([pitchView, chromaView]);
		}.value;

		// C. Time-Frequency Panel
		panelTimeFreq = CompositeView(containerView, Rect(0, 0, cw, ch));
		panelTimeFreq.resize = 5;
		panelTimeFreq.background = theme.background;
		panelTimeFreq.visible = false;
		{
			var halfH = ((ch - 8) * 0.5).floor;
			gramView = PB_SpectrogramView(server, panelTimeFreq, Rect(0, 0, cw, halfH), busIndex, 64, theme);
			fallView = PB_WaterfallView(server, panelTimeFreq, Rect(0, halfH + 8, cw, halfH), busIndex, 64, theme);
			allSubViews.addAll([gramView, fallView]);
		}.value;

		// D. Compare Panel
		panelCompare = CompositeView(containerView, Rect(0, 0, cw, ch));
		panelCompare.resize = 5;
		panelCompare.background = theme.background;
		panelCompare.visible = false;
		{
			var halfH = ((ch - 8) * 0.5).floor;
			specCompare = PB_SpectrumView(server, panelCompare, Rect(0, 0, cw, halfH), busIndex, 2048, theme);
			compareView = PB_SpectralCompareView(server, panelCompare, Rect(0, halfH + 8, cw, halfH), busIndex, theme);
			allSubViews.addAll([specCompare, compareView]);
		}.value;

		this.prHandleResize(w, h);
	}

	preset_ { |newPreset|
		currentPreset = newPreset.asSymbol;
		if (presetMenu.notNil and: { presetMenu.isClosed.not }) {
			presetMenu.value = switch(currentPreset,
				\overview, { 0 },
				\pitch,    { 1 },
				\timeFreq, { 2 },
				\compare,  { 3 },
				{ 0 }
			);
		};
		if (containerView.notNil and: { containerView.isClosed.not }) {
			panelOverview.visible = (currentPreset === \overview);
			panelPitch.visible    = (currentPreset === \pitch);
			panelTimeFreq.visible = (currentPreset === \timeFreq);
			panelCompare.visible  = (currentPreset === \compare);
			this.prHandleResize(view.bounds.width, view.bounds.height);
		};
	}

	prHandleResize { |w, h|
		var fs = theme.fontScale ? 1.25;
		var btnH = (22 * fs).max(22).min(28).round.asInteger;
		var topH = btnH + 8;
		var cw = w - 16, ch = h - topH - 8;
		var curY = ((topH - btnH) * 0.5).round.asInteger;
		var curX = 8;

		var titleW = (180 * fs).clip(160, 220).round.asInteger;
		var presetW = (130 * fs).clip(120, 160).round.asInteger;
		var themeW = (95 * fs).clip(85, 115).round.asInteger;
		var fontW = (60 * fs).clip(55, 75).round.asInteger;
		var freezeW = (85 * fs).clip(80, 105).round.asInteger;

		if (isClosed.not and: { view.notNil } and: { view.isClosed.not }) {
			view.bounds = Rect(view.bounds.left, view.bounds.top, w, h);

			if (txtTitle.notNil) {
				txtTitle.bounds = Rect(curX, curY, titleW, btnH);
				curX = curX + titleW + 4;
			};
			if (presetMenu.notNil) {
				presetMenu.bounds = Rect(curX, curY, presetW, btnH);
				curX = curX + presetW + 6;
			};
			if (themeMenu.notNil) {
				themeMenu.bounds = Rect(curX, curY, themeW, btnH);
				curX = curX + themeW + 6;
			};
			if (fontMenu.notNil) {
				fontMenu.bounds = Rect(curX, curY, fontW, btnH);
				curX = curX + fontW + 6;
			};
			if (freezeBtn.notNil) {
				freezeBtn.bounds = Rect(curX, curY, freezeW, btnH);
				curX = curX + freezeW + 8;
			};
			if (txtStatus.notNil) {
				txtStatus.bounds = Rect(curX, curY, (w - curX - 8).max(80), btnH);
			};

			if (containerView.notNil and: { containerView.isClosed.not }) {
				containerView.bounds = Rect(8, topH, cw, ch);

				// Panel A: Overview
				if (panelOverview.notNil and: { panelOverview.isClosed.not }) {
					var halfW = ((cw - 8) * 0.5).floor;
					var topPartH = (ch * 0.65).floor;
					var botPartH = ch - topPartH - 8;

					panelOverview.bounds = Rect(0, 0, cw, ch);

					if (specOverview.notNil and: { specOverview.view.notNil }) {
						specOverview.view.bounds = Rect(0, 0, halfW, topPartH);
						specOverview.prHandleResize(halfW, topPartH);
					};
					if (bandView.notNil and: { bandView.view.notNil }) {
						bandView.view.bounds = Rect(halfW + 8, 0, halfW, topPartH);
						bandView.prHandleResize(halfW, topPartH);
					};
					if (descView.notNil and: { descView.view.notNil }) {
						descView.view.bounds = Rect(0, topPartH + 8, cw, botPartH);
						descView.prHandleResize(cw, botPartH);
					};
				};

				// Panel B: Pitch & Chroma
				if (panelPitch.notNil and: { panelPitch.isClosed.not }) {
					var halfH = ((ch - 8) * 0.5).floor;

					panelPitch.bounds = Rect(0, 0, cw, ch);

					if (pitchView.notNil and: { pitchView.view.notNil }) {
						pitchView.view.bounds = Rect(0, 0, cw, halfH);
						pitchView.prHandleResize(cw, halfH);
					};
					if (chromaView.notNil and: { chromaView.view.notNil }) {
						chromaView.view.bounds = Rect(0, halfH + 8, cw, halfH);
						chromaView.prHandleResize(cw, halfH);
					};
				};

				// Panel C: Time-Frequency
				if (panelTimeFreq.notNil and: { panelTimeFreq.isClosed.not }) {
					var halfH = ((ch - 8) * 0.5).floor;

					panelTimeFreq.bounds = Rect(0, 0, cw, ch);

					if (gramView.notNil and: { gramView.view.notNil }) {
						gramView.view.bounds = Rect(0, 0, cw, halfH);
						gramView.prHandleResize(cw, halfH);
					};
					if (fallView.notNil and: { fallView.view.notNil }) {
						fallView.view.bounds = Rect(0, halfH + 8, cw, halfH);
						fallView.prHandleResize(cw, halfH);
					};
				};

				// Panel D: Compare
				if (panelCompare.notNil and: { panelCompare.isClosed.not }) {
					var halfH = ((ch - 8) * 0.5).floor;

					panelCompare.bounds = Rect(0, 0, cw, ch);

					if (specCompare.notNil and: { specCompare.view.notNil }) {
						specCompare.view.bounds = Rect(0, 0, cw, halfH);
						specCompare.prHandleResize(cw, halfH);
					};
					if (compareView.notNil and: { compareView.view.notNil }) {
						compareView.view.bounds = Rect(0, halfH + 8, cw, halfH);
						compareView.prHandleResize(cw, halfH);
					};
				};
			};
		};
	}

	prStartTelemetry {
		AppClock.sched(0.5, {
			if (isClosed.not and: { txtStatus.notNil and: { txtStatus.isClosed.not } }) {
				var b = Probe.broker;
				txtStatus.string = "Bus: " ++ busIndex ++ " | FFT: 2048 | Broker: " ++ b.sourceCount ++ " src / " ++ b.totalSubscriberCount ++ " sub";
				0.5;
			} {
				nil;
			};
		});
	}

	theme_ { |newTheme|
		theme = ProbeTheme.get(newTheme);
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

	doOnServerTree {
		if (server.serverRunning and: { isClosed.not }) {
			{
				if (isClosed.not and: { view.notNil } and: { view.isClosed.not }) {
					this.prHandleResize(view.bounds.width, view.bounds.height);
				};
			}.defer;
		};
	}

	doOnServerQuit {
		// Teardown handled by individual subviews
	}

	stop {
		if (isClosed.not) {
			isClosed = true;
			ServerTree.remove(this, server);
			ServerQuit.remove(this, server);
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

	*view { |parent, bounds, server, busIndex=0, theme=\graphite|
		^PB_SpectralDashboardView(server ?? { Server.default }, parent, bounds, busIndex, theme);
	}

	prInit { |server, busIndex, theme|
		var s = server ?? { Server.default };
		var w = 980, h = 600;

		window = Window(s.name ++ " Spectral Dashboard — Probe", Rect(80, 100, w, h));
		dashboardView = PB_SpectralDashboardView(s, window, Rect(0, 0, w, h), busIndex, theme);
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
