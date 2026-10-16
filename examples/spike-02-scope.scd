// SCVisuals — Spike 02: Themed ScopeView Wrapper
// P0 Prototype: Tests native ScopeView wrapper with dark theme, custom wave colors,
// transparent UserView grid overlay, and clean ScopeBuffer lifecycle.

(
s.waitForBoot({
	var win, scopeView, gridOverlay;
	var scopeBuf, synthDef, synth;
	var testSig;
	var bufSize = 4096, cycle = 1024, numChannels = 2;

	// Palette: Graphite theme
	var bgCol       = Color.fromHexString("#171A20");
	var panelBgCol  = Color.fromHexString("#20242C");
	var sigCol      = Color.fromHexString("#7ED6BA");
	var sigCol2     = Color.fromHexString("#E5B95F");
	var textCol     = Color.fromHexString("#D0D4DC");
	var gridCol     = Color.fromHexString("#3A404D").alpha_(0.5);

	win = Window("SCVisuals — Scope Spike (Graphite)", Rect(200, 300, 380, 240));
	win.background = bgCol;

	// Title bar info
	StaticText(win, Rect(12, 6, 200, 16))
		.string_("OSCILLOSCOPE [CH 1 & 2]")
		.font_(Font.sansSerif(9).boldVariant)
		.stringColor_(textCol);

	// ScopeView (native QcScopeShm)
	scopeView = ScopeView(win, Rect(12, 26, 356, 180));
	scopeView.server = s;
	scopeView.background = panelBgCol;
	scopeView.waveColors = [sigCol, sigCol2];
	scopeView.style = 1; // Overlay style
	scopeView.fill = false;
	scopeView.xZoom = 1.0;
	scopeView.yZoom = 1.0;

	// Transparent Grid Overlay
	gridOverlay = UserView(win, Rect(12, 26, 356, 180));
	gridOverlay.canFocus = false;
	gridOverlay.drawFunc = {
		var w = 356, h = 180;
		Pen.strokeColor = gridCol;
		Pen.width = 1;

		// Center zero axis
		Pen.line(Point(0, h * 0.5), Point(w, h * 0.5));
		Pen.stroke;

		// Upper and lower +0.5 / -0.5 divisions
		Pen.line(Point(0, h * 0.25), Point(w, h * 0.25));
		Pen.line(Point(0, h * 0.75), Point(w, h * 0.75));
		Pen.stroke;

		// Vertical divisions
		4.do { |i|
			var x = (i + 1) * (w / 5);
			Pen.line(Point(x, 0), Point(x, h));
		};
		Pen.stroke;

		// Text labels
		Pen.font = Font.monospace(8);
		Pen.color = textCol.alpha_(0.7);
		Pen.stringAtPoint("+1.0", Point(4, 2));
		Pen.stringAtPoint(" 0.0", Point(4, (h * 0.5) - 10));
		Pen.stringAtPoint("-1.0", Point(4, h - 12));
	};

	// Allocate ScopeBuffer and start ScopeOut2
	scopeBuf = ScopeBuffer.alloc(s);
	scopeView.bufnum = scopeBuf.index;

	synthDef = SynthDef(\scv_spike_scope, { |cycle=1024|
		var in = In.ar(0, numChannels);
		ScopeOut2.ar(in, scopeBuf.index, bufSize, cycle);
	}).add;

	s.sync;
	synth = Synth.tail(RootNode(s), \scv_spike_scope, [\cycle, cycle]);
	scopeView.start;

	// Bottom controls / info
	StaticText(win, Rect(12, 212, 160, 16))
		.string_("Style: Overlay | Buf: 4096")
		.font_(Font.sansSerif(8))
		.stringColor_(textCol.alpha_(0.7));

	// Test audio generator (stereo sine + detuned sine)
	testSig = {
		var sig = [
			SinOsc.ar(180) * 0.5,
			SinOsc.ar(181) * 0.4
		];
		Out.ar(0, sig);
	}.play;

	win.onClose = {
		"Cleaning up Spike 02 resources...".postln;
		testSig.free;
		scopeView.stop;
		synth.free;
		scopeBuf.free;
		"Cleanup complete.".postln;
	};

	win.front;
});
)
