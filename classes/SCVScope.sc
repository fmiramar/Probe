/*
    SCVScope.sc
    SCVisuals — Themed Oscilloscope Wrapper
    Wraps native ScopeView / ScopeOut2 with theme palettes, transparent grid overlay,
    multichannel color mapping, style modes (Tracks, Overlay, X/Y), dynamic window resizing, and clean lifecycle.
*/

SCVScopeView {
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
	var txtTitle;

	*new { |server, parent, bounds, busIndex=0, numChannels=2, theme=\graphite, rate=\audio, style=\overlay, bufSize=4096|
		^super.new.prInit(server, parent, bounds, busIndex, numChannels, theme, rate, style, bufSize);
	}

	prInit { |argServer, parent, bounds, argBusIndex, argNumChannels, argTheme, argRate, argStyle, argBufSize|
		var rect = bounds ?? { Rect(0, 0, 380, 240) };

		server      = argServer ?? { Server.default };
		busIndex    = argBusIndex.asInteger;
		numChannels = argNumChannels.asInteger.max(1);
		theme       = SCVTheme.get(argTheme);
		rate        = argRate.asSymbol;
		bufSize     = argBufSize.asInteger.max(128);
		cycle       = 1024;

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
		var topOffset = titleH + 6;
		var scopeRect = Rect(10, topOffset, (w - 20).max(50), (h - topOffset - 10).max(50));

		// 1. Native ScopeView
		scopeView = ScopeView(view, scopeRect);
		scopeView.server = server;
		scopeView.background = theme.panelBackground;
		scopeView.fill = false;
		scopeView.style = scopeStyle;
		scopeView.xZoom = xZoom;
		scopeView.yZoom = yZoom;
		this.prUpdateWaveColors;

		// 2. Transparent Grid & Level Overlay
		gridOverlay = UserView(view, scopeRect);
		gridOverlay.canFocus = false;
		gridOverlay.drawFunc = { |uv|
			var gw = uv.bounds.width, gh = uv.bounds.height;
			Pen.strokeColor = theme.grid.copy.alpha_(0.35);
			Pen.width = 1;

			// Horizontal Center Axis
			Pen.line(Point(0, gh * 0.5), Point(gw, gh * 0.5));
			Pen.stroke;

			// Horizontal +0.5 / -0.5 guidelines
			Pen.line(Point(0, gh * 0.25), Point(gw, gh * 0.25));
			Pen.line(Point(0, gh * 0.75), Point(gw, gh * 0.75));
			Pen.stroke;

			// Vertical divisions (5 subdivisions)
			4.do { |i|
				var x = (i + 1) * (gw / 5);
				Pen.line(Point(x, 0), Point(x, gh));
			};
			Pen.stroke;

			// Axis text markings
			Pen.font = theme.font(8, mono: true);
			Pen.color = theme.subtext;
			Pen.stringAtPoint("+1.0", Point(4, 2));
			Pen.stringAtPoint(" 0.0", Point(4, (gh * 0.5) - 10));
			Pen.stringAtPoint("-1.0", Point(4, gh - 12));
		};

		// 3. Header title and channel readout
		txtTitle = StaticText(view, Rect(10, 4, (w - 20).max(100), titleH))
			.string_("OSCILLOSCOPE [" ++ numChannels ++ " CH]")
			.font_(theme.font(theme.fontSize, bold: true))
			.stringColor_(theme.text);

		// Keyboard controls
		view.keyDownAction = { |v, char|
			case
				{ char === $+ or: { char === $= } } { this.yZoom = (yZoom * 1.25).clip(0.1, 16.0) }
				{ char === $- or: { char === $_ } } { this.yZoom = (yZoom * 0.8).clip(0.1, 16.0) }
				{ char === $s } { this.scopeStyle = (scopeStyle + 1) % 3 }
				{ char === $  } { this.toggleFreeze };
		};
	}

	prHandleResize { |w, h|
		var fs = theme.fontScale ? 1.25;
		var titleH = (18 * fs).max(18).round.asInteger;
		var topOffset = titleH + 6;
		var scopeRect = Rect(10, topOffset, (w - 20).max(50), (h - topOffset - 10).max(50));
		if (isClosed.not and: { view.notNil } and: { view.isClosed.not } and: { scopeView.notNil } and: { scopeView.isClosed.not } and: { gridOverlay.notNil } and: { gridOverlay.isClosed.not }) {
			view.bounds = Rect(view.bounds.left, view.bounds.top, w, h);
			if (txtTitle.notNil) {
				txtTitle.bounds = Rect(10, 4, (w - 20).max(100), titleH);
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
			"SCVScope: Local server required for shared memory scope.".warn;
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

		synthDefName = ("scv_scope_" ++ rate ++ "_" ++ numChannels).asSymbol;

		server.bind {
			var sdef;
			if (rate === \audio) {
				sdef = SynthDef(synthDefName, { |cycleFrames=1024|
					var in = In.ar(busIndex, numChannels);
					ScopeOut2.ar(in, scopeBuf.index, bufSize, cycleFrames);
				});
			} {
				sdef = SynthDef(synthDefName, { |cycleFrames=1024|
					var in = In.kr(busIndex, numChannels);
					ScopeOut2.ar(K2A.ar(in), scopeBuf.index, bufSize, cycleFrames);
				});
			};

			synth = sdef.play(RootNode(server), [\cycleFrames, cycle], \addToTail).register;
		};

		scopeView.start;
	}

	theme_ { |newTheme|
		theme = SCVTheme.get(newTheme);
		view.background = theme.background;
		scopeView.background = theme.panelBackground;
		this.prUpdateWaveColors;
		if (txtTitle.notNil) {
			txtTitle.stringColor = theme.text;
			txtTitle.font = theme.font(theme.fontSize, bold: true);
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
	}

	xZoom_ { |val|
		xZoom = val.asFloat.clip(0.1, 16.0);
		scopeView.xZoom = xZoom;
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
	}

	toggleFreeze {
		if (isFrozen) {
			scopeView.start;
			isFrozen = false;
		} {
			scopeView.stop;
			isFrozen = true;
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

SCVScope {
	var <window, <scopeView;

	*new { |server, busIndex=0, numChannels=2, theme=\graphite, rate=\audio, style=\overlay, bufSize=4096|
		^super.new.prInit(server, busIndex, numChannels, theme, rate, style, bufSize);
	}

	prInit { |server, busIndex, numChannels, theme, rate, style, bufSize|
		var s = server ?? { Server.default };
		var w = 400, h = 250;

		window = Window(s.name ++ " Scope — Probe", Rect(120, 200, w, h));
		scopeView = SCVScopeView(s, window, Rect(0, 0, w, h), busIndex, numChannels, theme, rate, style, bufSize);
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
