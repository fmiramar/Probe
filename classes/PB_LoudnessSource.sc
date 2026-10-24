/*
    PB_LoudnessSource.sc
    Probe — ITU-R BS.1770-4 / EBU R128 Loudness Measurement Source (Internal)
    Real-time K-weighting filter, Momentary (400ms), Short-term (3s),
    Integrated gated loudness with relative/absolute gating, and Loudness Range (LRA).
*/

PB_LoudnessSource {
	var <key;
	var <server;
	var <busType;
	var <busIndex;
	var <numChannels;
	var <updateFreq;
	var <oscCmd;

	var <synth, <oscFunc;
	var <momentaryLUFS = -99.0;
	var <shortTermLUFS = -99.0;
	var <integratedLUFS = -99.0;
	var <loudnessRange = 0.0;
	var <maxPeak = 0.0;
	var <maxMomentary = -99.0;
	var <maxShortTerm = -99.0;

	var blockHistory;       // List of 400ms power values (zi)
	var shortTermHistory;   // List of short-term LUFS values for LRA
	var maxHistoryBlocks = 36000; // ~1 hour at 10 Hz
	var <consumers;         // IdentityDictionary: consumer -> callback
	var isUpdating = false;

	*new { |server, busType=\outputs, busIndex=0, numChannels=2, updateFreq=10|
		^super.new.prInit(server, busType, busIndex, numChannels, updateFreq);
	}

	prInit { |argServer, argBusType, argBusIndex, argNumChannels, argUpdateFreq|
		server       = argServer ?? { Server.default };
		busType      = argBusType.asSymbol;
		busIndex     = argBusIndex.asInteger;
		numChannels  = argNumChannels.asInteger.clip(1, 2); // Mono or Stereo
		updateFreq   = argUpdateFreq.asInteger.clip(5, 30);

		key = (server.name ++ "_loudness_" ++ busType ++ "_" ++ busIndex ++ "_" ++ numChannels ++ "_" ++ updateFreq).asSymbol;
		oscCmd = ("/" ++ key).asSymbol;

		blockHistory = List.new;
		shortTermHistory = List.new;
		consumers = IdentityDictionary.new;
	}

	*calcCoefficients { |sr=48000|
		var db = 3.999843853973347;
		var f0_1 = 1681.974450955533;
		var q1 = 0.7071752369554193;
		var k1 = tan(pi * f0_1 / sr);
		var v = 10.pow(db / 20);
		var v_sqrt = v.sqrt;
		var norm1 = 1 + (k1 / q1) + (k1 * k1);
		var b0_1 = (v + (v_sqrt * (k1 / q1)) + (k1 * k1)) / norm1;
		var b1_1 = (2 * ((k1 * k1) - v)) / norm1;
		var b2_1 = (v - (v_sqrt * (k1 / q1)) + (k1 * k1)) / norm1;
		var a1_1 = (2 * ((k1 * k1) - 1)) / norm1;
		var a2_1 = (1 - (k1 / q1) + (k1 * k1)) / norm1;

		var f0_2 = 38.13547087602444;
		var q2 = 0.5003270373238773;
		var k2 = tan(pi * f0_2 / sr);
		var norm2 = 1 + (k2 / q2) + (k2 * k2);
		var b0_2 = 1.0 / norm2;
		var b1_2 = -2.0 / norm2;
		var b2_2 = 1.0 / norm2;
		var a1_2 = (2 * ((k2 * k2) - 1)) / norm2;
		var a2_2 = (1 - (k2 / q2) + (k2 * k2)) / norm2;

		^(
			stage1: [b0_1, b1_1, b2_1, a1_1.neg, a2_1.neg],
			stage2: [b0_2, b1_2, b2_2, a1_2.neg, a2_2.neg]
		);
	}

	start {
		var synthDefName, targetNode, addAction;
		var coeffs, sRate;
		var samps400, samps3s;

		if (this.isRunning) { ^this };
		if (server.serverRunning.not) { ^this };

		sRate = server.sampleRate ? 48000;
		coeffs = this.class.calcCoefficients(sRate);
		samps400 = (sRate * 0.400).round.asInteger;
		samps3s  = (sRate * 3.000).round.asInteger;

		synthDefName = ("probe_loudness_" ++ busType ++ "_" ++ numChannels ++ "_" ++ updateFreq).asSymbol;

		if (oscFunc.isNil) {
			oscFunc = OSCFunc({ |msg|
				this.prHandleOSC(msg);
			}, oscCmd, server.addr).fix;
		};

		case
			{ busType === \inputs } {
				targetNode = 1;
				addAction  = \addToHead;
			}
			{ busType === \outputs } {
				targetNode = 1;
				addAction  = \addToTail;
			}
			{ true } {
				targetNode = 1;
				addAction  = \addToTail;
			};

		server.bind {
			var sdef = SynthDef(synthDefName, {
				var inBus = case
					{ busType === \inputs }  { NumOutputBuses.ir + busIndex }
					{ busType === \outputs } { busIndex }
					{ busIndex };
				var sig = In.ar(inBus, numChannels);
				var sigK, pwr400, pwr3s, peak;

				// Stage 1: High shelf filter
				sigK = SOS.ar(sig, coeffs[\stage1][0], coeffs[\stage1][1], coeffs[\stage1][2], coeffs[\stage1][3], coeffs[\stage1][4]);
				// Stage 2: High pass filter (RLB)
				sigK = SOS.ar(sigK, coeffs[\stage2][0], coeffs[\stage2][1], coeffs[\stage2][2], coeffs[\stage2][3], coeffs[\stage2][4]);

				// Running mean square over 400ms and 3.0s
				pwr400 = RunningSum.ar(sigK.squared, samps400) / samps400;
				pwr3s  = RunningSum.ar(sigK.squared, samps3s)  / samps3s;
				peak   = Peak.ar(sig.abs);

				SendReply.kr(Impulse.kr(updateFreq), oscCmd, pwr400.asArray ++ pwr3s.asArray ++ peak.asArray);
			});

			synth = sdef.play(RootNode(server), nil, addAction);
		};
	}

	prHandleOSC { |msg|
		var vals = msg[3..];
		var n = numChannels;
		var pwr400 = vals[0 .. (n - 1)];
		var pwr3s  = vals[n .. (2 * n - 1)];
		var peaks  = vals[(2 * n) .. (3 * n - 1)];
		var zM = 0.0, zS = 0.0, currentPeak = 0.0;

		// Channel weights G_i: 1.0 for Left and Right
		n.do { |i|
			zM = zM + pwr400[i];
			zS = zS + pwr3s[i];
			currentPeak = currentPeak.max(peaks[i]);
		};

		// 1. Momentary LUFS (M)
		if (zM > 1e-10) {
			momentaryLUFS = -0.691 + (10.0 * zM.log10);
		} {
			momentaryLUFS = -99.0;
		};
		maxMomentary = maxMomentary.max(momentaryLUFS);

		// 2. Short-Term LUFS (S)
		if (zS > 1e-10) {
			shortTermLUFS = -0.691 + (10.0 * zS.log10);
		} {
			shortTermLUFS = -99.0;
		};
		maxShortTerm = maxShortTerm.max(shortTermLUFS);

		// 3. Peak Tracking
		maxPeak = maxPeak.max(currentPeak);

		// 4. Integrated Loudness (I) History & Gating
		if (blockHistory.size >= maxHistoryBlocks) {
			blockHistory.removeAt(0);
		};
		blockHistory.add(zM);

		if (shortTermLUFS > -70.0) {
			if (shortTermHistory.size >= 3600) { shortTermHistory.removeAt(0) };
			shortTermHistory.add(shortTermLUFS);
		};

		this.prCalculateIntegrated;

		// Dispatch to consumers on AppClock
		if (isUpdating.not and: { consumers.notEmpty }) {
			isUpdating = true;
			AppClock.sched(0, {
				isUpdating = false;
				consumers.do { |cb|
					try {
						cb.value(momentaryLUFS, shortTermLUFS, integratedLUFS, loudnessRange, maxPeak, this);
					} { |err|
						("PB_LoudnessSource callback error: " ++ err.errorString).warn;
					};
				};
				nil;
			});
		};
	}

	prCalculateIntegrated {
		var absThresh = 1.171e-7; // -70 LUFS in power units (10^(-70.691/10))
		var gatedBlocks = blockHistory.select({ |z| z >= absThresh });
		var meanUngated, relThresh, finalBlocks, finalMean;

		if (gatedBlocks.isEmpty) {
			integratedLUFS = -99.0;
			^this;
		};

		// Step 1: Preliminary ungated mean
		meanUngated = gatedBlocks.sum / gatedBlocks.size;
		// Step 2: Relative gate threshold (-10 LU below ungated loudness)
		relThresh = meanUngated * 0.1; // 10^(-1.0) = 0.1

		finalBlocks = gatedBlocks.select({ |z| z >= relThresh });
		if (finalBlocks.isEmpty) {
			integratedLUFS = -99.0;
			^this;
		};

		finalMean = finalBlocks.sum / finalBlocks.size;
		integratedLUFS = -0.691 + (10.0 * finalMean.log10);

		// Loudness Range (LRA): 95th percentile - 10th percentile of short-term values
		if (shortTermHistory.size >= 10) {
			var sorted = shortTermHistory.asArray.sort;
			var idx10 = (sorted.size * 0.10).asInteger.clip(0, sorted.size - 1);
			var idx95 = (sorted.size * 0.95).asInteger.clip(0, sorted.size - 1);
			loudnessRange = (sorted[idx95] - sorted[idx10]).max(0.0).round(0.1);
		};
	}

	reset {
		blockHistory.clear;
		shortTermHistory.clear;
		momentaryLUFS = -99.0;
		shortTermLUFS = -99.0;
		integratedLUFS = -99.0;
		loudnessRange = 0.0;
		maxPeak = 0.0;
		maxMomentary = -99.0;
		maxShortTerm = -99.0;
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
		blockHistory.clear;
		shortTermHistory.clear;
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
