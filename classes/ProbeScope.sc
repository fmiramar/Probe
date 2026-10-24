/*
    ProbeScope.sc
    Probe — Themed Oscilloscope Wrapper
    Dual-Engine Oscilloscope:
    - \smooth (default): Hardware antialiased vector renderer using Pen with configurable lineWidth and translucent ribbon fill.
    - \native: Native C++ ScopeView (QcScopeShm) shared-memory blit.
    Supports multichannel tracks, overlay, and X/Y modes, edge triggering, dynamic resizing, and clean lifecycle.
*/

PB_ScopeView {
	var <server;
	var <view;
	var <scopeView;
	var <gridOverlay;
	var <busIndex, <numChannels, <rate;
	var <bufSize, <cycle;
	var <scopeBuf, <recordBuf, <synth;
	var <theme;
	var <scopeStyle;     // 0 = \tracks, 1 = \overlay, 2 = \xy
	var <xZoom = 1.0, <yZoom = 1.0;
	var <isFrozen = false;
	var <isClosed = false;
	var <triggerMode = \free;     // \free, \rising, \falling
	var <triggerThreshold = 0.0;
	var <showToolbar = false;
	var <engine = \smooth;        // \smooth or \native
	var <lineWidth = 1.8;         // vector line thickness
	var <fill = false;            // ribbon area fill
	var channelData, oscFunc, poller;
	var txtTitle, styleMenu, timeMenu, trigBtn, engineBtn, fillBtn, resetBtn, freezeBtn;

	*new { |server, parent, bounds, busIndex=0, numChannels=2, theme=\graphite, rate=\audio, style=\tracks, bufSize=4096, showToolbar=false, engine=\smooth, lineWidth=1.8, fill=false|
		^super.new.prInit(server, parent, bounds, busIndex, numChannels, theme, rate, style, bufSize, showToolbar, engine, lineWidth, fill);
	}

	prInit { |argServer, parent, bounds, argBusIndex, argNumChannels, argTheme, argRate, argStyle, argBufSize, argShowToolbar, argEngine, argLineWidth, argFill|
		var rect = bounds ?? { Rect(0, 0, 380, 240) };

		server      = argServer ?? { Server.default };
		busIndex    = argBusIndex.asInteger;
		numChannels = argNumChannels.asInteger.max(1);
		theme       = ProbeTheme.get(argTheme);
		rate        = argRate.asSymbol;
		bufSize     = argBufSize.asInteger.max(128);
		cycle       = 1024;
		showToolbar = argShowToolbar ? false;
		engine      = if (argEngine.asSymbol === \native) { \native } { \smooth };
		lineWidth   = (argLineWidth ? 1.8).asFloat.clip(0.5, 10.0);
		fill        = argFill ? false;

		scopeStyle = switch(argStyle.asSymbol,
			\tracks,  { 0 },
			\overlay, { 1 },
			\xy,      { 2 },
			{ 0 }
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
			var curX = 10;
			var gap = 4;

			txtTitle = StaticText(view, Rect(curX, 4, 100, btnH))
				.string_("SCOPE [" ++ numChannels ++ " CH]")
				.font_(theme.font(9, bold: true))
				.stringColor_(theme.text);
			curX = curX + 100 + gap;

			styleMenu = PopUpMenu(view, Rect(curX, 4, 70, btnH))
				.items_(["Tracks", "Overlay", "X/Y"])
				.font_(theme.font(8))
				.value_(scopeStyle)
				.action_({ |m| this.scopeStyle = m.value });
			curX = curX + 70 + gap;

			timeMenu = PopUpMenu(view, Rect(curX, 4, 55, btnH))
				.items_(["0.25x", "0.5x", "1.0x", "2.0x", "4.0x"])
				.font_(theme.font(8))
				.value_(2)
				.action_({ |m|
					var scales = [0.25, 0.5, 1.0, 2.0, 4.0];
					this.xZoom = scales[m.value];
				});
			curX = curX + 55 + gap;

			trigBtn = Button(view, Rect(curX, 4, 75, btnH))
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
			curX = curX + 75 + gap;

			engineBtn = Button(view, Rect(curX, 4, 58, btnH))
				.states_([
					["Smooth", theme.background, theme.signal],
					["Native", theme.text, theme.panelBackground]
				])
				.font_(theme.font(8, bold: true))
				.value_(if (engine === \smooth) { 0 } { 1 })
				.action_({ |b|
					this.engine = if (b.value == 0) { \smooth } { \native };
				});
			curX = curX + 58 + gap;

			fillBtn = Button(view, Rect(curX, 4, 42, btnH))
				.states_([
					["Fill", theme.text, theme.panelBackground],
					["Fill", theme.background, theme.signal]
				])
				.font_(theme.font(8, bold: true))
				.value_(if (fill) { 1 } { 0 })
				.action_({ |b|
					this.fill = (b.value == 1);
				});
			curX = curX + 42 + gap;

			resetBtn = Button(view, Rect(curX, 4, 45, btnH))
				.states_([["Reset", theme.text, theme.panelBackground]])
				.font_(theme.font(8))
				.action_({ this.resetZoom });
			curX = curX + 45 + gap;

			freezeBtn = Button(view, Rect(curX, 4, 55, btnH))
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

		// 2. Native ScopeView (visible only in \native mode)
		scopeView = ScopeView(view, scopeRect);
		scopeView.server = server;
		scopeView.background = theme.panelBackground;
		scopeView.fill = fill;
		scopeView.style = scopeStyle;
		scopeView.xZoom = xZoom;
		scopeView.yZoom = yZoom;
		scopeView.visible = (engine === \native);
		this.prUpdateWaveColors;

		// 3. Transparent Calibrated Grid & Time/Level Overlay (and vector renderer in \smooth mode)
		gridOverlay = UserView(view, scopeRect);
		gridOverlay.canFocus = false;
		gridOverlay.drawFunc = { |uv|
			var gw = uv.bounds.width, gh = uv.bounds.height;
			var sRate = server.sampleRate ? 48000.0;
			var totalMs = ((cycle / sRate) * 1000.0) / xZoom.max(0.01);

			// A. Panel Background when in smooth mode
			if (engine === \smooth) {
				Pen.fillColor = theme.panelBackground;
				Pen.fillRect(Rect(0, 0, gw, gh));
			};

			// B. Horizontal Center Zero Line (Subtle accent)
			Pen.strokeColor = theme.subtext.copy.alpha_(0.5);
			Pen.width = 1.2;
			Pen.line(Point(0, gh * 0.5), Point(gw, gh * 0.5));
			Pen.stroke;

			// C. Horizontal +0.5 / -0.5 guidelines
			Pen.strokeColor = theme.grid.copy.alpha_(0.30);
			Pen.width = 1;
			Pen.line(Point(0, gh * 0.25), Point(gw, gh * 0.25));
			Pen.line(Point(0, gh * 0.75), Point(gw, gh * 0.75));
			Pen.stroke;

			// D. Vertical calibrated time subdivisions (4 subdivisions with ms markings)
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

			// E. Calibrated Vertical Amplitude markings (left ruler)
			Pen.font = theme.font(8, mono: true);
			Pen.color = theme.subtext;
			Pen.stringAtPoint("+1.0 (0 dB)", Point(4, 2));
			Pen.stringAtPoint("+0.5 (-6 dB)", Point(4, (gh * 0.25) - 6));
			Pen.stringAtPoint(" 0.0", Point(4, (gh * 0.5) - 6));
			Pen.stringAtPoint("-0.5 (-6 dB)", Point(4, (gh * 0.75) - 6));
			Pen.stringAtPoint("-1.0 (0 dB)", Point(4, gh - 14));

			// F. Smooth Hardware Antialiased Waveforms
			if (engine === \smooth and: { channelData.notNil }) {
				switch(scopeStyle,
					0, { // \tracks mode: vertically separated waveforms
						var trackH = gh / numChannels;
						numChannels.do { |c|
							var pts = channelData[c];
							if (pts.notNil and: { pts.size > 1 }) {
								var totalPts = pts.size;
								var visCount = (totalPts / xZoom).round.asInteger.clip(16, totalPts);
								var dx = gw / (visCount - 1);
								var baseY = (c + 0.5) * trackH;
								var ampScale = (trackH * 0.45) * yZoom;
								var col = if (c == 0) {
									theme.signal
								} {
									if (c == 1) {
										theme.secondarySignal
									} {
										theme.signal.blend(theme.warning, c / (numChannels - 1))
									}
								};

								// Track divider line & baseline
								if (c > 0) {
									Pen.strokeColor = theme.grid.copy.alpha_(0.25);
									Pen.width = 1.0;
									Pen.line(Point(0, c * trackH), Point(gw, c * trackH));
									Pen.stroke;
								};
								Pen.strokeColor = theme.grid.copy.alpha_(0.15);
								Pen.width = 0.8;
								Pen.line(Point(0, baseY), Point(gw, baseY));
								Pen.stroke;

								// Optional translucent ribbon fill
								if (fill) {
									Pen.fillColor = col.copy.alpha_(0.22);
									Pen.beginPath;
									Pen.moveTo(Point(0, baseY));
									visCount.do { |i|
										var px = i * dx;
										var py = (baseY - (pts[i] * ampScale)).clip(c * trackH, (c + 1) * trackH);
										Pen.lineTo(Point(px, py));
									};
									Pen.lineTo(Point(gw, baseY));
									Pen.fill;
								};

								// Antialiased waveform line
								Pen.strokeColor = col;
								Pen.width = lineWidth;
								Pen.capStyle = 1;
								Pen.joinStyle = 1;
								Pen.beginPath;
								Pen.moveTo(Point(0, (baseY - (pts[0] * ampScale)).clip(c * trackH, (c + 1) * trackH)));
								(1 .. visCount - 1).do { |i|
									var px = i * dx;
									var py = (baseY - (pts[i] * ampScale)).clip(c * trackH, (c + 1) * trackH);
									Pen.lineTo(Point(px, py));
								};
								Pen.stroke;
							};
						};
					},
					1, { // \overlay mode: all channels centered at 0.0
						var baseY = gh * 0.5;
						var ampScale = (gh * 0.45) * yZoom;
						numChannels.do { |c|
							var pts = channelData[c];
							if (pts.notNil and: { pts.size > 1 }) {
								var totalPts = pts.size;
								var visCount = (totalPts / xZoom).round.asInteger.clip(16, totalPts);
								var dx = gw / (visCount - 1);
								var col = if (c == 0) {
									theme.signal
								} {
									if (c == 1) {
										theme.secondarySignal
									} {
										theme.signal.blend(theme.warning, c / (numChannels - 1))
									}
								};

								// Optional translucent ribbon fill
								if (fill) {
									Pen.fillColor = col.copy.alpha_(0.16);
									Pen.beginPath;
									Pen.moveTo(Point(0, baseY));
									visCount.do { |i|
										var px = i * dx;
										var py = (baseY - (pts[i] * ampScale)).clip(0, gh);
										Pen.lineTo(Point(px, py));
									};
									Pen.lineTo(Point(gw, baseY));
									Pen.fill;
								};

								// Antialiased waveform line
								Pen.strokeColor = col;
								Pen.width = lineWidth;
								Pen.capStyle = 1;
								Pen.joinStyle = 1;
								Pen.beginPath;
								Pen.moveTo(Point(0, (baseY - (pts[0] * ampScale)).clip(0, gh)));
								(1 .. visCount - 1).do { |i|
									var px = i * dx;
									var py = (baseY - (pts[i] * ampScale)).clip(0, gh);
									Pen.lineTo(Point(px, py));
								};
								Pen.stroke;
							};
						};
					},
					2, { // \xy mode: Lissajous
						if (numChannels >= 2 and: { channelData[0].notNil } and: { channelData[1].notNil }) {
							var pts0 = channelData[0];
							var pts1 = channelData[1];
							var n = pts0.size.min(pts1.size);
							if (n > 1) {
								var cx = gw * 0.5;
								var cy = gh * 0.5;
								var scaleX = (gw * 0.45) * xZoom;
								var scaleY = (gh * 0.45) * yZoom;
								Pen.strokeColor = theme.signal;
								Pen.width = lineWidth;
								Pen.capStyle = 1;
								Pen.joinStyle = 1;
								Pen.beginPath;
								Pen.moveTo(Point(
									(cx + (pts0[0] * scaleX)).clip(0, gw),
									(cy - (pts1[0] * scaleY)).clip(0, gh)
								));
								(1 .. n - 1).do { |i|
									Pen.lineTo(Point(
										(cx + (pts0[i] * scaleX)).clip(0, gw),
										(cy - (pts1[i] * scaleY)).clip(0, gh)
									));
								};
								Pen.stroke;
							};
						};
					}
				);
			};

			// G. Trigger State Badge (top right)
			if (triggerMode !== \free) {
				var badgeStr = if (triggerMode === \rising) { "TRIG: RISE [▲]" } { "TRIG: FALL [▼]" };
				Pen.font = theme.font(8, bold: true);
				Pen.color = if (triggerMode === \rising) { theme.signal } { theme.warning };
				Pen.stringRightJustIn(badgeStr, Rect(gw - 100, 3, 96, 12));
			};

			// H. Channel chips legend (bottom right)
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
				{ char === $e or: { char === $E } } {
					this.engine = if (engine === \smooth) { \native } { \smooth };
				}
				{ char === $f or: { char === $F } } {
					this.fill = fill.not;
				}
				{ char === $w } { this.lineWidth = (lineWidth + 0.2).clip(0.5, 10.0) }
				{ char === $W } { this.lineWidth = (lineWidth - 0.2).clip(0.5, 10.0) }
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
				var gap = 4;
				if (txtTitle.notNil) {
					txtTitle.bounds = Rect(curX, 4, 100, btnH);
					curX = curX + 100 + gap;
				};
				if (styleMenu.notNil) {
					styleMenu.bounds = Rect(curX, 4, 70, btnH);
					curX = curX + 70 + gap;
				};
				if (timeMenu.notNil) {
					timeMenu.bounds = Rect(curX, 4, 55, btnH);
					curX = curX + 55 + gap;
				};
				if (trigBtn.notNil) {
					trigBtn.bounds = Rect(curX, 4, 75, btnH);
					curX = curX + 75 + gap;
				};
				if (engineBtn.notNil) {
					engineBtn.bounds = Rect(curX, 4, 58, btnH);
					curX = curX + 58 + gap;
				};
				if (fillBtn.notNil) {
					fillBtn.bounds = Rect(curX, 4, 42, btnH);
					curX = curX + 42 + gap;
				};
				if (resetBtn.notNil) {
					resetBtn.bounds = Rect(curX, 4, 45, btnH);
					curX = curX + 45 + gap;
				};
				if (freezeBtn.notNil) {
					freezeBtn.bounds = Rect(curX, 4, 55, btnH);
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

		if (recordBuf.isNil) {
			recordBuf = Buffer.alloc(server, 1024, numChannels);
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
						RecordBuf.ar(in.asArray, recordBuf.bufnum, loop: 1);
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
						RecordBuf.ar(rd, recordBuf.bufnum, loop: 1);
					});
				};
			} {
				sdef = SynthDef(synthDefName, { |cycleFrames=1024|
					var in = In.kr(busIndex, numChannels);
					var inA = K2A.ar(in);
					ScopeOut2.ar(inA, scopeBuf.index, bufSize, cycleFrames);
					RecordBuf.ar(inA, recordBuf.bufnum, loop: 1);
				});
			};

			synth = sdef.play(RootNode(server), [\cycleFrames, cycle, \thresh, triggerThreshold], \addToTail).register;
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
					var n = count div: numChannels;
					if (n > 0) {
						var chans = Array.fill(numChannels, { Array.newClear(n) });
						n.do { |frame|
							var base = frame * numChannels;
							numChannels.do { |c|
								chans[c][frame] = raw[base + c];
							};
						};
						channelData = chans;
						{
							if (isClosed.not and: { gridOverlay.notNil } and: { gridOverlay.isClosed.not }) {
								gridOverlay.refresh;
							};
						}.defer;
					};
				}, '/b_setn', server.addr, argTemplate: [recordBuf.bufnum]).fix;
			};

			poller = Routine({
				0.05.wait;
				while { isClosed.not and: { engine === \smooth } } {
					if (isFrozen.not and: { recordBuf.notNil } and: { server.serverRunning }) {
						server.sendMsg('/b_getn', recordBuf.bufnum, 0, 1024 * numChannels);
					};
					0.033.wait; // ~30 fps
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
				server.sendMsg('/b_getn', recordBuf.bufnum, 0, 1024 * numChannels);
			};
		} {
			this.prStopPoller;
			if (scopeView.notNil and: { scopeView.isClosed.not }) {
				scopeView.refresh;
			};
		};
		if (gridOverlay.notNil and: { gridOverlay.isClosed.not }) {
			gridOverlay.refresh;
		};
	}

	lineWidth_ { |width|
		lineWidth = width.asFloat.clip(0.5, 10.0);
		if (gridOverlay.notNil and: { gridOverlay.isClosed.not }) {
			gridOverlay.refresh;
		};
	}

	fill_ { |aFill|
		fill = aFill.asBoolean;
		if (scopeView.notNil and: { scopeView.isClosed.not }) {
			scopeView.fill = fill;
		};
		if (fillBtn.notNil and: { fillBtn.isClosed.not }) {
			fillBtn.value = if (fill) { 1 } { 0 };
		};
		if (gridOverlay.notNil and: { gridOverlay.isClosed.not }) {
			gridOverlay.refresh;
		};
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
		if (engineBtn.notNil) {
			engineBtn.states = [
				["Smooth", theme.background, theme.signal],
				["Native", theme.text, theme.panelBackground]
			];
			engineBtn.value = if (engine === \smooth) { 0 } { 1 };
		};
		if (fillBtn.notNil) {
			fillBtn.states = [
				["Fill", theme.text, theme.panelBackground],
				["Fill", theme.background, theme.signal]
			];
			fillBtn.value = if (fill) { 1 } { 0 };
		};
		if (styleMenu.notNil) { styleMenu.font = theme.font(8) };
		if (timeMenu.notNil) { timeMenu.font = theme.font(8) };
		if (trigBtn.notNil) {
			trigBtn.states = [
				["Trig: Free", theme.text, theme.panelBackground],
				["Trig: Rise", theme.background, theme.signal],
				["Trig: Fall", theme.background, theme.warning]
			];
			trigBtn.value = switch(triggerMode, \free, { 0 }, \rising, { 1 }, \falling, { 2 }, { 0 });
		};
		if (resetBtn.notNil) {
			resetBtn.states = [["Reset", theme.text, theme.panelBackground]];
		};
		if (freezeBtn.notNil) {
			freezeBtn.states = [
				["Freeze", theme.text, theme.panelBackground],
				["RUNNING", theme.background, theme.signal]
			];
			freezeBtn.value = if (isFrozen) { 1 } { 0 };
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
		if (gridOverlay.notNil and: { gridOverlay.isClosed.not }) {
			gridOverlay.refresh;
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
		if (gridOverlay.notNil and: { gridOverlay.isClosed.not }) {
			gridOverlay.refresh;
		};
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
			isFrozen = false;
			if (synth.notNil and: { synth.isPlaying }) {
				synth.run(true);
			};
			if (freezeBtn.notNil and: { freezeBtn.isClosed.not }) { freezeBtn.value = 0 };
		} {
			isFrozen = true;
			if (synth.notNil and: { synth.isPlaying }) {
				synth.run(false);
			};
			if (freezeBtn.notNil and: { freezeBtn.isClosed.not }) { freezeBtn.value = 1 };
		};
		if (gridOverlay.notNil and: { gridOverlay.isClosed.not }) {
			gridOverlay.refresh;
		};
	}

	doOnServerQuit {
		this.prStopPoller;
		{ if (scopeView.notNil and: { scopeView.isClosed.not }) { scopeView.stop }; }.defer;
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
			this.prStopPoller;
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
			if (recordBuf.notNil) {
				recordBuf.free;
				recordBuf = nil;
			};
		};
	}
}

