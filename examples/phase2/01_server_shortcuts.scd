// examples/phase2/01_server_shortcuts.scd
// Probe Phase II — Server Convenience Shortcuts & Facade Demo
// Demonstrates that vanilla s.meter, s.scope, s.freqscope remain 100% untouched
// while Probe shortcuts provide instant access to high-performance laboratory instruments.

s.waitForBoot({
	var testSound;

	"=== Probe Phase II: Server Convenience Shortcuts Demo ===".postln;

	// Start a rich test signal (stereo saw with resonant filter sweep)
	testSound = {
		var sig = Saw.ar([110, 110.5]) * 0.1;
		var cutoff = SinOsc.kr(0.2).exprange(300, 4000);
		RLPF.ar(sig, cutoff, 0.2);
	}.play;

	// 1. Vanilla Built-ins (Unmodified SuperCollider Behavior)
	// You can still call standard SuperCollider tools at any time:
	// s.meter;       // Standard Stethoscope meter
	// s.scope;       // Standard Stethoscope
	// s.freqscope;   // Standard FreqScope

	// 2. Probe High-Performance Server Shortcuts (*Pb)
	// A. Laboratory Compact Server Meter (Vertical channel strips, dB ticks)
	~meter = s.meterPb(2, 2, theme: \graphite, density: \compact);

	// B. Precision Oscilloscope (Hardware phosphor style, triggered waveform)
	~scope = s.scopePb(0, 2, theme: \phosphor, style: \overlay);

	// C. High-Resolution Spectrum Analyzer (Dual linear/log scale)
	~spectrum = s.freqscopePb(0, fftSize: 2048, theme: \ice, dbRange: 96.0);

	// D. 3D Waterfall Display
	~waterfall = s.waterfallPb(0, fftSize: 64, theme: \amber, numLines: 48);

	// E. Master Instrumentation Dashboard (All-in-one laboratory console)
	// ~dashboard = s.dashboardPb(2, 2, theme: \graphite);

	"All shortcuts launched cleanly. Call ~cleanup.value when finished.".postln;

	~cleanup = {
		~meter.close;
		~scope.close;
		~spectrum.close;
		~waterfall.close;
		testSound.free;
		"Cleaned up Probe shortcuts.".postln;
	};
});
