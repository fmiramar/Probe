// Probe — Example 04: Real-Time Spectrogram, Waterfall & Level History
// Runs ProbeSpectrogram, ProbeWaterfall, and ProbeHistory side-by-side on an active synthesizer.
// Demonstrates shared measurement sources (single FFT analysis for both Spectrogram and Waterfall),
// palette color mapping, freeze/clear controls, and crest factor visualization.

(
s.waitForBoot({
	var specWin, waterWin, histWin, controlWin;
	var spec, water, hist, testSig;
	var themeMenu, freezeBtn, clearBtn;
	var themes = [\graphite, \phosphor, \ice, \amber, \paper, \monochrome];

	"Starting Probe Spectrogram, Waterfall & History Demo...".postln;

	// Audio Generator: Harmonic chirp + sweeping resonant noise + pulsing bass
	testSig = {
		var mod = SinOsc.kr(0.1).exprange(150, 4500);
		var osc1 = Saw.ar([110, 110.4]) * SinOsc.kr(0.25).range(0.2, 0.6);
		var osc2 = Pulse.ar([220, 221], 0.3) * SinOsc.kr(0.4).range(0.0, 0.4);
		var noise = BPF.ar(WhiteNoise.ar(0.5), mod, 0.15) * 1.5;
		var sub = SinOsc.ar([55, 55]) * (Pulse.kr(2, 0.4) * 0.4);
		var sig = (osc1 + osc2 + noise + sub) * 0.25;
		Out.ar(0, sig);
	}.play;

	// 1. Launch Spectrogram (Top-left)
	spec = Probe.spectrogram(s, busIndex: 0, numBands: 64, theme: \graphite, updateFreq: 20);
	spec.window.bounds = Rect(60, 380, 440, 240);

	// 2. Launch Waterfall (Top-right)
	water = Probe.waterfall(s, busIndex: 0, numBands: 64, theme: \graphite, updateFreq: 20, numLines: 40);
	water.window.bounds = Rect(510, 380, 460, 240);

	// 3. Launch Level History (Bottom-left)
	hist = Probe.history(s, busType: \outputs, busIndex: 0, numChannels: 2, theme: \graphite, updateFreq: 20);
	hist.window.bounds = Rect(60, 110, 500, 230);

	// 4. Controller Window (Bottom-right)
	controlWin = Window("Probe Spectral & History Controller", Rect(570, 110, 400, 230), resizable: false);
	controlWin.background = Color.fromHexString("#171A20");

	StaticText(controlWin, Rect(16, 16, 360, 24))
		.string_("Probe Spectral & History Suite")
		.font_(Font.sansSerif(14).boldVariant)
		.stringColor_(Color.white);

	StaticText(controlWin, Rect(16, 44, 360, 32))
		.string_("Both Spectrogram and Waterfall share a single analysis Synth via ProbeBroker.")
		.font_(Font.sansSerif(10))
		.stringColor_(Color.gray(0.7));

	// Theme Selector
	StaticText(controlWin, Rect(16, 90, 60, 20))
		.string_("Theme:")
		.font_(Font.sansSerif(10).boldVariant)
		.stringColor_(Color.white);

	themeMenu = PopUpMenu(controlWin, Rect(80, 90, 120, 24))
		.items_(themes.collect(_.asString))
		.value_(0)
		.action_({ |m|
			var th = themes[m.value];
			spec.theme = th;
			water.theme = th;
			hist.theme = th;
		});

	// Freeze Button
	freezeBtn = Button(controlWin, Rect(16, 130, 110, 28))
		.states_([["Freeze (Space)", Color.white, Color.gray(0.25)], ["Unfreeze", Color.black, Color.fromHexString("#7ED6BA")]])
		.action_({ |btn|
			spec.toggleFreeze;
			water.toggleFreeze;
			hist.toggleFreeze;
		});

	// Clear Button
	clearBtn = Button(controlWin, Rect(136, 130, 90, 28))
		.states_([["Clear (C)", Color.white, Color.gray(0.25)]])
		.action_({
			spec.clear;
			hist.clear;
		});

	// Broker info label
	StaticText(controlWin, Rect(16, 180, 360, 30))
		.string_("Active Broker Sources: " ++ Probe.sources.size)
		.font_(Font.monospace(9))
		.stringColor_(Color.fromHexString("#7ED6BA"));

	// Clean cleanup
	controlWin.onClose = {
		testSig.free;
		spec.close;
		water.close;
		hist.close;
	};

	controlWin.front;
});
)
