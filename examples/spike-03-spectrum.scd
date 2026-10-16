// SCVisuals — Spike 03: Themed FreqScope Wrapper
// P0 Prototype: Tests themed spectral display wrapping the verified FreqScope FFT pipeline
// (FFT size 2048, hop 0.75, PV_MagSmear, ScopeOut2 phasor readout), Lin/Log frequency modes,
// dB scaling, and overlay grid.

(
s.waitForBoot({
	var win, scopeView, gridOverlay;
	var scopeBuf, synth, synthDefLin, synthDefLog;
	var testSig;
	var fftSize = 2048, rate = 4, dbRange = 96, freqMode = 1; // 0 = lin, 1 = log
	var inBus = 0;

	// Palette: Graphite theme
	var bgCol       = Color.fromHexString("#171A20");
	var panelBgCol  = Color.fromHexString("#20242C");
	var sigCol      = Color.fromHexString("#7ED6BA");
	var textCol     = Color.fromHexString("#D0D4DC");
	var gridCol     = Color.fromHexString("#3A404D").alpha_(0.5);

	win = Window("SCVisuals — Spectrum Spike (Graphite)", Rect(250, 300, 460, 260));
	win.background = bgCol;

	// Title
	StaticText(win, Rect(12, 6, 200, 16))
		.string_("SPECTRUM ANALYZER [FFT 2048]")
		.font_(Font.sansSerif(9).boldVariant)
		.stringColor_(textCol);

	// ScopeView
	scopeView = ScopeView(win, Rect(36, 26, 410, 190));
	scopeView.server = s;
	scopeView.background = panelBgCol;
	scopeView.waveColors = [sigCol];
	scopeView.style = 1; // Overlay
	scopeView.fill = false;
	scopeView.xZoom = (fftSize * 0.25) / 410;
	scopeView.yZoom = 1.0;

	// Overlay Grid & Scales
	gridOverlay = UserView(win, Rect(10, 26, 436, 190));
	gridOverlay.canFocus = false;
	gridOverlay.drawFunc = {
		var w = 410, h = 190, ox = 26;
		Pen.strokeColor = gridCol;
		Pen.width = 1;

		// dB horizontal lines
		[0, -12, -24, -36, -48, -60, -72, -96].do { |db|
			var y = db.linlin(0, -96, 0, h);
			Pen.line(Point(ox, y), Point(ox + w, y));
			Pen.stroke;
			Pen.font = Font.monospace(8);
			Pen.color = textCol.alpha_(0.7);
			Pen.stringRightJustIn(db.asString, Rect(0, y - 5, 22, 10));
		};

		// Frequency vertical lines (Logarithmic approx markings)
		if (freqMode == 1) {
			[100, 250, 500, 1000, 2500, 5000, 10000, 20000].do { |freq|
				var normX = (freq / 20.0).log / (24000 / 20.0).log;
				var x = ox + (normX.clip(0, 1) * w);
				Pen.color = gridCol;
				Pen.line(Point(x, 0), Point(x, h));
				Pen.stroke;
				Pen.font = Font.monospace(8);
				Pen.color = textCol.alpha_(0.7);
				Pen.stringCenteredIn(
					if (freq >= 1000) { (freq / 1000).asInteger.asString ++ "k" } { freq.asString },
					Rect(x - 15, h - 12, 30, 10)
				);
			};
		} {
			// Linear markings
			8.do { |i|
				var x = ox + ((i + 1) * (w / 8));
				var freqK = ((i + 1) * (s.sampleRate * 0.5 / 8000)).round(0.1);
				Pen.color = gridCol;
				Pen.line(Point(x, 0), Point(x, h));
				Pen.stroke;
				Pen.font = Font.monospace(8);
				Pen.color = textCol.alpha_(0.7);
				Pen.stringCenteredIn(freqK.asString ++ "k", Rect(x - 15, h - 12, 30, 10));
			};
		};
	};

	// Allocate scope buffer
	scopeBuf = ScopeBuffer.alloc(s);
	scopeView.bufnum = scopeBuf.index;

	// Logarithmic FFT SynthDef
	synthDefLog = SynthDef(\scv_spike_spec_log, { |in=0, scopebufnum=0, dbFactor=0.0208|
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
	}).add;

	s.sync;

	synth = Synth.tail(RootNode(s), \scv_spike_spec_log, [
		\in, inBus,
		\scopebufnum, scopeBuf.index,
		\dbFactor, 2 / dbRange
	]);

	scopeView.start;

	// Footer mode controls
	StaticText(win, Rect(36, 224, 200, 18))
		.string_("Mode: Logarithmic | Hann 75% | Range: -96 dB")
		.font_(Font.sansSerif(8))
		.stringColor_(textCol.alpha_(0.7));

	// Test audio generator (harmonic chords + band noise)
	testSig = {
		var sig = (SinOsc.ar([220, 440, 880, 1760]) * [0.4, 0.25, 0.15, 0.1]).sum;
		sig = sig + (BPF.ar(WhiteNoise.ar(0.15), 3500, 0.2) * 0.3);
		Out.ar(0, sig ! 2);
	}.play;

	win.onClose = {
		"Cleaning up Spike 03 resources...".postln;
		testSig.free;
		scopeView.stop;
		synth.free;
		scopeBuf.free;
		"Cleanup complete.".postln;
	};

	win.front;
});
)
