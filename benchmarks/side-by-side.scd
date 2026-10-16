// SCVisuals — Benchmark & Gate G3 Verification: Side-by-Side Performance
// Measures stock SC tools (Stethoscope, FreqScope) vs wrapped SCVisuals tools (SCVScope, SCVSpectrum)
// individually, combined, and simultaneously side-by-side on identical audio graphs.

(
s.waitForBoot({
	var testSig, results = List.new;
	var runMeasure;
	var csvPath = "/Users/fmmac/Desktop/SC New Visulations servers/SCVisuals/benchmarks/results/side-by-side.csv";
	var csvFile;

	"Starting SCVisuals Gate G3 Verification: Side-by-Side Benchmarking...".postln;

	// Active test audio graph: Harmonic chords + noise
	testSig = {
		var sig = (SinOsc.ar([220, 440, 880]) * [0.35, 0.2, 0.1]).sum;
		sig = sig + (BPF.ar(PinkNoise.ar(0.15), 2500, 0.3) * 0.25);
		Out.ar(0, sig ! 2);
	}.play;

	runMeasure = { |conditionName, setupAction, cleanupAction|
		var cpus = List.new, peakCpus = List.new;
		var toolObjs;

		("--> Measuring: " ++ conditionName).postln;
		toolObjs = setupAction.value;
		1.5.wait; // Warmup

		8.do {
			s.status;
			0.15.wait;
			cpus.add(s.avgCPU);
			peakCpus.add(s.peakCPU);
		};

		results.add((
			condition: conditionName,
			meanAvgCPU: cpus.mean.round(0.01),
			maxPeakCPU: peakCpus.maxItem.round(0.01),
			synths: s.numSynths,
			ugens: s.numUGens
		));

		cleanupAction.value(toolObjs);
		0.5.wait;

		("    " ++ conditionName ++ ": avgCPU=" ++ cpus.mean.round(0.01) ++ "%, peakCPU=" ++ peakCpus.maxItem.round(0.01) ++ "%").postln;
	};

	{
		// 1. Audio Graph Baseline
		runMeasure.value("01_Audio_Graph_Alone", { nil }, { nil });

		// 2. Stock Stethoscope
		runMeasure.value("02_Stock_Stethoscope", {
			Stethoscope(s, 2, 0);
		}, { |sc|
			sc.window.close;
		});

		// 3. SCVisuals SCVScope
		runMeasure.value("03_SCVisuals_SCVScope", {
			SCVisuals.scope(s, 0, 2, theme: \graphite);
		}, { |sc|
			sc.close;
		});

		// 4. Stock FreqScope
		runMeasure.value("04_Stock_FreqScope", {
			FreqScope(400, 220, 0, server: s);
		}, { |fs|
			fs.window.close;
		});

		// 5. SCVisuals SCVSpectrum
		runMeasure.value("05_SCVisuals_SCVSpectrum", {
			SCVisuals.spectrum(s, 0, 2048, theme: \graphite);
		}, { |fs|
			fs.close;
		});

		// 6. Stock Scope + FreqScope Together
		runMeasure.value("06_Stock_Scope_and_Spectrum_Combined", {
			var sc = Stethoscope(s, 2, 0);
			var fs = FreqScope(400, 220, 0, server: s);
			[sc, fs];
		}, { |objs|
			objs[0].window.close;
			objs[1].window.close;
		});

		// 7. SCVisuals Scope + Spectrum Together
		runMeasure.value("07_SCVisuals_Scope_and_Spectrum_Combined", {
			var sc = SCVisuals.scope(s, 0, 2, theme: \graphite);
			var fs = SCVisuals.spectrum(s, 0, 2048, theme: \graphite);
			[sc, fs];
		}, { |objs|
			objs[0].close;
			objs[1].close;
		});

		// 8. Simultaneous Side-by-Side: Stock & SCVisuals all 4 running!
		runMeasure.value("08_All_Four_Simultaneous_SideBySide", {
			var sc1 = Stethoscope(s, 2, 0);
			var fs1 = FreqScope(400, 220, 0, server: s);
			var sc2 = SCVisuals.scope(s, 0, 2, theme: \ice);
			var fs2 = SCVisuals.spectrum(s, 0, 2048, theme: \ice);
			[sc1, fs1, sc2, fs2];
		}, { |objs|
			objs[0].window.close;
			objs[1].window.close;
			objs[2].close;
			objs[3].close;
		});

		testSig.free;

		// Format output and save CSV
		("=================================================================").postln;
		("Gate G3 Verification Results: Stock vs SCVisuals Instruments").postln;
		("=================================================================").postln;

		csvFile = File(csvPath, "w");
		csvFile.write("condition,mean_avgCPU_pct,max_peakCPU_pct,num_synths,num_ugens\n");

		results.do { |r|
			var line = "% | avgCPU: % | peakCPU: % | synths: % | ugens: %".format(
				r.condition, r.meanAvgCPU, r.maxPeakCPU, r.synths, r.ugens
			);
			line.postln;
			csvFile.write("%,%,%,%,%\n".format(
				r.condition, r.meanAvgCPU, r.maxPeakCPU, r.synths, r.ugens
			));
		};

		csvFile.close;
		("=================================================================").postln;
		("Saved CSV to: " ++ csvPath).postln;

		s.quit;
		0.exit;
	}.fork(AppClock);
});
)
