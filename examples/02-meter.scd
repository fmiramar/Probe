// Probe — Example 02: Production ServerMeter (ProbeMeter)
// Demonstrates vertical channel strips, legible dB tick ruler, dynamic theme switching
// (Graphite, Phosphor, Ice, Amber, Paper, Monochrome), density switching, and peak-hold reset.

(
s.waitForBoot({
	var meter, testSig;
	var controlWin, themeMenu, densityMenu, styleMenu, resetBtn;
	var themes = [\graphite, \phosphor, \ice, \amber, \paper, \monochrome];
	var densities = [\micro, \compact, \extended];
	var styles = [\continuous, \led];

	"Starting Probe Meter Demo...".postln;

	// Start test audio: Stereo chords + transient pulse
	testSig = {
		var sig = [
			SinOsc.ar(220) * LFPulse.kr(1.5, 0, 0.4).range(0.05, 0.6),
			PinkNoise.ar(0.2) * LFNoise1.kr(3).range(0.1, 0.7)
		];
		Out.ar(0, sig);
	}.play;

	// Launch ProbeMeter
	meter = Probe.meter(s, 2, 2, theme: \graphite, density: \compact, meterStyle: \continuous);

	// Dedicated Control Panel Window
	controlWin = Window("ProbeMeter Controls", Rect(300, 450, 320, 110), resizable: false);
	controlWin.background = Color.fromHexString("#171A20");

	StaticText(controlWin, Rect(12, 10, 60, 20))
		.string_("Theme:")
		.font_(Font.sansSerif(9).boldVariant)
		.stringColor_(Color.white);

	themeMenu = PopUpMenu(controlWin, Rect(80, 10, 110, 22))
		.items_(themes.collect(_.asString))
		.value_(0)
		.action_({ |m|
			meter.theme = themes[m.value];
		});

	StaticText(controlWin, Rect(12, 40, 60, 20))
		.string_("Density:")
		.font_(Font.sansSerif(9).boldVariant)
		.stringColor_(Color.white);

	densityMenu = PopUpMenu(controlWin, Rect(80, 40, 110, 22))
		.items_(densities.collect(_.asString))
		.value_(1) // compact
		.action_({ |m|
			meter.density = densities[m.value];
		});

	StaticText(controlWin, Rect(12, 70, 60, 20))
		.string_("Style:")
		.font_(Font.sansSerif(9).boldVariant)
		.stringColor_(Color.white);

	styleMenu = PopUpMenu(controlWin, Rect(80, 70, 110, 22))
		.items_(styles.collect(_.asString))
		.value_(0)
		.action_({ |m|
			meter.meterStyle = styles[m.value];
		});

	resetBtn = Button(controlWin, Rect(204, 10, 104, 82))
		.states_([["Reset Peak\n(or press 'r')", Color.white, Color.fromHexString("#2A303C")]])
		.font_(Font.sansSerif(9).boldVariant)
		.action_({
			meter.resetPeakHold;
		});

	meter.window.onClose = {
		if (controlWin.isClosed.not) { controlWin.close };
		testSig.free;
		"ProbeMeter Demo stopped.".postln;
	};

	controlWin.onClose = {
		if (meter.window.isClosed.not) { meter.close };
		testSig.free;
	};

	controlWin.front;
});
)
