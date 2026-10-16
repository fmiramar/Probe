/*
    SCVSpectrum.sc
    SCVisuals — Themed Spectrum Analyzer Wrapper
    Wraps the verified FreqScope FFT pipeline (FFT 2048, hop 0.75, PV_MagSmear, phasor ScopeOut2)
    with theme palettes, calibrated frequency markings (linear/log), dB ruler, and clean lifecycle.
*/

SCVSpectrumView {
	var <server;
	var <view;
	var <scopeView;
	var <gridOverlay;
	var <busIndex;
	var <fftSize;
	var <freqMode;       // 0 = \linear, 1 = \logarithmic
	var <dbRange;        // e.g. 96
	var <rate = 4;
	var <scopeBuf, synth;
	var <theme;
	var <isFrozen = false;
	var <isClosed = false;

	*new { |server, parent, bounds, busIndex=0, fftSize=2048, theme=\graphite, freqMode=\logarithmic, dbRange=96|
		^super.new.prInit(server, parent, bounds, busIndex, fftSize, theme, freqMode, dbRange);
	}

	prInit { |argServer, parent, bounds, argBusIndex, argFFTSize, argTheme, argFreqMode, argDBRange|
		var rect = bounds ?? { Rect(0, 0, 460, 260) };

		server   = argServer ?? { Server.default };
		busIndex = argBusIndex.asInteger;
		fftSize  = argFFTSize.asInteger.max(512);
		theme    = SCVTheme.get(argTheme);
		dbRange  = argDBRange.asFloat.max(24.0);

		freqMode = switch(argFreqMode.asSymbol,
			\linear,      { 0 },
			\logarithmic, { 1 },
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
		var scopeW = w - 48, scopeH = h - 40;
		var scopeRect = Rect(36, 24, scopeW, scopeH);

		// 1. Native ScopeView
		scopeView = ScopeView(view, scopeRect);
		scopeView.server = server;
		scopeView.background = theme.panelBackground;
		scopeView.waveColors = [theme.signal];
		scopeView.style = 1; // Overlay
		scopeView.fill = false;
		scopeView.xZoom = (fftSize * 0.25) / scopeW;
		scopeView.yZoom = 1.0;

		// 2. Transparent Grid & Frequency/dB Overlay
		gridOverlay = UserView(view, Rect(8, 24, w - 16, scopeH));
		gridOverlay.canFocus = false;
		gridOverlay.drawFunc = { |uv|
			var gw = scopeW, gh = scopeH, ox = 28;
			Pen.strokeColor = theme.grid;
			Pen.width = 1;

			// Horizontal dB grid lines
			[0, -12, -24, -36, -48, -60, -72, -96].do { |db|
				var y = db.linlin(0, dbRange.neg, 0, gh);
				Pen.strokeColor = theme.grid;
				Pen.line(Point(ox, y), Point(ox + gw, y));
				Pen.stroke;

				Pen.font = Font.monospace(8);
				Pen.color = theme.subtext;
				Pen.stringRightJustIn(db.asString, Rect(0, y - 5, 24, 10));
			};

			// Vertical Frequency grid lines
			if (freqMode == 1) {
				// Logarithmic
				[100, 250, 500, 1000, 2500, 5000, 10000, 20000].do { |freq|
					var nyq = server.sampleRate * 0.5;
					var normX = (freq / 20.0).log / (nyq / 20.0).log;
					var x = ox + (normX.clip(0, 1) * gw);
					Pen.strokeColor = theme.grid;
					Pen.line(Point(x, 0), Point(x, gh));
					Pen.stroke;

					Pen.font = Font.monospace(8);
					Pen.color = theme.subtext;
					Pen.stringCenteredIn(
						if (freq >= 1000) { (freq / 1000).asInteger.asString ++ "k" } { freq.asString },
						Rect(x - 14, gh - 12, 28, 10)
					);
				};
			} {
				// Linear
				8.do { |i|
					var x = ox + ((i + 1) * (gw / 8));
					var freqK = ((i + 1) * (server.sampleRate * 0.5 / 8000)).round(0.1);
					Pen.strokeColor = theme.grid;
					Pen.line(Point(x, 0), Point(x, gh));
					Pen.stroke;

					Pen.font = Font.monospace(8);
					Pen.color = theme.subtext;
					Pen.stringCenteredIn(freqK.asString ++ "k", Rect(x - 14, gh - 12, 28, 10));
				};
			};
		};

		// 3. Header title
		StaticText(view, Rect(10, 4, 260, 16))
			.string_("SPECTRUM ANALYZER [FFT " ++ fftSize ++ "]")
			.font_(Font.sansSerif(theme.fontSize).boldVariant)
			.stringColor_(theme.text);

		// Keyboard controls
		view.keyDownAction = { |v, char|
			case
				{ char === $m } { this.freqMode = (freqMode + 1) % 2 }
				{ char === $  } { this.toggleFreeze };
		};
	}

	prStartAnalysis {
		var synthDefName;

		if (server.serverRunning.not) { ^this };
		if (server.isLocal.not) {
			"SCVSpectrum: Local server required for shared memory scope.".warn;
			^this;
		};

		if (scopeBuf.isNil) {
			scopeBuf = ScopeBuffer.alloc(server);
			scopeView.bufnum = scopeBuf.index;
		};

		synthDefName = ("scv_spectrum_" ++ freqMode ++ "_" ++ fftSize).asSymbol;

		server.bind {
			var sdef;
			if (freqMode == 0) {
				// Linear mode
				sdef = SynthDef(synthDefName, { |in=0, scopebufnum=0, dbFactor=0.0208|
					var sig, chain, phasor, mul = 0.00285;
					var numSamples, phase;
					var fftbuf = LocalBuf(fftSize, 1);
					phase = 1 - (rate * fftSize.reciprocal);
					numSamples = (BufSamples.ir(fftbuf) - 2) * 0.5;
					sig = In.ar(in, 1);
					chain = FFT(fftbuf, sig, hop: 0.75, wintype: 1);
					chain = PV_MagSmear(chain, 1);
					phasor = LFSaw.ar(rate / BufDur.ir(fftbuf), phase, numSamples, numSamples + 2);
					phasor = phasor.round(2);
					ScopeOut2.ar(((BufRd.ar(1, fftbuf, phasor, 1, 1) * mul).ampdb * dbFactor) + 1, scopebufnum, fftSize / rate);
				});
			} {
				// Logarithmic mode
				sdef = SynthDef(synthDefName, { |in=0, scopebufnum=0, dbFactor=0.0208|
					var sig, chain, phasor, mul = 0.00285;
					var halfSamples, phase;
					var fftbuf = LocalBuf(fftSize, 1);
					phase = 1 - (rate * fftSize.reciprocal);
					halfSamples = BufSamples.ir(fftbuf) * 0.5;
					sig = In.ar(in, 1);
					chain = FFT(fftbuf, sig, hop: 0.75, wintype: 1);
					chain = PV_MagSmear(chain, 1);
					phasor = halfSamples.pow(LFSaw.ar(rate / BufDur.ir(fftbuf), phase, 0.5, 0.5)) * 2;
					phasor = phasor.round(2);
					ScopeOut2.ar(((BufRd.ar(1, fftbuf, phasor, 1, 1) * mul).ampdb * dbFactor) + 1, scopebufnum, fftSize / rate);
				});
			};

			synth = sdef.play(RootNode(server), [
				\in, busIndex,
				\scopebufnum, scopeBuf.index,
				\dbFactor, 2 / dbRange
			], \addToTail);
		};

		scopeView.start;
	}

	theme_ { |newTheme|
		theme = SCVTheme.get(newTheme);
		view.background = theme.background;
		scopeView.background = theme.panelBackground;
		scopeView.waveColors = [theme.signal];
		if (gridOverlay.notNil) { gridOverlay.refresh };
	}

	freqMode_ { |newMode|
		freqMode = switch(newMode.asSymbol,
			\linear,      { 0 },
			\logarithmic, { 1 },
			{ newMode.asInteger.clip(0, 1) }
		);
		if (synth.notNil) {
			server.sendBundle(nil, ['/error', -1], [11, synth.nodeID], ['/error', -2]);
			synth = nil;
		};
		if (server.serverRunning) {
			this.prStartAnalysis;
		};
		if (gridOverlay.notNil) { gridOverlay.refresh };
	}

	dbRange_ { |range|
		dbRange = range.asFloat.max(24.0);
		if (synth.notNil) {
			synth.set(\dbFactor, 2 / dbRange);
		};
		if (gridOverlay.notNil) { gridOverlay.refresh };
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

SCVSpectrum {
	var <window, <spectrumView;

	*new { |server, busIndex=0, fftSize=2048, theme=\graphite, freqMode=\logarithmic, dbRange=96|
		^super.new.prInit(server, busIndex, fftSize, theme, freqMode, dbRange);
	}

	prInit { |server, busIndex, fftSize, theme, freqMode, dbRange|
		var s = server ?? { Server.default };
		var w = 480, h = 280;

		window = Window(s.name ++ " Spectrum — SCVisuals", Rect(250, 300, w, h));
		spectrumView = SCVSpectrumView(s, window, Rect(0, 0, w, h), busIndex, fftSize, theme, freqMode, dbRange);

		window.onClose = { spectrumView.stop };
		window.front;
	}

	close {
		window.close;
	}

	theme_ { |aTheme|
		spectrumView.theme = aTheme;
	}

	freqMode_ { |aMode|
		spectrumView.freqMode = aMode;
	}

	dbRange_ { |range|
		spectrumView.dbRange = range;
	}
}
