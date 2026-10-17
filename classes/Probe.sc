/*
    Probe.sc
    Probe — High-Performance Audio Visualization & Instrumentation Suite for SuperCollider
    Top-level facade providing clean, ergonomic access to all measurement instruments.
*/

Probe {
	*meter { |server, numIns, numOuts, theme, density, meterStyle|
		^SCVMeter(server, numIns: numIns, numOuts: numOuts, theme: theme, density: density, meterStyle: meterStyle);
	}

	*scope { |server, busIndex=0, numChannels=2, theme, rate=\audio, style=\overlay, bufSize=4096|
		^SCVScope(server, busIndex: busIndex, numChannels: numChannels, theme: theme, rate: rate, style: style, bufSize: bufSize);
	}

	*freqscope { |server, busIndex=0, fftSize=2048, theme, dbRange=96.0, freqMode=1|
		^SCVSpectrum(server, busIndex: busIndex, fftSize: fftSize, theme: theme, dbRange: dbRange, freqMode: freqMode);
	}

	*spectrum { |server, busIndex=0, fftSize=2048, theme, dbRange=96.0, freqMode=1|
		^this.freqscope(server, busIndex: busIndex, fftSize: fftSize, theme: theme, dbRange: dbRange, freqMode: freqMode);
	}

	*spectrogram { |server, busIndex=0, fftSize=64, theme, fps=25|
		^SCVSpectrogram(server, busIndex: busIndex, fftSize: fftSize, theme: theme, fps: fps);
	}

	*waterfall { |server, busIndex=0, fftSize=64, theme, fps=25, numLines=48|
		^SCVWaterfall(server, busIndex: busIndex, fftSize: fftSize, theme: theme, fps: fps, numLines: numLines);
	}

	*history { |server, busType=\outputs, busIndex=0, numChannels=2, theme, fps=25, maxHistory=200|
		^SCVHistory(server, busType: busType, busIndex: busIndex, numChannels: numChannels, theme: theme, fps: fps, maxHistory: maxHistory);
	}

	*loudness { |server, busType=\outputs, busIndex=0, numChannels=2, theme, targetLUFS= -23.0, updateFreq=15|
		^SCVLoudness(server, busType: busType, busIndex: busIndex, numChannels: numChannels, theme: theme, targetLUFS: targetLUFS, updateFreq: updateFreq);
	}

	*vectorscope { |server, busIndex=0, theme, fps=15|
		^SCVVectorscope(server, busIndex: busIndex, theme: theme, fps: fps);
	}

	*dashboard { |server, numIns, numOuts, busIndex=0, theme, targetLUFS= -23.0|
		^SCVDashboard(server, numIns: numIns, numOuts: numOuts, busIndex: busIndex, theme: theme, targetLUFS: targetLUFS);
	}

	*spectralDashboard { |server, busIndex=0, theme|
		^ProbeSpectralDashboard(server, busIndex: busIndex, theme: theme);
	}

	*bandAnalyzer { |server, busIndex=0, theme|
		^ProbeBandAnalyzer(server, busIndex: busIndex, theme: theme);
	}

	*inspect { |synthDef, theme|
		^ProbeInspector(synthDef, theme);
	}

	*inspector { |synthDef, theme|
		^this.inspect(synthDef, theme);
	}

	// Broker & Themes
	*broker {
		^SCVisuals.broker;
	}

	*theme {
		^SCVTheme.current;
	}

	*theme_ { |name|
		SCVisuals.theme = name;
	}

	*fontScale {
		^SCVisuals.fontScale;
	}

	*fontScale_ { |scale|
		SCVisuals.fontScale = scale;
	}

	*defaultFontScale {
		^SCVTheme.defaultFontScale;
	}

	*defaultFontScale_ { |scale|
		SCVTheme.defaultFontScale = scale;
	}
}
