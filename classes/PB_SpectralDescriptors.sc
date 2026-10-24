/*
    PB_SpectralDescriptors.sc
    Probe — Advanced Spectral Analysis Descriptor Views (Internal)
    Modular, high-performance visualizers for:
    1. Spectral Descriptors (Centroid, Flatness, Roll-off, Flux/Onsets)
    2. Pitch & Voicing Confidence Tracker
    3. 12-Tone Harmonic Chromagram
    4. Multi-Resolution Spectrogram (Dual-STFT)
    5. Spectral Comparison & Reference Hold (A/B Difference)
*/

PB_DescriptorsView {
	var <server, <busIndex, <theme;
	var <view, <userView;
	var <source;
	var centroid = 1000.0, flatness = 0.1, rolloff = 4000.0, onset = 0.0;
	var <isClosed = false;

	*new { |server, parent, bounds, busIndex=0, theme=\graphite|
		^super.new.prInit(server, parent, bounds, busIndex, theme);
	}

	prInit { |argServer, parent, bounds, argBusIndex, argTheme|
		server   = argServer ?? { Server.default };
		busIndex = argBusIndex.asInteger;
		theme    = ProbeTheme.get(argTheme);

		view = CompositeView(parent, bounds);
		view.resize = 5;
		view.background = theme.background;
		view.onClose = { this.stop };

		userView = UserView(view, Rect(0, 0, bounds.width, bounds.height));
		userView.resize = 5;
		userView.background = theme.panelBackground;
		userView.drawFunc = { |uv|
			var w = uv.bounds.width, h = uv.bounds.height;
			var colW = w / 4;

			// Draw 4 descriptor columns
			[
				["CENTROID", centroid.round(1).asString ++ " Hz", (centroid / 10000).clip(0.0, 1.0)],
				["FLATNESS", flatness.round(0.001).asString, flatness.clip(0.0, 1.0)],
				["ROLLOFF (85%)", rolloff.round(1).asString ++ " Hz", (rolloff / 15000).clip(0.0, 1.0)],
				["FLUX / ONSET", if (onset > 0.1) { "TRIGGER" } { "STABLE" }, onset.clip(0.0, 1.0)]
			].do { |item, i|
				var title = item[0], valStr = item[1], norm = item[2];
				var x = i * colW;
				var barH = (h - 44).max(10);
				var fillH = norm * barH;

				// Column separator
				if (i > 0) {
					Pen.strokeColor = theme.grid.copy.alpha_(0.3);
					Pen.width = 1;
					Pen.line(Point(x, 4), Point(x, h - 4));
					Pen.stroke;
				};

				// Title
				Pen.font = theme.font(9, bold: true);
				Pen.color = theme.subtext;
				Pen.stringCenteredIn(title, Rect(x, 4, colW, 14));

				// Value string
				Pen.font = theme.font(10, mono: true);
				Pen.color = if (i == 3 and: { onset > 0.1 }) { theme.warning } { theme.signal };
				Pen.stringCenteredIn(valStr, Rect(x, 20, colW, 14));

				// Gauge bar
				Pen.strokeColor = theme.grid.copy.alpha_(0.4);
				Pen.width = 1;
				Pen.strokeRect(Rect(x + 12, 38, colW - 24, barH));

				Pen.fillColor = theme.signal.copy.alpha_(0.7);
				Pen.fillRect(Rect(x + 13, 38 + barH - fillH, colW - 26, fillH));
			};
		};

		source = Probe.broker.subscribeSpectral(this, server, busIndex, 15, { |data|
			if (isClosed.not) {
				centroid = data[\centroid] ? centroid;
				flatness = data[\flatness] ? flatness;
				rolloff  = data[\rolloff] ? rolloff;
				onset    = data[\onset] ? onset;
				{ if (isClosed.not and: { userView.notNil } and: { userView.isClosed.not }) { userView.refresh } }.defer;
			};
		});
	}

	theme_ { |newTheme|
		theme = ProbeTheme.get(newTheme);
		view.background = theme.background;
		userView.background = theme.panelBackground;
		userView.refresh;
	}

	prHandleResize { |w, h|
		if (isClosed.not and: { view.notNil } and: { view.isClosed.not } and: { userView.notNil } and: { userView.isClosed.not }) {
			view.bounds = Rect(view.bounds.left, view.bounds.top, w, h);
			userView.bounds = Rect(0, 0, w, h);
			userView.refresh;
		};
	}

	stop {
		if (isClosed.not) {
			isClosed = true;
			Probe.broker.unsubscribeAll(this);
			source = nil;
		};
	}
}

