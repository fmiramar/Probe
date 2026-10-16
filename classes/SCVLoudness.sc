/*
    SCVLoudness.sc
    SCVisuals — ITU-R BS.1770 / EBU R128 Loudness Meter
    Comprehensive M (Momentary 400ms), S (Short-term 3s), and I (Integrated gated)
    loudness meters with LRA, Max Peak, target reference lines (-23 / -14 LUFS),
    dynamic window resizing, theme contrast, and shared measurement broker integration.
*/

SCVLoudnessView {
	var <server;
	var <view;
	var <meterView, <readoutView;
	var <busType, <busIndex, <numChannels, <updateFreq;
	var <targetLUFS = -23.0; // Default EBU R128 (-23 LUFS), or streaming (-14 LUFS)
	var <source;
	var <theme;
	var <isFrozen = false;
	var <isClosed = false;

	// Current displayed values
	var mLUFS = -99.0, sLUFS = -99.0, iLUFS = -99.0;
	var lra = 0.0, maxPk = 0.0;
	var txtTitle, txtM, txtS, txtI, txtDelta, txtLRA, txtMaxPk;
	var btnTarget, btnReset;
	var readoutLabels, readoutValues;

	*new { |server, parent, bounds, busType=\outputs, busIndex=0, numChannels=2, theme=\graphite, targetLUFS=(-23.0), updateFreq=10|
		^super.new.prInit(server, parent, bounds, busType, busIndex, numChannels, theme, targetLUFS, updateFreq);
	}

	prInit { |argServer, parent, bounds, argBusType, argBusIndex, argNumChannels, argTheme, argTargetLUFS, argUpdateFreq|
		var rect = bounds ?? { Rect(0, 0, 420, 280) };

		server       = argServer ?? { Server.default };
		busType      = argBusType.asSymbol;
		busIndex     = argBusIndex.asInteger;
		numChannels  = argNumChannels.asInteger.clip(1, 2);
		theme        = SCVTheme.get(argTheme);
		targetLUFS   = argTargetLUFS.asFloat;
		updateFreq   = argUpdateFreq.asInteger.clip(5, 30);

		readoutLabels = List.new;
		readoutValues = List.new;

		view = CompositeView(parent, rect);
		view.resize = 5;
		view.background = theme.background;
		view.onClose = { this.stop };
		view.onResize = { |v| this.prHandleResize(v.bounds.width, v.bounds.height) };

		this.prBuildUI(rect.width, rect.height);
		this.prSubscribeSource;
	}

	prBuildUI { |w, h|
		var fs = theme.fontScale ? 1.0;
		var btnTargetH = (24 * fs).max(22).min(34).round.asInteger;
		var btnTargetW = (120 * fs).max(104).round.asInteger;
		var topOffset = (btnTargetH + 8).max(28).round.asInteger;
		var meterW = (w * 0.38).clip(130, 220);
		var readoutW = (w - meterW - 20).max(120);

		// 1. Header
		txtTitle = StaticText(view, Rect(12, 4, (w - btnTargetW - 20).max(80), btnTargetH))
			.string_("LOUDNESS [BS.1770]")
			.font_(theme.font(theme.fontSize, bold: true))
			.stringColor_(theme.text);

		// Target Toggle Button
		btnTarget = Button(view, Rect(w - btnTargetW - 8, 4, btnTargetW, btnTargetH))
			.states_([
				["Ref: " ++ targetLUFS.round(1).asInteger ++ " LUFS", theme.text, theme.panelBackground]
			])
			.font_(theme.font(8.5, mono: true))
			.action_({ this.toggleTarget });

		// 2. Triple Meter View (M, S, I)
		meterView = UserView(view, Rect(10, topOffset, meterW, (h - topOffset - 10).max(50)));
		meterView.canFocus = false;
		meterView.drawFunc = { |uv|
			var mw = uv.bounds.width;
			var mh = uv.bounds.height - 24; // Leave room for M, S, I labels
			var minLUFS = -54.0, maxLUFS = 0.0;
			var rulerW = (32 * (theme.fontScale ? 1.0)).max(32).round.asInteger;
			var colW = ((mw - rulerW - 28) / 3).clip(16, 40);
			var colGap = 8;
			var startX = rulerW + 6;

			// Draw Ruler on Left
			Pen.font = theme.font(8, mono: true);
			Pen.color = theme.subtext;
			#[0, -6, -12, -18, -23, -30, -36, -42, -54].do { |val|
				var normY = (val / minLUFS).clip(0.0, 1.0);
				var y = normY * mh;
				Pen.stringRightJustIn(val.asString, Rect(0, y - 6, rulerW - 4, 12));
				Pen.strokeColor = theme.grid.copy.alpha_(0.35);
				Pen.width = 1.0;
				Pen.line(Point(rulerW, y), Point(mw - 4, y));
				Pen.stroke;
			};

			// Target Reference Line
			block { |break|
				var targetNormY = (targetLUFS / minLUFS).clip(0.0, 1.0);
				var targetY = targetNormY * mh;
				Pen.strokeColor = theme.warning;
				Pen.width = 1.5;
				Pen.line(Point(rulerW, targetY), Point(mw - 4, targetY));
				Pen.stroke;
			};

			// Draw M, S, I Bars
			[
				[\m, mLUFS, "M"],
				[\s, sLUFS, "S"],
				[\i, iLUFS, "I"]
			].do { |item, idx|
				var val = item[1];
				var label = item[2];
				var x = startX + (idx * (colW + colGap));
				var normH = (val.clip(minLUFS, 0.0) - minLUFS) / (0.0 - minLUFS);
				var barH = normH * mh;
				var barY = mh - barH;
				var color;

				// Background slot
				Pen.fillColor = theme.panelBackground;
				Pen.fillRect(Rect(x, 0, colW, mh));

				// Meter fill
				color = if (val >= (targetLUFS + 1.0)) {
					theme.critical
				} {
					if (val >= (targetLUFS - 1.0)) {
						theme.warning
					} {
						theme.signal
					}
				};

				Pen.fillColor = color;
				Pen.fillRect(Rect(x, barY, colW, barH));

				// Column label
				Pen.font = theme.font(10, bold: true);
				Pen.color = theme.text;
				Pen.stringCenteredIn(label, Rect(x, mh + 4, colW, 16));
			};
		};

		// 3. Readout & Controls Panel on Right
		readoutView = CompositeView(view, Rect(meterW + 16, 28, readoutW, h - 38));
		readoutView.background = theme.panelBackground;

		this.prBuildReadouts(readoutW, h - 38);

		// Keyboard controls
		view.keyDownAction = { |v, char|
			case
				{ char === $r } { this.reset }
				{ char === $t } { this.toggleTarget }
				{ char === $  } { this.toggleFreeze };
		};
	}

	prBuildReadouts { |rw, rh|
		var fs = theme.fontScale ? 1.0;
		var btnResetH = (24 * fs).max(22).min(36).round.asInteger;
		var y = 6;
		var rowH = ((rh - btnResetH - 16) / 6).floor.clip((18 * fs).max(18), (32 * fs).max(30));
		var labelW = (rw * 0.44).clip((68 * fs).max(65), (120 * fs).max(110));
		var valW = (rw - labelW - 18).max(30);

		// Helper to create a readout row
		var addRow = { |title, initialVal, isHighlight=false|
			var tView, vView;
			tView = StaticText(readoutView, Rect(8, y, labelW, rowH - 2))
				.string_(title)
				.font_(theme.font(8.5, bold: true))
				.stringColor_(theme.subtext);

			vView = StaticText(readoutView, Rect(8 + labelW + 4, y, valW, rowH - 2))
				.string_(initialVal)
				.font_(theme.font(9.5, bold: true, mono: true))
				.stringColor_(if (isHighlight) { theme.signal } { theme.text });

			readoutLabels.add(tView);
			readoutValues.add(vView);

			y = y + rowH;
			vView;
		};

		txtM     = addRow.value("Mom (M):", "-inf LUFS");
		txtS     = addRow.value("Short (S):", "-inf LUFS");
		txtI     = addRow.value("Integ (I):", "-inf LUFS", true);
		txtDelta = addRow.value("Delta:", "+0.0 LU");
		txtLRA   = addRow.value("LRA:", "0.0 LU");
		txtMaxPk = addRow.value("Max Peak:", "-inf dBFS");

		// Reset Button
		btnReset = Button(readoutView, Rect(8, rh - btnResetH - 4, rw - 16, btnResetH))
			.states_([["Reset ('r')", theme.text, theme.background]])
			.font_(theme.font(8.5, bold: true))
			.action_({ this.reset });
	}

	prHandleResize { |w, h|
		var fs = theme.fontScale ? 1.0;
		var btnTargetH = (24 * fs).max(22).min(34).round.asInteger;
		var btnTargetW = (120 * fs).max(104).round.asInteger;
		var topOffset = (btnTargetH + 8).max(28).round.asInteger;
		var contentH = (h - topOffset - 10).max(50);
		var meterW = (w * 0.38).clip(110, 200);
		var readoutW = (w - meterW - 18).max(100);
		var btnResetH = (24 * fs).max(22).min(36).round.asInteger;
		var rowH = ((contentH - btnResetH - 16) / 6).floor.clip((18 * fs).max(18), (32 * fs).max(30));
		var labelW = (readoutW * 0.44).clip((68 * fs).max(65), (120 * fs).max(110));
		var valW = (readoutW - labelW - 18).max(30);

		if (isClosed.not and: { meterView.notNil } and: { readoutView.notNil }) {
			view.bounds = Rect(view.bounds.left, view.bounds.top, w, h);
			if (txtTitle.notNil) {
				txtTitle.bounds = Rect(12, 4, (w - btnTargetW - 20).max(80), btnTargetH);
			};
			if (btnTarget.notNil) {
				btnTarget.bounds = Rect(w - btnTargetW - 8, 4, btnTargetW, btnTargetH);
			};
			meterView.bounds = Rect(10, topOffset, meterW, contentH);
			readoutView.bounds = Rect(meterW + 14, topOffset, readoutW, contentH);
			if (btnReset.notNil) {
				btnReset.bounds = Rect(8, contentH - btnResetH - 4, readoutW - 16, btnResetH);
			};
			if (readoutLabels.notNil) {
				readoutLabels.do { |lbl, idx|
					lbl.bounds = Rect(8, 6 + (idx * rowH), labelW, rowH - 2);
				};
			};
			if (readoutValues.notNil) {
				readoutValues.do { |valView, idx|
					valView.bounds = Rect(8 + labelW + 4, 6 + (idx * rowH), valW, rowH - 2);
				};
			};
			meterView.refresh;
		};
	}

	prSubscribeSource {
		var broker = SCVisuals.broker;
		source = broker.subscribeLoudness(this, server, busType, busIndex, numChannels, updateFreq, { |m, s, i, lraVal, maxP|
			this.prHandleData(m, s, i, lraVal, maxP);
		});
	}

	prHandleData { |m, s, i, lraVal, maxP|
		var delta;

		if (isClosed.not and: { isFrozen.not }) {
			mLUFS = m;
			sLUFS = s;
			iLUFS = i;
			lra   = lraVal;
			maxPk = maxP;

			meterView.refresh;

			// Update text readouts
			txtM.string = if (mLUFS <= -70.0) { "-inf LUFS" } { mLUFS.round(0.1).asString ++ " LUFS" };
			txtS.string = if (sLUFS <= -70.0) { "-inf LUFS" } { sLUFS.round(0.1).asString ++ " LUFS" };
			txtI.string = if (iLUFS <= -70.0) { "-inf LUFS" } { iLUFS.round(0.1).asString ++ " LUFS" };

			if (iLUFS > -70.0) {
				delta = (iLUFS - targetLUFS).round(0.1);
				txtDelta.string = if (delta >= 0) { "+" ++ delta ++ " LU" } { delta.asString ++ " LU" };
				if (delta.abs <= 0.5) {
					txtDelta.stringColor = theme.signal;
				} {
					if (delta > 0.5) {
						txtDelta.stringColor = theme.critical;
					} {
						txtDelta.stringColor = theme.warning;
					};
				};
			} {
				txtDelta.string = "+0.0 LU";
				txtDelta.stringColor = theme.subtext;
			};

			txtLRA.string = lra.round(0.1).asString ++ " LU";
			txtMaxPk.string = if (maxPk <= 0.00001) { "-inf dBFS" } { maxPk.ampdb.round(0.1).asString ++ " dBFS" };
		};
	}

	targetLUFS_ { |val|
		targetLUFS = val.asFloat;
		btnTarget.states = [["Target: " ++ targetLUFS.round(1) ++ " LUFS", theme.text, theme.panelBackground]];
		meterView.refresh;
	}

	toggleTarget {
		if (targetLUFS == -23.0) {
			this.targetLUFS = -14.0;
		} {
			this.targetLUFS = -23.0;
		};
	}

	reset {
		if (source.notNil) {
			source.reset;
		};
		mLUFS = -99.0;
		sLUFS = -99.0;
		iLUFS = -99.0;
		lra   = 0.0;
		maxPk = 0.0;
		meterView.refresh;
		txtM.string = "-inf LUFS";
		txtS.string = "-inf LUFS";
		txtI.string = "-inf LUFS";
		txtDelta.string = "+0.0 LU";
		txtLRA.string = "0.0 LU";
		txtMaxPk.string = "-inf dBFS";
	}

	toggleFreeze {
		isFrozen = isFrozen.not;
	}

	theme_ { |newTheme|
		theme = SCVTheme.get(newTheme);
		view.background = theme.background;
		readoutView.background = theme.panelBackground;
		if (txtTitle.notNil) {
			txtTitle.stringColor = theme.text;
			txtTitle.font = theme.font(theme.fontSize, bold: true);
		};
		if (readoutLabels.notNil) {
			readoutLabels.do { |lbl|
				lbl.stringColor = theme.subtext;
				lbl.font = theme.font(8.5, bold: true);
			};
		};
		if (readoutValues.notNil) {
			readoutValues.do { |v|
				v.stringColor = theme.text;
				v.font = theme.font(9.5, bold: true, mono: true);
			};
		};
		if (txtI.notNil) { txtI.stringColor = theme.signal };
		if (btnTarget.notNil) {
			btnTarget.states = [["Ref: " ++ targetLUFS.round(1).asInteger ++ " LUFS", theme.text, theme.panelBackground]];
			btnTarget.font = theme.font(8.5, mono: true);
		};
		if (btnReset.notNil) {
			btnReset.states = [["Reset ('r')", theme.text, theme.background]];
			btnReset.font = theme.font(8.5, bold: true);
		};
		this.prHandleResize(view.bounds.width, view.bounds.height);
		meterView.refresh;
	}

	fontScale {
		^theme.fontScale;
	}

	fontScale_ { |scale|
		this.theme = theme.withFontScale(scale);
		this.prHandleResize(view.bounds.width, view.bounds.height);
	}

	stop {
		if (isClosed.not) {
			isClosed = true;
			SCVisuals.broker.unsubscribe(this, source.key);
			source = nil;
		};
	}
}

