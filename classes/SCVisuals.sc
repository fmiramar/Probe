/*
    SCVisuals.sc
    SCVisuals — Main Class and Facade Entry Point
    Cohesive, lightweight real-time audio analysis visualizers for SuperCollider.
*/

SCVisuals {
	classvar <version = "0.2.0";

	*broker {
		^SCVMeasurementBroker.default;
	}

	*theme {
		^SCVTheme.current;
	}

	*theme_ { |aTheme|
		SCVTheme.current = SCVTheme.get(aTheme);
	}

	*fontScale {
		^SCVTheme.defaultFontScale;
	}

	*fontScale_ { |scale|
		var s = scale.asFloat.clip(0.6, 2.5);
		SCVTheme.defaultFontScale = s;
		SCVTheme.themes.do { |th| th.fontScale = s };
		if (SCVTheme.current.notNil) { SCVTheme.current.fontScale = s };
	}

	*themes {
		^SCVTheme.themes;
	}

	*sources {
		^this.broker.sources;
	}

	*freeAll {
		this.broker.freeAll;
	}

	*meter { |server, numIns, numOuts, theme=\graphite, density=\compact, meterStyle=\continuous|
		^SCVMeter(server, numIns, numOuts, theme, density, meterStyle);
	}

	*scope { |server, busIndex=0, numChannels=2, theme=\graphite, rate=\audio, style=\overlay, bufSize=4096|
		^SCVScope(server, busIndex, numChannels, theme, rate, style, bufSize);
	}

	*spectrum { |server, busIndex=0, fftSize=2048, theme=\graphite, freqMode=\logarithmic, dbRange=96|
		^SCVSpectrum(server, busIndex, fftSize, theme, freqMode, dbRange);
	}

	*spectrogram { |server, busIndex=0, numBands=64, theme=\graphite, updateFreq=20|
		^SCVSpectrogram(server, busIndex, numBands, theme, updateFreq);
	}

	*waterfall { |server, busIndex=0, numBands=64, theme=\graphite, updateFreq=20, numLines=48|
		^SCVWaterfall(server, busIndex, numBands, theme, updateFreq, numLines);
	}

	*history { |server, busType=\outputs, busIndex=0, numChannels=2, theme=\graphite, updateFreq=20, maxPoints=200|
		^SCVHistory(server, busType, busIndex, numChannels, theme, updateFreq, maxPoints);
	}

	*loudness { |server, busType=\outputs, busIndex=0, numChannels=2, theme=\graphite, targetLUFS=(-23.0), updateFreq=10|
		^SCVLoudness(server, busType, busIndex, numChannels, theme, targetLUFS, updateFreq);
	}

	*vectorscope { |server, busIndex=0, theme=\graphite, updateFreq=15|
		^SCVVectorscope(server, busIndex, theme, updateFreq);
	}

	*dashboard { |server, numIns=2, numOuts=2, busIndex=0, theme=\graphite, targetLUFS=(-23.0)|
		^SCVDashboard(server, numIns, numOuts, busIndex, theme, targetLUFS);
	}
}
