// SCVisuals — Example 05: ITU-R BS.1770 / EBU R128 Loudness Meter
// Runs SCVLoudness on live audio, demonstrating Momentary (400ms), Short-term (3s),
// Integrated gated program loudness, Loudness Range (LRA), and target switching (-23 / -14 LUFS).

(
s.waitForBoot({
	var loudWin, controlWin;
	var loud, testSig;
	var themeMenu, targetMenu, resetBtn;
	var themes = [\graphite, \phosphor, \ice, \amber, \paper, \monochrome];

	"Starting SCVisuals Loudness Meter Demo...".postln;

	// Audio Generator: Dynamic music-like synthesizer with alternating loud bursts and quiet passages
	testSig = {
		var trig = Impulse.kr(0.3); // Burst every ~3.3 seconds
		var env = EnvGen.kr(Env([0.02, 0.4, 0.15, 0.02], [0.3, 1.2, 1.5], \exp), trig);
		var osc1 = Saw.ar([110, 110.5]) * env;
		var osc2 = Pulse.ar([220, 330], 0.25) * (env * 0.7);
		var filtered = RLPF.ar(osc1 + osc2, SinOsc.kr(0.2).range(300, 3500), 0.3);
		Out.ar(0, filtered);
	}.play;

	// 1. Launch Loudness Meter
	loud = SCVisuals.loudness(s, \outputs, 0, 2, theme: \graphite, targetLUFS: -23.0, updateFreq: 15);
	loud.window.bounds = Rect(120, 280, 440, 290);

	// 2. Control Window
	controlWin = Window("Loudness Demo Controller", Rect(580, 280, 340, 180), resizable: false);
	controlWin.background = Color.fromHexString("#171A20");

	StaticText(controlWin, Rect(16, 14, 300, 20))
		.string_("SCVisuals Loudness Suite")
		.font_(Font.sansSerif(13).boldVariant)
		.stringColor_(Color.white);

	StaticText(controlWin, Rect(16, 38, 300, 16))
		.string_("ITU-R BS.1770-4 / EBU R128 compliant K-weighting.")
		.font_(Font.sansSerif(9))
		.stringColor_(Color.gray(0.7));

	// Theme Selector
	StaticText(controlWin, Rect(16, 68, 60, 20))
		.string_("Theme:")
		.font_(Font.sansSerif(9).boldVariant)
		.stringColor_(Color.white);

	themeMenu = PopUpMenu(controlWin, Rect(76, 68, 110, 22))
		.items_(themes.collect(_.asString))
		.value_(0)
		.action_({ |m|
			loud.theme = themes[m.value];
		});

	// Target Selector
	StaticText(controlWin, Rect(16, 100, 60, 20))
		.string_("Target:")
		.font_(Font.sansSerif(9).boldVariant)
		.stringColor_(Color.white);

	targetMenu = PopUpMenu(controlWin, Rect(76, 100, 160, 22))
		.items_(["-23 LUFS (EBU R128)", "-14 LUFS (Streaming)"])
		.value_(0)
		.action_({ |m|
			loud.targetLUFS = if (m.value == 0) { -23.0 } { -14.0 };
		});

	// Reset Button
	resetBtn = Button(controlWin, Rect(16, 134, 180, 26))
		.states_([["Reset Integrated / Peak ('r')", Color.white, Color.gray(0.25)]])
		.action_({ loud.reset });

	controlWin.onClose = {
		testSig.free;
		loud.close;
	};

	controlWin.front;
});
)
