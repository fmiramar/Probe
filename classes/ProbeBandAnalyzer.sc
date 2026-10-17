/*
    ProbeBandAnalyzer.sc
    Probe — 16-Band Calibrated Logarithmic Spectral Band Analyzer
    Visualizes ISO-standard fractional spectral band energy (-80 dBFS to 0 dBFS)
    with segmented level bars, instantaneous peak-hold indicators, dynamic resizing,
    and broker deduplication.
*/

ProbeBandAnalyzerView {
	classvar <freqLabels = #[
		"31", "63", "125", "250", "400", "630", "800", "1k",
		"1.6k", "2.5k", "4k", "6.3k", "8k", "10k", "12.5k", "16k"
	];

	var <server;
	var <view, <userView;
	var <busIndex;
	var <theme;
	var <source;
	var currentBands;    // FloatArray[16]
	var peakBands;       // FloatArray[16]
	var <isFrozen = false;
	var <isClosed = false;
	var txtTitle;

	*new { |server, parent, bounds, busIndex=0, theme=\graphite|
		^super.new.prInit(server, parent, bounds, busIndex, theme);
	}

	prInit { |argServer, parent, bounds, argBusIndex, argTheme|
		var rect = bounds ?? { Rect(0, 0, 480, 240) };

		server   = argServer ?? { Server.default };
		busIndex = argBusIndex.asInteger;
		theme    = SCVTheme.get(argTheme);

		currentBands = FloatArray.fill(16, -90.0);
		peakBands    = FloatArray.fill(16, -90.0);

		view = CompositeView(parent, rect);
		view.resize = 5;
		view.background = theme.background;
		view.onClose = { this.stop };
		view.onResize = { |v|
			var b = if (v.notNil and: { v.isClosed.not }) { v.bounds };
			if (b.notNil and: { isClosed.not }) { this.prHandleResize(b.width, b.height) };
		};

		this.prBuildUI(rect.width, rect.height);
		this.prSubscribeSource;
	}

	prBuildUI { |w, h|
		var fs = theme.fontScale ? 1.25;
		var titleH = (18 * fs).max(18).round.asInteger;
		var topOffset = titleH + 6;
		var labelH = 16;
		var meterH = (h - topOffset - labelH - 8).max(40);

		txtTitle = StaticText(view, Rect(10, 4, (w - 20).max(100), titleH))
			.string_("SPECTRAL BAND ANALYZER [16 BANDS]")
			.font_(theme.font(theme.fontSize, bold: true))
			.stringColor_(theme.text);

		userView = UserView(view, Rect(10, topOffset, (w - 20).max(100), (h - topOffset - 6).max(60)));
		userView.background = theme.panelBackground;
		userView.drawFunc = { |uv|
			var cw = uv.bounds.width, ch = uv.bounds.height;
			var plotH = ch - labelH - 4;
			var numBands = 16;
			var bandW = cw / numBands;
			var barW = (bandW * 0.75).max(4);
			var pad = (bandW - barW) * 0.5;

			// 1. Grid dB lines (-60, -40, -20, -6, 0 dB)
			[-60, -40, -20, -6, 0].do { |db|
				var norm = (db + 80) / 80;
				var y = plotH - (norm.clip(0.0, 1.0) * plotH);
				Pen.strokeColor = theme.grid.copy.alpha_(0.35);
				Pen.width = 1;
				Pen.line(Point(0, y), Point(cw, y));
				Pen.stroke;

				Pen.font = theme.font(8, mono: true);
				Pen.color = theme.subtext;
				Pen.stringAtPoint(db.asString ++ " dB", Point(4, y - 9));
			};

			// 2. Vertical Bars & Peak Indicators
			numBands.do { |i|
				var val = currentBands[i];
				var pk = peakBands[i];
				var norm = (val + 80) / 80;
				var pkNorm = (pk + 80) / 80;
				var barH = norm.clip(0.0, 1.0) * plotH;
				var x = (i * bandW) + pad;
				var y = plotH - barH;
				var pkY = plotH - (pkNorm.clip(0.0, 1.0) * plotH);

				// Bar body
				Pen.fillColor = if (val > -6.0) {
					theme.warning
				} {
					if (val > -1.0) { theme.critical } { theme.signal }
				};
				Pen.fillRect(Rect(x, y, barW, barH));

				// Peak hold tick
				Pen.strokeColor = theme.text;
				Pen.width = 2;
				Pen.line(Point(x, pkY), Point(x + barW, pkY));
				Pen.stroke;

				// Frequency label
				Pen.font = theme.font(8, mono: true);
				Pen.color = theme.subtext;
				Pen.stringCenteredIn(freqLabels[i], Rect(i * bandW, plotH + 2, bandW, labelH));
			};
		};
	}

	prHandleResize { |w, h|
		var fs = theme.fontScale ? 1.25;
		var titleH = (18 * fs).max(18).round.asInteger;
		var topOffset = titleH + 6;

		if (isClosed.not and: { view.notNil } and: { view.isClosed.not } and: { userView.notNil } and: { userView.isClosed.not }) {
			view.bounds = Rect(view.bounds.left, view.bounds.top, w, h);
			if (txtTitle.notNil) {
				txtTitle.bounds = Rect(10, 4, (w - 20).max(100), titleH);
			};
			userView.bounds = Rect(10, topOffset, (w - 20).max(100), (h - topOffset - 6).max(60));
			userView.refresh;
		};
	}

	prSubscribeSource {
		source = SCVisuals.broker.subscribeSpectral(this, server, busIndex, 20, { |data|
			if (isClosed.not and: { isFrozen.not }) {
				var b = data[\bands];
				if (b.notNil) {
					16.do { |i|
						var val = b[i] ? -90.0;
						currentBands[i] = val;
						peakBands[i] = peakBands[i].max(val) - 0.4; // gradual peak decay
					};
					{ if (isClosed.not and: { userView.notNil } and: { userView.isClosed.not }) { userView.refresh } }.defer;
				};
			};
		});
	}

	theme_ { |newTheme|
		theme = SCVTheme.get(newTheme);
		view.background = theme.background;
		userView.background = theme.panelBackground;
		if (txtTitle.notNil) {
			txtTitle.stringColor = theme.text;
			txtTitle.font = theme.font(theme.fontSize, bold: true);
		};
		userView.refresh;
	}

	fontScale {
		^theme.fontScale;
	}

	fontScale_ { |scale|
		this.theme = theme.withFontScale(scale);
		this.prHandleResize(view.bounds.width, view.bounds.height);
	}

	toggleFreeze {
		isFrozen = isFrozen.not;
	}

	stop {
		if (isClosed.not) {
			isClosed = true;
			SCVisuals.broker.unsubscribeAll(this);
			source = nil;
		};
	}
}

