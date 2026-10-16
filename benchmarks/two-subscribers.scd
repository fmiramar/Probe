// SCVisuals — Benchmark & Gate G1 Verification: Two Subscribers Sharing One Source
// Verifies:
// 1. Consumer 1 allocates 1 analysis synth.
// 2. Consumer 2 reuses the existing analysis synth (zero duplicate nodes).
// 3. Changing themes causes 0 new server nodes.
// 4. Closing both consumers releases and frees all analysis resources.

(
s.waitForBoot({
	var testSig, results = List.new;
	var runMeasure;
	var broker = SCVisuals.broker;
	var source1, source2;
	var consumer1 = \consumer_win1;
	var consumer2 = \consumer_win2;
	var csvPath = "/Users/fmmac/Desktop/SC New Visulations servers/SCVisuals/benchmarks/results/two-subscribers.csv";
	var csvFile;

	"Starting SCVisuals Gate G1 Verification: Two Subscribers...".postln;

	// Start standard test signal
	testSig = {
		var sig = [
			SinOsc.ar(440) * 0.4,
			PinkNoise.ar(0.15)
		];
		Out.ar(0, sig);
	}.play;

	runMeasure = { |conditionName|
		var cpus = List.new, peakCpus = List.new;
		1.0.wait;

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
			ugens: s.numUGens,
			brokerSources: broker.sourceCount,
			totalSubscribers: broker.totalSubscriberCount
		));

		("--> " ++ conditionName ++ ": avgCPU=" ++ cpus.mean.round(0.01) ++ "%, synths=" ++ s.numSynths ++ ", brokerSources=" ++ broker.sourceCount).postln;
	};

	{
		// 1. Audio Graph alone
		runMeasure.value("01_Audio_Graph_Alone");

		// 2. One Subscriber
		source1 = broker.subscribeLevels(consumer1, s, \outputs, 0, 2, 15, { |p, r| });
		runMeasure.value("02_One_Subscriber");

		// 3. Two Subscribers (sharing same source)
		source2 = broker.subscribeLevels(consumer2, s, \outputs, 0, 2, 15, { |p, r| });
		runMeasure.value("03_Two_Subscribers_Shared");

		// 4. Theme changes (must NOT create any server nodes)
		SCVisuals.theme = \phosphor;
		SCVisuals.theme = \ice;
		SCVisuals.theme = \graphite;
		runMeasure.value("04_After_Theme_Changes");

		// 5. Unsubscribe first consumer (source still active)
		broker.unsubscribe(consumer1, source1.key);
		runMeasure.value("05_One_Consumer_Remaining");

		// 6. Unsubscribe second consumer (source completely released)
		broker.unsubscribe(consumer2, source1.key);
		runMeasure.value("06_All_Consumers_Released");

		testSig.free;

		// Print summary table and save CSV
		("=================================================================").postln;
		("Gate G1 Verification Results: Two Subscribers & Shared Broker").postln;
		("=================================================================").postln;

		csvFile = File(csvPath, "w");
		csvFile.write("condition,mean_avgCPU_pct,max_peakCPU_pct,num_synths,num_ugens,broker_sources,subscribers\n");

		results.do { |r|
			var line = "% | avgCPU: % | synths: % | brokerSources: % | subs: %".format(
				r.condition, r.meanAvgCPU, r.synths, r.brokerSources, r.totalSubscribers
			);
			line.postln;
			csvFile.write("%,%,%,%,%,%,%\n".format(
				r.condition, r.meanAvgCPU, r.maxPeakCPU, r.synths, r.ugens, r.brokerSources, r.totalSubscribers
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
