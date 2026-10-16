/*
    SCVHistory.sc
    SCVisuals — Real-Time Peak, RMS & Crest Factor Rolling Timeline History
    Rolling multi-channel level history subscribed to SCVLevelsSource via broker,
    visualizing Peak curves, RMS contours, and shaded Crest Factor headroom.
*/

SCVHistoryView {
	var <server;
	var <view;
	var <userView;
	var <busType, <busIndex, <numChannels, <updateFreq;
	var <maxPoints = 200;
	var historyPeak;      // Array of FloatArray[numChannels]
	var historyRMS;       // Array of FloatArray[numChannels]
	var count = 0;
	var <source;
	var <theme;
	var <isFrozen = false;
	var <isClosed = false;

	*new { |server, parent, bounds, busType=\outputs, busIndex=0, numChannels=2, theme=\graphite, updateFreq=20, maxPoints=200|
		^super.new.prInit(server, parent, bounds, busType, busIndex, numChannels, theme, updateFreq, maxPoints);
	}

	prInit { |argServer, parent, bounds, argBusType, argBusIndex, argNumChannels, argTheme, argUpdateFreq, argMaxPoints|
		var rect = bounds ?? { Rect(0, 0, 460, 240) };

		server       = argServer ?? { Server.default };
		busType      = argBusType.asSymbol;
		busIndex     = argBusIndex.asInteger;
		numChannels  = argNumChannels.asInteger.clip(1, 16);
		theme        = SCVTheme.get(argTheme);
		updateFreq   = argUpdateFreq.asInteger.clip(5, 60);
		maxPoints    = argMaxPoints.asInteger.clip(50, 600);

		historyPeak  = Array.fill(maxPoints, { FloatArray.fill(numChannels, 0.0) });
		historyRMS   = Array.fill(maxPoints, { FloatArray.fill(numChannels, 0.0) });

		view = CompositeView(parent, rect);
		view.background = theme.background;
		view.onClose = { this.stop };

		this.prBuildUI(rect.width, rect.height);
		this.prSubscribeSource;
	}

	prBuildUI { |w, h|
		var canvasRect = Rect(36, 24, w - 46, h - 32);

		userView = UserView(view, canvasRect);
		userView.canFocus = false;
		userView.drawFunc = { |uv|
			var cw = uv.bounds.width;
			var ch = uv.bounds.height;
			var laneH = ch / numChannels;
			var minDb = -72.0;

			numChannels.do { |chIdx|
				var laneY = chIdx * laneH;
				var laneInnerH = laneH - 6;

				// Background panel for lane
				Pen.fillColor = theme.panelBackground;
				Pen.fillRect(Rect(0, laneY, cw, laneInnerH));

				// dB Grid lines: 0 dB, -6 dB, -18 dB, -36 dB
				#[-6, -18, -36].do { |dbVal|
					var normY = (dbVal / minDb).clip(0.0, 1.0);
					var y = laneY + (normY * laneInnerH);
					Pen.strokeColor = theme.subtext.copy.alpha_(0.2);
					Pen.width = 1.0;
					Pen.line(Point(0, y), Point(cw, y));
					Pen.stroke;
				};

				// Channel label
				Pen.font = Font.monospace(8);
				Pen.color = theme.subtext.copy.alpha_(0.6);
				Pen.stringLeftJustIn("CH " ++ (chIdx + 1), Rect(4, laneY + 2, 40, 12));

				if (count > 1) {
					var ptsPeak = Array.new(count);
					var ptsRMS  = Array.new(count);
					var startIdx = if (count < maxPoints) { 0 } { 0 };
					var numVisible = count.min(maxPoints);

					numVisible.do { |ptIdx|
						var ringIdx = if (count < maxPoints) {
							ptIdx
						} {
							(count + ptIdx) % maxPoints
						};
						var x = (ptIdx / (numVisible - 1)) * cw;
						var pkDb  = historyPeak[ringIdx][chIdx].ampdb.clip(minDb, 0.0);
						var rmsDb = historyRMS[ringIdx][chIdx].ampdb.clip(minDb, 0.0);

						var pkNorm  = (pkDb / minDb).clip(0.0, 1.0);
						var rmsNorm = (rmsDb / minDb).clip(0.0, 1.0);

						var yPk  = laneY + (pkNorm * laneInnerH);
						var yRms = laneY + (rmsNorm * laneInnerH);

						ptsPeak.add(Point(x, yPk));
						ptsRMS.add(Point(x, yRms));
					};

					// 1. Shaded Crest Factor Area (between RMS and Peak)
					Pen.fillColor = theme.signal.copy.alpha_(0.22);
					Pen.moveTo(ptsPeak[0]);
					(1..(numVisible - 1)).do { |i| Pen.lineTo(ptsPeak[i]) };
					forBy(numVisible - 1, 0, -1, { |i| Pen.lineTo(ptsRMS[i]) });
					Pen.lineTo(ptsPeak[0]);
					Pen.fill;

					// 2. RMS line (solid, muted signal)
					Pen.strokeColor = theme.signal.copy.alpha_(0.65);
					Pen.width = 1.2;
					Pen.moveTo(ptsRMS[0]);
					(1..(numVisible - 1)).do { |i| Pen.lineTo(ptsRMS[i]) };
					Pen.stroke;

					// 3. Peak line (sharp, bright signal)
					Pen.strokeColor = theme.signal;
					Pen.width = 1.5;
					Pen.moveTo(ptsPeak[0]);
					(1..(numVisible - 1)).do { |i| Pen.lineTo(ptsPeak[i]) };
					Pen.stroke;
				};

				// Bottom border for lane
				Pen.strokeColor = theme.subtext.copy.alpha_(0.3);
				Pen.width = 1.0;
				Pen.line(Point(0, laneY + laneInnerH), Point(cw, laneY + laneInnerH));
				Pen.stroke;
			};
		};

		// Left Axis Ruler (dB ticks)
		UserView(view, Rect(4, 24, 30, h - 32))
			.canFocus_(false)
			.drawFunc_({ |uv|
				var laneH = uv.bounds.height / numChannels;
				var laneInnerH = laneH - 6;
				var minDb = -72.0;

				numChannels.do { |chIdx|
					var laneY = chIdx * laneH;
					Pen.font = Font.monospace(7);
					Pen.color = theme.subtext;

					#[0, -6, -18, -36, -72].do { |dbVal|
						var normY = (dbVal / minDb).clip(0.0, 1.0);
						var y = laneY + (normY * laneInnerH) - 4;
						Pen.stringRightJustIn(dbVal.asString, Rect(0, y, 26, 10));
					};
				};
			});

		// Header
		StaticText(view, Rect(10, 4, 300, 16))
			.string_("LEVEL HISTORY [PEAK / RMS / CREST] — " ++ busType.asString.toUpper)
			.font_(Font.sansSerif(theme.fontSize).boldVariant)
			.stringColor_(theme.text);

		// Keyboard controls
		view.keyDownAction = { |v, char|
			case
				{ char === $  } { this.toggleFreeze }
				{ char === $c } { this.clear };
		};
	}

	prSubscribeSource {
		var broker = SCVisuals.broker;
		source = broker.subscribeLevels(this, server, busType, busIndex, numChannels, updateFreq, { |pk, rms|
			this.prHandleLevels(pk, rms);
		});
	}

	prHandleLevels { |pk, rms|
		var idx;
		if (isClosed.not and: { isFrozen.not }) {
			idx = count % maxPoints;
			numChannels.do { |ch|
				historyPeak[idx][ch] = pk[ch];
				historyRMS[idx][ch]  = rms[ch];
			};
			count = count + 1;
			userView.refresh;
		};
	}

	theme_ { |newTheme|
		theme = SCVTheme.get(newTheme);
		view.background = theme.background;
		if (userView.notNil) { userView.refresh };
	}

	clear {
		maxPoints.do { |i|
			numChannels.do { |ch|
				historyPeak[i][ch] = 0.0;
				historyRMS[i][ch]  = 0.0;
			};
		};
		count = 0;
		if (userView.notNil) { userView.refresh };
	}

	toggleFreeze {
		isFrozen = isFrozen.not;
	}

	stop {
		if (isClosed.not) {
			isClosed = true;
			SCVisuals.broker.unsubscribe(this, source.key);
			source = nil;
		};
	}
}

SCVHistory {
	var <window, <historyView;

	*new { |server, busType=\outputs, busIndex=0, numChannels=2, theme=\graphite, updateFreq=20, maxPoints=200|
		^super.new.prInit(server, busType, busIndex, numChannels, theme, updateFreq, maxPoints);
	}

	prInit { |server, busType, busIndex, numChannels, theme, updateFreq, maxPoints|
		var s = server ?? { Server.default };
		var w = 520, h = 260;

		window = Window(s.name ++ " Level History — SCVisuals", Rect(320, 240, w, h));
		historyView = SCVHistoryView(s, window, Rect(0, 0, w, h), busType, busIndex, numChannels, theme, updateFreq, maxPoints);

		window.onClose = { historyView.stop };
		window.front;
	}

	close {
		window.close;
	}

	theme_ { |aTheme|
		historyView.theme = aTheme;
	}

	clear {
		historyView.clear;
	}

	toggleFreeze {
		historyView.toggleFreeze;
	}
}
