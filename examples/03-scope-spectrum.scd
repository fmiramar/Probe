// SCVisuals — Example 03: Themed Scope & Spectrum Analyzer
// Runs SCVScope and SCVSpectrum side-by-side on an active harmonic synthesizer,
// with interactive theme switching, scope styles (\tracks, \overlay, \xy),
// spectrum frequency modes (\linear, \logarithmic), and zoom controls.

(
s.waitForBoot({
	var scopeWin, specWin, controlWin;
	var scope, spec, testSig;
	var themeMenu, scopeStyleMenu, specModeMenu, dbRangeMenu, freezeBtn;
	var themes = [\graphite, \phosphor, \ice, \amber, \paper, \monochrome];

	"Starting SCVisuals Scope & Spectrum Demo...".postln;

	// Audio Generator: Harmonic rich waveform with slow vibrato and filter sweep
	testSig = {
		var lfo = SinOsc.kr(0.2).range(100, 300);
		var sig = Saw.ar([110, 110.5] + (SinOsc.kr(5) * 2)) * 0.3;
		sig = RLPF.ar(sig, SinOsc.kr(0.15).range(400, 4000), 0.25);
		sig = sig + (SinOsc.ar([440, 660]) * 0.15);
		Out.ar(0, sig);
	}.play;

	// 1. Launch Scope (Positioned left)
	scope = SCVisuals.scope(s, 0, 2, theme: \graphite, style: \overlay);
	scope.window.bounds = Rect(80, 320, 380, 250);

	// 2. Launch Spectrum (Positioned right)
	spec = SCVisuals.spectrum(s, 0, 2048, theme: \graphite, freqMode: \logarithmic, dbRange: 96);
	spec.window.bounds = Rect(470, 320, 440, 250);

	// 3. Consolidated Control Panel
	controlWin = Window("SCVisuals Tools Controller", Rect(80, 180, 830, 100), resizable: false);
	controlWin.background = Color.fromHexString("#171A20");

	// Theme Selector
	StaticText(controlWin, Rect(12, 10, 60, 20))
		.string_("Theme:")
		.font_(Font.sansSerif(9).boldVariant)
		.stringColor_(Color.white);

	themeMenu = PopUpMenu(controlWin, Rect(70, 10, 110, 22))
		.items_(themes.collect(_.asString))
		.value_(0)
		.action_({ |m|
			var th = themes[m.value];
			scope.theme = th;
			spec.theme = th;
		});

	// Scope Style
	StaticText(controlWin, Rect(200, 10, 70, 20))
		.string_("Scope Style:")
		.font_(Font.sansSerif(9).boldVariant)
		.stringColor_(Color.white);

	scopeStyleMenu = PopUpMenu(controlWin, Rect(280, 10, 100, 22))
		.items_(["Overlay", "Tracks", "X/Y"])
		.value_(0)
		.action_({ |m|
			scope.scopeStyle = switch(m.value, 0, { \overlay }, 1, { \tracks }, 2, { \xy });
		});

	// Spectrum Mode
	StaticText(controlWin, Rect(400, 10, 70, 20))
		.string_("Freq Mode:")
		.font_(Font.sansSerif(9).boldVariant)
		.stringColor_(Color.white);

	specModeMenu = PopUpMenu(controlWin, Rect(480, 10, 110, 22))
		.items_(["Logarithmic", "Linear"])
		.value_(0)
		.action_({ |m|
			spec.freqMode = switch(m.value, 0, { \logarithmic }, 1, { \linear });
		});

	// dB Range
	StaticText(controlWin, Rect(610, 10, 60, 20))
		.string_("dB Range:")
		.font_(Font.sansSerif(9).boldVariant)
		.stringColor_(Color.white);

	dbRangeMenu = PopUpMenu(controlWin, Rect(680, 10, 80, 22))
		.items_(["48 dB", "72 dB", "96 dB", "120 dB"])
		.value_(2) // 96 dB
		.action_({ |m|
			spec.dbRange = switch(m.value, 0, { 48 }, 1, { 72 }, 2, { 96 }, 3, { 120 });
		});

	// Freeze Toggle Button
	freezeBtn = Button(controlWin, Rect(12, 50, 168, 36))
		.states_([["Freeze Both (Spacebar)", Color.white, Color.fromHexString("#2A303C")], ["Resume Both", Color.white, Color.fromHexString("#F07878").alpha_(0.6)]])
		.font_(Font.sansSerif(9).boldVariant)
		.action_({
			scope.scopeView.toggleFreeze;
			spec.spectrumView.toggleFreeze;
		});

	StaticText(controlWin, Rect(200, 58, 600, 20))
		.string_("Keyboard: [s] cycle scope style | [m] toggle spectrum mode | [+/-] zoom scope | [Space] freeze")
		.font_(Font.sansSerif(8))
		.stringColor_(Color.gray(0.6));

	// Window close cleanup
	controlWin.onClose = {
		if (scope.window.isClosed.not) { scope.close };
		if (spec.window.isClosed.not) { spec.close };
		testSig.free;
		"SCVisuals Scope & Spectrum Demo stopped.".postln;
	};

	controlWin.front;
});
)
