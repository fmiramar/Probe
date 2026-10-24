/*
    extUGenProbe.sc
    Probe — Transparent Signal Tracing Extensions for UGen and Array
    Provides non-intrusive runtime diagnostic taps that return the exact input
    signal unmodified while automatically launching synchronized Probe instruments.

    Examples:
        { SinOsc.ar(440, 0, 0.1).trace }.play;
        { LFNoise1.kr(2).exprange(100, 2000).trace(\cutoff) }.play;
        { Saw.ar([110, 220], 0.05).trace(\stereo) }.play;
*/

+ UGen {
	trace { |label, mode, theme, options|
		^PB_Trace.wrap(this, label, mode, theme, options);
	}
}

+ Array {
	trace { |label, mode, theme, options|
		^PB_Trace.wrap(this, label, mode, theme, options);
	}
}