SCVLoudness {
	var <window, <loudnessView;

	*new { |server, busType=\outputs, busIndex=0, numChannels=2, theme=\graphite, targetLUFS=(-23.0), updateFreq=10|
		^super.new.prInit(server, busType, busIndex, numChannels, theme, targetLUFS, updateFreq);
	}

	prInit { |server, busType, busIndex, numChannels, theme, targetLUFS, updateFreq|
		var s = server ?? { Server.default };
		var w = 440, h = 290;

		window = Window(s.name ++ " Loudness Meter — SCVisuals", Rect(360, 240, w, h));
		loudnessView = SCVLoudnessView(s, window, Rect(0, 0, w, h), busType, busIndex, numChannels, theme, targetLUFS, updateFreq);
		window.view.background = loudnessView.theme.background;

		window.view.onResize = { |v| loudnessView.prHandleResize(v.bounds.width, v.bounds.height) };
		window.onClose = { loudnessView.stop };
		window.front;
	}

	close {
		loudnessView.stop;
		window.close;
	}

	theme_ { |aTheme|
		loudnessView.theme = aTheme;
		window.view.background = loudnessView.theme.background;
	}

	fontScale {
		^loudnessView.fontScale;
	}

	fontScale_ { |aScale|
		loudnessView.fontScale = aScale;
		window.view.background = loudnessView.theme.background;
	}

	targetLUFS_ { |val|
		loudnessView.targetLUFS = val;
	}

	toggleTarget {
		loudnessView.toggleTarget;
	}

	reset {
		loudnessView.reset;
	}

	toggleFreeze {
		loudnessView.toggleFreeze;
	}
}
