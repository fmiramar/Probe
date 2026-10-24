/*
    ProbeLevelsSource.sc
    Probe — Peak & RMS Levels Measurement Source
    Centralized SendPeakRMS analysis source with consumer reference counting,
    bounded presentation state, and coalesced AppClock notification.
*/

ProbeLevelsSource {
	var <key;
	var <server;
	var <busType;       // \inputs, \outputs, \bus
	var <busIndex;      // bus index if busType == \bus
	var <numChannels;
	var <updateFreq;
	var <oscCmd;

	var <synth, <oscFunc;
	var <peakLevels, <rmsLevels, <peakHoldLevels;
	var <history;       // Bounded ring buffer: Array of [peak, rms] snapshots
	var historyMax = 120, historyIdx = 0;
	var <consumers;     // IdentityDictionary: consumer -> callback
	var isUpdating = false;

	*new { |server, busType=\outputs, busIndex=0, numChannels=2, updateFreq=15|
		^super.new.prInit(server, busType, busIndex, numChannels, updateFreq);
	}

	prInit { |argServer, argBusType, argBusIndex, argNumChannels, argUpdateFreq|
		server      = argServer ?? { Server.default };
		busType     = argBusType.asSymbol;
		busIndex    = argBusIndex.asInteger;
		numChannels = argNumChannels.asInteger.max(1);
		updateFreq  = argUpdateFreq.asInteger.clip(5, 60);

		key = (server.name ++ "_levels_" ++ busType ++ "_" ++ busIndex ++ "_" ++ numChannels ++ "_" ++ updateFreq).asSymbol;
		oscCmd = ("/" ++ key).asSymbol;

		peakLevels     = FloatArray.fill(numChannels, 0.0);
		rmsLevels      = FloatArray.fill(numChannels, 0.0);
		peakHoldLevels = FloatArray.fill(numChannels, 0.0);
		history        = Array.fill(historyMax, { [FloatArray.fill(numChannels, 0.0), FloatArray.fill(numChannels, 0.0)] });

		consumers = IdentityDictionary.new;
	}

	start {
		var synthDefName, targetNode, addAction;

		if (this.isRunning) { ^this };
		if (server.serverRunning.not) { ^this };

		synthDefName = ("probe_levels_" ++ busType ++ "_" ++ numChannels ++ "_" ++ updateFreq).asSymbol;

		// Single OSCFunc for all consumers of this source
		if (oscFunc.isNil) {
			oscFunc = OSCFunc({ |msg|
				this.prHandleOSC(msg);
			}, oscCmd, server.addr).fix;
		};

		server.bind {
			var sdef;
			switch(busType,
				\inputs, {
					targetNode = RootNode(server);
					addAction  = \addToHead;
					sdef = SynthDef(synthDefName, {
						var in = In.ar(NumOutputBuses.ir, numChannels);
						SendPeakRMS.kr(in, updateFreq, 3, oscCmd);
					});
				},
				\outputs, {
					targetNode = RootNode(server);
					addAction  = \addToTail;
					sdef = SynthDef(synthDefName, {
						var out = In.ar(0, numChannels);
						SendPeakRMS.kr(out, updateFreq, 3, oscCmd);
					});
				},
				{
					targetNode = RootNode(server);
					addAction  = \addToTail;
					sdef = SynthDef(synthDefName, {
						var sig = In.ar(busIndex, numChannels);
						SendPeakRMS.kr(sig, updateFreq, 3, oscCmd);
					});
				}
			);
			synth = sdef.play(targetNode, nil, addAction);
		};
	}

	prHandleOSC { |msg|
		var chCount = min((msg.size - 3) / 2, numChannels);

		chCount.do { |ch|
			var base = 3 + (ch * 2);
			var p = msg[base];
			var r = msg[base + 1];
			peakLevels[ch] = p;
			rmsLevels[ch] = r;
			if (p > peakHoldLevels[ch]) {
				peakHoldLevels[ch] = p;
			};
		};

		// Record snapshot in bounded ring buffer
		history[historyIdx] = [peakLevels.copy, rmsLevels.copy];
		historyIdx = (historyIdx + 1) % historyMax;

		// Coalesce notification to registered consumers via single AppClock dispatch
		if (isUpdating.not and: { consumers.notEmpty }) {
			isUpdating = true;
			AppClock.sched(0, {
				isUpdating = false;
				consumers.do { |callback|
					try {
						callback.value(peakLevels, rmsLevels, peakHoldLevels, this);
					} { |err|
						("ProbeLevelsSource callback error: " ++ err.errorString).warn;
					};
				};
				nil;
			});
		};
	}

	resetPeakHold {
		numChannels.do { |ch|
			peakHoldLevels[ch] = peakLevels[ch];
		};
	}

	addConsumer { |consumer, callback|
		consumers.put(consumer, callback);
		if (this.isRunning.not and: { server.serverRunning }) {
			this.start;
		};
	}

	removeConsumer { |consumer|
		consumers.removeAt(consumer);
	}

	consumerCount {
		^consumers.size;
	}

	isRunning {
		^synth.notNil and: { oscFunc.notNil };
	}

	stop {
		if (synth.notNil) {
			synth.free;
			synth = nil;
		};
		if (oscFunc.notNil) {
			oscFunc.free;
			oscFunc = nil;
		};
		isUpdating = false;
	}

	free {
		this.stop;
		consumers.clear;
	}

	onServerQuit {
		synth = nil;
		if (oscFunc.notNil) {
			oscFunc.free;
			oscFunc = nil;
		};
		isUpdating = false;
	}

	onServerTree {
		synth = nil;
		if (consumers.notEmpty and: { server.serverRunning }) {
			this.start;
		};
	}

	printOn { |stream|
		stream << "ProbeLevelsSource(" << key << ", consumers: " << consumers.size << ", running: " << this.isRunning << ")";
	}
}
