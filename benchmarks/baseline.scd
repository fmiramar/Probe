// SCVisuals — Baseline Profiling Benchmark (P0)
// Automates measurement of idle server, stock SC visual tools, and SCVisuals P0 spikes.
// Records avgCPU, peakCPU, numSynths, numUGens, and writes results to CSV.

(
s.waitForBoot({
	var testSig, results = List.new;
	var runMeasure, cleanup;
	var csvPath = "/Users/fmmac/Desktop/SC New Visulations servers/Probe/benchmarks/baseline.csv";
	var csvFile;

	"Starting SCVisuals P0 Baseline Profiling Benchmark...".postln;

	// Start standard test signal
	testSig = {
		var sig = [
			SinOsc.ar(440) * 0.4,
			PinkNoise.ar(0.15)
		];
		Out.ar(0, sig);
	}.play;

	// Helper to sample CPU over 2 seconds
	runMeasure = { |conditionName, action, cleanupAction|
		var cpus = List.new, peakCpus = List.new;
		var synths = 0, ugens = 0;
		var toolObj;

		("--> Measuring condition: " ++ conditionName).postln;
		toolObj = action.value;
		1.5.wait; // Warmup / stabilization

		10.do {
			s.status; // request fresh status
			0.15.wait;
			cpus.add(s.avgCPU);
			peakCpus.add(s.peakCPU);
		};

		synths = s.numSynths;
		ugens = s.numUGens;

		cleanupAction.value(toolObj);
		0.5.wait;

		results.add((
			condition: conditionName,
			meanAvgCPU: cpus.mean.round(0.01),
			maxPeakCPU: peakCpus.maxItem.round(0.01),
			synths: synths,
			ugens: ugens
		));

		("    Completed: avgCPU=" ++ cpus.mean.round(0.01) ++ "%, peakCPU=" ++ peakCpus.maxItem.round(0.01) ++ "%").postln;
	};

	{
		// 1. Audio Graph Baseline (no tools)
		runMeasure.value("01_Audio_Graph_Alone", { nil }, { nil });

		// 2. Stock ServerMeter
		runMeasure.value("02_Stock_ServerMeter", {
			ServerMeter(s, 2, 2);
		}, { |m|
			m.close;
		});

		// 3. Spike 01 Meter Prototype
		runMeasure.value("03_Spike01_SCVMeter", {
			var win, inMeters, outMeters, synthIn, synthOut, oscIn, oscOut;
			win = Window("Bench Meter", Rect(100, 100, 100, 230)).front;
			synthIn = SynthDef(\bench_in, {
				SendPeakRMS.kr(In.ar(NumOutputBuses.ir, 2), 15, 3, "/bench_in");
			}).play(RootNode(s), nil, \addToHead);
			synthOut = SynthDef(\bench_out, {
				SendPeakRMS.kr(In.ar(0, 2), 15, 3, "/bench_out");
			}).play(RootNode(s), nil, \addToTail);
			oscIn = OSCFunc({ |msg| }, "/bench_in", s.addr);
			oscOut = OSCFunc({ |msg| }, "/bench_out", s.addr);
			[win, synthIn, synthOut, oscIn, oscOut];
		}, { |objs|
			objs[0].close;
			objs[1].free;
			objs[2].free;
			objs[3].free;
			objs[4].free;
		});

		// 4. Stock Stethoscope (s.scope)
		runMeasure.value("04_Stock_Stethoscope", {
			Stethoscope(s, 2, 0);
		}, { |sc|
			sc.window.close;
		});

		// 5. Spike 02 Scope Prototype
		runMeasure.value("05_Spike02_SCVScope", {
			var win, sv, sbuf, synth;
			win = Window("Bench Scope", Rect(100, 100, 300, 200)).front;
			sv = ScopeView(win, Rect(10, 10, 280, 180));
			sv.server = s;
			sbuf = ScopeBuffer.alloc(s);
			sv.bufnum = sbuf.index;
			synth = SynthDef(\bench_scope, {
				ScopeOut2.ar(In.ar(0, 2), sbuf.index, 4096, 1024);
			}).play(RootNode(s), nil, \addToTail);
			sv.start;
			[win, sv, sbuf, synth];
		}, { |objs|
			objs[0].close;
			objs[1].stop;
			objs[3].free;
			objs[2].free;
		});

		// 6. Stock FreqScope
		runMeasure.value("06_Stock_FreqScope", {
			FreqScope(400, 200, 0, server: s);
		}, { |fs|
			fs.window.close;
		});

		// 7. Spike 03 Spectrum Prototype
		runMeasure.value("07_Spike03_SCVSpectrum", {
			var win, sv, sbuf, synth;
			win = Window("Bench Spectrum", Rect(100, 100, 400, 200)).front;
			sv = ScopeView(win, Rect(10, 10, 380, 180));
			sv.server = s;
			sbuf = ScopeBuffer.alloc(s);
			sv.bufnum = sbuf.index;
			synth = SynthDef(\bench_spec, {
				var sig = In.ar(0, 1);
				var fftbuf = LocalBuf(2048, 1);
				var chain = FFT(fftbuf, sig, hop: 0.75, wintype: 1);
				var phasor = (BufSamples.ir(fftbuf) * 0.5).pow(LFSaw.ar(4 / BufDur.ir(fftbuf), 0, 0.5, 0.5)) * 2;
				ScopeOut2.ar(((BufRd.ar(1, fftbuf, phasor.round(2), 1, 1) * 0.00285).ampdb * (2/96)) + 1, sbuf.index, 2048/4);
			}).play(RootNode(s), nil, \addToTail);
			sv.start;
			[win, sv, sbuf, synth];
		}, { |objs|
			objs[0].close;
			objs[1].stop;
			objs[3].free;
			objs[2].free;
		});

		// 8. Spike 04 Spectrogram Prototype (GUI only)
		runMeasure.value("08_Spike04_SCVSpectrogram", {
			var win, img, uv, rout;
			win = Window("Bench Spectrogram", Rect(100, 100, 250, 120)).front;
			img = Image(200, 100);
			uv = UserView(win, Rect(10, 10, 200, 100));
			uv.drawFunc = { Pen.drawImage(Point(0, 0), img) };
			rout = Routine({
				while { win.isClosed.not } {
					uv.refresh;
					(1/25).yield;
				};
			}).play(AppClock);
			[win, img, rout];
		}, { |objs|
			objs[0].close;
			objs[2].stop;
			objs[1].free;
		});

		testSig.free;

		// Format and write CSV
		("=================================================================").postln;
		("SCVisuals Baseline Benchmark Results (macOS arm64, SC 3.14.1)").postln;
		("=================================================================").postln;
		("Condition, Mean_avgCPU_%, Max_peakCPU_%, Synths, UGens").postln;

		csvFile = File(csvPath, "w");
		csvFile.write("condition,mean_avgCPU_pct,max_peakCPU_pct,num_synths,num_ugens\n");

		results.do { |r|
			var line = "% , % , % , % , %".format(r.condition, r.meanAvgCPU, r.maxPeakCPU, r.synths, r.ugens);
			line.postln;
			csvFile.write("%,%,%,%,%\n".format(r.condition, r.meanAvgCPU, r.maxPeakCPU, r.synths, r.ugens));
		};

		csvFile.close;
		("=================================================================").postln;
		("Saved CSV to: " ++ csvPath).postln;

		s.quit;
		0.exit;
	}.fork(AppClock);
});
)
