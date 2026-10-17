/*
    SCVWaterfall.sc
    SCVisuals — 2.5D Stacked Line Waterfall Spectrum
    Isometric 2.5D stacked line history, bounded line memory, theme gradient shading,
    dynamic window resizing, and shared measurement broker integration (shares SCVSpectrumSource with Spectrogram).
*/

SCVWaterfallView {
	var <server;
	var <view;
	var <userView;
	var <busIndex, <numBands, <updateFreq;
	var <numLines = 48;
	var history;        // Array of FloatArray[numBands]
	var <source;
	var <theme;
	var <isFrozen = false;
	var <isClosed = false;
	var txtTitle;

	*new { |server, parent, bounds, busIndex=0, numBands=64, theme=\graphite, updateFreq=20, numLines=48|
		^super.new.prInit(server, parent, bounds, busIndex, numBands, theme, updateFreq, numLines);
	}

	prInit { |argServer, parent, bounds, argBusIndex, argNumBands, argTheme, argUpdateFreq, argNumLines|
		var rect = bounds ?? { Rect(0, 0, 440, 260) };

		server      = argServer ?? { Server.default };
		busIndex    = argBusIndex.asInteger;
		numBands    = argNumBands.asInteger.clip(16, 128);
		theme       = SCVTheme.get(argTheme);
		updateFreq  = argUpdateFreq.asInteger.clip(5, 30);
		numLines    = argNumLines.asInteger.clip(16, 64);

		history = Array.fill(numLines, { FloatArray.fill(numBands, 0.0) });

		view = CompositeView(parent, rect);
		view.resize = 5;
		view.background = theme.background;
		view.onClose = { this.stop };
		view.onResize = { |v| this.prHandleResize(v.bounds.width, v.bounds.height) };

		this.prBuildUI(rect.width, rect.height);
		this.prSubscribeSource;
	}

	prBuildUI { |w, h|
		var fs = theme.fontScale ? 1.25;
		var titleH = (18 * fs).max(18).round.asInteger;
		var topOffset = titleH + 6;
		var canvasRect = Rect(10, topOffset, (w - 20).max(50), (h - topOffset - 10).max(50));

		userView = UserView(view, canvasRect);
		userView.canFocus = false;
		userView.drawFunc = { |uv|
			var cw = uv.bounds.width - 40, ch = uv.bounds.height;
			var lineStepY = (ch * 0.55) / numLines;
			var lineStepX = 35 / numLines;

			// Draw from back (oldest) to front (newest)
			numLines.do { |lineIdx|
				var histIdx = numLines - 1 - lineIdx;
				var frame = history[histIdx];
				var normAge = lineIdx / (numLines - 1); // 0 = oldest (back), 1 = newest (front)
				var ox = 10 + (lineIdx * lineStepX);
				var oy = 20 + (lineIdx * lineStepY);
				var color;

				// Color gradient from dim subtext (back) to bright signal (front)
				color = theme.panelBackground.blend(theme.signal, normAge.linlin(0, 1, 0.3, 1.0));
				if (normAge > 0.85) {
					color = color.blend(theme.warning, normAge.linlin(0.85, 1.0, 0, 0.5));
				};

				Pen.strokeColor = color;
				Pen.width = if (normAge > 0.8) { 1.5 } { 1.0 };

				numBands.do { |b|
					var x = ox + (b / (numBands - 1) * cw);
					var amp = (frame[b] * 60).clip(0, 45); // amplitude height
					var y = oy + (ch * 0.35) - amp;

					if (b == 0) {
						Pen.moveTo(Point(x, y));
					} {
						Pen.lineTo(Point(x, y));
					};
				};
				Pen.stroke;
			};
		};

		// Header
		txtTitle = StaticText(view, Rect(10, 4, (w - 20).max(100), titleH))
			.string_("WATERFALL [2.5D " ++ numLines ++ " LINES]")
			.font_(theme.font(theme.fontSize, bold: true))
			.stringColor_(theme.text);

		// Keyboard controls
		view.keyDownAction = { |v, char|
			case
				{ char === $  } { this.toggleFreeze };
		};
	}

	prHandleResize { |w, h|
		var fs = theme.fontScale ? 1.25;
		var titleH = (18 * fs).max(18).round.asInteger;
		var topOffset = titleH + 6;
		if (isClosed.not and: { userView.notNil }) {
			view.bounds = Rect(view.bounds.left, view.bounds.top, w, h);
			if (txtTitle.notNil) {
				txtTitle.bounds = Rect(10, 4, (w - 20).max(100), titleH);
			};
			userView.bounds = Rect(10, topOffset, (w - 20).max(50), (h - topOffset - 10).max(50));
			userView.refresh;
		};
	}

	prSubscribeSource {
		var broker = SCVisuals.broker;
		source = broker.subscribeSpectrum(this, server, busIndex, numBands, updateFreq, { |bands|
			this.prHandleBands(bands);
		});
	}

	prHandleBands { |bands|
		if (isClosed.not and: { isFrozen.not }) {
			// Shift history: newest frame at index 0
			forBy(numLines - 1, 1, -1, { |i|
				history[i] = history[i - 1];
			});
			history[0] = bands.copy;

			userView.refresh;
		};
	}

	theme_ { |newTheme|
		theme = SCVTheme.get(newTheme);
		view.background = theme.background;
		if (txtTitle.notNil) {
			txtTitle.stringColor = theme.text;
			txtTitle.font = theme.font(theme.fontSize, bold: true);
		};
		if (userView.notNil) { userView.refresh };
		this.prHandleResize(view.bounds.width, view.bounds.height);
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
			SCVisuals.broker.unsubscribe(this, source.key);
			source = nil;
		};
	}
}

SCVWaterfall {
	var <window, <waterfallView;

	*new { |server, busIndex=0, numBands=64, theme=\graphite, updateFreq=20, numLines=48|
		^super.new.prInit(server, busIndex, numBands, theme, updateFreq, numLines);
	}

	prInit { |server, busIndex, numBands, theme, updateFreq, numLines|
		var s = server ?? { Server.default };
		var w = 460, h = 280;

		window = Window(s.name ++ " Waterfall — SCVisuals", Rect(320, 260, w, h));
		waterfallView = SCVWaterfallView(s, window, Rect(0, 0, w, h), busIndex, numBands, theme, updateFreq, numLines);

		window.view.onResize = { |v| waterfallView.prHandleResize(v.bounds.width, v.bounds.height) };
		window.onClose = { waterfallView.stop };
		window.front;
	}

	close {
		window.close;
	}

	theme_ { |aTheme|
		waterfallView.theme = aTheme;
		window.view.background = waterfallView.theme.background;
	}

	fontScale {
		^waterfallView.fontScale;
	}

	fontScale_ { |aScale|
		waterfallView.fontScale = aScale;
		window.view.background = waterfallView.theme.background;
	}

	toggleFreeze {
		waterfallView.toggleFreeze;
	}
}
