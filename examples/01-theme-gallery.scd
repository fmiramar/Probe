// Probe — Example 01: Theme & Density Gallery
// Visual reference displaying all six themes (Graphite, Phosphor, Ice, Amber, Paper, Monochrome)
// and allowing interactive switching between themes and densities (\micro, \compact, \extended).

(
var win, themeTabs, currentTheme, mainLayout;
var themes = [\graphite, \phosphor, \ice, \amber, \paper, \monochrome, \puredata];
var densities = [\micro, \compact, \extended];
var densityMenu, themeMenu;
var renderGallery;

currentTheme = ProbeTheme.get(\graphite);

win = Window("Probe — Theme & Density Gallery", Rect(100, 150, 720, 360));
win.background = currentTheme.background;

// Header controls
themeMenu = PopUpMenu(win, Rect(16, 12, 130, 24))
	.items_(themes.collect(_.asString))
	.value_(0)
	.font_(Font.sansSerif(9).boldVariant);

densityMenu = PopUpMenu(win, Rect(156, 12, 110, 24))
	.items_(densities.collect(_.asString))
	.value_(1) // compact
	.font_(Font.sansSerif(9));

StaticText(win, Rect(280, 14, 420, 20))
	.string_("Pure data themes: changing theme causes zero audio DSP or node restarts.")
	.font_(Font.sansSerif(8))
	.stringColor_(currentTheme.text);

// Main composite container
mainLayout = CompositeView(win, Rect(16, 44, 688, 300));

renderGallery = {
	var mw = currentTheme.meterWidth;
	var gw = currentTheme.gapWidth;
	var fs = currentTheme.fontSize;

	mainLayout.removeAll;
	win.background = currentTheme.background;

	// Render preview cards for each theme
	themes.do { |thName, idx|
		var th = ProbeTheme.get(thName).withDensity(currentTheme.density);
		var cardX = (idx % 3) * 230;
		var cardY = (idx.div(3)) * 145;
		var card = CompositeView(mainLayout, Rect(cardX, cardY, 220, 138));
		card.background = th.panelBackground;

		// Theme title
		StaticText(card, Rect(8, 6, 120, 14))
			.string_(thName.asString.toUpper)
			.font_(Font.sansSerif(8).boldVariant)
			.stringColor_(th.text);

		// Mini dB scale
		UserView(card, Rect(8, 22, 18, 90)).drawFunc_({
			Pen.font = Font.monospace(fs - 1);
			Pen.color = th.subtext;
			Pen.stringRightJustIn("0", Rect(0, 0, 16, 8));
			Pen.stringRightJustIn("-12", Rect(0, 20, 16, 8));
			Pen.stringRightJustIn("-36", Rect(0, 50, 16, 8));
			Pen.stringRightJustIn("-80", Rect(0, 80, 16, 8));
		});

		// Channel strips (2 bars)
		2.do { |ch|
			var meter = LevelIndicator(card, Rect(28 + (ch * (mw + gw)), 22, mw, 90))
				.background_(th.background)
				.warning_(0.7)
				.critical_(0.95)
				.drawsPeak_(true)
				.value_(if (ch == 0) { 0.65 } { 0.82 })
				.peakLevel_(if (ch == 0) { 0.78 } { 0.92 });
		};

		// Mini Scope trace preview
		UserView(card, Rect(76 + (mw * 2), 22, 130 - (mw * 2), 90)).drawFunc_({ |uv|
			var w = uv.bounds.width, h = uv.bounds.height;
			Pen.strokeColor = th.grid;
			Pen.line(Point(0, h * 0.5), Point(w, h * 0.5));
			Pen.stroke;

			// Primary signal trace
			Pen.strokeColor = th.signal;
			Pen.width = 1.5;
			w.do { |x|
				var normX = x / w * 2pi * 2;
				var y = (sin(normX) * (h * 0.35)) + (h * 0.5);
				if (x == 0) { Pen.moveTo(Point(x, y)) } { Pen.lineTo(Point(x, y)) };
			};
			Pen.stroke;

			// Secondary signal trace
			Pen.strokeColor = th.secondarySignal;
			Pen.width = 1;
			w.do { |x|
				var normX = (x / w * 2pi * 2) + 0.5;
				var y = (cos(normX) * (h * 0.25)) + (h * 0.5);
				if (x == 0) { Pen.moveTo(Point(x, y)) } { Pen.lineTo(Point(x, y)) };
			};
			Pen.stroke;
		});

		// Subtext color swatch bar
		UserView(card, Rect(8, 118, 204, 12)).drawFunc_({
			var sw = 204 / 5;
			[th.background, th.signal, th.secondarySignal, th.warning, th.critical].do { |c, ci|
				Pen.fillColor = c;
				Pen.fillRect(Rect(ci * sw, 0, sw - 2, 8));
			};
		});
	};
};

themeMenu.action = { |m|
	currentTheme = ProbeTheme.get(themes[m.value]).withDensity(currentTheme.density);
	renderGallery.value;
};

densityMenu.action = { |m|
	currentTheme = currentTheme.withDensity(densities[m.value]);
	renderGallery.value;
};

renderGallery.value;
win.front;
)
