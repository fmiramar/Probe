/*
    ProbeTrace.sc
    Probe — Transparent Internal-Signal Trace Manager & Instrumentation Windows
    Provides zero-overhead, pass-through signal inspection with automated GUI lifecycle,
    dual transport (ScopeOut2 for audio waveforms, SendReply for control rates),
    cascading window geometry, and leak-free resource deallocation.
*/

ProbeTrace {
	classvar <activeTraces;
	classvar <tapCounter = 0;
	classvar <cascadeCount = 0;
	classvar <>autoOpen = true;

	*initClass {
		activeTraces = List.new;
		CmdPeriod.add(this);
	}

	*cmdPeriod {
		this.closeAll;
	}

	*nextTapId {
		tapCounter = tapCounter + 1;
		^tapCounter;
	}

	*cascadeBounds { |width=380, height=220|
		var screen = Window.screenBounds;
		var startX = 60, startY = 120;
		var step = 32;
		var maxSteps = 10;
		var x = startX + ((cascadeCount % maxSteps) * step);
		var y = startY + ((cascadeCount % maxSteps) * step);
		cascadeCount = cascadeCount + 1;
		// Wrap around if overflowing screen
		if ((x + width) > screen.width) { x = startX; cascadeCount = 0 };
		if ((y + height) > screen.height) { y = startY; cascadeCount = 0 };
		^Rect(x, y, width, height);
	}

	*wrap { |receiver, label, mode, theme, options|
		var server = Server.default;
		var rate, numChannels, cleanLabel;
		var tapId = this.nextTapId;

		// 1. Determine rate and channel count
		if (receiver.isSequenceableCollection) {
			numChannels = receiver.size;
			rate = receiver[0].rate;
		} {
			numChannels = receiver.numChannels ? 1;
			rate = receiver.rate ? \audio;
		};

		// 2. Default mode selection
		mode = mode ?? {
			if (rate === \audio) { \scope } { \history }
		};
		mode = mode.asSymbol;

		// 3. Fallback label
		cleanLabel = if (label.notNil) { label.asString } {
			if (receiver.isSequenceableCollection) {
				"Array[" ++ numChannels ++ "]"
			} {
				receiver.class.asString
			}
		};

		// 4. Wrap inside SynthDef graph
		^SynthDef.wrap({
			var bus;
			case
				{ mode === \scope or: { mode === \spectrum } } {
					// Route A: Private Bus + ScopeOut2
					bus = Bus.perform(rate, server, numChannels);
					switch(rate,
						\audio,   { Out.ar(bus.index, receiver) },
						\control, { Out.kr(bus.index, receiver) }
					);

					if (autoOpen) {
						{
							if (server.serverRunning) {
								if (mode === \spectrum) {
									this.prOpenSpectrum(server, bus, numChannels, cleanLabel, theme, options);
								} {
									this.prOpenScope(server, bus, numChannels, rate, cleanLabel, theme, options);
								};
							} {
								("Probe.trace: Server '" ++ server.name ++ "' not running. Tap '" ++ cleanLabel ++ "' registered.").postln;
								bus.free;
							};
						}.defer(0.01);
					};
				}

				{ mode === \history } {
					// Route B: SendReply.kr Telemetry
					var updateFreq = (options ? ()).at(\updateFreq) ? 20;
					var trig = Impulse.kr(updateFreq);
					var cmd = '/probe_trace_kr';
					var sig = if (rate === \audio) { A2K.kr(receiver) } { receiver };

					SendReply.kr(trig, cmd, sig, tapId);

					if (autoOpen) {
						{
							if (server.serverRunning) {
								this.prOpenHistory(server, tapId, numChannels, cleanLabel, theme, updateFreq, options);
							} {
								("Probe.trace: Server '" ++ server.name ++ "' not running. Tap '" ++ cleanLabel ++ "' registered.").postln;
							};
						}.defer(0.01);
					};
				}

				{ mode === \meter } {
					// Route C: Private bus + Meter
					bus = Bus.perform(rate, server, numChannels);
					switch(rate,
						\audio,   { Out.ar(bus.index, receiver) },
						\control, { Out.kr(bus.index, receiver) }
					);

					if (autoOpen) {
						{
							if (server.serverRunning) {
								this.prOpenMeter(server, bus, numChannels, cleanLabel, theme, options);
							} {
								("Probe.trace: Server '" ++ server.name ++ "' not running. Tap '" ++ cleanLabel ++ "' registered.").postln;
								bus.free;
							};
						}.defer(0.01);
					};
				};

			// Return receiver unmodified (pass-through)
			receiver;
		});
	}

	*prOpenScope { |server, bus, numChannels, rate, label, theme, options|
		var win = ProbeTraceScopeWindow(server, bus, numChannels, rate, label, theme, options);
		activeTraces.add(win);
		^win;
	}

	*prOpenHistory { |server, tapId, numChannels, label, theme, updateFreq, options|
		var win = ProbeTraceHistoryWindow(server, tapId, numChannels, label, theme, updateFreq, options);
		activeTraces.add(win);
		^win;
	}

	*prOpenMeter { |server, bus, numChannels, label, theme, options|
		var win = ProbeTraceMeterWindow(server, bus, numChannels, label, theme, options);
		activeTraces.add(win);
		^win;
	}

	*prOpenSpectrum { |server, bus, numChannels, label, theme, options|
		var win = ProbeTraceSpectrumWindow(server, bus, numChannels, label, theme, options);
		activeTraces.add(win);
		^win;
	}

	*removeTrace { |traceWin|
		activeTraces.remove(traceWin);
	}

	*closeAll {
		var list = activeTraces.copy;
		list.do { |tr| tr.close };
		activeTraces.clear;
	}
}