ProbeScope {
	var <window, <scopeView;

	*new { |server, busIndex=0, numChannels=2, theme=\graphite, rate=\audio, style=\tracks, bufSize=4096, engine=\smooth, lineWidth=1.8, fill=false|
		^super.new.prInit(server, busIndex, numChannels, theme, rate, style, bufSize, engine, lineWidth, fill);
	}

	*view { |parent, bounds, server, busIndex=0, numChannels=2, theme=\graphite, rate=\audio, style=\tracks, bufSize=4096, showToolbar=false, engine=\smooth, lineWidth=1.8, fill=false|
		^PB_ScopeView(server ?? { Server.default }, parent, bounds, busIndex, numChannels, theme, rate, style, bufSize, showToolbar, engine, lineWidth, fill);
	}

	prInit { |server, busIndex, numChannels, theme, rate, style, bufSize, engine, lineWidth, fill|
		var s = server ?? { Server.default };
		var w = 580, h = 300;

		window = Window(s.name ++ " Scope — Probe", Rect(120, 200, w, h));
		scopeView = PB_ScopeView(s, window, Rect(0, 0, w, h), busIndex, numChannels, theme, rate, style, bufSize, showToolbar: true, engine: engine, lineWidth: lineWidth, fill: fill);
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

	engine {
		^scopeView.engine;
	}

	engine_ { |aEngine|
		scopeView.engine = aEngine;
	}

	lineWidth {
		^scopeView.lineWidth;
	}

	lineWidth_ { |aWidth|
		scopeView.lineWidth = aWidth;
	}

	fill {
		^scopeView.fill;
	}

	fill_ { |aFill|
		scopeView.fill = aFill;
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

	isFrozen {
		^scopeView.isFrozen;
	}

	fontScale {
		^scopeView.fontScale;
	}

	fontScale_ { |aScale|
		scopeView.fontScale = aScale;
		window.view.background = scopeView.theme.background;
	}
}
