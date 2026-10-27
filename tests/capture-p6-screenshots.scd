// tests/capture-p6-screenshots.scd
// Launches SCVLoudness on live synthesized audio and captures screenshot.

(
s.waitForBoot({
	var loud, testSig;
	var outPath = "/Users/fmmac/Desktop/SC New Visulations servers/SCVisuals/docs/screenshots/scvloudness-live.png";

	"Generating live audio for P6 loudness screenshot...".postln;

	testSig = {
		var mod = SinOsc.kr(0.15).range(300, 2500);
		var sig = Saw.ar([130, 130.4]) * 0.15 + (Pulse.ar([260, 261], 0.3) * 0.1);
		sig = RLPF.ar(sig, mod, 0.3);
		Out.ar(0, sig);
	}.play;

	{
		loud = Probe.loudness(s, \outputs, 0, 2, \graphite, -23.0, 15);

		// Allow audio to accumulate in momentary and short-term windows
		3.5.wait;

		Image.fromWindow(loud.window).write(outPath);
		("Saved: " ++ outPath).postln;

		0.5.wait;
		loud.close;
		testSig.free;
		s.quit;
		0.exit;
	}.fork(AppClock);
});
)
