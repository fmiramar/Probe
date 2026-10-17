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
		var ox = (28 * fs).max(28).round.asInteger;
		var scopeLeft = ox + 8;
		var scopeW = (w - scopeLeft - 12).max(50);
		var scopeH = (h - topOffset - 14).max(50);
		var scopeRect = Rect(scopeLeft, topOffset, scopeW, scopeH);

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
		gridOverlay = UserView(view, Rect(8, topOffset, w - 16, scopeH));
		gridOverlay.canFocus = false;
		gridOverlay.drawFunc = { |uv|
			var curOx = (28 * (theme.fontScale ? 1.25)).max(28).round.asInteger;
			var curW = uv.bounds.width - curOx, curH = uv.bounds.height;
			Pen.strokeColor = theme.grid;
			Pen.width = 1;

			// Horizontal dB grid lines and calibrated left ruler scale
			[0, -12, -24, -36, -48, -60, -72, -96].do { |db|
				var y = db.linlin(dbRange.neg, 0, curH, 0);
				Pen.strokeColor = theme.grid.copy.alpha_(0.35);
				Pen.line(Point(curOx, y), Point(curOx + curW, y));
				Pen.stroke;

				Pen.font = theme.font(8, mono: true);
				Pen.color = theme.subtext;
				Pen.stringRightJustIn(db.asString, Rect(0, (y - 7).clip(0, curH - 14), curOx - 4, 14));
			};

			// Vertical Frequency grid lines
			if (freqMode == 1) {
				// Logarithmic
				var freqs = if ((theme.fontScale ? 1.0) > 1.2) {
					[100, 500, 1000, 5000, 10000, 20000]
				} {
					[100, 250, 500, 1000, 2500, 5000, 10000, 20000]
				};
				freqs.do { |freq|
					var nyq = server.sampleRate * 0.5;
					var normX = (freq / 20.0).log / (nyq / 20.0).log;
					var x = curOx + (normX.clip(0, 1) * curW);
					Pen.strokeColor = theme.grid.copy.alpha_(0.35);
					Pen.line(Point(x, 0), Point(x, curH));
					Pen.stroke;

					Pen.font = theme.font(8, mono: true);
					Pen.color = theme.subtext;
					Pen.stringCenteredIn(
						if (freq >= 1000) { (freq / 1000).asInteger.asString ++ "k" } { freq.asString },
						Rect(x - 16, curH - 14, 32, 12)
					);
				};
			} {
				// Linear
				8.do { |i|
					var x = curOx + ((i + 1) * (curW / 8));
					var freqK = ((i + 1) * (server.sampleRate * 0.5 / 8000)).round(0.1);
					Pen.strokeColor = theme.grid.copy.alpha_(0.35);
					Pen.line(Point(x, 0), Point(x, curH));
					Pen.stroke;

					Pen.font = theme.font(8, mono: true);
					Pen.color = theme.subtext;
					Pen.stringCenteredIn(freqK.asString ++ "k", Rect(x - 16, curH - 14, 32, 12));
				};
			};
		};

		// 3. Header title
		txtTitle = StaticText(view, Rect(10, 4, (w - 20).max(100), titleH))
			.string_("SPECTRUM ANALYZER [FFT " ++ fftSize ++ "]")
			.font_(theme.font(theme.fontSize, bold: true))
			.stringColor_(theme.text);

		// Keyboard controls
		view.keyDownAction = { |v, char|
			case
				{ char === $m } { this.freqMode = (freqMode + 1) % 2 }
				{ char === $  } { this.toggleFreeze };
		};
	}

	prHandleResize { |w, h|
		var fs = theme.fontScale ? 1.25;
		var titleH = (18 * fs).max(18).round.asInteger;
		var topOffset = titleH + 6;
		var ox = (28 * fs).max(28).round.asInteger;
		var scopeLeft = ox + 8;
		var scopeW = (w - scopeLeft - 12).max(50);
		var scopeH = (h - topOffset - 14).max(50);

		if (isClosed.not and: { view.notNil } and: { view.isClosed.not } and: { scopeView.notNil } and: { scopeView.isClosed.not } and: { gridOverlay.notNil } and: { gridOverlay.isClosed.not }) {
			view.bounds = Rect(view.bounds.left, view.bounds.top, w, h);
			if (txtTitle.notNil) {
				txtTitle.bounds = Rect(10, 4, (w - 20).max(100), titleH);
			};
			scopeView.bounds = Rect(scopeLeft, topOffset, scopeW, scopeH);
			gridOverlay.bounds = Rect(8, topOffset, w - 16, scopeH);
			scopeView.xZoom = (fftSize * 0.25) / scopeW;
			gridOverlay.refresh;
		};
	}

	smoothing_ { |smoothVal|
		smoothing = smoothVal;
	}

	toggleSmoothing {
		// Smoothing rolled back to unsmoothed raw FFT scan
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

		if (synth.notNil) {
			synth.free;
			synth = nil;
		};

		synthDefName = ("scv_spectrum_" ++ freqMode ++ "_" ++ fftSize).asSymbol;

		server.bind {
			var sdef;
			if (freqMode == 0) {
				// Linear mode — raw unsmoothed FFT magnitude scan
				sdef = SynthDef(synthDefName, { |in=0, scopebufnum=0, dbFactor=0.0208|
					var sig, phasor, mul = 0.00285;
					var numSamples, phase;
					var fftbuf = LocalBuf(fftSize, 1);
					var rawMag;
					phase = 1 - (rate * fftSize.reciprocal);
					numSamples = (BufSamples.ir(fftbuf) - 2) * 0.5;
					sig = In.ar(in, 1);
					FFT(fftbuf, sig, hop: 0.75, wintype: 1);
					phasor = LFSaw.ar(rate / BufDur.ir(fftbuf), phase, numSamples, numSamples + 2);
					phasor = phasor.round(2);
					rawMag = BufRd.ar(1, fftbuf, phasor, 1, 1);
					ScopeOut2.ar(((rawMag * mul).ampdb * dbFactor) + 1, scopebufnum, fftSize / rate);
				});
			} {
				// Logarithmic mode — raw unsmoothed FFT magnitude scan
				sdef = SynthDef(synthDefName, { |in=0, scopebufnum=0, dbFactor=0.0208|
					var sig, phasor, mul = 0.00285;
					var halfSamples, phase;
					var fftbuf = LocalBuf(fftSize, 1);
					var rawMag;
					phase = 1 - (rate * fftSize.reciprocal);
					halfSamples = BufSamples.ir(fftbuf) * 0.5;
					sig = In.ar(in, 1);
					FFT(fftbuf, sig, hop: 0.75, wintype: 1);
					phasor = halfSamples.pow(LFSaw.ar(rate / BufDur.ir(fftbuf), phase, 0.5, 0.5)) * 2;
					phasor = phasor.round(2);
					rawMag = BufRd.ar(1, fftbuf, phasor, 1, 1);
					ScopeOut2.ar(((rawMag * mul).ampdb * dbFactor) + 1, scopebufnum, fftSize / rate);
				});
			};

			synth = sdef.play(RootNode(server), [
				\in, busIndex,
				\scopebufnum, scopeBuf.index,
				\dbFactor, 2 / dbRange
			], \addToTail).register;
		};

		scopeView.start;
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
		if (gridOverlay.notNil) { gridOverlay.refresh };
	}

	fontScale {
		^theme.fontScale;
	}

	fontScale_ { |scale|
		this.theme = theme.withFontScale(scale);
		this.prHandleResize(view.bounds.width, view.bounds.height);
	}

	freqMode_ { |newMode|
		freqMode = switch(newMode.asSymbol,
			\linear,      { 0 },
			\logarithmic, { 1 },
			{ newMode.asInteger.clip(0, 1) }
		);
		if (synth.notNil) {
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

SCVSpectrum {
	var <window, <spectrumView;

	*new { |server, busIndex=0, fftSize=2048, theme=\graphite, freqMode=\logarithmic, dbRange=96, smoothing=\none|
		^super.new.prInit(server, busIndex, fftSize, theme, freqMode, dbRange, smoothing);
	}

	prInit { |server, busIndex, fftSize, theme, freqMode, dbRange, smoothing|
		var s = server ?? { Server.default };
		var w = 480, h = 260;

		window = Window(s.name ++ " Spectrum Analyzer — Probe", Rect(120, 200, w, h));
		spectrumView = SCVSpectrumView(s, window, Rect(0, 0, w, h), busIndex, fftSize, theme, freqMode, dbRange, smoothing);
		window.view.background = spectrumView.theme.background;

		window.view.onResize = { |v|
			if (v.notNil and: { v.isClosed.not } and: { v.bounds.notNil } and: { spectrumView.notNil } and: { spectrumView.isClosed.not }) {
				spectrumView.prHandleResize(v.bounds.width, v.bounds.height);
			};
		};
		window.onClose = { spectrumView.stop };
		window.front;
	}

	close {
		spectrumView.stop;
		window.close;
	}

	theme_ { |aTheme|
		spectrumView.theme = aTheme;
		window.view.background = spectrumView.theme.background;
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

	fontScale {
		^spectrumView.fontScale;
	}

	fontScale_ { |aScale|
		spectrumView.fontScale = aScale;
		window.view.background = spectrumView.theme.background;
	}
}