ProbeTraceScopeWindow {
	var <window, <scopeView;
	var <server, <bus, <numChannels, <rate, <label, <theme;
	var <scopeBuf, synth;
	var <isClosed = false;

	*new { |server, bus, numChannels, rate, label, theme, options|
		^super.new.prInit(server, bus, numChannels, rate, label, theme, options);
	}

	prInit { |argServer, argBus, argNumChannels, argRate, argLabel, argTheme, options|
		var bounds = ProbeTrace.cascadeBounds(380, 240);
		var th = SCVTheme.get(argTheme);
		var bufSize = (options ? ()).at(\bufSize) ? 4096;
		var cycle = (options ? ()).at(\cycle) ? 1024;
		var titleH = (18 * (th.fontScale ? 1.25)).max(18).round.asInteger;
		var topOffset = titleH + 6;
		var synthDefName;

		server      = argServer;
		bus         = argBus;
		numChannels = argNumChannels;
		rate        = argRate;
		label       = argLabel;
		theme       = th;

		window = Window(label ++ " [" ++ rate ++ ", " ++ numChannels ++ "ch] — Probe Trace", bounds);
		window.view.background = theme.background;

		// 1. Header label
		StaticText(window, Rect(10, 4, bounds.width - 20, titleH))
			.string_(label.asString.toUpper ++ " [" ++ numChannels ++ " CH " ++ rate.asString.toUpper ++ "]")
			.font_(theme.font(theme.fontSize, bold: true))
			.stringColor_(theme.text);

		// 2. ScopeView
		scopeView = ScopeView(window, Rect(10, topOffset, bounds.width - 20, bounds.height - topOffset - 10));
		scopeView.server = server;
		scopeView.background = theme.panelBackground;
		scopeView.fill = false;
		scopeView.style = 1; // overlay
		scopeView.waveColors = Array.fill(numChannels, { |i|
			if (i == 0) { theme.signal } { theme.secondarySignal }
		});

		// 3. Transparent Graticule Overlay
		UserView(window, scopeView.bounds)
			.canFocus_(false)
			.drawFunc_({ |uv|
				var gw = uv.bounds.width, gh = uv.bounds.height;
				Pen.strokeColor = theme.grid.copy.alpha_(0.35);
				Pen.width = 1;
				Pen.line(Point(0, gh * 0.5), Point(gw, gh * 0.5));
				Pen.stroke;
				Pen.line(Point(0, gh * 0.25), Point(gw, gh * 0.25));
				Pen.line(Point(0, gh * 0.75), Point(gw, gh * 0.75));
				Pen.stroke;
				Pen.font = theme.font(8, mono: true);
				Pen.color = theme.subtext;
				Pen.stringAtPoint("+1.0", Point(4, 2));
				Pen.stringAtPoint(" 0.0", Point(4, (gh * 0.5) - 10));
				Pen.stringAtPoint("-1.0", Point(4, gh - 12));
			});

		// 4. Server Scope Buffer & Reader Synth
		if (server.isLocal) {
			scopeBuf = ScopeBuffer.alloc(server, numChannels);
			scopeView.bufnum = scopeBuf.index;

			synthDefName = ("probe_trace_scope_" ++ rate ++ "_" ++ numChannels).asSymbol;
			server.bind {
				var sdef;
				if (rate === \audio) {
					sdef = SynthDef(synthDefName, { |cycleFrames=1024|
						var in = In.ar(bus.index, numChannels);
						ScopeOut2.ar(in, scopeBuf.index, bufSize, cycleFrames);
					});
				} {
					sdef = SynthDef(synthDefName, { |cycleFrames=1024|
						var in = In.kr(bus.index, numChannels);
						ScopeOut2.ar(K2A.ar(in), scopeBuf.index, bufSize, cycleFrames);
					});
				};
				synth = sdef.play(RootNode(server), [\cycleFrames, cycle], \addToTail).register;
			};
			scopeView.start;
		};

		window.onClose = { this.close };
		window.front;
	}

	close {
		if (isClosed.not) {
			isClosed = true;
			if (scopeView.notNil and: { scopeView.isClosed.not }) { scopeView.stop };
			if (synth.notNil) { synth.free; synth = nil };
			if (scopeBuf.notNil) { scopeBuf.free; scopeBuf = nil };
			if (bus.notNil) { bus.free; bus = nil };
			ProbeTrace.removeTrace(this);
			if (window.notNil and: { window.isClosed.not }) { window.close };
		};
	}
}

