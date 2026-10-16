/*
    SCVSpectrum.sc
    SCVisuals — Themed Spectrum Analyzer Wrapper
    Wraps the verified FreqScope FFT pipeline (FFT 2048, hop 0.75, PV_MagSmear, phasor ScopeOut2)
    with theme palettes, calibrated frequency markings (linear/log), calibrated vertical dB ruler
    with full horizontal grid lines, multi-level smoothing controls, dynamic window resizing, and clean lifecycle.
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
	var <smoothing = \none; // \none, \low, \med, \high, or integer bins
	var <rate = 4;
	var <scopeBuf, synth;
	var <theme;
	var <isFrozen = false;
	var <isClosed = false;
	var txtTitle, smoothMenu;

	*new { |server, parent, bounds, busIndex=0, fftSize=2048, theme=\graphite, freqMode=\logarithmic, dbRange=96, smoothing=\none|
		^super.new.prInit(server, parent, bounds, busIndex, fftSize, theme, freqMode, dbRange, smoothing);
	}

	prInit { |argServer, parent, bounds, argBusIndex, argFFTSize, argTheme, argFreqMode, argDBRange, argSmoothing|
		var rect = bounds ?? { Rect(0, 0, 460, 260) };

		server    = argServer ?? { Server.default };
		busIndex  = argBusIndex.asInteger;
		fftSize   = argFFTSize.asInteger.max(512);
		theme     = SCVTheme.get(argTheme);
		dbRange   = argDBRange.asFloat.max(24.0);
		smoothing = argSmoothing ?? { \none };

		freqMode = switch(argFreqMode.asSymbol,
			\linear,      { 0 },
			\logarithmic, { 1 },
			{ 1 }
		);

		view = CompositeView(parent, rect);
		view.background = theme.background;
		view.onClose = { this.stop };
		view.onResize = { |v| this.prHandleResize(v.bounds.width, v.bounds.height) };

		this.prBuildUI(rect.width, rect.height);
		this.prStartAnalysis;

		ServerQuit.add(this, server);
		ServerTree.add(this, server);
	}

	prBuildUI { |w, h|
		var scopeW = (w - 48).max(50), scopeH = (h - 40).max(50);
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
			var curW = uv.bounds.width - 28, curH = uv.bounds.height, ox = 28;
			Pen.strokeColor = theme.grid;
			Pen.width = 1;

			// Horizontal dB grid lines and calibrated left ruler scale
			[0, -12, -24, -36, -48, -60, -72, -96].do { |db|
				var y = db.linlin(dbRange.neg, 0, curH, 0);
				Pen.strokeColor = theme.grid.copy.alpha_(0.35);
				Pen.line(Point(ox, y), Point(ox + curW, y));
				Pen.stroke;

				Pen.font = Font.monospace(8);
				Pen.color = theme.subtext;
				Pen.stringRightJustIn(db.asString, Rect(0, (y - 6).clip(0, curH - 12), 24, 12));
			};

			// Vertical Frequency grid lines
			if (freqMode == 1) {
				// Logarithmic
				[100, 250, 500, 1000, 2500, 5000, 10000, 20000].do { |freq|
					var nyq = server.sampleRate * 0.5;
					var normX = (freq / 20.0).log / (nyq / 20.0).log;
					var x = ox + (normX.clip(0, 1) * curW);
					Pen.strokeColor = theme.grid.copy.alpha_(0.35);
					Pen.line(Point(x, 0), Point(x, curH));
					Pen.stroke;

					Pen.font = Font.monospace(8);
					Pen.color = theme.subtext;
					Pen.stringCenteredIn(
						if (freq >= 1000) { (freq / 1000).asInteger.asString ++ "k" } { freq.asString },
						Rect(x - 14, curH - 12, 28, 10)
					);
				};
			} {
				// Linear
				8.do { |i|
					var x = ox + ((i + 1) * (curW / 8));
					var freqK = ((i + 1) * (server.sampleRate * 0.5 / 8000)).round(0.1);
					Pen.strokeColor = theme.grid.copy.alpha_(0.35);
					Pen.line(Point(x, 0), Point(x, curH));
					Pen.stroke;

					Pen.font = Font.monospace(8);
					Pen.color = theme.subtext;
					Pen.stringCenteredIn(freqK.asString ++ "k", Rect(x - 14, curH - 12, 28, 10));
				};
			};
		};

		// 3. Header title
		txtTitle = StaticText(view, Rect(10, 4, 250, 16))
			.string_("SPECTRUM ANALYZER [FFT " ++ fftSize ++ "]")
			.font_(Font.sansSerif(theme.fontSize).boldVariant)
			.stringColor_(theme.text);

		// 4. Smoothing Dropdown Menu
		smoothMenu = PopUpMenu(view, Rect(w - 95, 3, 85, 18))
			.items_(["Smooth: Off", "Smooth: Lo", "Smooth: Med", "Smooth: Hi"])
			.font_(Font.sansSerif(8))
			.value_(this.prSmoothingToIndex(smoothing))
			.action_({ |m|
				var modes = [\none, \low, \med, \high];
				this.smoothing = modes[m.value];
			});

		// Keyboard controls
		view.keyDownAction = { |v, char|
			case
				{ char === $m } { this.freqMode = (freqMode + 1) % 2 }
				{ char === $s } { this.toggleSmoothing }
				{ char === $  } { this.toggleFreeze };
		};
	}

	prHandleResize { |w, h|
		var scopeW = (w - 48).max(50), scopeH = (h - 40).max(50);
		if (isClosed.not and: { scopeView.notNil } and: { gridOverlay.notNil }) {
			scopeView.bounds = Rect(36, 24, scopeW, scopeH);
			gridOverlay.bounds = Rect(8, 24, w - 16, scopeH);
			scopeView.xZoom = (fftSize * 0.25) / scopeW;
			if (smoothMenu.notNil) {
				smoothMenu.bounds = Rect(w - 95, 3, 85, 18);
			};
			gridOverlay.refresh;
		};
	}

	prSmoothingToBins { |smoothVal|
		^switch(smoothVal.asSymbol,
			\none, { 1 },
			\off,  { 1 },
			\low,  { 3 },
			\med,  { 7 },
			\high, { 15 },
			{ smoothVal.asInteger.clip(1, 32) }
		);
	}

	prSmoothingToIndex { |smoothVal|
		^switch(smoothVal.asSymbol,
			\none, { 0 },
			\off,  { 0 },
			\low,  { 1 },
			\med,  { 2 },
			\high, { 3 },
			{ 0 }
		);
	}

	smoothing_ { |smoothVal|
		smoothing = smoothVal;
		if (synth.notNil and: { synth.isPlaying }) {
			synth.set(\smooth, this.prSmoothingToBins(smoothing));
		};
		if (smoothMenu.notNil) {
			smoothMenu.value = this.prSmoothingToIndex(smoothing);
		};
	}

	toggleSmoothing {
		var nextMode = switch(smoothing.asSymbol,
			\none, { \low },
			\low,  { \med },
			\med,  { \high },
			\high, { \none },
			{ \none }
		);
		this.smoothing = nextMode;
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

		if (synth.notNil and: { synth.isPlaying }) {
			synth.free;
			synth = nil;
		};

		synthDefName = ("scv_spectrum_" ++ freqMode ++ "_" ++ fftSize).asSymbol;

		server.bind {
			var sdef;
			if (freqMode == 0) {
				// Linear mode
				sdef = SynthDef(synthDefName, { |in=0, scopebufnum=0, dbFactor=0.0208, smooth=1|
					var sig, chain, phasor, mul = 0.00285;
					var numSamples, phase;
					var fftbuf = LocalBuf(fftSize, 1);
					phase = 1 - (rate * fftSize.reciprocal);
					numSamples = (BufSamples.ir(fftbuf) - 2) * 0.5;
					sig = In.ar(in, 1);
					chain = FFT(fftbuf, sig, hop: 0.75, wintype: 1);
					chain = PV_MagSmear(chain, smooth);
					phasor = LFSaw.ar(rate / BufDur.ir(fftbuf), phase, numSamples, numSamples + 2);
					phasor = phasor.round(2);
					ScopeOut2.ar(((BufRd.ar(1, fftbuf, phasor, 1, 1) * mul).ampdb * dbFactor) + 1, scopebufnum, fftSize / rate);
				});
			} {
				// Logarithmic mode
				sdef = SynthDef(synthDefName, { |in=0, scopebufnum=0, dbFactor=0.0208, smooth=1|
					var sig, chain, phasor, mul = 0.00285;
					var halfSamples, phase;
					var fftbuf = LocalBuf(fftSize, 1);
					phase = 1 - (rate * fftSize.reciprocal);
					halfSamples = BufSamples.ir(fftbuf) * 0.5;
					sig = In.ar(in, 1);
					chain = FFT(fftbuf, sig, hop: 0.75, wintype: 1);
					chain = PV_MagSmear(chain, smooth);
					phasor = halfSamples.pow(LFSaw.ar(rate / BufDur.ir(fftbuf), phase, 0.5, 0.5)) * 2;
					phasor = phasor.round(2);
					ScopeOut2.ar(((BufRd.ar(1, fftbuf, phasor, 1, 1) * mul).ampdb * dbFactor) + 1, scopebufnum, fftSize / rate);
				});
			};

			synth = sdef.play(RootNode(server), [
				\in, busIndex,
				\scopebufnum, scopeBuf.index,
				\dbFactor, 2 / dbRange,
				\smooth, this.prSmoothingToBins(smoothing)
			], \addToTail);
		};

		scopeView.start;
	}

	theme_ { |newTheme|
		theme = SCVTheme.get(newTheme);
		view.background = theme.background;
		scopeView.background = theme.panelBackground;
		scopeView.waveColors = [theme.signal];
		if (txtTitle.notNil) { txtTitle.stringColor = theme.text };
		if (gridOverlay.notNil) { gridOverlay.refresh };
	}

	freqMode_ { |newMode|
		freqMode = switch(newMode.asSymbol,
			\linear,      { 0 },
			\logarithmic, { 1 },
			{ newMode.asInteger.clip(0, 1) }
		);
		if (synth.notNil and: { synth.isPlaying }) {
			synth.free;
			synth = nil;
		};
		if (server.serverRunning) {
			this.prStartAnalysis;
		};
		if (gridOverlay.notNil) { gridOverlay.refresh };
	}

	dbRange_ { |range|
		dbRange = range.asFloat.max(24.0);
		if (synth.notNil and: { synth.isPlaying }) {
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
		if (server.serverRunning) {
			this.prStartAnalysis;
		};
	}

	stop {
		if (isClosed.not) {
			isClosed = true;
			ServerQuit.remove(this, server);
			ServerTree.remove(this, server);
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

SCVSpectrum {
	var <window, <spectrumView;

	*new { |server, busIndex=0, fftSize=2048, theme=\graphite, freqMode=\logarithmic, dbRange=96, smoothing=\none|
		^super.new.prInit(server, busIndex, fftSize, theme, freqMode, dbRange, smoothing);
	}

	prInit { |server, busIndex, fftSize, theme, freqMode, dbRange, smoothing|
		var s = server ?? { Server.default };
		var w = 480, h = 260;

		window = Window(s.name ++ " Spectrum Analyzer — SCVisuals", Rect(120, 200, w, h));
		spectrumView = SCVSpectrumView(s, window, Rect(0, 0, w, h), busIndex, fftSize, theme, freqMode, dbRange, smoothing);

		window.view.onResize = { |v| spectrumView.prHandleResize(v.bounds.width, v.bounds.height) };
		window.onClose = { spectrumView.stop };
		window.front;
	}

	close {
		spectrumView.stop;
		window.close;
	}

	theme_ { |aTheme|
		spectrumView.theme = aTheme;
	}

	freqMode_ { |aMode|
		spectrumView.freqMode = aMode;
	}

	dbRange_ { |aRange|
		spectrumView.dbRange = aRange;
	}

	smoothing_ { |aSmoothing|
		spectrumView.smoothing = aSmoothing;
	}

	toggleFreeze {
		spectrumView.toggleFreeze;
	}
}
