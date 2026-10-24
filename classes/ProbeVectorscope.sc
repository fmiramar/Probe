/*
    ProbeVectorscope.sc
    Probe — Real-Time Stereo Vectorscope & Phase Correlation Meter
    Dual-Engine Lissajous X/Y plotting:
    - \smooth (default): Antialiased hardware vector renderer with configurable lineWidth (default 2.0).
    - \native: High-speed native ScopeView (QcScopeShm) blit.
    Includes 45-degree Mid/Side polar graticule overlay, real-time phase correlation meter [-1.0, +1.0],
    stereo balance meter, and broker integration.
*/

PB_VectorscopeView {
	var <server;
	var <view;
	var <scopeView, <overlayView;
	var <busIndex, <updateFreq;
	var <scopeBuf, <recordBuf, <synth;
	var <source;
	var <theme;
	var <correlation = 1.0;
	var <balance = 0.0;
	var <isFrozen = false;
	var <isClosed = false;
	var <engine = \smooth;      // \smooth (antialiased Pen vector) or \native (ScopeView)
	var <lineWidth = 2.0;       // line width for smooth vector
	var xData, yData, oscFunc, poller;
	var txtTitle, engineBtn;
	var scopeSize, scopeX, scopeY;

	*new { |server, parent, bounds, busIndex=0, theme=\graphite, updateFreq=15, engine=\smooth, lineWidth=2.0|
		^super.new.prInit(server, parent, bounds, busIndex, theme, updateFreq, engine, lineWidth);
	}

	prInit { |argServer, parent, bounds, argBusIndex, argTheme, argUpdateFreq, argEngine, argLineWidth|
		var rect = bounds ?? { Rect(0, 0, 320, 320) };

		server     = argServer ?? { Server.default };
		busIndex   = argBusIndex.asInteger;
		theme      = ProbeTheme.get(argTheme);
		updateFreq = argUpdateFreq.asInteger.clip(5, 60);
		engine     = if (argEngine.asSymbol === \native) { \native } { \smooth };
		lineWidth  = (argLineWidth ? 2.0).asFloat.clip(0.5, 10.0);

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

		// 1. Native ScopeView for high-speed hardware Lissajous (visible only in \native mode)
		scopeView = ScopeView(view, Rect(scopeX, scopeY, scopeSize, scopeSize));
		scopeView.server = server;
		scopeView.style = 2; // X/Y Lissajous mode
		scopeView.canFocus = false;
		scopeView.background = theme.panelBackground;
		scopeView.waveColors = [theme.signal];
		scopeView.visible = (engine === \native);

		// 2. Overlay UserView for Polar Grid, Lissajous Vector & Phase Correlation
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

			// Scope background panel when in smooth mode
			if (engine === \smooth) {
				Pen.fillColor = theme.panelBackground;
				Pen.fillRect(Rect(curScopeX, curScopeY, curScopeSize, curScopeSize));
			};

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

			// Smooth Hardware Vector Antialiased Lissajous
			if (engine === \smooth and: { xData.notNil and: { yData.notNil } }) {
				var n = xData.size.min(yData.size);
				if (n > 1) {
					Pen.strokeColor = theme.signal;
					Pen.width = lineWidth;
					Pen.capStyle = 1; // Round cap
					Pen.joinStyle = 1; // Round join
					Pen.beginPath;
					Pen.moveTo(Point(
						(cx + (xData[0] * radius)).clip(curScopeX, curScopeX + curScopeSize),
						(cy - (yData[0] * radius)).clip(curScopeY, curScopeY + curScopeSize)
					));
					(1 .. n - 1).do { |i|
						Pen.lineTo(Point(
							(cx + (xData[i] * radius)).clip(curScopeX, curScopeX + curScopeSize),
							(cy - (yData[i] * radius)).clip(curScopeY, curScopeY + curScopeSize)
						));
					};
					Pen.stroke;
				};
			};

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
		txtTitle = StaticText(view, Rect(10, 4, (w - 85).max(100), titleH))
			.string_("VECTORSCOPE [LISSAJOUS / M-S]")
			.font_(theme.font(theme.fontSize, bold: true))
			.stringColor_(theme.text);

		// Engine button (Smooth vs Native)
		engineBtn = Button(view, Rect((w - 72).max(110), 3, 62, titleH))
			.states_([
				["Smooth", theme.background, theme.signal],
				["Native", theme.text, theme.panelBackground]
			])
			.font_(theme.font(8, bold: true))
			.value_(if (engine === \smooth) { 0 } { 1 })
			.action_({ |b|
				this.engine = if (b.value == 0) { \smooth } { \native };
			});

		// Keyboard controls
		view.keyDownAction = { |v, char|
			case
				{ char === $  } { this.toggleFreeze }
				{ char === $e or: { char === $E } } {
					this.engine = if (engine === \smooth) { \native } { \smooth };
				}
				{ char === $+ or: { char === $= } } { this.lineWidth = (lineWidth + 0.5).clip(0.5, 10.0) }
				{ char === $- or: { char === $_ } } { this.lineWidth = (lineWidth - 0.5).clip(0.5, 10.0) };
		};
	}

	prHandleResize { |w, h|
		var fs = theme.fontScale ? 1.25;
		var titleH = (18 * fs).max(18).round.asInteger;
		var topOffset = titleH + 6;
		if (isClosed.not and: { view.notNil } and: { view.isClosed.not } and: { scopeView.notNil } and: { scopeView.isClosed.not } and: { overlayView.notNil } and: { overlayView.isClosed.not }) {
			view.bounds = Rect(view.bounds.left, view.bounds.top, w, h);
			if (txtTitle.notNil) {
				txtTitle.bounds = Rect(10, 4, (w - 85).max(100), titleH);
			};
			if (engineBtn.notNil) {
				engineBtn.bounds = Rect((w - 72).max(110), 3, 62, titleH);
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

		if (recordBuf.isNil) {
			recordBuf = Buffer.alloc(server, 1024, 2);
		};

		if (synth.notNil) {
			synth.free;
			synth = nil;
		};

		synthDefName = ("probe_vecscope_" ++ busIndex).asSymbol;

		server.bind {
			var sdef = SynthDef(synthDefName, { |cycleFrames=1024|
				var in = In.ar(busIndex, 2);
				ScopeOut2.ar(in, scopeBuf.index, 4096, cycleFrames);
				RecordBuf.ar(in, recordBuf.bufnum, loop: 1);
			});
			synth = sdef.play(RootNode(server), [\cycleFrames, 1024], \addToTail).register;
		};

		scopeView.start;
		if (isFrozen) {
			synth.run(false);
		};
		if (engine === \smooth) {
			this.prStartPoller;
		};
	}

	prStartPoller {
		if (poller.isNil and: { isClosed.not }) {
			if (oscFunc.isNil and: { recordBuf.notNil }) {
				oscFunc = OSCFunc({ |msg|
					var count = msg[3].asInteger;
					var raw = msg[4 .. 3 + count];
					var n = count div: 2;
					if (n > 0) {
						var xArr = Array.newClear(n);
						var yArr = Array.newClear(n);
						n.do { |i|
							var idx = i * 2;
							xArr[i] = raw[idx];
							yArr[i] = raw[idx + 1];
						};
						xData = xArr;
						yData = yArr;
						{
							if (isClosed.not and: { overlayView.notNil } and: { overlayView.isClosed.not }) {
								overlayView.refresh;
							};
						}.defer;
					};
				}, '/b_setn', server.addr, argTemplate: [recordBuf.bufnum]).fix;
			};

			poller = Routine({
				0.05.wait;
				while { isClosed.not and: { engine === \smooth } } {
					if (isFrozen.not and: { recordBuf.notNil } and: { server.serverRunning }) {
						server.sendMsg('/b_getn', recordBuf.bufnum, 0, 1024 * 2);
					};
					(1.0 / updateFreq).wait;
				};
				poller = nil;
			}).play(AppClock);
		};
	}

	prStopPoller {
		if (poller.notNil) {
			poller.stop;
			poller = nil;
		};
		if (oscFunc.notNil) {
			oscFunc.free;
			oscFunc = nil;
		};
	}

	engine_ { |newEngine|
		engine = if (newEngine.asSymbol === \native) { \native } { \smooth };
		if (scopeView.notNil and: { scopeView.isClosed.not }) {
			scopeView.visible = (engine === \native);
		};
		if (engineBtn.notNil and: { engineBtn.isClosed.not }) {
			engineBtn.value = if (engine === \smooth) { 0 } { 1 };
		};
		if (engine === \smooth) {
			this.prStartPoller;
			if (isFrozen and: { recordBuf.notNil } and: { server.serverRunning }) {
				server.sendMsg('/b_getn', recordBuf.bufnum, 0, 1024 * 2);
			};
		} {
			this.prStopPoller;
			if (scopeView.notNil and: { scopeView.isClosed.not }) {
				scopeView.refresh;
			};
		};
		if (overlayView.notNil and: { overlayView.isClosed.not }) {
			overlayView.refresh;
		};
	}

	lineWidth_ { |width|
		lineWidth = width.asFloat.clip(0.5, 10.0);
		if (overlayView.notNil and: { overlayView.isClosed.not }) {
			overlayView.refresh;
		};
	}

	prSubscribeSource {
		var broker = Probe.broker;
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
		theme = ProbeTheme.get(newTheme);
		view.background = theme.background;
		scopeView.background = theme.panelBackground;
		scopeView.waveColors = [theme.signal];
		if (txtTitle.notNil) {
			txtTitle.stringColor = theme.text;
			txtTitle.font = theme.font(theme.fontSize, bold: true);
		};
		if (engineBtn.notNil) {
			engineBtn.states = [
				["Smooth", theme.background, theme.signal],
				["Native", theme.text, theme.panelBackground]
			];
			engineBtn.value = if (engine === \smooth) { 0 } { 1 };
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
			isFrozen = false;
			if (synth.notNil and: { synth.isPlaying }) {
				synth.run(true);
			};
		} {
			isFrozen = true;
			if (synth.notNil and: { synth.isPlaying }) {
				synth.run(false);
			};
		};
		overlayView.refresh;
	}

	doOnServerTree {
		if (server.serverRunning) {
			this.prInitScope;
		};
	}

	doOnServerQuit {
		this.prStopPoller;
		if (scopeBuf.notNil) {
			scopeBuf.free;
			scopeBuf = nil;
		};
		if (recordBuf.notNil) {
			recordBuf.free;
			recordBuf = nil;
		};
		synth = nil;
	}

	stop {
		if (isClosed.not) {
			isClosed = true;
			ServerQuit.remove(this, server);
			ServerTree.remove(this, server);
			Probe.broker.unsubscribe(this, source.key);
			source = nil;
			this.prStopPoller;
			if (synth.notNil and: { synth.isPlaying }) {
				synth.free;
				synth = nil;
			};
			if (scopeBuf.notNil) {
				scopeBuf.free;
				scopeBuf = nil;
			};
			if (recordBuf.notNil) {
				recordBuf.free;
				recordBuf = nil;
			};
		};
	}
}

ProbeVectorscope {
	var <window, <vectorscopeView;

	*new { |server, busIndex=0, theme=\graphite, updateFreq=15, engine=\smooth, lineWidth=2.0|
		^super.new.prInit(server, busIndex, theme, updateFreq, engine, lineWidth);
	}

	*view { |parent, bounds, server, busIndex=0, theme=\graphite, updateFreq=15, engine=\smooth, lineWidth=2.0|
		^PB_VectorscopeView(server ?? { Server.default }, parent, bounds, busIndex, theme, updateFreq, engine, lineWidth);
	}

	prInit { |server, busIndex, theme, updateFreq, engine, lineWidth|
		var s = server ?? { Server.default };
		var w = 340, h = 360;

		window = Window(s.name ++ " Vectorscope — Probe", Rect(340, 240, w, h));
		vectorscopeView = PB_VectorscopeView(s, window, Rect(0, 0, w, h), busIndex, theme, updateFreq, engine, lineWidth);
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

	engine {
		^vectorscopeView.engine;
	}

	engine_ { |aEngine|
		vectorscopeView.engine = aEngine;
	}

	lineWidth {
		^vectorscopeView.lineWidth;
	}

	lineWidth_ { |aWidth|
		vectorscopeView.lineWidth = aWidth;
	}

	theme_ { |aTheme|
		vectorscopeView.theme = aTheme;
		window.view.background = vectorscopeView.theme.background;
	}

	toggleFreeze {
		vectorscopeView.toggleFreeze;
	}

	isFrozen {
		^vectorscopeView.isFrozen;
	}

	fontScale {
		^vectorscopeView.fontScale;
	}

	fontScale_ { |aScale|
		vectorscopeView.fontScale = aScale;
		window.view.background = vectorscopeView.theme.background;
	}
}
