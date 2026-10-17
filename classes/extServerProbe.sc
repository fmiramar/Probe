/*
    extServerProbe.sc
    Probe — Server Class Extensions
    Adds non-conflicting convenience methods to Server.
    NOTE: s.meter, s.scope, and s.freqscope are core SuperCollider methods and are NEVER overridden.
*/

+ Server {
	meter2 { |numIns, numOuts, theme, density, meterStyle|
		^Probe.meter(this, numIns, numOuts, theme, density, meterStyle);
	}

	scope2 { |busIndex=0, numChannels=2, theme, rate=\audio, style=\overlay, bufSize=4096|
		^Probe.scope(this, busIndex, numChannels, theme, rate, style, bufSize);
	}

	freqscope2 { |busIndex=0, fftSize=2048, theme, dbRange=96.0, freqMode=1|
		^Probe.freqscope(this, busIndex, fftSize, theme, dbRange, freqMode);
	}

	spectrum { |busIndex=0, fftSize=2048, theme, dbRange=96.0, freqMode=1|
		^Probe.spectrum(this, busIndex, fftSize, theme, dbRange, freqMode);
	}

	spectrum2 { |busIndex=0, fftSize=2048, theme, dbRange=96.0, freqMode=1|
		^Probe.spectrum(this, busIndex, fftSize, theme, dbRange, freqMode);
	}

	spectrogram { |busIndex=0, fftSize=64, theme, fps=25|
		^Probe.spectrogram(this, busIndex, fftSize, theme, fps);
	}

	waterfall { |busIndex=0, fftSize=64, theme, fps=25, numLines=48|
		^Probe.waterfall(this, busIndex, fftSize, theme, fps, numLines);
	}

	history { |busType=\outputs, busIndex=0, numChannels=2, theme, fps=25, maxHistory=200|
		^Probe.history(this, busType, busIndex, numChannels, theme, fps, maxHistory);
	}

	loudness { |busType=\outputs, busIndex=0, numChannels=2, theme, targetLUFS= -23.0, updateFreq=15|
		^Probe.loudness(this, busType, busIndex, numChannels, theme, targetLUFS, updateFreq);
	}

	vectorscope { |busIndex=0, theme, fps=15|
		^Probe.vectorscope(this, busIndex, theme, fps);
	}

	dashboard { |numIns, numOuts, busIndex=0, theme, targetLUFS= -23.0|
		^Probe.dashboard(this, numIns, numOuts, busIndex, theme, targetLUFS);
	}

	spectralDashboard { |busIndex=0, theme|
		^Probe.spectralDashboard(this, busIndex, theme);
	}

	bandAnalyzer { |busIndex=0, theme|
		^Probe.bandAnalyzer(this, busIndex, theme);
	}
}
