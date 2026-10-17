/*
    SCVVectorscope.sc
    SCVisuals — Real-Time Stereo Vectorscope & Phase Correlation Meter
    Audio-rate Lissajous X/Y plotting via native ScopeView with 45-degree Mid/Side
    polar graticule overlay, real-time phase correlation meter [-1.0, +1.0],
    stereo balance meter, and broker integration.
*/

SCVVectorscopeView {
	var <server;
	var <view;
	var <scopeView, <overlayView;
	var <busIndex, <updateFreq;
	var <scopeBuf, synth;
	var <source;
	var <theme;
	var <correlation = 1.0;
	var <balance = 0.0;
	var <isFrozen = false;
	var <isClosed = false;
	var txtTitle;
	var scopeSize, scopeX, scopeY;

	*new { |server, parent, bounds, busIndex=0, theme=\graphite, updateFreq=15|
		^super.new.prInit(server, parent, bounds, busIndex, theme, updateFreq);
	}

	prInit { |argServer, parent, bounds, argBusIndex, argTheme, argUpdateFreq|
		var rect = bounds ?? { Rect(0, 0, 320, 320) };

		server     = argServer ?? { Server.default };
		busIndex   = argBusIndex.asInteger;
		theme      = SCVTheme.get(argTheme);
		updateFreq = argUpdateFreq.asInteger.clip(5, 30);

		view = CompositeView(parent, rect);
		view.resize = 5;
		view.background = theme.background;
		view.onClose = { this.stop };
		view.onResize = { |v|
			var b = if (v.notNil and: { v.isClosed.not }) { v.bounds };
			if (b.notNil and: { isClosed.not }) { this.prHandleResize(b.width, b.height) };
		};

		this.prBuildUI(rect.width, rect.height);
		this.prInitScope;
		this.prSubscribeSource;

		ServerQuit.add(this, server);
		ServerTree.add(this, server);
	}

	prBuildUI { |w, h|
		var fs = theme.fontScale ? 1.25;
		var titleH = (18 * fs).max(18).round.asInteger;
		var topOffset = titleH + 6;

		scopeSize = (w.min(h - topOffset - 24) - 16).max(80);
		scopeX = (w - scopeSize) / 2;
		scopeY = topOffset;

		// 1. Native ScopeView for high-speed hardware Lissajous
		scopeView = ScopeView(view, Rect(scopeX, scopeY, scopeSize, scopeSize));
		scopeView.server = server;
		scopeView.style = 2; // X/Y Lissajous mode
		scopeView.canFocus = false;
		scopeView.background = theme.panelBackground;
		scopeView.waveColors = [theme.signal];

		// 2. Overlay UserView for Polar Grid & Phase Correlation
		overlayView = UserView(view, Rect(0, 0, w, h));
		overlayView.canFocus = false;
		overlayView.drawFunc = { |uv|
			var curW = uv.bounds.width, curH = uv.bounds.height;
			var curFs = theme.fontScale ? 1.25;
			var curTitleH = (18 * curFs).max(18).round.asInteger;
			var curTopOffset = curTitleH + 6;
			var curScopeSize = (curW.min(curH - curTopOffset - 24) - 16).max(80);
			var curScopeX = (curW - curScopeSize) / 2;
			var curScopeY = curTopOffset;
			var cx = curScopeX + (curScopeSize / 2);
			var cy = curScopeY + (curScopeSize / 2);
			var radius = curScopeSize / 2;
			var corrY = curScopeY + curScopeSize + 12;
			var corrW = (curW - 60).max(40);
			var corrX = 30;

			// Polar Concentric Rings
			#[0.33, 0.66, 1.0].do { |rNorm|
				var r = radius * rNorm;
				Pen.strokeColor = theme.grid.copy.alpha_(0.4);
				Pen.width = 1.0;
				Pen.strokeOval(Rect(cx - r, cy - r, r * 2, r * 2));
			};

			// Mid / Side 45-degree diagonal axes
			Pen.strokeColor = theme.grid.copy.alpha_(0.5);
			Pen.line(Point(curScopeX, curScopeY), Point(curScopeX + curScopeSize, curScopeY + curScopeSize)); // Left/Right
			Pen.line(Point(curScopeX, curScopeY + curScopeSize), Point(curScopeX + curScopeSize, curScopeY)); // Mid/Side
			Pen.stroke;

			// Crosshairs
			Pen.strokeColor = theme.grid.copy.alpha_(0.3);
			Pen.line(Point(cx, curScopeY), Point(cx, curScopeY + curScopeSize));
			Pen.line(Point(curScopeX, cy), Point(curScopeX + curScopeSize, cy));
			Pen.stroke;

			// M/S and Polar Labels
			Pen.font = theme.font(8, mono: true);
			Pen.color = theme.subtext;
			Pen.stringCenteredIn("+M", Rect(cx - 12, curScopeY + 2, 24, 12));
			Pen.stringCenteredIn("-M", Rect(cx - 12, curScopeY + curScopeSize - 14, 24, 12));
			Pen.stringLeftJustIn("+S", Rect(curScopeX + curScopeSize - 18, cy - 6, 24, 12));
			Pen.stringRightJustIn("-S", Rect(curScopeX - 6, cy - 6, 24, 12));

			// Phase Correlation Bar Meter
			Pen.font = theme.font(8, bold: true);
			Pen.color = theme.subtext;
			Pen.stringLeftJustIn("-1", Rect(corrX - 16, corrY - 2, 16, 12));
			Pen.stringCenteredIn("0", Rect(cx - 8, corrY - 14, 16, 12));
			Pen.stringRightJustIn("+1", Rect(corrX + corrW + 2, corrY - 2, 16, 12));

			// Correlation Bar Background
			Pen.fillColor = theme.panelBackground;
			Pen.fillRect(Rect(corrX, corrY, corrW, 8));

			// Center Tick
			Pen.strokeColor = theme.subtext;
			Pen.line(Point(cx, corrY - 2), Point(cx, corrY + 10));
			Pen.stroke;

			// Correlation Pip / Bar
			block { |break|
				var normCorr = ((correlation + 1.0) / 2.0).clip(0.0, 1.0);
				var pipX = corrX + (normCorr * corrW);
				var col = if (correlation < 0.0) {
					theme.critical // Out-of-phase warning
				} {
					if (correlation < 0.5) {
						theme.warning
					} {
						theme.signal
					}
				};

				Pen.fillColor = col;
				if (correlation >= 0.0) {
					Pen.fillRect(Rect(cx, corrY, pipX - cx, 8));
				} {
					Pen.fillRect(Rect(pipX, corrY, cx - pipX, 8));
				};

				// Pointer marker
				Pen.strokeColor = theme.text;
				Pen.width = 1.5;
				Pen.line(Point(pipX, corrY - 3), Point(pipX, corrY + 11));
				Pen.stroke;
			};

			// Text Readout: Correlation & Balance
			Pen.font = theme.font(8, mono: true);
			Pen.color = theme.text;
			Pen.stringLeftJustIn(
				"CORR: " ++ (if (correlation >= 0) { "+" } { "" }) ++ correlation.round(0.01),
				Rect(corrX, corrY + 12, 100, 14)
			);
			Pen.stringRightJustIn(
				"BAL: " ++ (if (balance >= 0) { "+" } { "" }) ++ balance.round(0.01),
				Rect(corrX + corrW - 90, corrY + 12, 90, 14)
			);
		};

		// Header title
		txtTitle = StaticText(view, Rect(10, 4, (w - 20).max(100), titleH))
			.string_("VECTORSCOPE [LISSAJOUS / M-S]")
			.font_(theme.font(theme.fontSize, bold: true))
			.stringColor_(theme.text);

		// Keyboard controls
		view.keyDownAction = { |v, char|
			case
				{ char === $  } { this.toggleFreeze };
		};
	}

	prHandleResize { |w, h|
		var fs = theme.fontScale ? 1.25;
		var titleH = (18 * fs).max(18).round.asInteger;
		var topOffset = titleH + 6;
		if (isClosed.not and: { view.notNil } and: { view.isClosed.not } and: { scopeView.notNil } and: { scopeView.isClosed.not } and: { overlayView.notNil } and: { overlayView.isClosed.not }) {
			view.bounds = Rect(view.bounds.left, view.bounds.top, w, h);
			if (txtTitle.notNil) {
				txtTitle.bounds = Rect(10, 4, (w - 20).max(100), titleH);
			};
			scopeSize = (w.min(h - topOffset - 24) - 16).max(80);
			scopeX = (w - scopeSize) / 2;
			scopeY = topOffset;
			scopeView.bounds = Rect(scopeX, scopeY, scopeSize, scopeSize);
			overlayView.bounds = Rect(0, 0, w, h);
			overlayView.refresh;
		};
	}

	prInitScope {
		var synthDefName;

		if (server.serverRunning.not) { ^this };
		if (server.isLocal.not) { ^this };

		if (scopeBuf.isNil) {
			scopeBuf = ScopeBuffer.alloc(server, 2);
			scopeView.bufnum = scopeBuf.index;
		};

		if (synth.notNil) {
			synth.free;
			synth = nil;
		};

		synthDefName = ("scv_vecscope_" ++ busIndex).asSymbol;

		server.bind {
			var sdef = SynthDef(synthDefName, { |cycleFrames=1024|
				var in = In.ar(busIndex, 2);
				ScopeOut2.ar(in, scopeBuf.index, 4096, cycleFrames);
			});
			synth = sdef.play(RootNode(server), [\cycleFrames, 1024], \addToTail).register;
		};

		scopeView.start;
	}

	prSubscribeSource {
		var broker = SCVisuals.broker;
		source = broker.subscribeCorrelation(this, server, busIndex, updateFreq, { |corr, bal|
			this.prHandleData(corr, bal);
		});
	}

	prHandleData { |corr, bal|
		if (isClosed.not and: { isFrozen.not }) {
			correlation = corr;
			balance     = bal;
			overlayView.refresh;
		};
	}

	theme_ { |newTheme|
		theme = SCVTheme.get(newTheme);
		view.background = theme.background;
		scopeView.background = theme.panelBackground;
		scopeView.waveColors = [theme.signal];
		if (txtTitle.notNil) {
			txtTitle.stringColor = theme.text;
			txtTitle.font = theme.font(theme.fontSize, bold: true);
		};
		overlayView.refresh;
		this.prHandleResize(view.bounds.width, view.bounds.height);
	}

	fontScale {
		^theme.fontScale;
	}

	fontScale_ { |scale|
		this.theme = theme.withFontScale(scale);
		this.prHandleResize(view.bounds.width, view.bounds.height);
	}

	toggleFreeze {
		if (isFrozen) {
			scopeView.start;
			isFrozen = false;
		} {
			scopeView.stop;
			isFrozen = true;
		};
		overlayView.refresh;
	}

	doOnServerTree {
		if (server.serverRunning) {
			this.prInitScope;
		};
	}

	doOnServerQuit {
		if (scopeBuf.notNil) {
			scopeBuf.free;
			scopeBuf = nil;
		};
		synth = nil;
	}

	stop {
		if (isClosed.not) {
			isClosed = true;
			ServerQuit.remove(this, server);
			ServerTree.remove(this, server);
			SCVisuals.broker.unsubscribe(this, source.key);
			source = nil;
			if (synth.notNil and: { synth.isPlaying }) {
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

SCVVectorscope {
	var <window, <vectorscopeView;

	*new { |server, busIndex=0, theme=\graphite, updateFreq=15|
		^super.new.prInit(server, busIndex, theme, updateFreq);
	}

	prInit { |server, busIndex, theme, updateFreq|
		var s = server ?? { Server.default };
		var w = 340, h = 360;

		window = Window(s.name ++ " Vectorscope — Probe", Rect(340, 240, w, h));
		vectorscopeView = SCVVectorscopeView(s, window, Rect(0, 0, w, h), busIndex, theme, updateFreq);
		window.view.background = vectorscopeView.theme.background;

		window.view.onResize = { |v|
			if (v.notNil and: { v.isClosed.not } and: { v.bounds.notNil } and: { vectorscopeView.notNil } and: { vectorscopeView.isClosed.not }) {
				vectorscopeView.prHandleResize(v.bounds.width, v.bounds.height);
			};
		};
		window.onClose = { vectorscopeView.stop };
		window.front;
	}

	close {
		vectorscopeView.stop;
		window.close;
	}

	theme_ { |aTheme|
		vectorscopeView.theme = aTheme;
		window.view.background = vectorscopeView.theme.background;
	}

	toggleFreeze {
		vectorscopeView.toggleFreeze;
	}

	fontScale {
		^vectorscopeView.fontScale;
	}

	fontScale_ { |aScale|
		vectorscopeView.fontScale = aScale;
		window.view.background = vectorscopeView.theme.background;
	}
}
