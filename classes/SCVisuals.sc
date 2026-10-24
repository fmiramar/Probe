/*
    SCVisuals.sc
    SCVisuals — Main Class and Facade Entry Point
    Cohesive, lightweight real-time audio analysis visualizers for SuperCollider.
*/

SCVisuals {
	classvar <version = "0.4.0";

	*broker {
		^Probe.broker;
	}

	*theme {
		^Probe.theme;
	}

	*theme_ { |aTheme|
		Probe.theme = aTheme;
	}

	*fontScale {
		^Probe.fontScale;
	}

	*fontScale_ { |scale|
		Probe.fontScale = scale;
	}

	*themes {
		^Probe.themes;
	}

	*sources {
		^Probe.sources;
	}

	*freeAll {
		Probe.freeAll;
	}

	*meter { |server, numIns, numOuts, theme=\graphite, density=\compact, meterStyle=\continuous|
		^Probe.meter(server, numIns, numOuts, theme, density, meterStyle);
	}

	*scope { |server, busIndex=0, numChannels=2, theme=\graphite, rate=\audio, style=\overlay, bufSize=4096|
		^Probe.scope(server, busIndex, numChannels, theme, rate, style, bufSize);
	}

	*spectrum { |server, busIndex=0, fftSize=2048, theme=\graphite, freqMode=\logarithmic, dbRange=96|
		^Probe.spectrum(server, busIndex, fftSize, theme, dbRange, freqMode);
	}

	*spectrogram { |server, busIndex=0, numBands=64, theme=\graphite, updateFreq=20|
		^Probe.spectrogram(server, busIndex, numBands, theme, updateFreq);
	}

	*waterfall { |server, busIndex=0, numBands=64, theme=\graphite, updateFreq=20, numLines=48|
		^Probe.waterfall(server, busIndex, numBands, theme, updateFreq, numLines);
	}

	*history { |server, busType=\outputs, busIndex=0, numChannels=2, theme=\graphite, updateFreq=20, maxPoints=200|
		^Probe.history(server, busType, busIndex, numChannels, theme, updateFreq, maxPoints);
	}

	*loudness { |server, busType=\outputs, busIndex=0, numChannels=2, theme=\graphite, targetLUFS=(-23.0), updateFreq=10|
		^Probe.loudness(server, busType, busIndex, numChannels, theme, targetLUFS, updateFreq);
	}

	*vectorscope { |server, busIndex=0, theme=\graphite, updateFreq=15|
		^Probe.vectorscope(server, busIndex, theme, updateFreq);
	}

	*dashboard { |server, numIns=2, numOuts=2, busIndex=0, theme=\graphite, targetLUFS=(-23.0)|
		^Probe.dashboard(server, numIns, numOuts, busIndex, theme, targetLUFS);
	}
}
