/*
    Probe.sc
    Probe — High-Performance Audio Visualization & Instrumentation Suite for SuperCollider
    Top-level facade providing clean, ergonomic access to all measurement instruments.
*/

Probe {
	*version {
		^"0.4.0";
	}

	*meter { |server, numIns, numOuts, theme, density, meterStyle|
		^ProbeMeter(server, numIns: numIns, numOuts: numOuts, theme: theme, density: density, meterStyle: meterStyle);
	}

	*scope { |server, busIndex=0, numChannels=2, theme, rate=\audio, style=\tracks, bufSize=4096|
		^ProbeScope(server, busIndex: busIndex, numChannels: numChannels, theme: theme, rate: rate, style: style, bufSize: bufSize);
	}

	*freqscope { |server, busIndex=0, fftSize=2048, theme, dbRange=96.0, freqMode=1|
		^ProbeSpectrum(server, busIndex: busIndex, fftSize: fftSize, theme: theme, dbRange: dbRange, freqMode: freqMode);
	}

	*spectrum { |server, busIndex=0, fftSize=2048, theme, dbRange=96.0, freqMode=1|
		^this.freqscope(server, busIndex: busIndex, fftSize: fftSize, theme: theme, dbRange: dbRange, freqMode: freqMode);
	}

	*spectrogram { |server, busIndex=0, fftSize=64, theme, fps=25|
		^ProbeSpectrogram(server, busIndex: busIndex, fftSize: fftSize, theme: theme, fps: fps);
	}

	*waterfall { |server, busIndex=0, fftSize=64, theme, fps=25, numLines=48|
		^ProbeWaterfall(server, busIndex: busIndex, fftSize: fftSize, theme: theme, fps: fps, numLines: numLines);
	}

	*history { |server, busType=\outputs, busIndex=0, numChannels=2, theme, fps=25, maxHistory=200|
		^ProbeHistory(server, busType: busType, busIndex: busIndex, numChannels: numChannels, theme: theme, fps: fps, maxHistory: maxHistory);
	}

	*loudness { |server, busType=\outputs, busIndex=0, numChannels=2, theme, targetLUFS= -23.0, updateFreq=15|
		^ProbeLoudness(server, busType: busType, busIndex: busIndex, numChannels: numChannels, theme: theme, targetLUFS: targetLUFS, updateFreq: updateFreq);
	}

	*vectorscope { |server, busIndex=0, theme, fps=15|
		^ProbeVectorscope(server, busIndex: busIndex, theme: theme, fps: fps);
	}

	*dashboard { |server, numIns, numOuts, busIndex=0, theme, targetLUFS= -23.0|
		^ProbeDashboard(server, numIns: numIns, numOuts: numOuts, busIndex: busIndex, theme: theme, targetLUFS: targetLUFS);
	}

	*spectralDashboard { |server, busIndex=0, theme|
		^ProbeSpectralDashboard(server, busIndex: busIndex, theme: theme);
	}

	*bandAnalyzer { |server, busIndex=0, theme, smooth=0.7|
		^ProbeBandAnalyzer(server, busIndex: busIndex, theme: theme, smooth: smooth);
	}

	*inspect { |synthDef, theme|
		^ProbeInspector(synthDef, theme);
	}

	*inspector { |synthDef, theme|
		^this.inspect(synthDef, theme);
	}

	// Broker & Themes
	*broker {
		^PB_Broker.default;
	}

	*theme {
		^ProbeTheme.current;
	}

	*theme_ { |aTheme|
		ProbeTheme.current = ProbeTheme.get(aTheme);
	}

	*fontScale {
		^ProbeTheme.defaultFontScale;
	}

	*fontScale_ { |scale|
		var s = scale.asFloat.clip(0.6, 2.5);
		ProbeTheme.defaultFontScale = s;
		ProbeTheme.themes.do { |th| th.fontScale = s };
		if (ProbeTheme.current.notNil) { ProbeTheme.current.fontScale = s };
	}

	*defaultFontScale {
		^ProbeTheme.defaultFontScale;
	}

	*defaultFontScale_ { |scale|
		this.fontScale = scale;
	}

	*themes {
		^ProbeTheme.themes;
	}

	*sources {
		^this.broker.sources;
	}

	*freeAll {
		this.broker.freeAll;
	}
}
