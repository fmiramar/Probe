/*
    SCVScope.sc
    SCVisuals — Themed Oscilloscope Wrapper
    Wraps native ScopeView / ScopeOut2 with theme palettes, transparent grid overlay,
    multichannel color mapping, style modes (Tracks, Overlay, X/Y), and clean lifecycle.
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
		view.background = theme.background;
		view.onClose = { this.stop };

		this.prBuildUI(rect.width, rect.height);
		this.prStartAnalysis;

		ServerQuit.add(this, server);
		ServerTree.add(this, server);
	}

	prBuildUI { |w, h|
		var scopeRect = Rect(10, 24, w - 20, h - 34);

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
			Pen.strokeColor = theme.grid;
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
			Pen.font = Font.monospace(8);
			Pen.color = theme.subtext;
			Pen.stringAtPoint("+1.0", Point(4, 2));
			Pen.stringAtPoint(" 0.0", Point(4, (gh * 0.5) - 10));
			Pen.stringAtPoint("-1.0", Point(4, gh - 12));
		};

		// 3. Header title and channel readout
		StaticText(view, Rect(10, 4, 200, 16))
			.string_("OSCILLOSCOPE [" ++ numChannels ++ " CH]")
			.font_(Font.sansSerif(theme.fontSize).boldVariant)
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
			scopeBuf = ScopeBuffer.alloc(server);
			scopeView.bufnum = scopeBuf.index;
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
			synth = sdef.play(RootNode(server), [\cycleFrames, cycle], \addToTail);
		};

		scopeView.start;
	}

	theme_ { |newTheme|
		theme = SCVTheme.get(newTheme);
		view.background = theme.background;
		scopeView.background = theme.panelBackground;
		this.prUpdateWaveColors;
		if (gridOverlay.notNil) { gridOverlay.refresh };
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

	yZoom_ { |val|
		yZoom = val.asFloat;
		scopeView.yZoom = yZoom;
	}

	xZoom_ { |val|
		xZoom = val.asFloat;
		scopeView.xZoom = xZoom;
	}

	cycle_ { |frames|
		cycle = frames.asInteger.clip(64, bufSize);
		if (synth.notNil) { synth.set(\cycleFrames, cycle) };
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
		scopeView.stop;
		synth = nil;
		scopeBuf = nil;
	}

	doOnServerTree {
		synth = nil;
		if (isClosed.not and: { server.serverRunning }) {
			this.prStartAnalysis;
		};
	}

	stop {
		if (isClosed.not) {
			isClosed = true;
			scopeView.stop;
			if (synth.notNil) {
				server.sendBundle(nil, ['/error', -1], [11, synth.nodeID], ['/error', -2]);
				synth = nil;
			};
			if (scopeBuf.notNil) { scopeBuf.free; scopeBuf = nil };
			ServerQuit.remove(this, server);
			ServerTree.remove(this, server);
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
		var w = 400, h = 260;

		window = Window(s.name ++ " Scope — SCVisuals", Rect(200, 300, w, h));
		scopeView = SCVScopeView(s, window, Rect(0, 0, w, h), busIndex, numChannels, theme, rate, style, bufSize);

		window.onClose = { scopeView.stop };
		window.front;
	}

	close {
		window.close;
	}

	theme_ { |aTheme|
		scopeView.theme = aTheme;
	}

	scopeStyle_ { |aStyle|
		scopeView.scopeStyle = aStyle;
	}

	yZoom_ { |val|
		scopeView.yZoom = val;
	}

	xZoom_ { |val|
		scopeView.xZoom = val;
	}

	cycle_ { |val|
		scopeView.cycle = val;
	}
}
