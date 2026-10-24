/*
    PB_SpectrumSource.sc
    Probe — Shared Logarithmic Spectral Band Analysis Source (Internal)
    Generates calibrated logarithmic spectral bands and streams frames via SendReply.kr,
    allowing multiple subscribers (Spectrogram, Waterfall) to share a single analysis synth.
*/

PB_SpectrumSource {
	var <key;
	var <server;
	var <busIndex;
	var <numBands;
	var <updateFreq;
	var <oscCmd;

	var synth, oscFunc;
	var <currentBands;
	var <consumers;     // IdentityDictionary: consumer -> callback
	var isUpdating = false;

	*new { |server, busIndex=0, numBands=64, updateFreq=20|
		^super.new.prInit(server, busIndex, numBands, updateFreq);
	}

	prInit { |argServer, argBusIndex, argNumBands, argUpdateFreq|
		server      = argServer ?? { Server.default };
		busIndex    = argBusIndex.asInteger;
		numBands    = argNumBands.asInteger.clip(16, 128);
		updateFreq  = argUpdateFreq.asInteger.clip(5, 30);

		key = (server.name ++ "_spectrum_" ++ busIndex ++ "_" ++ numBands ++ "_" ++ updateFreq).asSymbol;
		oscCmd = ("/" ++ key).asSymbol;

		currentBands = FloatArray.fill(numBands, 0.0);
		consumers = IdentityDictionary.new;
	}

	start {
		var synthDefName;

		if (this.isRunning) { ^this };
		if (server.serverRunning.not) { ^this };

		synthDefName = ("probe_specsource_" ++ numBands ++ "_" ++ updateFreq).asSymbol;

		if (oscFunc.isNil) {
			oscFunc = OSCFunc({ |msg|
				this.prHandleOSC(msg);
			}, oscCmd, server.addr).fix;
		};

		server.bind {
			var sdef = SynthDef(synthDefName, {
				var in = In.ar(busIndex, 1);
				var trig = Impulse.kr(updateFreq);
				// Logarithmically spaced filterbank from 40 Hz to 18,000 Hz
				var bands = Array.fill(numBands, { |i|
					var freq = (i / (numBands - 1)).linexp(0, 1, 40, 18000);
					var q = 0.15;
					Amplitude.kr(BPF.ar(in, freq, q), 0.01, 0.06);
				});
				SendReply.kr(trig, oscCmd, bands);
			});
			synth = sdef.play(RootNode(server), nil, \addToTail);
		};
	}

	prHandleOSC { |msg|
		var bandCount = min(msg.size - 3, numBands);

		bandCount.do { |i|
			currentBands[i] = msg[3 + i];
		};

		if (isUpdating.not and: { consumers.notEmpty }) {
			isUpdating = true;
			AppClock.sched(0, {
				isUpdating = false;
				consumers.do { |callback|
					try {
						callback.value(currentBands, this);
					} { |err|
						("PB_SpectrumSource callback error: " ++ err.errorString).warn;
					};
				};
				nil;
			});
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
			server.sendBundle(nil, ['/error', -1], [11, synth.nodeID], ['/error', -2]);
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
		stream << "PB_SpectrumSource(" << key << ", consumers: " << consumers.size << ", running: " << this.isRunning << ")";
	}
}
