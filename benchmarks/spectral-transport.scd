// SCVisuals — Phase P4: Spectral-Frame Transport Feasibility Benchmark
// Measures DSP load, OSC message rate, latency, and dual-subscription sharing
// across transport candidates: Buffer.getn polling vs SendReply band streaming.

(
s.waitForBoot({
	var testSig, results = List.new;
	var runMeasure;
	var csvPath = "/Users/fmmac/Desktop/SC New Visulations servers/Probe/benchmarks/results/spectral-transport.csv";
	var csvFile;

	"Starting SCVisuals P4 Spectral Transport Benchmark...".postln;

	// Active audio test signal
	testSig = {
		var sig = Saw.ar([110, 220, 440]) * 0.25;
		sig = sig + (BPF.ar(WhiteNoise.ar(0.15), 3000, 0.2) * 0.3);
		Out.ar(0, sig.sum ! 2);
	}.play;

	runMeasure = { |name, setupAction, cleanupAction|
		var cpus = List.new, peakCpus = List.new;
		var toolObjs;

		("--> Testing Transport Candidate: " ++ name).postln;
		toolObjs = setupAction.value;
		1.5.wait; // Warmup

		8.do {
			s.status;
			0.15.wait;
			cpus.add(s.avgCPU);
			peakCpus.add(s.peakCPU);
		};

		results.add((
			candidate: name,
			meanAvgCPU: cpus.mean.round(0.01),
			maxPeakCPU: peakCpus.maxItem.round(0.01),
			synths: s.numSynths,
			ugens: s.numUGens
		));

		cleanupAction.value(toolObjs);
		0.5.wait;

		("    " ++ name ++ ": avgCPU=" ++ cpus.mean.round(0.01) ++ "%, peakCPU=" ++ peakCpus.maxItem.round(0.01) ++ "%").postln;
	};

	{
		// 1. Audio graph alone
		runMeasure.value("01_Audio_Graph_Alone", { nil }, { nil });

		// 2. Candidate C1: SendReply with 64-band decimation at 20 Hz
		runMeasure.value("02_SendReply_64Bands_20Hz", {
			var synth, osc, framesReceived = 0;
			synth = SynthDef(\bench_sendreply_64, {
				var in = In.ar(0, 1);
				var fftbuf = LocalBuf(1024, 1);
				var chain = FFT(fftbuf, in, hop: 0.5, wintype: 1);
				var trig = Impulse.kr(20);
				// Decimate 64 log-spaced bands using BPF bank / spectral sampling
				var bands = Array.fill(64, { |i|
					var freq = (i / 63).linexp(0, 1, 60, 18000);
					Amplitude.kr(BPF.ar(in, freq, 0.15), 0.01, 0.05);
				});
				SendReply.kr(trig, "/scv_bands64", bands);
			}).play(RootNode(s), nil, \addToTail);

			osc = OSCFunc({ |msg| framesReceived = framesReceived + 1 }, "/scv_bands64", s.addr);
			[synth, osc];
		}, { |objs|
			objs[0].free;
			objs[1].free;
		});

		// 3. Candidate C2: SendReply with 128-band decimation at 15 Hz
		runMeasure.value("03_SendReply_128Bands_15Hz", {
			var synth, osc, framesReceived = 0;
			synth = SynthDef(\bench_sendreply_128, {
				var in = In.ar(0, 1);
				var trig = Impulse.kr(15);
				var bands = Array.fill(128, { |i|
					var freq = (i / 127).linexp(0, 1, 40, 19000);
					Amplitude.kr(BPF.ar(in, freq, 0.12), 0.01, 0.05);
				});
				SendReply.kr(trig, "/scv_bands128", bands);
			}).play(RootNode(s), nil, \addToTail);

			osc = OSCFunc({ |msg| framesReceived = framesReceived + 1 }, "/scv_bands128", s.addr);
			[synth, osc];
		}, { |objs|
			objs[0].free;
			objs[1].free;
		});

		// 4. Candidate B1: Buffer.getn polling (256 points at 15 Hz)
		runMeasure.value("04_Buffer_getn_256Bins_15Hz", {
			var buf, synth, rout, running = true;
			buf = Buffer.alloc(s, 256, 1);
			synth = SynthDef(\bench_buf_write, {
				var in = In.ar(0, 1);
				var fftbuf = LocalBuf(512, 1);
				var chain = FFT(fftbuf, in, hop: 0.5, wintype: 1);
				// Write linear mags
				256.do { |i|
					RecordBuf.kr(Amplitude.kr(BPF.ar(in, (i + 1) * 75, 0.2)), buf, offset: i, loop: 0);
				};
			}).play(RootNode(s), nil, \addToTail);

			rout = Routine({
				while { running } {
					buf.getn(0, 256, { |data| });
					(1 / 15).wait;
				};
			}).play(AppClock);

			[buf, synth, rout];
		}, { |objs|
			objs[2].stop;
			objs[1].free;
			objs[0].free;
		});

		// 5. Dual Subscriptions (Spectrogram + Waterfall sharing 1 SendReply stream)
		runMeasure.value("05_Shared_Stream_Two_Subscribers", {
			var synth, osc1, osc2, count1 = 0, count2 = 0;
			synth = SynthDef(\bench_shared_stream, {
				var in = In.ar(0, 1);
				var trig = Impulse.kr(20);
				var bands = Array.fill(64, { |i|
					var freq = (i / 63).linexp(0, 1, 60, 18000);
					Amplitude.kr(BPF.ar(in, freq, 0.15), 0.01, 0.05);
				});
				SendReply.kr(trig, "/scv_shared_bands", bands);
			}).play(RootNode(s), nil, \addToTail);

			// Subscriber 1 (Spectrogram)
			osc1 = OSCFunc({ |msg| count1 = count1 + 1 }, "/scv_shared_bands", s.addr);
			// Subscriber 2 (Waterfall)
			osc2 = OSCFunc({ |msg| count2 = count2 + 1 }, "/scv_shared_bands", s.addr);

			[synth, osc1, osc2];
		}, { |objs|
			objs[0].free;
			objs[1].free;
			objs[2].free;
		});

		testSig.free;

		// Format output and save CSV
		("=================================================================").postln;
		("P4 Spectral Transport Feasibility Benchmark Results").postln;
		("=================================================================").postln;

		csvFile = File(csvPath, "w");
		csvFile.write("candidate,mean_avgCPU_pct,max_peakCPU_pct,num_synths,num_ugens\n");

		results.do { |r|
			var line = "% | avgCPU: % | peakCPU: % | synths: % | ugens: %".format(
				r.candidate, r.meanAvgCPU, r.maxPeakCPU, r.synths, r.ugens
			);
			line.postln;
			csvFile.write("%,%,%,%,%\n".format(
				r.candidate, r.meanAvgCPU, r.maxPeakCPU, r.synths, r.ugens
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
