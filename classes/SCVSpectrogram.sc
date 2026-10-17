/*
    SCVSpectrogram.sc
    SCVisuals — Real-Time Scrolling Spectrogram
    Preallocated 2D raster with circular ring-buffer blit, theme palette LUT mapping,
    frequency axis markings, dynamic window resizing, freeze/clear controls, and shared measurement broker integration.
*/

SCVSpectrogramView {
	var <server;
	var <view;
	var <userView;
	var <axisView;
	var <busIndex, <numBands, <updateFreq;
	var <rasterW = 320;
	var <img;
	var <source;
	var <theme;
	var <colorLUT;
	var colPixels;
	var writeIdx = 0;
	var <isFrozen = false;
	var <isClosed = false;
	var txtTitle;

	*new { |server, parent, bounds, busIndex=0, numBands=64, theme=\graphite, updateFreq=20|
		^super.new.prInit(server, parent, bounds, busIndex, numBands, theme, updateFreq);
	}

	prInit { |argServer, parent, bounds, argBusIndex, argNumBands, argTheme, argUpdateFreq|
		var rect = bounds ?? { Rect(0, 0, 420, 220) };

		server      = argServer ?? { Server.default };
		busIndex    = argBusIndex.asInteger;
		numBands    = argNumBands.asInteger.clip(16, 128);
		theme       = SCVTheme.get(argTheme);
		updateFreq  = argUpdateFreq.asInteger.clip(5, 30);
		rasterW     = (rect.width - 50).max(100).asInteger;

		colPixels   = Int32Array.newClear(numBands);
		this.prBuildLUT;

		// Preallocate raster image
		img = Image(rasterW.asInteger, numBands.asInteger);
		img.fill(theme.panelBackground);

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

	prBuildLUT {
		var lutSize = 256;
		colorLUT = Int32Array.newClear(lutSize);
		lutSize.do { |i|
			var norm = i / (lutSize - 1);
			var c;
			case
				{ norm < 0.35 } {
					c = theme.panelBackground.blend(theme.signal, norm.linlin(0, 0.35, 0, 0.6));
				}
				{ norm < 0.70 } {
					c = theme.signal.blend(theme.warning, norm.linlin(0.35, 0.70, 0, 1));
				}
				{ true } {
					c = theme.warning.blend(theme.critical, norm.linlin(0.70, 1.0, 0, 1));
				};
			colorLUT[i] = Image.colorToPixel(c);
		};
	}
	prBuildUI { |w, h|
		var fs = theme.fontScale ? 1.25;
		var titleH = (18 * fs).max(18).round.asInteger;
		var topOffset = titleH + 6;
		var rulerW = (28 * fs).max(28).round.asInteger;
		var canvasW = (w - rulerW - 16).max(50);
		var canvasH = (h - topOffset - 10).max(50);

		// 1. Spectrogram UserView (Ring-buffer blit with horizontal stretch)
		userView = UserView(view, Rect(rulerW + 8, topOffset, canvasW, canvasH));
		userView.canFocus = false;
		userView.drawFunc = { |uv|
			var part1W = rasterW - writeIdx;
			var cw = uv.bounds.width;
			var ch = uv.bounds.height;
			var scaleX = cw / rasterW;

			// Draw older slice shifted left
			Pen.drawImage(Rect(0, 0, part1W * scaleX, ch), img, Rect(writeIdx, 0, part1W, numBands));
			// Draw newer slice shifted right
			if (writeIdx > 0) {
				Pen.drawImage(Rect(part1W * scaleX, 0, writeIdx * scaleX, ch), img, Rect(0, 0, writeIdx, numBands));
			};
		};

		// 2. Frequency Axis Ruler (Left)
		axisView = UserView(view, Rect(6, topOffset, rulerW, canvasH));
		axisView.canFocus = false;
		axisView.drawFunc = { |uv|
			var ch = uv.bounds.height;
			Pen.font = theme.font(8, mono: true);
			Pen.color = theme.subtext;
			Pen.stringRightJustIn("18k", Rect(0, 0, rulerW - 4, 12));
			Pen.stringRightJustIn(" 5k", Rect(0, (ch * 0.3) - 6, rulerW - 4, 12));
			Pen.stringRightJustIn(" 1k", Rect(0, (ch * 0.6) - 6, rulerW - 4, 12));
			Pen.stringRightJustIn("40",  Rect(0, ch - 14, rulerW - 4, 12));
		};

		// 3. Header title
		txtTitle = StaticText(view, Rect(10, 4, (w - 20).max(100), titleH))
			.string_("SPECTROGRAM [" ++ numBands ++ " BANDS]")
			.font_(theme.font(theme.fontSize, bold: true))
			.stringColor_(theme.text);

		// Keyboard controls
		view.keyDownAction = { |v, char|
			case
				{ char === $  } { this.toggleFreeze }
				{ char === $c } { this.clear };
		};
	}

	prHandleResize { |w, h|
		var fs = theme.fontScale ? 1.25;
		var titleH = (18 * fs).max(18).round.asInteger;
		var topOffset = titleH + 6;
		var rulerW = (28 * fs).max(28).round.asInteger;
		var canvasW = (w - rulerW - 16).max(50);
		var canvasH = (h - topOffset - 10).max(50);
		if (isClosed.not and: { view.notNil } and: { view.isClosed.not } and: { userView.notNil } and: { userView.isClosed.not } and: { axisView.notNil } and: { axisView.isClosed.not }) {
			view.bounds = Rect(view.bounds.left, view.bounds.top, w, h);
			if (txtTitle.notNil) {
				txtTitle.bounds = Rect(10, 4, (w - 20).max(100), titleH);
			};
			userView.bounds = Rect(rulerW + 8, topOffset, canvasW, canvasH);
			axisView.bounds = Rect(6, topOffset, rulerW, canvasH);
			axisView.refresh;
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
		if (isClosed.not and: { isFrozen.not and: { img.notNil } }) {
			numBands.do { |y|
				var val = (bands[y] * 3.5).clip(0.0, 1.0); // Calibrate amplitude to [0, 1]
				var lutIdx = (val * 255).round.asInteger;
				// Invert Y so low frequencies are at the bottom
				colPixels[numBands - 1 - y] = colorLUT[lutIdx];
			};

			// Blit column into Image at writeIdx (< 1 µs)
			img.setPixels(colPixels, Rect(writeIdx, 0, 1, numBands));
			writeIdx = (writeIdx + 1) % rasterW;

			userView.refresh;
		};
	}

	theme_ { |newTheme|
		theme = SCVTheme.get(newTheme);
		view.background = theme.background;
		this.prBuildLUT;
		if (txtTitle.notNil) {
			txtTitle.stringColor = theme.text;
			txtTitle.font = theme.font(theme.fontSize, bold: true);
		};
		if (axisView.notNil) { axisView.refresh };
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

	clear {
		if (img.notNil) {
			img.fill(theme.panelBackground);
			writeIdx = 0;
			userView.refresh;
		};
	}

	toggleFreeze {
		isFrozen = isFrozen.not;
	}

	stop {
		if (isClosed.not) {
			isClosed = true;
			SCVisuals.broker.unsubscribe(this, source.key);
			source = nil;
			if (img.notNil) {
				img.free;
				img = nil;
			};
		};
	}
}

SCVSpectrogram {
	var <window, <spectrogramView;

	*new { |server, busIndex=0, numBands=64, theme=\graphite, updateFreq=20|
		^super.new.prInit(server, busIndex, numBands, theme, updateFreq);
	}

	prInit { |server, busIndex, numBands, theme, updateFreq|
		var s = server ?? { Server.default };
		var w = 460, h = 240;

		window = Window(s.name ++ " Spectrogram — Probe", Rect(300, 280, w, h));
		spectrogramView = SCVSpectrogramView(s, window, Rect(0, 0, w, h), busIndex, numBands, theme, updateFreq);
		window.view.background = spectrogramView.theme.background;

		window.view.onResize = { |v|
			if (v.notNil and: { v.isClosed.not } and: { v.bounds.notNil } and: { spectrogramView.notNil } and: { spectrogramView.isClosed.not }) {
				spectrogramView.prHandleResize(v.bounds.width, v.bounds.height);
			};
		};
		window.onClose = { spectrogramView.stop };
		window.front;
	}

	close {
		window.close;
	}

	theme_ { |aTheme|
		spectrogramView.theme = aTheme;
		window.view.background = spectrogramView.theme.background;
	}

	clear {
		spectrogramView.clear;
	}

	toggleFreeze {
		spectrogramView.toggleFreeze;
	}

	fontScale {
		^spectrogramView.fontScale;
	}

	fontScale_ { |aScale|
		spectrogramView.fontScale = aScale;
		window.view.background = spectrogramView.theme.background;
	}
}