PB_PitchTrackerView {
	var <server, <busIndex, <theme;
	var <view, <userView;
	var <source;
	var pitch = 440.0, hasPitch = 1.0;
	var history;
	var maxPts = 80;
	var <isClosed = false;

	*new { |server, parent, bounds, busIndex=0, theme=\graphite|
		^super.new.prInit(server, parent, bounds, busIndex, theme);
	}

	prInit { |argServer, parent, bounds, argBusIndex, argTheme|
		server   = argServer ?? { Server.default };
		busIndex = argBusIndex.asInteger;
		theme    = ProbeTheme.get(argTheme);
		history  = FloatArray.fill(maxPts, 440.0);

		view = CompositeView(parent, bounds);
		view.resize = 5;
		view.background = theme.background;
		view.onClose = { this.stop };

		userView = UserView(view, Rect(0, 0, bounds.width, bounds.height));
		userView.resize = 5;
		userView.background = theme.panelBackground;
		userView.drawFunc = { |uv|
			var w = uv.bounds.width, h = uv.bounds.height;
			var midiNote = pitch.cpsmidi.round(0.1);
			var noteNames = #["C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B"];
			var noteIdx = midiNote.round.asInteger % 12;
			var noteOct = (midiNote.round.asInteger / 12).floor - 1;
			var noteStr = noteNames[noteIdx] ++ noteOct;

			// Header HUD
			Pen.font = theme.font(11, bold: true);
			Pen.color = theme.text;
			Pen.stringAtPoint("FUNDAMENTAL f0: " ++ pitch.round(0.1) ++ " Hz (" ++ noteStr ++ ")", Point(10, 6));

			Pen.font = theme.font(9, mono: true);
			Pen.color = if (hasPitch > 0.5) { theme.signal } { theme.subtext };
			Pen.stringAtPoint("VOICING CONFIDENCE: " ++ (hasPitch * 100).round.asInteger ++ "%", Point(w - 180, 8));

			// Pitch timeline (log-scaled 50 Hz to 2000 Hz)
			if (history.size > 1) {
				var pts = Array.new(maxPts);
				maxPts.do { |i|
					var p = history[i];
					var norm = (p.max(50).log2 - 50.log2) / (2000.log2 - 50.log2);
					var x = (i / (maxPts - 1)) * w;
					var y = (h - 10) - (norm.clip(0.0, 1.0) * (h - 40));
					pts.add(Point(x, y));
				};

				Pen.strokeColor = theme.signal;
				Pen.width = 1.8;
				Pen.moveTo(pts[0]);
				(1..(maxPts - 1)).do { |i| Pen.lineTo(pts[i]) };
				Pen.stroke;
			};
		};

		source = Probe.broker.subscribeSpectral(this, server, busIndex, 15, { |data|
			if (isClosed.not) {
				pitch = data[\pitch] ? pitch;
				hasPitch = data[\hasPitch] ? hasPitch;
				history.removeAt(0);
				history.add(pitch);
				{ if (isClosed.not and: { userView.notNil } and: { userView.isClosed.not }) { userView.refresh } }.defer;
			};
		});
	}

	theme_ { |newTheme|
		theme = ProbeTheme.get(newTheme);
		view.background = theme.background;
		userView.background = theme.panelBackground;
		userView.refresh;
	}

	prHandleResize { |w, h|
		if (isClosed.not and: { view.notNil } and: { view.isClosed.not } and: { userView.notNil } and: { userView.isClosed.not }) {
			view.bounds = Rect(view.bounds.left, view.bounds.top, w, h);
			userView.bounds = Rect(0, 0, w, h);
			userView.refresh;
		};
	}

	stop {
		if (isClosed.not) {
			isClosed = true;
			Probe.broker.unsubscribeAll(this);
			source = nil;
		};
	}
}

PB_ChromagramView {
	classvar <notes = #["C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B"];
	var <server, <busIndex, <theme;
	var <view, <userView;
	var <source;
	var chroma;          // FloatArray[12]
	var <isClosed = false;

	*new { |server, parent, bounds, busIndex=0, theme=\graphite|
		^super.new.prInit(server, parent, bounds, busIndex, theme);
	}

	prInit { |argServer, parent, bounds, argBusIndex, argTheme|
		server   = argServer ?? { Server.default };
		busIndex = argBusIndex.asInteger;
		theme    = ProbeTheme.get(argTheme);
		chroma   = FloatArray.fill(12, 0.0);

		view = CompositeView(parent, bounds);
		view.resize = 5;
		view.background = theme.background;
		view.onClose = { this.stop };

		userView = UserView(view, Rect(0, 0, bounds.width, bounds.height));
		userView.resize = 5;
		userView.background = theme.panelBackground;
		userView.drawFunc = { |uv|
			var w = uv.bounds.width, h = uv.bounds.height;
			var colW = w / 12;
			var plotH = h - 24;

			// Header
			Pen.font = theme.font(9, bold: true);
			Pen.color = theme.subtext;
			Pen.stringAtPoint("12-TONE CHROMAGRAM (HARMONIC DISTRIBUTION)", Point(10, 4));

			12.do { |i|
				var val = chroma[i];
				var x = i * colW;
				var barH = val.clip(0.0, 1.0) * (plotH - 20);
				var y = plotH - barH;

				// Bar
				Pen.fillColor = theme.secondarySignal.copy.alpha_(0.8);
				Pen.fillRect(Rect(x + 4, y, colW - 8, barH));

				// Note Label
				Pen.font = theme.font(9, mono: true);
				Pen.color = theme.text;
				Pen.stringCenteredIn(notes[i], Rect(x, plotH + 2, colW, 16));
			};
		};

		source = Probe.broker.subscribeSpectral(this, server, busIndex, 15, { |data|
			if (isClosed.not) {
				var ch = data[\chroma];
				if (ch.notNil) {
					12.do { |i| chroma[i] = ch[i] ? 0.0 };
					{ if (isClosed.not and: { userView.notNil } and: { userView.isClosed.not }) { userView.refresh } }.defer;
				};
			};
		});
	}

	theme_ { |newTheme|
		theme = ProbeTheme.get(newTheme);
		view.background = theme.background;
		userView.background = theme.panelBackground;
		userView.refresh;
	}

	prHandleResize { |w, h|
		if (isClosed.not and: { view.notNil } and: { view.isClosed.not } and: { userView.notNil } and: { userView.isClosed.not }) {
			view.bounds = Rect(view.bounds.left, view.bounds.top, w, h);
			userView.bounds = Rect(0, 0, w, h);
			userView.refresh;
		};
	}

	stop {
		if (isClosed.not) {
			isClosed = true;
			Probe.broker.unsubscribeAll(this);
			source = nil;
		};
	}
}

