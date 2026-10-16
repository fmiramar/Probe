// SCVisuals — Spike 01: Minimal Vertical Peak/RMS Meter
// P0 Prototype: Tests vertical channel strips, legible dB ruler, SendPeakRMS transport,
// decoupled updates, and theme palette without altering server global state.

(
s.waitForBoot({
	var win, innerView, numIns = 2, numOuts = 2;
	var meterWidth = 16, gapWidth = 4, height = 230;
	var totalWidth, inMeters, outMeters;
	var synthIn, synthOut, oscIn, oscOut;
	var updateFreq = 15;
	var testSig;

	// Palette: Graphite theme
	var bgCol       = Color.fromHexString("#171A20");
	var panelBgCol  = Color.fromHexString("#20242C");
	var sigCol      = Color.fromHexString("#7ED6BA");
	var warnCol     = Color.fromHexString("#E5B95F");
	var critCol     = Color.fromHexString("#F07878");
	var textCol     = Color.fromHexString("#D0D4DC");
	var gridCol     = Color.fromHexString("#3A404D");

	totalWidth = 24 + ((numIns + numOuts + 2) * (meterWidth + gapWidth));

	win = Window("SCVisuals — Meter Spike (Graphite)", Rect(150, 400, totalWidth, height), resizable: false);
	win.background = bgCol;

	// Top Labels
	StaticText(win, Rect(10, 4, 80, 16))
		.string_("INPUTS")
		.font_(Font.sansSerif(9).boldVariant)
		.stringColor_(textCol);

	StaticText(win, Rect(10 + ((numIns + 1) * (meterWidth + gapWidth)), 4, 80, 16))
		.string_("OUTPUTS")
		.font_(Font.sansSerif(9).boldVariant)
		.stringColor_(textCol);

	// dB Scale Ruler (left side)
	UserView(win, Rect(8, 24, 24, 175)).drawFunc_({
		var dBs = [0, -6, -12, -18, -24, -36, -48, -60, -80];
		Pen.font = Font.monospace(8);
		dBs.do { |db|
			var normY = db.linlin(-80, 0, 165, 0);
			Pen.color = gridCol;
			Pen.line(Point(18, normY + 5), Point(24, normY + 5));
			Pen.stroke;
			Pen.color = textCol;
			Pen.stringRightJustIn(db.asString, Rect(0, normY, 16, 10));
		};
	});

	// Container for channels
	innerView = CompositeView(win, Rect(36, 24, totalWidth - 40, 195));
	innerView.addFlowLayout(0@0, gapWidth@gapWidth);

	// Create Input Meters
	inMeters = Array.fill(numIns, { |i|
		var comp, meter, label;
		comp = CompositeView(innerView, Rect(0, 0, meterWidth, 195));
		meter = LevelIndicator(comp, Rect(0, 0, meterWidth, 175))
			.warning_( -12.linlin(-80, 0, 0, 1) )
			.critical_( -0.5.linlin(-80, 0, 0, 1) )
			.drawsPeak_(true)
			.numTicks_(9)
			.numMajorTicks_(3)
			.background_(panelBgCol);

		label = StaticText(comp, Rect(0, 177, meterWidth, 14))
			.font_(Font.sansSerif(8).boldVariant)
			.align_(\center)
			.stringColor_(textCol)
			.string_((i + 1).asString);

		meter;
	});

	// Divider
	UserView(innerView, Rect(0, 0, meterWidth, 175)).drawFunc_({
		Pen.color = gridCol;
		Pen.line(Point(meterWidth * 0.5, 0), Point(meterWidth * 0.5, 175));
		Pen.stroke;
	});

	// Create Output Meters
	outMeters = Array.fill(numOuts, { |i|
		var comp, meter, label;
		comp = CompositeView(innerView, Rect(0, 0, meterWidth, 195));
		meter = LevelIndicator(comp, Rect(0, 0, meterWidth, 175))
			.warning_( -12.linlin(-80, 0, 0, 1) )
			.critical_( -0.5.linlin(-80, 0, 0, 1) )
			.drawsPeak_(true)
			.numTicks_(9)
			.numMajorTicks_(3)
			.background_(panelBgCol);

		label = StaticText(comp, Rect(0, 177, meterWidth, 14))
			.font_(Font.sansSerif(8).boldVariant)
			.align_(\center)
			.stringColor_(textCol)
			.string_((i + 1).asString);

		meter;
	});

	// Analysis Synths
	synthIn = SynthDef(\scv_spike_inlevels, {
		var in = In.ar(NumOutputBuses.ir, numIns);
		SendPeakRMS.kr(in, updateFreq, 3, "/scv_spike_in");
	}).play(RootNode(s), nil, \addToHead);

	synthOut = SynthDef(\scv_spike_outlevels, {
		var out = In.ar(0, numOuts);
		SendPeakRMS.kr(out, updateFreq, 3, "/scv_spike_out");
	}).play(RootNode(s), nil, \addToTail);

	// OSC Responders (coalescing to UI)
	oscIn = OSCFunc({ |msg|
		{
			if (win.isClosed.not) {
				numIns.do { |ch|
					var base = 3 + (ch * 2);
					var peak = msg[base];
					var rms = msg[base + 1];
					if (inMeters[ch].notNil and: { inMeters[ch].isClosed.not }) {
						inMeters[ch].peakLevel = peak.ampdb.linlin(-80, 0, 0, 1, \min);
						inMeters[ch].value = rms.ampdb.linlin(-80, 0, 0, 1);
					};
				};
			};
		}.defer;
	}, "/scv_spike_in", s.addr).fix;

	oscOut = OSCFunc({ |msg|
		{
			if (win.isClosed.not) {
				numOuts.do { |ch|
					var base = 3 + (ch * 2);
					var peak = msg[base];
					var rms = msg[base + 1];
					if (outMeters[ch].notNil and: { outMeters[ch].isClosed.not }) {
						outMeters[ch].peakLevel = peak.ampdb.linlin(-80, 0, 0, 1, \min);
						outMeters[ch].value = rms.ampdb.linlin(-80, 0, 0, 1);
					};
				};
			};
		}.defer;
	}, "/scv_spike_out", s.addr).fix;

	// Test audio generator to see meter move
	testSig = {
		var sig = [
			SinOsc.ar(220) * LFPulse.kr(1, 0, 0.4).range(0.05, 0.6),
			PinkNoise.ar(0.2) * LFNoise1.kr(2).range(0.1, 0.8)
		];
		Out.ar(0, sig);
	}.play;

	// Window close cleanup
	win.onClose = {
		"Cleaning up Spike 01 resources...".postln;
		testSig.free;
		synthIn.free;
		synthOut.free;
		oscIn.free;
		oscOut.free;
		"Cleanup complete.".postln;
	};

	win.front;
});
)
