/*
    SCVVectorscopeSource.sc
    SCVisuals — Stereo Correlation & Balance Analysis Source
    Real-time stereo phase correlation [-1.0, +1.0] and left/right balance [-1.0, +1.0]
    analysis source managed by SCVMeasurementBroker.
*/

SCVVectorscopeSource {
	var <key;
	var <server;
	var <busIndex;
	var <updateFreq;
	var <oscCmd;

	var <synth, <oscFunc;
	var <correlation = 1.0;
	var <balance = 0.0;
	var <consumers;
	var isUpdating = false;

	*new { |server, busIndex=0, updateFreq=15|
		^super.new.prInit(server, busIndex, updateFreq);
	}

	prInit { |argServer, argBusIndex, argUpdateFreq|
		server      = argServer ?? { Server.default };
		busIndex    = argBusIndex.asInteger;
		updateFreq  = argUpdateFreq.asInteger.clip(5, 30);

		key = (server.name ++ "_corr_" ++ busIndex ++ "_" ++ updateFreq).asSymbol;
		oscCmd = ("/" ++ key).asSymbol;

		consumers = IdentityDictionary.new;
	}

	start {
		var synthDefName, samps;
		var sRate = server.sampleRate ? 48000;

		if (this.isRunning) { ^this };
		if (server.serverRunning.not) { ^this };

		samps = (sRate * 0.25).round.asInteger; // 250ms integration window
		synthDefName = ("scv_corr_" ++ busIndex ++ "_" ++ updateFreq).asSymbol;

		if (oscFunc.isNil) {
			oscFunc = OSCFunc({ |msg|
				this.prHandleOSC(msg);
			}, oscCmd, server.addr).fix;
		};

		server.bind {
			var sdef = SynthDef(synthDefName, {
				var sig = In.ar(busIndex, 2);
				var sigL = sig[0];
				var sigR = sig[1];

				var dot  = RunningSum.ar(sigL * sigR, samps) / samps;
				var pwrL = RunningSum.ar(sigL.squared, samps) / samps;
				var pwrR = RunningSum.ar(sigR.squared, samps) / samps;

				var corr = (dot / ((pwrL * pwrR).sqrt + 1e-9)).clip(-1.0, 1.0);
				var bal  = ((pwrR.sqrt - pwrL.sqrt) / (pwrR.sqrt + pwrL.sqrt + 1e-9)).clip(-1.0, 1.0);

				SendReply.kr(Impulse.kr(updateFreq), oscCmd, [corr, bal]);
			});

			synth = sdef.play(RootNode(server), nil, \addToTail);
		};
	}

	prHandleOSC { |msg|
		correlation = msg[3];
		balance     = msg[4];

		if (isUpdating.not and: { consumers.notEmpty }) {
			isUpdating = true;
			AppClock.sched(0, {
				isUpdating = false;
				consumers.do { |callback|
					try {
						callback.value(correlation, balance, this);
					} { |err|
						("SCVVectorscopeSource callback error: " ++ err.errorString).warn;
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
}
