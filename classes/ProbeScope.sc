/*
    ProbeScope.sc
    Probe — Themed Oscilloscope Wrapper
    Wraps native ScopeView / ScopeOut2 with theme palettes, transparent grid overlay,
    multichannel color mapping, style modes (Tracks, Overlay, X/Y), dynamic window resizing, and clean lifecycle.
*/

ProbeScopeView {
	var <server;
	var <view;
	var <scopeView;
	var <gridOverlay;
	var <busIndex, <numChannels, <rate;
	var <bufSize, <cycle;
	var <scopeBuf, synth;
	var <theme;
	var <scopeStyle;     // 0 = \tracks, 1 = \overlay, 2 = \xy
	var <xZoom = 1.0, <yZoom = 1.0;
	var <isFrozen = false;
	var <isClosed = false;
	var <triggerMode = \free;     // \free, \rising, \falling
	var <triggerThreshold = 0.0;
	var <showToolbar = false;
	var txtTitle, styleMenu, timeMenu, trigBtn, resetBtn, freezeBtn;

	*new { |server, parent, bounds, busIndex=0, numChannels=2, theme=\graphite, rate=\audio, style=\overlay, bufSize=4096, showToolbar=false|
		^super.new.prInit(server, parent, bounds, busIndex, numChannels, theme, rate, style, bufSize, showToolbar);
	}

	prInit { |argServer, parent, bounds, argBusIndex, argNumChannels, argTheme, argRate, argStyle, argBufSize, argShowToolbar|
		var rect = bounds ?? { Rect(0, 0, 380, 240) };

		server      = argServer ?? { Server.default };
		busIndex    = argBusIndex.asInteger;
		numChannels = argNumChannels.asInteger.max(1);
		theme       = ProbeTheme.get(argTheme);
		rate        = argRate.asSymbol;
		bufSize     = argBufSize.asInteger.max(128);
		cycle       = 1024;
		showToolbar = argShowToolbar ? false;

		scopeStyle = switch(argStyle.asSymbol,
			\tracks,  { 0 },
			\overlay, { 1 },
			\xy,      { 2 },
			{ 1 }
		);

		view = CompositeView(parent, rect);
		view.resize = 5;
		view.background = theme.background;
		view.onClose = { this.stop };
		view.onResize = { |v|
			var b = if (v.notNil and: { v.isClosed.not }) { v.bounds };
			if (b.notNil and: { isClosed.not }) { this.prHandleResize(b.width, b.height) };
		};

		this.prBuildUI(rect.width, rect.height);
		this.prStartAnalysis;

		ServerQuit.add(this, server);
		ServerTree.add(this, server);
	}

	prBuildUI { |w, h|
		var fs = theme.fontScale ? 1.25;
		var titleH = (18 * fs).max(18).round.asInteger;
		var btnH = (20 * fs).max(20).min(26).round.asInteger;
		var topOffset = if (showToolbar) { btnH + 10 } { titleH + 6 };
		var scopeRect = Rect(10, topOffset, (w - 20).max(50), (h - topOffset - 10).max(50));

		// 1. Top Controls (Standalone or Toolbar mode)
		if (showToolbar) {
			txtTitle = StaticText(view, Rect(10, 4, 130, btnH))
				.string_("SCOPE [" ++ numChannels ++ " CH]")
				.font_(theme.font(9, bold: true))
				.stringColor_(theme.text);

			styleMenu = PopUpMenu(view, Rect(145, 4, 75, btnH))
				.items_(["Overlay", "Tracks", "X/Y"])
				.font_(theme.font(8))
				.value_(scopeStyle)
				.action_({ |m| this.scopeStyle = m.value });

			timeMenu = PopUpMenu(view, Rect(224, 4, 65, btnH))
				.items_(["0.25x", "0.5x", "1.0x", "2.0x", "4.0x"])
				.font_(theme.font(8))
				.value_(2)
				.action_({ |m|
					var scales = [0.25, 0.5, 1.0, 2.0, 4.0];
					this.xZoom = scales[m.value];
				});

			trigBtn = Button(view, Rect(293, 4, 80, btnH))
				.states_([
					["Trig: Free", theme.text, theme.panelBackground],
					["Trig: Rise", theme.background, theme.signal],
					["Trig: Fall", theme.background, theme.warning]
				])
				.font_(theme.font(8, bold: true))
				.action_({ |b|
					var modes = [\free, \rising, \falling];
					this.triggerMode = modes[b.value];
				});

			resetBtn = Button(view, Rect(377, 4, 45, btnH))
				.states_([["Reset", theme.text, theme.panelBackground]])
				.font_(theme.font(8))
				.action_({ this.resetZoom });

			freezeBtn = Button(view, Rect(426, 4, 60, btnH))
				.states_([
					["Freeze", theme.text, theme.panelBackground],
					["RUNNING", theme.background, theme.signal]
				])
				.font_(theme.font(8, bold: true))
				.action_({ this.toggleFreeze });
		} {
			txtTitle = StaticText(view, Rect(10, 4, (w - 20).max(100), titleH))
				.string_("OSCILLOSCOPE [" ++ numChannels ++ " CH]")
				.font_(theme.font(theme.fontSize, bold: true))
				.stringColor_(theme.text);
		};

		// 2. Native ScopeView
		scopeView = ScopeView(view, scopeRect);
		scopeView.server = server;
		scopeView.background = theme.panelBackground;
		scopeView.fill = false;
		scopeView.style = scopeStyle;
		scopeView.xZoom = xZoom;
		scopeView.yZoom = yZoom;
		this.prUpdateWaveColors;

		// 3. Transparent Calibrated Grid & Time/Level Overlay
		gridOverlay = UserView(view, scopeRect);
		gridOverlay.canFocus = false;
		gridOverlay.drawFunc = { |uv|
			var gw = uv.bounds.width, gh = uv.bounds.height;
			var sRate = server.sampleRate ? 48000.0;
			var totalMs = ((cycle / sRate) * 1000.0) / xZoom.max(0.01);

			// A. Horizontal Center Zero Line (Subtle accent)
			Pen.strokeColor = theme.subtext.copy.alpha_(0.5);
			Pen.width = 1.2;
			Pen.line(Point(0, gh * 0.5), Point(gw, gh * 0.5));
			Pen.stroke;

			// B. Horizontal +0.5 / -0.5 guidelines
			Pen.strokeColor = theme.grid.copy.alpha_(0.30);
			Pen.width = 1;
			Pen.line(Point(0, gh * 0.25), Point(gw, gh * 0.25));
			Pen.line(Point(0, gh * 0.75), Point(gw, gh * 0.75));
			Pen.stroke;

			// C. Vertical calibrated time subdivisions (4 subdivisions with ms markings)
			4.do { |i|
				var x = (i + 1) * (gw / 5);
				var ms = ((i + 1) * (totalMs / 5)).round(0.1);

				Pen.strokeColor = theme.grid.copy.alpha_(0.25);
				Pen.line(Point(x, 0), Point(x, gh - 14));
				Pen.stroke;

				// Calibrated time labels along bottom axis
				Pen.font = theme.font(8, mono: true);
				Pen.color = theme.subtext;
				Pen.stringCenteredIn(ms.asString ++ " ms", Rect(x - 25, gh - 14, 50, 12));
			};

			// D. Calibrated Vertical Amplitude markings (left ruler)
			Pen.font = theme.font(8, mono: true);
			Pen.color = theme.subtext;
			Pen.stringAtPoint("+1.0 (0 dB)", Point(4, 2));
			Pen.stringAtPoint("+0.5 (-6 dB)", Point(4, (gh * 0.25) - 6));
			Pen.stringAtPoint(" 0.0", Point(4, (gh * 0.5) - 6));
			Pen.stringAtPoint("-0.5 (-6 dB)", Point(4, (gh * 0.75) - 6));
			Pen.stringAtPoint("-1.0 (0 dB)", Point(4, gh - 14));

			// E. Trigger State Badge (top right)
			if (triggerMode !== \free) {
				var badgeStr = if (triggerMode === \rising) { "TRIG: RISE [▲]" } { "TRIG: FALL [▼]" };
				Pen.font = theme.font(8, bold: true);
				Pen.color = if (triggerMode === \rising) { theme.signal } { theme.warning };
				Pen.stringRightJustIn(badgeStr, Rect(gw - 100, 3, 96, 12));
			};

			// F. Channel chips legend (bottom right)
			if (numChannels > 1) {
				var chipW = 28, pad = 4;
				numChannels.do { |c|
					var chipX = gw - ((numChannels - c) * (chipW + pad)) - 4;
					var chipColor = if (c == 0) { theme.signal } { if (c == 1) { theme.secondarySignal } { theme.signal.blend(theme.warning, c / (numChannels - 1)) } };
					Pen.fillColor = chipColor.copy.alpha_(0.85);
					Pen.fillRect(Rect(chipX, gh - 14, chipW, 11));
					Pen.font = theme.font(7, bold: true);
					Pen.color = theme.background;
					Pen.stringCenteredIn("Ch" ++ (c + 1), Rect(chipX, gh - 14, chipW, 11));
				};
			};
		};

		// Keyboard controls
		view.keyDownAction = { |v, char|
			case
				{ char === $+ or: { char === $= } } { this.yZoom = (yZoom * 1.25).clip(0.1, 16.0) }
				{ char === $- or: { char === $_ } } { this.yZoom = (yZoom * 0.8).clip(0.1, 16.0) }
				{ char === $] } { this.xZoom = (xZoom * 1.25).clip(0.1, 16.0) }
				{ char === $[ } { this.xZoom = (xZoom * 0.8).clip(0.1, 16.0) }
				{ char === $0 or: { char === $r } } { this.resetZoom }
				{ char === $t } {
					var nextMode = switch(triggerMode,
						\free,    { \rising },
						\rising,  { \falling },
						\falling, { \free },
						{ \free }
					);
					this.triggerMode = nextMode;
				}
				{ char === $s } { this.scopeStyle = (scopeStyle + 1) % 3 }
				{ char === $  } { this.toggleFreeze };
		};
	}

	prHandleResize { |w, h|
		var fs = theme.fontScale ? 1.25;
		var titleH = (18 * fs).max(18).round.asInteger;
		var btnH = (20 * fs).max(20).min(26).round.asInteger;
		var topOffset = if (showToolbar) { btnH + 10 } { titleH + 6 };
		var scopeRect = Rect(10, topOffset, (w - 20).max(50), (h - topOffset - 10).max(50));

		if (isClosed.not and: { view.notNil } and: { view.isClosed.not } and: { scopeView.notNil } and: { scopeView.isClosed.not } and: { gridOverlay.notNil } and: { gridOverlay.isClosed.not }) {
			view.bounds = Rect(view.bounds.left, view.bounds.top, w, h);

			if (showToolbar) {
				var curX = 10;
				if (txtTitle.notNil) {
					txtTitle.bounds = Rect(curX, 4, 120, btnH);
					curX = curX + 124;
				};
				if (styleMenu.notNil) {
					styleMenu.bounds = Rect(curX, 4, 75, btnH);
					curX = curX + 79;
				};
				if (timeMenu.notNil) {
					timeMenu.bounds = Rect(curX, 4, 65, btnH);
					curX = curX + 69;
				};
				if (trigBtn.notNil) {
					trigBtn.bounds = Rect(curX, 4, 80, btnH);
					curX = curX + 84;
				};
				if (resetBtn.notNil) {
					resetBtn.bounds = Rect(curX, 4, 45, btnH);
					curX = curX + 49;
				};
				if (freezeBtn.notNil) {
					freezeBtn.bounds = Rect(curX, 4, 60, btnH);
				};
			} {
				if (txtTitle.notNil) {
					txtTitle.bounds = Rect(10, 4, (w - 20).max(100), titleH);
				};
			};

			scopeView.bounds = scopeRect;
			gridOverlay.bounds = scopeRect;
			gridOverlay.refresh;
		};
	}

	prUpdateWaveColors {
		var colors = Array.newClear(numChannels);
		numChannels.do { |i|
			colors[i] = if (i == 0) {
				theme.signal
			} {
				if (i == 1) {
					theme.secondarySignal
				} {
					theme.signal.blend(theme.warning, (i / (numChannels - 1)))
				}
			};
		};
		scopeView.waveColors = colors;
	}

	prStartAnalysis {
		var synthDefName;

		if (server.serverRunning.not) { ^this };
		if (server.isLocal.not) {
			"ProbeScope: Local server required for shared memory scope.".warn;
			^this;
		};

		if (scopeBuf.isNil) {
			scopeBuf = ScopeBuffer.alloc(server, numChannels);
			scopeView.bufnum = scopeBuf.index;
		};

		if (synth.notNil) {
			synth.free;
			synth = nil;
		};

		synthDefName = ("probe_scope_" ++ rate ++ "_" ++ numChannels ++ "_" ++ triggerMode).asSymbol;

		server.bind {
			var sdef;
			if (rate === \audio) {
				if (triggerMode === \free) {
					sdef = SynthDef(synthDefName, { |cycleFrames=1024|
						var in = In.ar(busIndex, numChannels);
						ScopeOut2.ar(in, scopeBuf.index, bufSize, cycleFrames);
					});
				} {
					sdef = SynthDef(synthDefName, { |cycleFrames=1024, thresh=0.0|
						var in = In.ar(busIndex, numChannels);
						var in0 = in.asArray[0];
						var trig = if (triggerMode === \rising) {
							(in0 >= thresh) * (Delay1.ar(in0) < thresh)
						} {
							(in0 <= thresh) * (Delay1.ar(in0) > thresh)
						};
						var autoTrig = Impulse.ar(20);
						var combinedTrig = (trig + autoTrig).clip(0, 1);
						var lbuf = LocalBuf(bufSize, numChannels);
						var wrPhasor = Phasor.ar(0, 1, 0, bufSize);
						var wr = BufWr.ar(in.asArray, lbuf, wrPhasor);
						var startIdx = Latch.ar(wrPhasor, combinedTrig);
						var rdPhasor = Phasor.ar(combinedTrig, 1, 0, cycleFrames);
						var rdIdx = (startIdx + rdPhasor) % bufSize;
						var rd = BufRd.ar(numChannels, lbuf, rdIdx, loop: 1, interpolation: 1);
						ScopeOut2.ar(rd, scopeBuf.index, bufSize, cycleFrames);
					});
				};
			} {
				sdef = SynthDef(synthDefName, { |cycleFrames=1024|
					var in = In.kr(busIndex, numChannels);
					ScopeOut2.ar(K2A.ar(in), scopeBuf.index, bufSize, cycleFrames);
				});
			};

			synth = sdef.play(RootNode(server), [\cycleFrames, cycle, \thresh, triggerThreshold], \addToTail).register;
		};

		scopeView.start;
	}

	theme_ { |newTheme|
		theme = ProbeTheme.get(newTheme);
		view.background = theme.background;
		scopeView.background = theme.panelBackground;
		this.prUpdateWaveColors;
		if (txtTitle.notNil) {
			txtTitle.stringColor = theme.text;
			txtTitle.font = theme.font(if (showToolbar) { 9 } { theme.fontSize }, bold: true);
		};
		if (gridOverlay.notNil) { gridOverlay.refresh };
		this.prHandleResize(view.bounds.width, view.bounds.height);
	}

	fontScale {
		^theme.fontScale;
	}

	fontScale_ { |scale|
		this.theme = theme.withFontScale(scale);
		this.prHandleResize(view.bounds.width, view.bounds.height);
	}

	scopeStyle_ { |newStyle|
		scopeStyle = switch(newStyle.asSymbol,
			\tracks,  { 0 },
			\overlay, { 1 },
			\xy,      { 2 },
			{ newStyle.asInteger.clip(0, 2) }
		);
		scopeView.style = scopeStyle;
		if (styleMenu.notNil and: { styleMenu.isClosed.not }) {
			styleMenu.value = scopeStyle;
		};
	}

	xZoom_ { |val|
		xZoom = val.asFloat.clip(0.1, 16.0);
		scopeView.xZoom = xZoom;
		if (gridOverlay.notNil and: { gridOverlay.isClosed.not }) {
			gridOverlay.refresh;
		};
	}

	yZoom_ { |val|
		yZoom = val.asFloat.clip(0.1, 16.0);
		scopeView.yZoom = yZoom;
	}

	cycle_ { |frames|
		cycle = frames.asInteger.clip(64, bufSize);
		if (synth.notNil) {
			synth.set(\cycleFrames, cycle);
		};
		if (gridOverlay.notNil and: { gridOverlay.isClosed.not }) {
			gridOverlay.refresh;
		};
	}

	triggerMode_ { |mode|
		triggerMode = mode.asSymbol;
		if (trigBtn.notNil and: { trigBtn.isClosed.not }) {
			trigBtn.value = switch(triggerMode,
				\free,    { 0 },
				\rising,  { 1 },
				\falling, { 2 },
				{ 0 }
			);
		};
		if (server.serverRunning) {
			this.prStartAnalysis;
		};
		if (gridOverlay.notNil and: { gridOverlay.isClosed.not }) {
			gridOverlay.refresh;
		};
	}

	triggerThreshold_ { |val|
		triggerThreshold = val.asFloat;
		if (synth.notNil) {
			synth.set(\thresh, triggerThreshold);
		};
	}

	resetZoom {
		xZoom = 1.0;
		yZoom = 1.0;
		scopeView.xZoom = 1.0;
		scopeView.yZoom = 1.0;
		if (timeMenu.notNil and: { timeMenu.isClosed.not }) {
			timeMenu.value = 2; // "1.0x"
		};
		if (gridOverlay.notNil and: { gridOverlay.isClosed.not }) {
			gridOverlay.refresh;
		};
	}

	toggleFreeze {
		if (isFrozen) {
			scopeView.start;
			isFrozen = false;
			if (freezeBtn.notNil and: { freezeBtn.isClosed.not }) { freezeBtn.value = 0 };
		} {
			scopeView.stop;
			isFrozen = true;
			if (freezeBtn.notNil and: { freezeBtn.isClosed.not }) { freezeBtn.value = 1 };
		};
	}

	doOnServerQuit {
		{ if (scopeView.notNil and: { scopeView.isClosed.not }) { scopeView.stop }; }.defer;
		synth = nil;
		scopeBuf = nil;
	}

	doOnServerTree {
		if (server.serverRunning) {
			this.prStartAnalysis;
		};
	}

	stop {
		if (isClosed.not) {
			isClosed = true;
			ServerQuit.remove(this, server);
			ServerTree.remove(this, server);
			if (scopeView.notNil and: { scopeView.isClosed.not }) {
				scopeView.stop;
			};
			if (synth.notNil) {
				synth.free;
				synth = nil;
			};
			if (scopeBuf.notNil) {
				scopeBuf.free;
				scopeBuf = nil;
			};
		};
	}
}