ProbeBandAnalyzer {
	var <window, <analyzerView;

	*new { |server, busIndex=0, theme=\graphite|
		^super.new.prInit(server, busIndex, theme);
	}

	prInit { |server, busIndex, theme|
		var s = server ?? { Server.default };
		var w = 500, h = 260;

		window = Window(s.name ++ " Spectral Bands — Probe", Rect(140, 200, w, h));
		analyzerView = ProbeBandAnalyzerView(s, window, Rect(0, 0, w, h), busIndex, theme);
		window.view.background = analyzerView.theme.background;

		window.view.onResize = { |v|
			if (v.notNil and: { v.isClosed.not } and: { v.bounds.notNil } and: { analyzerView.notNil } and: { analyzerView.isClosed.not }) {
				analyzerView.prHandleResize(v.bounds.width, v.bounds.height);
			};
		};
		window.onClose = { analyzerView.stop };
		window.front;
	}

	close {
		window.close;
	}

	theme_ { |aTheme|
		analyzerView.theme = aTheme;
		window.view.background = analyzerView.theme.background;
	}

	fontScale_ { |scale|
		analyzerView.fontScale = scale;
		window.view.background = analyzerView.theme.background;
	}

	toggleFreeze {
		analyzerView.toggleFreeze;
	}
}
