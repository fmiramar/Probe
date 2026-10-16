// SCVisuals — Spike 04: GUI-Only Spectrogram Prototype
// P0 Prototype: Preallocated raster with circular ring-buffer blit, synthetic spectral frames
// (no audio DSP or FFT transport claim), smooth scrolling, palette mapping, freeze/resume,
// and bounded memory.

(
var win, userView, img;
var rasterW = 280, numBands = 120;
var writeIdx = 0, isRunning = true, fps = 25;
var updateRoutine;
var colPixels = Int32Array.newClear(numBands);
var lutSize = 256, colorLUT;
var step = 0;

// Palette: Graphite theme gradient
var bgCol       = Color.fromHexString("#171A20");
var panelBgCol  = Color.fromHexString("#20242C");
var sigCol      = Color.fromHexString("#7ED6BA");
var warnCol     = Color.fromHexString("#E5B95F");
var critCol     = Color.fromHexString("#F07878");
var textCol     = Color.fromHexString("#D0D4DC");
var gridCol     = Color.fromHexString("#3A404D").alpha_(0.5);

// Precompute 256-entry Color Lookup Table (LUT) for fast mapping
colorLUT = Int32Array.newClear(lutSize);
lutSize.do { |i|
	var norm = i / (lutSize - 1);
	var c;
	case
		{ norm < 0.35 } {
			c = panelBgCol.blend(sigCol, norm.linlin(0, 0.35, 0, 0.6));
		}
		{ norm < 0.70 } {
			c = sigCol.blend(warnCol, norm.linlin(0.35, 0.70, 0, 1));
		}
		{ true } {
			c = warnCol.blend(critCol, norm.linlin(0.70, 1.0, 0, 1));
		};
	colorLUT[i] = Image.colorToPixel(c);
};

// Preallocate Image
img = Image(rasterW, numBands);
img.fill(panelBgCol);

win = Window("SCVisuals — Spectrogram Spike (Graphite)", Rect(280, 260, 390, 220), resizable: false);
win.background = bgCol;

// Title
StaticText(win, Rect(12, 6, 260, 16))
	.string_("SPECTROGRAM [SYNTHETIC RASTER SPIKE]")
	.font_(Font.sansSerif(9).boldVariant)
	.stringColor_(textCol);

// UserView drawing the ring buffer raster
userView = UserView(win, Rect(36, 26, rasterW, numBands));
userView.canFocus = false;
userView.drawFunc = {
	var part1W = rasterW - writeIdx;
	// Draw right part (older frames) shifted left
	Pen.drawImage(Point(0, 0), img, Rect(writeIdx, 0, part1W, numBands));
	// Draw left part (newer frames) shifted right
	if (writeIdx > 0) {
		Pen.drawImage(Point(part1W, 0), img, Rect(0, 0, writeIdx, numBands));
	};
};

// Axis Labels & Grid
UserView(win, Rect(6, 26, 28, numBands)).drawFunc_({
	Pen.font = Font.monospace(8);
	Pen.color = textCol.alpha_(0.7);
	Pen.stringRightJustIn("20k", Rect(0, 0, 24, 10));
	Pen.stringRightJustIn("10k", Rect(0, (numBands * 0.25) - 5, 24, 10));
	Pen.stringRightJustIn(" 1k", Rect(0, (numBands * 0.60) - 5, 24, 10));
	Pen.stringRightJustIn(" 0k", Rect(0, numBands - 10, 24, 10));
});

// Controls
Button(win, Rect(36, 156, 70, 20))
	.states_([["Freeze", textCol, panelBgCol], ["Resume", textCol, critCol.alpha_(0.5)]])
	.font_(Font.sansSerif(8).boldVariant)
	.action_({ |btn|
		isRunning = (btn.value == 0);
	});

Button(win, Rect(112, 156, 60, 20))
	.states_([["Clear", textCol, panelBgCol]])
	.font_(Font.sansSerif(8))
	.action_({
		img.fill(panelBgCol);
		writeIdx = 0;
		userView.refresh;
	});

StaticText(win, Rect(180, 158, 190, 16))
	.string_("Ring-buffer blit: 25 FPS | Memory bounded")
	.font_(Font.sansSerif(8))
	.stringColor_(textCol.alpha_(0.7));

// Synthetic spectral frame update routine
updateRoutine = Routine({
	while { win.isClosed.not } {
		if (isRunning) {
			var centerBin, centerBin2, harmBin;
			step = step + 1;

			// Swept sine ridge (bin 15 to 95)
			centerBin = (sin(step * 0.05) * 40 + 55).round.asInteger;
			centerBin2 = (cos(step * 0.03) * 25 + 35).round.asInteger;
			harmBin = (centerBin * 1.5).clip(0, numBands - 1).asInteger;

			// Generate 1 column of synthetic spectral magnitudes
			numBands.do { |y|
				var val = 0.03; // noise floor
				var dist1 = (y - centerBin).abs;
				var dist2 = (y - centerBin2).abs;
				var dist3 = (y - harmBin).abs;

				if (dist1 < 6) { val = max(val, (1 - (dist1 / 6)) * 0.95) };
				if (dist2 < 4) { val = max(val, (1 - (dist2 / 4)) * 0.70) };
				if (dist3 < 3) { val = max(val, (1 - (dist3 / 3)) * 0.50) };

				// Add slight noise texture
				val = (val + 0.05.rand2).clip(0.0, 1.0);

				// Flip y so 0 is at bottom
				colPixels[numBands - 1 - y] = colorLUT[(val * 255).round.asInteger];
			};

			// Blit column into Image at writeIdx
			img.setPixels(colPixels, Rect(writeIdx, 0, 1, numBands));
			writeIdx = (writeIdx + 1) % rasterW;

			userView.refresh;
		};
		(1 / fps).yield;
	};
}).play(AppClock);

win.onClose = {
	"Cleaning up Spike 04 resources...".postln;
	updateRoutine.stop;
	img.free;
	"Cleanup complete.".postln;
};

win.front;
)