ProbeScope {
	var <window, <scopeView;

	*new { |server, busIndex=0, numChannels=2, theme=\graphite, rate=\audio, style=\overlay, bufSize=4096|
		^super.new.prInit(server, busIndex, numChannels, theme, rate, style, bufSize);
	}

	prInit { |server, busIndex, numChannels, theme, rate, style, bufSize|
		var s = server ?? { Server.default };
		var w = 520, h = 300;

		window = Window(s.name ++ " Scope — Probe", Rect(120, 200, w, h));
		scopeView = ProbeScopeView(s, window, Rect(0, 0, w, h), busIndex, numChannels, theme, rate, style, bufSize, showToolbar: true);
		window.view.background = scopeView.theme.background;

		window.view.onResize = { |v|
			if (v.notNil and: { v.isClosed.not } and: { v.bounds.notNil } and: { scopeView.notNil } and: { scopeView.isClosed.not }) {
				scopeView.prHandleResize(v.bounds.width, v.bounds.height);
			};
		};
		window.onClose = { scopeView.stop };
		window.front;
	}

	close {
		scopeView.stop;
		window.close;
	}

	theme_ { |aTheme|
		scopeView.theme = aTheme;
		window.view.background = scopeView.theme.background;
	}

	scopeStyle_ { |aStyle|
		scopeView.scopeStyle = aStyle;
	}

	xZoom_ { |val|
		scopeView.xZoom = val;
	}

	yZoom_ { |val|
		scopeView.yZoom = val;
	}

	cycle_ { |frames|
		scopeView.cycle = frames;
	}

	triggerMode_ { |mode|
		scopeView.triggerMode = mode;
	}

	triggerThreshold_ { |val|
		scopeView.triggerThreshold = val;
	}

	resetZoom {
		scopeView.resetZoom;
	}

	toggleFreeze {
		scopeView.toggleFreeze;
	}

	fontScale {
		^scopeView.fontScale;
	}

	fontScale_ { |aScale|
		scopeView.fontScale = aScale;
		window.view.background = scopeView.theme.background;
	}
}