ProbeTraceHistoryWindow {
	var <window, <userView;
	var <server, <tapId, <numChannels, <label, <theme, <updateFreq;
	var history;          // Array of FloatArray[numChannels]
	var count = 0;
	var maxPoints = 150;
	var oscFunc;
	var <minVal = 0.0, <maxVal = 1.0;
	var <lastVal;
	var <isClosed = false;

	*new { |server, tapId, numChannels, label, theme, updateFreq=20, options|
		^super.new.prInit(server, tapId, numChannels, label, theme, updateFreq, options);
	}

	prInit { |argServer, argTapId, argNumChannels, argLabel, argTheme, argUpdateFreq, options|
		var bounds = ProbeTrace.cascadeBounds(380, 200);
		var th = SCVTheme.get(argTheme);
		var titleH = (18 * (th.fontScale ? 1.25)).max(18).round.asInteger;
		var topOffset = titleH + 6;

		server      = argServer;
		tapId       = argTapId;
		numChannels = argNumChannels;
		label       = argLabel;
		theme       = th;
		updateFreq  = argUpdateFreq;
		lastVal     = FloatArray.fill(numChannels, 0.0);
		history     = Array.fill(maxPoints, { FloatArray.fill(numChannels, 0.0) });

		window = Window(label ++ " [kr, " ++ numChannels ++ "ch] — Probe Trace", bounds);
		window.view.background = theme.background;

		// 1. Header label
		StaticText(window, Rect(10, 4, bounds.width - 20, titleH))
			.string_(label.asString.toUpper ++ " [" ++ numChannels ++ " CH CONTROL TIMELINE]")
			.font_(theme.font(theme.fontSize, bold: true))
			.stringColor_(theme.text);

		// 2. Rolling History Canvas
		userView = UserView(window, Rect(10, topOffset, bounds.width - 20, bounds.height - topOffset - 10));
		userView.background = theme.panelBackground;
		userView.drawFunc = { |uv|
			var w = uv.bounds.width, h = uv.bounds.height;
			var numVisible = count.min(maxPoints);
			var range = (maxVal - minVal);
			if (range.abs < 0.0001) { range = 1.0 };

			// A. Graticules
			Pen.strokeColor = theme.grid.copy.alpha_(0.35);
			Pen.width = 1;
			Pen.line(Point(0, h * 0.5), Point(w, h * 0.5));
			Pen.stroke;

			// B. Rolling traces
			if (numVisible > 1) {
				numChannels.do { |ch|
					var pts = Array.new(numVisible);
					var col = if (ch == 0) { theme.signal } { theme.secondarySignal };

					numVisible.do { |i|
						var ringIdx = (count - numVisible + i) % maxPoints;
						var val = history[ringIdx][ch];
						var x = (i / (maxPoints - 1)) * w;
						var norm = (val - minVal) / range;
						var y = h - (norm.clip(0.0, 1.0) * h);
						pts.add(Point(x, y));
					};

					Pen.strokeColor = col;
					Pen.width = 1.6;
					Pen.moveTo(pts[0]);
					(1..(numVisible - 1)).do { |i| Pen.lineTo(pts[i]) };
					Pen.stroke;
				};
			};

			// C. Numerical HUD Readout
			Pen.font = theme.font(9, mono: true);
			Pen.color = theme.subtext;
			Pen.stringAtPoint("MAX: " ++ maxVal.round(0.01), Point(w - 90, 4));
			Pen.stringAtPoint("MIN: " ++ minVal.round(0.01), Point(w - 90, h - 16));
			Pen.color = theme.signal;
			Pen.stringAtPoint("NOW: " ++ lastVal[0].round(0.01), Point(8, 4));
		};

		// 3. OSC Listener
		oscFunc = OSCFunc({ |msg|
			var vals = msg[3..];
			if (isClosed.not) {
				var idx = count % maxPoints;
				numChannels.do { |ch|
					var v = vals[ch] ? 0.0;
					history[idx][ch] = v;
					lastVal[ch] = v;
					if (count == 0) {
						minVal = v; maxVal = v;
					} {
						minVal = minVal.min(v);
						maxVal = maxVal.max(v);
					};
				};
				count = count + 1;
				{ if (isClosed.not and: { userView.notNil } and: { userView.isClosed.not }) { userView.refresh } }.defer;
			};
		}, '/probe_trace_kr', server.addr, argTemplate: [nil, tapId]);

		window.onClose = { this.close };
		window.front;
	}

	close {
		if (isClosed.not) {
			isClosed = true;
			if (oscFunc.notNil) { oscFunc.free; oscFunc = nil };
			ProbeTrace.removeTrace(this);
			if (window.notNil and: { window.isClosed.not }) { window.close };
		};
	}
}

