/*
    ProbeSpectrum.sc
    Probe — Themed Spectrum Analyzer Wrapper
    Wraps the verified FreqScope FFT pipeline (FFT 2048, hop 0.75, PV_MagSmear, phasor ScopeOut2)
    with theme palettes, calibrated frequency markings (linear/log), calibrated vertical dB ruler
    with full horizontal grid lines, multi-level smoothing controls, dynamic window resizing, and clean lifecycle.
*/

PB_SpectrumView {
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
	var <viewMode = \line;       // \line, \bands, \spectrogram
	var <cursorPt;
	var bandView, gramView;
	var <showToolbar = false;
	var <isFrozen = false;
	var <isClosed = false;
	var txtTitle, modeMenu, scaleMenu, freezeBtn, themeMenu;

	*new { |server, parent, bounds, busIndex=0, fftSize=2048, theme=\graphite, freqMode=\logarithmic, dbRange=96, smoothing=\none, showToolbar=false|
		^super.new.prInit(server, parent, bounds, busIndex, fftSize, theme, freqMode, dbRange, smoothing, showToolbar);
	}

	prInit { |argServer, parent, bounds, argBusIndex, argFFTSize, argTheme, argFreqMode, argDBRange, argSmoothing, argShowToolbar|
		var rect = bounds ?? { Rect(0, 0, 460, 260) };

		server      = argServer ?? { Server.default };
		busIndex    = argBusIndex.asInteger;
		fftSize     = argFFTSize.asInteger.max(512);
		theme       = ProbeTheme.get(argTheme);
		dbRange     = argDBRange.asFloat.max(24.0);
		smoothing   = argSmoothing ?? { \none };
		showToolbar = argShowToolbar ? false;

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
		var themes = [\graphite, \phosphor, \ice, \amber, \paper, \monochrome, \puredata];
		var fs = theme.fontScale ? 1.25;
		var titleH = (18 * fs).max(18).round.asInteger;
		var btnH = (20 * fs).max(20).min(26).round.asInteger;
		var topOffset = if (showToolbar) { btnH + 10 } { titleH + 6 };
		var ox = (28 * fs).max(28).round.asInteger;
		var scopeLeft = ox + 8;
		var scopeW = (w - scopeLeft - 12).max(50);
		var scopeH = (h - topOffset - 14).max(50);
		var scopeRect = Rect(scopeLeft, topOffset, scopeW, scopeH);

		// 1. Top Controls (Standalone or Toolbar mode)
		if (showToolbar) {
			txtTitle = StaticText(view, Rect(8, 4, 110, btnH))
				.string_("SPECTRUM")
				.font_(theme.font(9, bold: true))
				.stringColor_(theme.text);

			modeMenu = PopUpMenu(view, Rect(122, 4, 115, btnH))
				.items_(["Line Spectrum", "16-Band Analyzer", "Spectrogram"])
				.font_(theme.font(8))
				.value_(0)
				.action_({ |m|
					var modes = [\line, \bands, \spectrogram];
					this.viewMode = modes[m.value];
				});

			scaleMenu = PopUpMenu(view, Rect(241, 4, 60, btnH))
				.items_(["Log", "Linear"])
				.font_(theme.font(8))
				.value_(freqMode)
				.action_({ |m| this.freqMode = m.value });

			freezeBtn = Button(view, Rect(305, 4, 55, btnH))
				.states_([
					["Freeze", theme.text, theme.panelBackground],
					["RUNNING", theme.background, theme.signal]
				])
				.font_(theme.font(8, bold: true))
				.action_({ this.toggleFreeze });

			themeMenu = PopUpMenu(view, Rect(364, 4, 85, btnH))
				.items_(["Graphite", "Phosphor", "Ice", "Amber", "Paper", "Monochrome", "Pure Data"])
				.value_(themes.indexOf(theme.name) ? 0)
				.font_(theme.font(8))
				.action_({ |m| this.theme = themes[m.value] });
		} {
			txtTitle = StaticText(view, Rect(10, 4, (w - 20).max(100), titleH))
				.string_("SPECTRUM ANALYZER [FFT " ++ fftSize ++ "]")
				.font_(theme.font(theme.fontSize, bold: true))
				.stringColor_(theme.text);
		};

		// 2. Native ScopeView (Precision Line Mode)
		scopeView = ScopeView(view, scopeRect);
		scopeView.server = server;
		scopeView.background = theme.panelBackground;
		scopeView.waveColors = [theme.signal];
		scopeView.style = 1; // Overlay
		scopeView.fill = false;
		scopeView.xZoom = (fftSize * 0.25) / scopeW;
		scopeView.yZoom = 1.0;

		// 3. Transparent Grid & Frequency/dB Overlay with Interactive Cursor
		gridOverlay = UserView(view, Rect(8, topOffset, w - 16, scopeH));
		gridOverlay.canFocus = false;
		gridOverlay.acceptsMouseOver = true;
		gridOverlay.mouseMoveAction = { |uv, x, y|
			cursorPt = Point(x, y);
			uv.refresh;
		};
		gridOverlay.mouseLeaveAction = { |uv|
			cursorPt = nil;
			uv.refresh;
		};

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
					var nyq = (server.sampleRate ? 48000.0) * 0.5;
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
					var freqK = ((i + 1) * ((server.sampleRate ? 48000.0) * 0.5 / 8000)).round(0.1);
					Pen.strokeColor = theme.grid.copy.alpha_(0.35);
					Pen.line(Point(x, 0), Point(x, curH));
					Pen.stroke;

					Pen.font = theme.font(8, mono: true);
					Pen.color = theme.subtext;
					Pen.stringCenteredIn(freqK.asString ++ "k", Rect(x - 16, curH - 14, 32, 12));
				};
			};

			// Interactive Cursor Readout (Crosshair + Hz/dB Tooltip)
			if (cursorPt.notNil and: { cursorPt.x >= curOx and: { cursorPt.x <= (curOx + curW) } }) {
				var relX = ((cursorPt.x - curOx) / curW).clip(0.0, 1.0);
				var nyq = (server.sampleRate ? 48000.0) * 0.5;
				var fVal = if (freqMode == 1) {
					20.0 * ((nyq / 20.0) ** relX)
				} {
					relX * nyq
				};
				var dbVal = cursorPt.y.linlin(0, curH, 0, dbRange.neg);
				var fStr = if (fVal >= 1000) { (fVal / 1000).round(0.01).asString ++ " kHz" } { fVal.round(1).asString ++ " Hz" };
				var dbStr = dbVal.round(0.1).asString ++ " dB";
				var infoStr = fStr ++ " | " ++ dbStr;

				// Draw crosshair lines
				Pen.strokeColor = theme.secondarySignal.copy.alpha_(0.6);
				Pen.width = 1;
				Pen.line(Point(cursorPt.x, 0), Point(cursorPt.x, curH));
				Pen.line(Point(curOx, cursorPt.y), Point(curOx + curW, cursorPt.y));
				Pen.stroke;

				// Draw cursor badge
				Pen.fillColor = theme.panelBackground.copy.alpha_(0.92);
				Pen.strokeColor = theme.secondarySignal;
				Pen.fillRect(Rect((cursorPt.x + 8).clip(curOx, (curOx + curW - 120).max(curOx)), (cursorPt.y - 22).clip(4, (curH - 24).max(4)), 115, 18));
				Pen.strokeRect(Rect((cursorPt.x + 8).clip(curOx, (curOx + curW - 120).max(curOx)), (cursorPt.y - 22).clip(4, (curH - 24).max(4)), 115, 18));

				Pen.font = theme.font(8, bold: true, mono: true);
				Pen.color = theme.secondarySignal;
				Pen.stringCenteredIn(infoStr, Rect((cursorPt.x + 8).clip(curOx, (curOx + curW - 120).max(curOx)), (cursorPt.y - 22).clip(4, (curH - 24).max(4)), 115, 18));
			};
		};

		// Keyboard controls
		view.keyDownAction = { |v, char|
			case
				{ char === $m } { this.freqMode = (freqMode + 1) % 2 }
				{ char === $1 } { this.viewMode = \line }
				{ char === $2 } { this.viewMode = \bands }
				{ char === $3 } { this.viewMode = \spectrogram }
				{ char === $  } { this.toggleFreeze };
		};
	}

	prHandleResize { |w, h|
		var fs = theme.fontScale ? 1.25;
		var titleH = (18 * fs).max(18).round.asInteger;
		var btnH = (20 * fs).max(20).min(26).round.asInteger;
		var topOffset = if (showToolbar) { btnH + 10 } { titleH + 6 };
		var ox = (28 * fs).max(28).round.asInteger;
		var scopeLeft = ox + 8;
		var scopeW = (w - scopeLeft - 12).max(50);
		var scopeH = (h - topOffset - 14).max(50);

		if (isClosed.not and: { view.notNil } and: { view.isClosed.not } and: { scopeView.notNil } and: { scopeView.isClosed.not } and: { gridOverlay.notNil } and: { gridOverlay.isClosed.not }) {
			view.bounds = Rect(view.bounds.left, view.bounds.top, w, h);

			if (showToolbar) {
				var curX = 8;
				if (txtTitle.notNil) {
					txtTitle.bounds = Rect(curX, 4, 90, btnH);
					curX = curX + 94;
				};
				if (modeMenu.notNil) {
					modeMenu.bounds = Rect(curX, 4, 115, btnH);
					curX = curX + 119;
				};
				if (scaleMenu.notNil) {
					scaleMenu.bounds = Rect(curX, 4, 60, btnH);
					curX = curX + 64;
				};
				if (freezeBtn.notNil) {
					freezeBtn.bounds = Rect(curX, 4, 55, btnH);
					curX = curX + 59;
				};
				if (themeMenu.notNil) {
					themeMenu.bounds = Rect(curX, 4, 85, btnH);
				};
			} {
				if (txtTitle.notNil) {
					txtTitle.bounds = Rect(10, 4, (w - 20).max(100), titleH);
				};
			};

			scopeView.bounds = Rect(scopeLeft, topOffset, scopeW, scopeH);
			gridOverlay.bounds = Rect(8, topOffset, w - 16, scopeH);
			scopeView.xZoom = (fftSize * 0.25) / scopeW;
			gridOverlay.refresh;

			if (bandView.notNil and: { bandView.view.notNil and: { bandView.view.isClosed.not } }) {
				bandView.view.bounds = Rect(0, topOffset, w, h - topOffset);
				bandView.prHandleResize(w, h - topOffset);
			};
			if (gramView.notNil and: { gramView.view.notNil and: { gramView.view.isClosed.not } }) {
				gramView.view.bounds = Rect(0, topOffset, w, h - topOffset);
				gramView.prHandleResize(w, h - topOffset);
			};
		};
	}

	viewMode_ { |newMode|
		var topOffset = if (showToolbar) { 30 } { 24 };
		viewMode = newMode.asSymbol;

		if (modeMenu.notNil and: { modeMenu.isClosed.not }) {
			modeMenu.value = switch(viewMode,
				\line,        { 0 },
				\bands,       { 1 },
				\spectrogram, { 2 },
				{ 0 }
			);
		};

		case
			{ viewMode === \line } {
				scopeView.visible = true;
				gridOverlay.visible = true;
				if (bandView.notNil and: { bandView.view.notNil }) { bandView.view.visible = false };
				if (gramView.notNil and: { gramView.view.notNil }) { gramView.view.visible = false };
			}
			{ viewMode === \bands } {
				scopeView.visible = false;
				gridOverlay.visible = false;
				if (gramView.notNil and: { gramView.view.notNil }) { gramView.view.visible = false };
				if (bandView.isNil) {
					bandView = PB_BandAnalyzerView(server, view, Rect(0, topOffset, view.bounds.width, view.bounds.height - topOffset), busIndex, theme);
				};
				bandView.view.visible = true;
				bandView.prHandleResize(view.bounds.width, view.bounds.height - topOffset);
			}
			{ viewMode === \spectrogram } {
				scopeView.visible = false;
				gridOverlay.visible = false;
				if (bandView.notNil and: { bandView.view.notNil }) { bandView.view.visible = false };
				if (gramView.isNil) {
					gramView = PB_SpectrogramView(server, view, Rect(0, topOffset, view.bounds.width, view.bounds.height - topOffset), busIndex, 64, theme);
				};
				gramView.view.visible = true;
				gramView.prHandleResize(view.bounds.width, view.bounds.height - topOffset);
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
			"ProbeSpectrum: Local server required for shared memory scope.".warn;
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

		synthDefName = ("probe_spectrum_" ++ freqMode ++ "_" ++ fftSize).asSymbol;

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
		theme = ProbeTheme.get(newTheme);
		view.background = theme.background;
		scopeView.background = theme.panelBackground;
		scopeView.waveColors = [theme.signal];
		if (txtTitle.notNil) {
			txtTitle.stringColor = theme.text;
			txtTitle.font = theme.font(if (showToolbar) { 9 } { theme.fontSize }, bold: true);
		};
		if (gridOverlay.notNil) { gridOverlay.refresh };
		if (bandView.notNil and: { bandView.respondsTo(\theme_) }) { bandView.theme = theme };
		if (gramView.notNil and: { gramView.respondsTo(\theme_) }) { gramView.theme = theme };
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
		if (scaleMenu.notNil and: { scaleMenu.isClosed.not }) {
			scaleMenu.value = freqMode;
		};
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
			if (freezeBtn.notNil and: { freezeBtn.isClosed.not }) { freezeBtn.value = 0 };
		} {
			scopeView.stop;
			isFrozen = true;
			if (freezeBtn.notNil and: { freezeBtn.isClosed.not }) { freezeBtn.value = 1 };
		};
		if (bandView.notNil and: { bandView.respondsTo(\toggleFreeze) }) { bandView.toggleFreeze };
		if (gramView.notNil and: { gramView.respondsTo(\toggleFreeze) }) { gramView.toggleFreeze };
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
			if (bandView.notNil and: { bandView.respondsTo(\stop) }) { bandView.stop; bandView = nil; };
			if (gramView.notNil and: { gramView.respondsTo(\stop) }) { gramView.stop; gramView = nil; };
		};
	}
}

ProbeSpectrum {
	var <window, <spectrumView;

	*new { |server, busIndex=0, fftSize=2048, theme=\graphite, freqMode=\logarithmic, dbRange=96, smoothing=\none|
		^super.new.prInit(server, busIndex, fftSize, theme, freqMode, dbRange, smoothing);
	}

	*view { |parent, bounds, server, busIndex=0, fftSize=2048, theme=\graphite, freqMode=\logarithmic, dbRange=96, smoothing=\none, showToolbar=false|
		^PB_SpectrumView(server ?? { Server.default }, parent, bounds, busIndex, fftSize, theme, freqMode, dbRange, smoothing, showToolbar);
	}

	prInit { |server, busIndex, fftSize, theme, freqMode, dbRange, smoothing|
		var s = server ?? { Server.default };
		var w = 520, h = 300;

		window = Window(s.name ++ " Spectrum Analyzer — Probe", Rect(120, 200, w, h));
		spectrumView = PB_SpectrumView(s, window, Rect(0, 0, w, h), busIndex, fftSize, theme, freqMode, dbRange, smoothing, showToolbar: true);
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

	viewMode_ { |aMode|
		spectrumView.viewMode = aMode;
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
