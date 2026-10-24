/*
    ProbeBandAnalyzer.sc
    Probe — 16-Band Calibrated Logarithmic Spectral Band Analyzer
    Visualizes ISO-standard fractional spectral band energy (-80 dBFS to 0 dBFS)
    with segmented level bars, instantaneous peak-hold indicators, controllable smoothing/lag,
    dynamic resizing, and broker deduplication.
*/

PB_BandAnalyzerView {
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
	var <smooth = 0.7;   // Smoothing factor: 0.0 = raw/instantaneous, 0.95 = very smooth
	var <isFrozen = false;
	var <isClosed = false;
	var txtTitle, txtSmooth, smoothMenu, freezeBtn;

	*new { |server, parent, bounds, busIndex=0, theme=\graphite, smooth=0.7|
		^super.new.prInit(server, parent, bounds, busIndex, theme, smooth);
	}

	prInit { |argServer, parent, bounds, argBusIndex, argTheme, argSmooth=0.7|
		var rect = bounds ?? { Rect(0, 0, 480, 240) };

		server   = argServer ?? { Server.default };
		busIndex = argBusIndex.asInteger;
		theme    = ProbeTheme.get(argTheme);
		smooth   = (argSmooth ? 0.7).asFloat.clip(0.0, 0.98);

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
		var btnH = (20 * fs).max(20).min(26).round.asInteger;
		var topOffset = btnH + 10;
		var labelH = 16;
		var meterH = (h - topOffset - labelH - 8).max(40);

		txtTitle = StaticText(view, Rect(10, 4, 180, btnH))
			.string_("BAND ANALYZER [16 ISO]")
			.font_(theme.font(theme.fontSize, bold: true))
			.stringColor_(theme.text);

		txtSmooth = StaticText(view, Rect(195, 4, 52, btnH))
			.string_("Smooth:")
			.font_(theme.font(8))
			.stringColor_(theme.subtext);

		smoothMenu = PopUpMenu(view, Rect(250, 4, 82, btnH))
			.items_(["Off", "Fast", "Normal", "Slow"])
			.font_(theme.font(8))
			.value_(this.prSmoothToMenuIndex(smooth))
			.action_({ |m|
				var factors = [0.0, 0.4, 0.7, 0.88];
				smooth = factors[m.value];
			});

		freezeBtn = Button(view, Rect(338, 4, 60, btnH))
			.states_([
				["Freeze", theme.text, theme.panelBackground],
				["FROZEN", theme.background, theme.warning]
			])
			.font_(theme.font(8, bold: true))
			.action_({ this.toggleFreeze });

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

		// Keyboard controls
		view.keyDownAction = { |v, char|
			case
				{ char === $  } { this.toggleFreeze }
				{ char === $s or: { char === $l } } {
					if (smoothMenu.notNil and: { smoothMenu.isClosed.not }) {
						var nextIdx = (smoothMenu.value + 1) % 4;
						smoothMenu.valueAction_(nextIdx);
					};
				}
				{ char === $r } {
					peakBands.fill(-90.0);
					userView.refresh;
				};
		};
	}

	prSmoothToMenuIndex { |val|
		^case
			{ val < 0.20 } { 0 }
			{ val < 0.55 } { 1 }
			{ val < 0.80 } { 2 }
			{ true } { 3 };
	}

	prHandleResize { |w, h|
		var fs = theme.fontScale ? 1.25;
		var titleH = (18 * fs).max(18).round.asInteger;
		var btnH = (20 * fs).max(20).min(26).round.asInteger;
		var topOffset = btnH + 10;
		var curW = (w - 20).max(100);

		if (isClosed.not and: { view.notNil } and: { view.isClosed.not } and: { userView.notNil } and: { userView.isClosed.not }) {
			view.bounds = Rect(view.bounds.left, view.bounds.top, w, h);
			if (txtTitle.notNil and: { freezeBtn.notNil } and: { smoothMenu.notNil } and: { txtSmooth.notNil }) {
				var rFreeze = Rect(w - 70, 4, 60, btnH);
				var rMenu   = Rect(w - 158, 4, 82, btnH);
				var rLbl    = Rect(w - 214, 4, 52, btnH);
				var rTitle  = Rect(10, 4, (w - 228).max(80), btnH);

				freezeBtn.bounds = rFreeze;
				smoothMenu.bounds = rMenu;
				txtSmooth.bounds = rLbl;
				txtTitle.bounds = rTitle;
			};
			userView.bounds = Rect(10, topOffset, curW, (h - topOffset - 6).max(60));
			userView.refresh;
		};
	}

	prSubscribeSource {
		source = Probe.broker.subscribeSpectral(this, server, busIndex, 20, { |data|
			if (isClosed.not and: { isFrozen.not }) {
				var b = data[\bands];
				if (b.notNil) {
					16.do { |i|
						var val = b[i] ? -90.0;
						// One-pole smoothing / lag filter
						currentBands[i] = (currentBands[i] * smooth) + (val * (1.0 - smooth));
						peakBands[i] = peakBands[i].max(val) - 0.4; // gradual peak decay
					};
					{ if (isClosed.not and: { userView.notNil } and: { userView.isClosed.not }) { userView.refresh } }.defer;
				};
			};
		});
	}

	smooth_ { |factor|
		smooth = factor.asFloat.clip(0.0, 0.98);
		if (smoothMenu.notNil and: { smoothMenu.isClosed.not }) {
			smoothMenu.value = this.prSmoothToMenuIndex(smooth);
		};
	}

	lag {
		if (smooth <= 0.005) { ^0.0 };
		^(-0.05 / log(smooth.max(0.001))).round(0.01);
	}

	lag_ { |secs|
		if (secs.asFloat <= 0.005) {
			this.smooth = 0.0;
		} {
			this.smooth = exp(-0.05 / secs.asFloat.max(0.005)).clip(0.0, 0.98);
		};
	}

	theme_ { |newTheme|
		theme = ProbeTheme.get(newTheme);
		view.background = theme.background;
		userView.background = theme.panelBackground;
		if (txtTitle.notNil) {
			txtTitle.stringColor = theme.text;
			txtTitle.font = theme.font(theme.fontSize, bold: true);
		};
		if (txtSmooth.notNil) {
			txtSmooth.stringColor = theme.subtext;
			txtSmooth.font = theme.font(8);
		};
		if (smoothMenu.notNil) {
			smoothMenu.font = theme.font(8);
		};
		if (freezeBtn.notNil) {
			freezeBtn.states = [
				["Freeze", theme.text, theme.panelBackground],
				["FROZEN", theme.background, theme.warning]
			];
			freezeBtn.font = theme.font(8, bold: true);
			freezeBtn.value = if (isFrozen) { 1 } { 0 };
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
		if (freezeBtn.notNil and: { freezeBtn.isClosed.not }) {
			freezeBtn.value = if (isFrozen) { 1 } { 0 };
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

ProbeBandAnalyzer {
	var <window, <analyzerView;

	*new { |server, busIndex=0, theme=\graphite, smooth=0.7|
		^super.new.prInit(server, busIndex, theme, smooth);
	}

	*view { |parent, bounds, server, busIndex=0, theme=\graphite, smooth=0.7|
		^PB_BandAnalyzerView(server ?? { Server.default }, parent, bounds, busIndex, theme, smooth);
	}

	prInit { |server, busIndex, theme, smooth=0.7|
		var s = server ?? { Server.default };
		var w = 500, h = 260;

		window = Window(s.name ++ " Spectral Bands — Probe", Rect(140, 200, w, h));
		analyzerView = PB_BandAnalyzerView(s, window, Rect(0, 0, w, h), busIndex, theme, smooth);
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

	smooth {
		^analyzerView.smooth;
	}

	smooth_ { |val|
		analyzerView.smooth = val;
	}

	lag {
		^analyzerView.lag;
	}

	lag_ { |val|
		analyzerView.lag = val;
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
