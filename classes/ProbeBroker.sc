/*
    ProbeBroker.sc
    Probe — Centralized Measurement Broker
    Manages analysis source lifecycles, reference-counts consumer subscriptions,
    prevents duplicate measurement synths, and handles server reboot/CmdPeriod cleanly.
*/

ProbeBroker {
	classvar <default;

	var <sources;          // key -> sourceInstance
	var <subscriptions;    // key -> Set of consumers
	var serversMonitored;

	*initClass {
		default = this.new;
	}

	*new {
		^super.new.prInit;
	}

	prInit {
		sources = IdentityDictionary.new;
		subscriptions = IdentityDictionary.new;
		serversMonitored = Set.new;

		CmdPeriod.add(this);
	}

	prEnsureServerMonitored { |server|
		if (serversMonitored.includes(server).not) {
			serversMonitored.add(server);
			ServerQuit.add(this, server);
			ServerTree.add(this, server);
		};
	}

	// Subscribe to a Levels measurement source
	subscribeLevels { |consumer, server, busType=\outputs, busIndex=0, numChannels=2, updateFreq=15, onDataCallback|
		var source, key;

		server = server ?? { Server.default };
		this.prEnsureServerMonitored(server);

		key = (server.name ++ "_levels_" ++ busType ++ "_" ++ busIndex ++ "_" ++ numChannels ++ "_" ++ updateFreq).asSymbol;

		source = sources[key];
		if (source.isNil) {
			source = ProbeLevelsSource(server, busType, busIndex, numChannels, updateFreq);
			sources.put(key, source);
			subscriptions.put(key, Set.new);
		};

		subscriptions[key].add(consumer);
		source.addConsumer(consumer, onDataCallback);

		^source;
	}

	// Subscribe to a Spectrum measurement source
	subscribeSpectrum { |consumer, server, busIndex=0, numBands=64, updateFreq=20, onDataCallback|
		var source, key;

		server = server ?? { Server.default };
		this.prEnsureServerMonitored(server);

		key = (server.name ++ "_spectrum_" ++ busIndex ++ "_" ++ numBands ++ "_" ++ updateFreq).asSymbol;

		source = sources[key];
		if (source.isNil) {
			source = ProbeSpectrumSource(server, busIndex, numBands, updateFreq);
			sources.put(key, source);
			subscriptions.put(key, Set.new);
		};

		subscriptions[key].add(consumer);
		source.addConsumer(consumer, onDataCallback);

		^source;
	}

	// Subscribe to a Loudness measurement source (ITU-R BS.1770 / EBU R128)
	subscribeLoudness { |consumer, server, busType=\outputs, busIndex=0, numChannels=2, updateFreq=10, onDataCallback|
		var source, key;

		server = server ?? { Server.default };
		this.prEnsureServerMonitored(server);

		key = (server.name ++ "_loudness_" ++ busType ++ "_" ++ busIndex ++ "_" ++ numChannels ++ "_" ++ updateFreq).asSymbol;

		source = sources[key];
		if (source.isNil) {
			source = ProbeLoudnessSource(server, busType, busIndex, numChannels, updateFreq);
			sources.put(key, source);
			subscriptions.put(key, Set.new);
		};

		subscriptions[key].add(consumer);
		source.addConsumer(consumer, onDataCallback);

		^source;
	}

	// Subscribe to a Stereo Correlation & Balance measurement source
	subscribeCorrelation { |consumer, server, busIndex=0, updateFreq=15, onDataCallback|
		var source, key;

		server = server ?? { Server.default };
		this.prEnsureServerMonitored(server);

		key = (server.name ++ "_corr_" ++ busIndex ++ "_" ++ updateFreq).asSymbol;

		source = sources[key];
		if (source.isNil) {
			source = ProbeVectorscopeSource(server, busIndex, updateFreq);
			sources.put(key, source);
			subscriptions.put(key, Set.new);
		};

		subscriptions[key].add(consumer);
		source.addConsumer(consumer, onDataCallback);

		^source;
	}

	// Subscribe to a Probe spectral telemetry source (descriptors, pitch, chroma, bands)
	subscribeSpectral { |consumer, server, busIndex=0, updateFreq=15, onDataCallback|
		var source, key;

		server = server ?? { Server.default };
		this.prEnsureServerMonitored(server);

		key = (server.name ++ "_probespec_" ++ busIndex ++ "_" ++ updateFreq).asSymbol;

		source = sources[key];
		if (source.isNil) {
			source = ProbeSpectralSource(server, busIndex, updateFreq);
			sources.put(key, source);
			subscriptions.put(key, Set.new);
		};

		subscriptions[key].add(consumer);
		source.addConsumer(consumer, onDataCallback);

		^source;
	}

	// Unsubscribe consumer from a specific source
	unsubscribe { |consumer, sourceKey|
		var source = sources[sourceKey.asSymbol];
		var subs = subscriptions[sourceKey.asSymbol];

		if (subs.notNil) {
			subs.remove(consumer);
			if (source.notNil) {
				source.removeConsumer(consumer);
				if (subs.isEmpty) {
					source.free;
					sources.removeAt(sourceKey.asSymbol);
					subscriptions.removeAt(sourceKey.asSymbol);
				};
			};
		};
	}

	// Unsubscribe consumer from all sources it currently consumes
	unsubscribeAll { |consumer|
		var keysToRelease = List.new;

		subscriptions.keysValuesDo { |key, subs|
			if (subs.includes(consumer)) {
				keysToRelease.add(key);
			};
		};

		keysToRelease.do { |key|
			this.unsubscribe(consumer, key);
		};
	}

	// Active source count
	sourceCount {
		^sources.size;
	}

	// Active subscriber count across all sources
	totalSubscriberCount {
		var count = 0;
		subscriptions.do { |s| count = count + s.size };
		^count;
	}

	// ServerQuit hook
	doOnServerQuit { |server|
		sources.do { |source|
			if (source.server === server) {
				source.onServerQuit;
			};
		};
	}

	// ServerTree hook (triggered on server boot and CmdPeriod)
	doOnServerTree { |server|
		sources.do { |source|
			if (source.server === server) {
				source.onServerTree;
			};
		};
	}

	// CmdPeriod hook
	cmdPeriod {
		sources.do { |source|
			if (source.server.serverRunning) {
				source.onServerTree;
			};
		};
	}

	// Complete teardown
	freeAll {
		sources.do { |source| source.free };
		sources.clear;
		subscriptions.clear;
	}

	printOn { |stream|
		stream << "ProbeBroker(sources: " << sources.size << ", subscribers: " << this.totalSubscriberCount << ")";
	}
}