ProbeTraceMeterWindow {
	var <window, <meter;
	var <server, <bus, <numChannels, <label, <theme;
	var <isClosed = false;

	*new { |server, bus, numChannels, label, theme, options|
		^super.new.prInit(server, bus, numChannels, label, theme, options);
	}

	prInit { |argServer, argBus, argNumChannels, argLabel, argTheme, options|
		var bounds = ProbeTrace.cascadeBounds(200, 260);
		server = argServer;
		bus = argBus;
		numChannels = argNumChannels;
		label = argLabel;
		theme = SCVTheme.get(argTheme);

		window = Window(label ++ " [Meter] — Probe Trace", bounds);
		meter = SCVMeterView(server, window, 0@0, 0, numChannels, theme, \compact);
		window.view.background = theme.background;
		window.onClose = { this.close };
		window.front;
	}

	close {
		if (isClosed.not) {
			isClosed = true;
			if (meter.notNil) { meter.stop };
			if (bus.notNil) { bus.free; bus = nil };
			ProbeTrace.removeTrace(this);
			if (window.notNil and: { window.isClosed.not }) { window.close };
		};
	}
}

ProbeTraceSpectrumWindow {
	var <window, <spectrum;
	var <server, <bus, <numChannels, <label, <theme;
	var <isClosed = false;

	*new { |server, bus, numChannels, label, theme, options|
		^super.new.prInit(server, bus, numChannels, label, theme, options);
	}

	prInit { |argServer, argBus, argNumChannels, argLabel, argTheme, options|
		var bounds = ProbeTrace.cascadeBounds(420, 260);
		server = argServer;
		bus = argBus;
		numChannels = argNumChannels;
		label = argLabel;
		theme = SCVTheme.get(argTheme);

		window = Window(label ++ " [Spectrum] — Probe Trace", bounds);
		spectrum = SCVSpectrumView(server, window, Rect(0, 0, bounds.width, bounds.height), bus.index, 2048, theme);
		window.view.background = theme.background;
		window.onClose = { this.close };
		window.front;
	}

	close {
		if (isClosed.not) {
			isClosed = true;
			if (spectrum.notNil) { spectrum.stop };
			if (bus.notNil) { bus.free; bus = nil };
			ProbeTrace.removeTrace(this);
			if (window.notNil and: { window.isClosed.not }) { window.close };
		};
	}
}
