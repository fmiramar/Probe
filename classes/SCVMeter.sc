/*
    SCVMeter.sc
    SCVisuals — Compact Vertical Server Meter
    Production visualizer featuring vertical channel strips, dual-sided dB tick rulers
    (inputs left, outputs right), dynamic resizing, stereo grouping, peak-hold reset,
    density modes, and measurement broker deduplication.
*/

SCVMeterView {
	classvar <defaultHeight = 230;
	classvar <dBLow = -80.0;

	var <server;
	var <view;
	var <numIns, <numOuts;
	var <theme;
	var <density;
	var <meterStyle;     // \continuous or \led
	var inMeters, outMeters;
	var inLabels, outLabels;
	var inComps, outComps;
	var inSource, outSource;
	var dbRulerView, dbRulerViewRight, dividerView;
	var inHeaderView, outHeaderView;
	var <isClosed = false;

	*new { |server, parent, leftUp, numIns, numOuts, theme=\graphite, density=\compact, meterStyle=\continuous|
		^super.new.prInit(server, parent, leftUp, numIns, numOuts, theme, density, meterStyle);
	}

	*getWidth { |numIns, numOuts, theme|
		var th = SCVTheme.get(theme);
		var mw = th.meterWidth;
		var gw = th.gapWidth;
		var rulerW = 24;
		var pad = 16;
		var divW = if ((numIns > 0) and: { numOuts > 0 }) { 12 } { 0 };
		^(rulerW * 2) + pad + ((numIns + numOuts) * (mw + gw)) + divW;
	}

	prInit { |argServer, parent, leftUp, argNumIns, argNumOuts, argTheme, argDensity, argStyle|
		var totalWidth, pt;
		var point = leftUp ? (0@0);

		server     = argServer ?? { Server.default };
		theme      = SCVTheme.get(argTheme);
		density    = argDensity ?? { theme.density };
		theme      = theme.withDensity(density);
		meterStyle = argStyle ?? { \continuous };

		numIns  = argNumIns  ?? { server.options.numInputBusChannels };
		numOuts = argNumOuts ?? { server.options.numOutputBusChannels };

		totalWidth = this.class.getWidth(numIns, numOuts, theme);

		pt = if (point.isKindOf(Rect)) { point.origin } { point ? Point(0, 0) };
		view = CompositeView(parent, Rect(pt.x, pt.y, totalWidth, defaultHeight));
		view.background = theme.background;
		view.onClose = { this.stop };
		view.onResize = { |v| this.prHandleResize(v.bounds.width, v.bounds.height) };

		this.prBuildUI(totalWidth, defaultHeight);
		this.prSubscribeSources;
	}

	prBuildUI { |totalWidth, totalHeight|
		var mw = theme.meterWidth;
		var gw = theme.gapWidth;
		var fs = theme.fontSize;
		var rulerW = 24;
		var h = totalHeight ?? { view.bounds.height };
		var meterH = (h - 55).max(40);
		var curX = 6;

		view.removeAll;
		view.background = theme.background;

		// 1. dB Ruler (Left side - facing inputs)
		dbRulerView = UserView(view, Rect(curX, 24, rulerW, meterH + 20));
		dbRulerView.canFocus = false;
		dbRulerView.drawFunc = { |uv|
			var curH = uv.bounds.height - 20;
			var dBs = [0, -3, -6, -12, -18, -24, -36, -48, -60, -80];
			Pen.font = Font.monospace(fs - 1);
			dBs.do { |db|
				var normY = db.linlin(dBLow, 0, curH - 10, 0);
				Pen.strokeColor = theme.grid;
				Pen.line(Point(rulerW - 6, normY + 5), Point(rulerW, normY + 5));
				Pen.stroke;
				Pen.color = theme.text;
				Pen.stringRightJustIn(db.asString, Rect(0, normY, rulerW - 8, 12));
			};
		};

		curX = curX + rulerW + 6;

		// 2. Inputs
		if (numIns > 0) {
			inHeaderView = StaticText(view, Rect(curX, 4, (numIns * (mw + gw)).max(50), 16))
				.string_("IN")
				.font_(Font.sansSerif(fs).boldVariant)
				.stringColor_(theme.text);

			inComps  = Array.newClear(numIns);
			inMeters = Array.newClear(numIns);
			inLabels = Array.newClear(numIns);

			numIns.do { |i|
				var comp = CompositeView(view, Rect(curX, 24, mw, meterH + 20));
				var meter = LevelIndicator(comp, Rect(0, 0, mw, meterH))
					.warning_( -12.linlin(dBLow, 0, 0, 1) )
					.critical_( -0.1.linlin(dBLow, 0, 0, 1) )
					.drawsPeak_(true)
					.numTicks_(9)
					.numMajorTicks_(3)
					.style_(if (meterStyle === \led) { \led } { \continuous })
					.background_(theme.panelBackground)
					.meterColor_(theme.signal)
					.warningColor_(theme.warning)
					.criticalColor_(theme.critical);

				var label = StaticText(comp, Rect(0, meterH + 2, mw, 14))
					.font_(Font.sansSerif(fs - 1).boldVariant)
					.align_(\center)
					.stringColor_(theme.subtext)
					.string_((i + 1).asString);

				meter.mouseDownAction = { this.resetPeakHold };

				inComps[i]  = comp;
				inMeters[i] = meter;
				inLabels[i] = label;
				curX = curX + mw + gw;
			};
		};

		// 3. Divider
		if ((numIns > 0) and: { numOuts > 0 }) {
			dividerView = UserView(view, Rect(curX, 24, 12, meterH)).drawFunc_({ |uv|
				var dh = uv.bounds.height;
				Pen.strokeColor = theme.grid;
				Pen.width = 1;
				Pen.line(Point(6, 0), Point(6, dh));
				Pen.stroke;
			});
			curX = curX + 12;
		};

		// 4. Outputs
		if (numOuts > 0) {
			outHeaderView = StaticText(view, Rect(curX, 4, (numOuts * (mw + gw)).max(50), 16))
				.string_("OUT")
				.font_(Font.sansSerif(fs).boldVariant)
				.stringColor_(theme.text);

			outComps  = Array.newClear(numOuts);
			outMeters = Array.newClear(numOuts);
			outLabels = Array.newClear(numOuts);

			numOuts.do { |i|
				var comp = CompositeView(view, Rect(curX, 24, mw, meterH + 20));
				var meter = LevelIndicator(comp, Rect(0, 0, mw, meterH))
					.warning_( -12.linlin(dBLow, 0, 0, 1) )
					.critical_( -0.1.linlin(dBLow, 0, 0, 1) )
					.drawsPeak_(true)
					.numTicks_(9)
					.numMajorTicks_(3)
					.style_(if (meterStyle === \led) { \led } { \continuous })
					.background_(theme.panelBackground)
					.meterColor_(theme.signal)
					.warningColor_(theme.warning)
					.criticalColor_(theme.critical);

				var label = StaticText(comp, Rect(0, meterH + 2, mw, 14))
					.font_(Font.sansSerif(fs - 1).boldVariant)
					.align_(\center)
					.stringColor_(theme.subtext)
					.string_((i + 1).asString);

				meter.mouseDownAction = { this.resetPeakHold };

				outComps[i]  = comp;
				outMeters[i] = meter;
				outLabels[i] = label;
				curX = curX + mw + gw;
			};
		};

		// 5. dB Ruler (Right side - facing outputs)
		dbRulerViewRight = UserView(view, Rect(curX + 2, 24, rulerW, meterH + 20));
		dbRulerViewRight.canFocus = false;
		dbRulerViewRight.drawFunc = { |uv|
			var curH = uv.bounds.height - 20;
			var dBs = [0, -3, -6, -12, -18, -24, -36, -48, -60, -80];
			Pen.font = Font.monospace(fs - 1);
			dBs.do { |db|
				var normY = db.linlin(dBLow, 0, curH - 10, 0);
				Pen.strokeColor = theme.grid;
				Pen.line(Point(0, normY + 5), Point(6, normY + 5));
				Pen.stroke;
				Pen.color = theme.text;
				Pen.stringLeftJustIn(db.asString, Rect(8, normY, rulerW - 8, 12));
			};
		};

		// Keyboard shortcut 'r' or space to reset peak hold
		view.keyDownAction = { |v, char|
			if (char === $r or: { char === $  }) {
				this.resetPeakHold;
			};
		};
	}

	prHandleResize { |w, h|
		var rulerW = 24;
		var pad = 16;
		var gw = theme.gapWidth;
		var divW = if ((numIns > 0) and: { numOuts > 0 }) { 12 } { 0 };
		var availW = (w - (rulerW * 2) - divW - pad).max(20);
		var totalCh = numIns + numOuts;
		var mw = if (totalCh > 0) { ((availW / totalCh) - gw).clip(6, 40) } { 16 };
		var meterH = (h - 55).max(40);
		var curX = 6;

		if (isClosed.not and: { dbRulerView.notNil }) {
			// 1. Left ruler
			dbRulerView.bounds = Rect(curX, 24, rulerW, meterH + 20);
			dbRulerView.refresh;
			curX = curX + rulerW + 6;

			// 2. Inputs
			if (numIns > 0 and: { inMeters.notNil }) {
				if (inHeaderView.notNil) {
					inHeaderView.bounds = Rect(curX, 4, (numIns * (mw + gw)).max(50), 16);
				};
				numIns.do { |i|
					if (inComps[i].notNil) {
						inComps[i].bounds = Rect(curX, 24, mw, meterH + 20);
						inMeters[i].bounds = Rect(0, 0, mw, meterH);
						inLabels[i].bounds = Rect(0, meterH + 2, mw, 14);
					};
					curX = curX + mw + gw;
				};
			};

			// 3. Divider
			if (numIns > 0 and: { numOuts > 0 } and: { dividerView.notNil }) {
				dividerView.bounds = Rect(curX, 24, divW, meterH);
				dividerView.refresh;
				curX = curX + divW;
			};

			// 4. Outputs
			if (numOuts > 0 and: { outMeters.notNil }) {
				if (outHeaderView.notNil) {
					outHeaderView.bounds = Rect(curX, 4, (numOuts * (mw + gw)).max(50), 16);
				};
				numOuts.do { |i|
					if (outComps[i].notNil) {
						outComps[i].bounds = Rect(curX, 24, mw, meterH + 20);
						outMeters[i].bounds = Rect(0, 0, mw, meterH);
						outLabels[i].bounds = Rect(0, meterH + 2, mw, 14);
					};
					curX = curX + mw + gw;
				};
			};

			// 5. Right ruler
			if (dbRulerViewRight.notNil) {
				dbRulerViewRight.bounds = Rect(curX + 2, 24, rulerW, meterH + 20);
				dbRulerViewRight.refresh;
			};
		};
	}

	prSubscribeSources {
		var broker = SCVisuals.broker;
		var updateFreq = theme.updateFrequency;

		if (numIns > 0) {
			inSource = broker.subscribeLevels(this, server, \inputs, 0, numIns, updateFreq, { |peaks, rms, peakHold|
				this.prUpdateInLevels(peaks, rms, peakHold);
			});
		};

		if (numOuts > 0) {
			outSource = broker.subscribeLevels(this, server, \outputs, 0, numOuts, updateFreq, { |peaks, rms, peakHold|
				this.prUpdateOutLevels(peaks, rms, peakHold);
			});
		};
	}

	prUpdateInLevels { |peaks, rms, peakHold|
		if (isClosed.not and: { inMeters.notNil }) {
			numIns.do { |i|
				var m = inMeters[i];
				if (m.notNil and: { m.isClosed.not }) {
					m.peakLevel = peakHold[i].ampdb.linlin(dBLow, 0, 0, 1, \min);
					m.value     = rms[i].ampdb.linlin(dBLow, 0, 0, 1, \min);
				};
			};
		};
	}

	prUpdateOutLevels { |peaks, rms, peakHold|
		if (isClosed.not and: { outMeters.notNil }) {
			numOuts.do { |i|
				var m = outMeters[i];
				if (m.notNil and: { m.isClosed.not }) {
					m.peakLevel = peakHold[i].ampdb.linlin(dBLow, 0, 0, 1, \min);
					m.value     = rms[i].ampdb.linlin(dBLow, 0, 0, 1, \min);
				};
			};
		};
	}

	resetPeakHold {
		if (inSource.notNil)  { inSource.resetPeakHold };
		if (outSource.notNil) { outSource.resetPeakHold };
	}

	theme_ { |newTheme|
		theme = SCVTheme.get(newTheme).withDensity(density);
		this.prApplyTheme;
	}

	density_ { |newDensity|
		density = newDensity.asSymbol;
		theme = theme.withDensity(density);
		this.prBuildUI(this.class.getWidth(numIns, numOuts, theme), view.bounds.height);
	}

	meterStyle_ { |newStyle|
		meterStyle = newStyle.asSymbol;
		if (inMeters.notNil) {
			inMeters.do { |m| m.style = if (meterStyle === \led) { \led } { \continuous } };
		};
		if (outMeters.notNil) {
			outMeters.do { |m| m.style = if (meterStyle === \led) { \led } { \continuous } };
		};
	}

	prApplyTheme {
		view.background = theme.background;
		if (inHeaderView.notNil) { inHeaderView.stringColor = theme.text };
		if (outHeaderView.notNil) { outHeaderView.stringColor = theme.text };
		if (dbRulerView.notNil) { dbRulerView.refresh };
		if (dbRulerViewRight.notNil) { dbRulerViewRight.refresh };
		if (dividerView.notNil) { dividerView.refresh };

		if (inMeters.notNil) {
			inMeters.do { |m|
				m.background    = theme.panelBackground;
				m.meterColor    = theme.signal;
				m.warningColor  = theme.warning;
				m.criticalColor = theme.critical;
			};
		};
		if (inLabels.notNil) {
			inLabels.do { |lbl| lbl.stringColor = theme.subtext };
		};

		if (outMeters.notNil) {
			outMeters.do { |m|
				m.background    = theme.panelBackground;
				m.meterColor    = theme.signal;
				m.warningColor  = theme.warning;
				m.criticalColor = theme.critical;
			};
		};
		if (outLabels.notNil) {
			outLabels.do { |lbl| lbl.stringColor = theme.subtext };
		};
	}

	stop {
		if (isClosed.not) {
			isClosed = true;
			SCVisuals.broker.unsubscribeAll(this);
			inSource = nil;
			outSource = nil;
		};
	}
}

