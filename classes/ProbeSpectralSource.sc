/*
    ProbeSpectralSource.sc
    Probe — High-Performance Shared Spectral Telemetry Source
    Computes spectral descriptors (Centroid, Flatness, Roll-off, Flux/Onsets, Pitch, Chroma, and Bands)
    in a single reference-counted analysis synth per monitored bus, eliminating duplicate FFT chains.
*/

ProbeSpectralSource {
	var <server;
	var <busIndex;
	var <updateFreq;
	var <key, <oscCmd;

	var synth, oscFunc, fftBuf;
	var <currentData;
	var <consumers;
	var isUpdating = false;

	*new { |server, busIndex=0, updateFreq=15|
		^super.new.prInit(server, busIndex, updateFreq);
	}

	prInit { |argServer, argBusIndex, argUpdateFreq|
		server      = argServer ?? { Server.default };
		busIndex    = argBusIndex.asInteger;
		updateFreq  = argUpdateFreq.asInteger.clip(5, 30);

		key = (server.name ++ "_probespec_" ++ busIndex ++ "_" ++ updateFreq).asSymbol;
		oscCmd = ("/" ++ key).asSymbol;

		consumers = IdentityDictionary.new;
		currentData = (
			centroid: 1000.0,
			flatness: 0.1,
			rolloff: 4000.0,
			onset: 0.0,
			pitch: 440.0,
			hasPitch: 1.0,
			bands: FloatArray.fill(16, -80.0),
			chroma: FloatArray.fill(12, 0.0)
		);
	}

	isRunning {
		^synth.notNil;
	}

	start {
		var synthDefName, sdef;

		if (this.isRunning) { ^this };
		if (server.serverRunning.not) { ^this };

		synthDefName = ("probe_spec_source_" ++ updateFreq).asSymbol;

		if (oscFunc.isNil) {
			oscFunc = OSCFunc({ |msg|
				this.prHandleOSC(msg);
			}, oscCmd, server.addr).fix;
		};

		server.bind {
			if (fftBuf.isNil) {
				fftBuf = Buffer.alloc(server, 2048, 1);
			};

			sdef = SynthDef(synthDefName, {
				var in = In.ar(busIndex, 1);
				var chain = FFT(fftBuf.bufnum, in);
				var trig = Impulse.kr(updateFreq);

				// 1. Spectral Descriptors
				var centroid = SpecCentroid.kr(chain).max(20.0);
				var flatness = SpecFlatness.kr(chain).clip(0.0, 1.0);
				var rolloff  = SpecPcile.kr(chain, 0.85).max(20.0);
				var onset    = Onsets.kr(chain, threshold: 0.3);

				// 2. Pitch & Confidence Tracker
				var pitchData = Pitch.kr(in, initFreq: 440, minFreq: 40, maxFreq: 4000);
				var pitchFreq = pitchData[0];
				var hasPitch  = pitchData[1];

				// 3. 16-Band Spectral Energy (Logarithmic ISO-spaced centers: 31Hz - 16kHz)
				var freqs = [31, 63, 125, 250, 400, 630, 800, 1000, 1600, 2500, 4000, 6300, 8000, 10000, 12500, 16000];
				var bands = freqs.collect { |f|
					var bpf = BPF.ar(in, f, 0.25);
					var amp = Amplitude.kr(bpf, 0.01, 0.08);
					amp.ampdb.clip(-90.0, 0.0);
				};

				// 4. Chroma 12-pitch-class approximations (C through B)
				var chromaFreqs = [261.63, 277.18, 293.66, 311.13, 329.63, 349.23, 369.99, 392.00, 415.30, 440.00, 466.16, 493.88];
				var chroma = chromaFreqs.collect { |cf|
					var oct1 = BPF.ar(in, cf, 0.08);
					var oct2 = BPF.ar(in, cf * 2, 0.08);
					var oct3 = BPF.ar(in, cf * 0.5, 0.08);
					var sum = Amplitude.kr(oct1 + oct2 + oct3, 0.01, 0.08);
					sum.clip(0.0, 1.0);
				};

				// Bundle telemetry and transmit at updateFreq
				SendReply.kr(trig, oscCmd, [centroid, flatness, rolloff, onset, pitchFreq, hasPitch] ++ bands ++ chroma);
			});
			synth = sdef.play(RootNode(server), nil, \addToTail);
		};
	}

	prHandleOSC { |msg|
		var vals = msg[3..];
		if (vals.size >= 34) {
			currentData[\centroid] = vals[0];
			currentData[\flatness] = vals[1];
			currentData[\rolloff]  = vals[2];
			currentData[\onset]    = vals[3];
			currentData[\pitch]    = vals[4];
			currentData[\hasPitch] = vals[5];

			16.do { |i| currentData[\bands][i] = vals[6 + i] };
			12.do { |i| currentData[\chroma][i] = vals[22 + i] };

			consumers.do { |callback|
				callback.value(currentData);
			};
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
		if (consumers.size == 0) {
			this.stop;
		};
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
		if (fftBuf.notNil) {
			fftBuf.free;
			fftBuf = nil;
		};
		isUpdating = false;
	}

	free {
		this.stop;
		consumers.clear;
	}

	onServerQuit {
		synth = nil;
		fftBuf = nil;
		if (oscFunc.notNil) {
			oscFunc.free;
			oscFunc = nil;
		};
		isUpdating = false;
	}

	onServerTree {
		synth = nil;
		fftBuf = nil;
		if (consumers.notEmpty and: { server.serverRunning }) {
			this.start;
		};
	}
}