PB_SpectralCompareView {
	var <server, <busIndex, <theme;
	var <view, <userView;
	var <source;
	var liveBands, refBands;
	var isHoldActive = false;
	var <isClosed = false;

	*new { |server, parent, bounds, busIndex=0, theme=\graphite|
		^super.new.prInit(server, parent, bounds, busIndex, theme);
	}

	prInit { |argServer, parent, bounds, argBusIndex, argTheme|
		server    = argServer ?? { Server.default };
		busIndex  = argBusIndex.asInteger;
		theme     = ProbeTheme.get(argTheme);
		liveBands = FloatArray.fill(16, -90.0);
		refBands  = FloatArray.fill(16, -90.0);

		view = CompositeView(parent, bounds);
		view.resize = 5;
		view.background = theme.background;
		view.onClose = { this.stop };

		userView = UserView(view, Rect(0, 0, bounds.width, bounds.height));
		userView.resize = 5;
		userView.background = theme.panelBackground;
		userView.drawFunc = { |uv|
			var w = uv.bounds.width, h = uv.bounds.height;
			var colW = w / 16;
			var plotH = h - 30;

			// Header HUD
			Pen.font = theme.font(9, bold: true);
			Pen.color = theme.text;
			Pen.stringAtPoint("SPECTRAL COMPARISON: LIVE (Cyan) vs REFERENCE HOLD (Amber)", Point(10, 4));

			// Center 0 dB difference line
			Pen.strokeColor = theme.grid.copy.alpha_(0.5);
			Pen.width = 1;
			Pen.line(Point(0, plotH * 0.5), Point(w, plotH * 0.5));
			Pen.stroke;

			// Differential bars: (Live - Ref) in dB (-24 dB to +24 dB)
			16.do { |i|
				var diff = liveBands[i] - refBands[i];
				var norm = diff.clip(-24.0, 24.0) / 48.0; // -0.5 to +0.5
				var x = (i * colW) + (colW * 0.15);
				var barW = colW * 0.7;
				var centerY = plotH * 0.5;
				var barH = norm.abs * plotH;
				var y = if (norm >= 0) { centerY - barH } { centerY };

				Pen.fillColor = if (diff >= 0) { theme.signal } { theme.warning };
				Pen.fillRect(Rect(x, y, barW, barH));

				// Ticks
				Pen.font = theme.font(8, mono: true);
				Pen.color = theme.subtext;
				Pen.stringCenteredIn(diff.round(0.1).asString, Rect(i * colW, plotH + 4, colW, 16));
			};
		};

		source = Probe.broker.subscribeSpectral(this, server, busIndex, 15, { |data|
			if (isClosed.not) {
				var b = data[\bands];
				if (b.notNil) {
					16.do { |i|
						liveBands[i] = b[i] ? -90.0;
						if (isHoldActive.not) {
							refBands[i] = liveBands[i]; // follow until hold
						};
					};
					{ if (isClosed.not and: { userView.notNil } and: { userView.isClosed.not }) { userView.refresh } }.defer;
				};
			};
		});
	}

	toggleHold {
		isHoldActive = isHoldActive.not;
	}

	theme_ { |newTheme|
		theme = ProbeTheme.get(newTheme);
		view.background = theme.background;
		userView.background = theme.panelBackground;
		userView.refresh;
	}

	prHandleResize { |w, h|
		if (isClosed.not and: { view.notNil } and: { view.isClosed.not } and: { userView.notNil } and: { userView.isClosed.not }) {
			view.bounds = Rect(view.bounds.left, view.bounds.top, w, h);
			userView.bounds = Rect(0, 0, w, h);
			userView.refresh;
		};
	}

	stop {
		if (isClosed.not) {
			isClosed = true;
			Probe.broker.unsubscribeAll(this);
			source = nil;
		};
	}
}