SCVMeter {
	var <window, <meterView;

	*new { |server, numIns, numOuts, theme=\graphite, density=\compact, meterStyle=\continuous|
		^super.new.prInit(server, numIns, numOuts, theme, density, meterStyle);
	}

	prInit { |server, numIns, numOuts, theme, density, meterStyle|
		var width, s;
		s = server ?? { Server.default };
		numIns  = numIns  ?? { s.options.numInputBusChannels };
		numOuts = numOuts ?? { s.options.numOutputBusChannels };

		width = SCVMeterView.getWidth(numIns, numOuts, theme);
		window = Window(s.name ++ " levels (dBFS) — SCVisuals", Rect(100, 200, width, SCVMeterView.defaultHeight));

		meterView = SCVMeterView(s, window, 0@0, numIns, numOuts, theme, density, meterStyle);

		window.view.onResize = { |v| meterView.prHandleResize(v.bounds.width, v.bounds.height) };
		window.onClose = { meterView.stop };
		window.front;
	}

	close {
		window.close;
	}

	theme_ { |aTheme|
		meterView.theme = aTheme;
	}

	density_ { |aDensity|
		meterView.density = aDensity;
		window.bounds = window.bounds.width_(SCVMeterView.getWidth(meterView.numIns, meterView.numOuts, meterView.theme));
	}

	meterStyle_ { |aStyle|
		meterView.meterStyle = aStyle;
	}

	resetPeakHold {
		meterView.resetPeakHold;
	}
}
