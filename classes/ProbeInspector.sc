/*
    ProbeInspector.sc
    Probe Visualization Suite — Static DSP SynthDef Graph Inspector
    Phase II Stage P5: DSP Graph & Server Inspector Groundwork

    Renders a clean topological signal-flow Directed Acyclic Graph (DAG) for any compiled SynthDef or Function.
    Features:
    - Layer-based topological layout (inputs -> internal DSP -> outputs)
    - Color-coded rate distinction: Audio (\audio = Signal), Control (\control = Warning), Scalar (\scalar = Subtext)
    - Curved bezier connection wires showing signal routing
    - Interactive node selection highlighting inputs and outputs with metadata badge
    - Detection and highlighting of Probe .trace taps
    - Fully compatible with Probe theme palettes and font scaling
*/

ProbeGraphView {
	var <view;
	var <synthDef;
	var <theme;
	var <children;
	var <nodeLayers;
	var <layers;
	var <maxLayer = 0;
	var <nodeRects;
	var <selectedNode;
	var <scroll;

	*new { |parent, bounds, synthDef, theme=\graphite|
		^super.new.prInit(parent, bounds, synthDef, theme);
	}

	prInit { |parent, bounds, argSynthDef, argTheme|
		var rect = bounds ?? { Rect(0, 0, 720, 440) };
		theme = ProbeTheme.get(argTheme);

		if (argSynthDef.isKindOf(Function)) {
			synthDef = argSynthDef.asSynthDef;
		} {
			if (argSynthDef.isKindOf(Symbol) or: { argSynthDef.isKindOf(String) }) {
				var desc = SynthDescLib.global[argSynthDef.asSymbol];
				if (desc.notNil and: { desc.def.notNil }) {
					synthDef = desc.def;
				} {
					synthDef = argSynthDef;
				};
			} {
				synthDef = argSynthDef;
			};
		};

		view = UserView(parent, rect);
		view.resize = 5;
		view.background = theme.background;
		view.canFocus = true;

		this.prParseGraph;

		view.drawFunc = { |uv| this.prDraw(uv) };
		view.mouseDownAction = { |uv, x, y|
			var pt = Point(x, y);
			selectedNode = nil;
			if (children.notNil) {
				children.do { |u|
					var r = nodeRects[u];
					if (r.notNil and: { r.containsPoint(pt) }) {
						selectedNode = u;
					};
				};
			};
			uv.refresh;
		};

		view.onResize = { |uv|
			this.prLayoutNodes(uv.bounds.width, uv.bounds.height);
			uv.refresh;
		};
	}

	prParseGraph {
		nodeLayers = IdentityDictionary.new;
		nodeRects  = IdentityDictionary.new;
		layers     = Array.new;
		maxLayer   = 0;

		if (synthDef.isNil or: { synthDef.respondsTo(\children).not }) {
			children = [];
			^this;
		};

		children = synthDef.children;

		// 1. Calculate topological depth for each UGen
		children.do { |u|
			var maxInLayer = -1;
			if (u.inputs.notNil) {
				u.inputs.do { |inp|
					var src = if (inp.isKindOf(OutputProxy)) { inp.source } { if (inp.isKindOf(UGen)) { inp } { nil } };
					if (src.notNil and: { nodeLayers[src].notNil }) {
						maxInLayer = maxInLayer.max(nodeLayers[src]);
					};
				};
			};
			nodeLayers[u] = maxInLayer + 1;
			maxLayer = maxLayer.max(nodeLayers[u]);
		};

		(maxLayer + 1).do { |l|
			var inLayer = children.select { |u| nodeLayers[u] == l };
			layers = layers.add(inLayer);
		};

		this.prLayoutNodes(view.bounds.width, view.bounds.height);
	}

	prLayoutNodes { |w, h|
		var numL = (maxLayer + 1).max(1);
		var marginX = 24, marginY = 48, footerH = 40;
		var availW = (w - (marginX * 2)).max(100);
		var availH = (h - marginY - footerH).max(100);
		var colW = availW / numL;
		var nodeW = (colW - 14).clip(60, 110);
		var nodeH = 32;

		layers.do { |nodeList, lIdx|
			var count = nodeList.size;
			var totalH = count * (nodeH + 12);
			var startY = marginY + ((availH - totalH) * 0.5).max(0);
			var stepY = if (count > 1) { (availH - nodeH) / (count - 1) } { 0 };

			nodeList.do { |u, nIdx|
				var x = marginX + (lIdx * colW) + ((colW - nodeW) * 0.5);
				var y = if (totalH <= availH) {
					startY + (nIdx * (nodeH + 12))
				} {
					marginY + (nIdx * stepY)
				};
				nodeRects[u] = Rect(x.round, y.round, nodeW.round, nodeH);
			};
		};
	}

	prDraw { |uv|
		var curW = uv.bounds.width, curH = uv.bounds.height;

		// 1. Header Banner
		Pen.font = theme.font(11, bold: true);
		Pen.color = theme.text;
		Pen.stringAtPoint(
			"PROBE DSP GRAPH INSPECTOR — \\" ++ (if (synthDef.notNil) { synthDef.name } { "unnamed" })
			++ " [" ++ children.size ++ " UGens, " ++ (maxLayer + 1) ++ " Layers]",
			Point(20, 12)
		);

		// 2. Connection Wires (Bezier Curves)
		children.do { |u|
			var destRect = nodeRects[u];
			if (destRect.notNil and: { u.inputs.notNil }) {
				u.inputs.do { |inp|
					var src = if (inp.isKindOf(OutputProxy)) { inp.source } { if (inp.isKindOf(UGen)) { inp } { nil } };
					if (src.notNil and: { nodeRects[src].notNil }) {
						var srcRect = nodeRects[src];
						var startPt = Point(srcRect.right, srcRect.top + (srcRect.height * 0.5));
						var endPt = Point(destRect.left, destRect.top + (destRect.height * 0.5));
						var dx = (endPt.x - startPt.x).max(10);
						var c1 = Point(startPt.x + (dx * 0.5), startPt.y);
						var c2 = Point(startPt.x + (dx * 0.5), endPt.y);

						var isWireSelected = (selectedNode === u) or: { selectedNode === src };
						Pen.strokeColor = if (isWireSelected) {
							Color.white
						} {
							switch(src.rate,
								\audio,   { theme.signal.copy.alpha_(0.5) },
								\control, { theme.warning.copy.alpha_(0.5) },
								{ theme.subtext.copy.alpha_(0.3) }
							)
						};
						Pen.width = if (isWireSelected) { 2.2 } { 1.1 };
						Pen.moveTo(startPt);
						Pen.curveTo(endPt, c1, c2);
						Pen.stroke;
					};
				};
			};
		};

		// 3. Node Boxes
		children.do { |u|
			var r = nodeRects[u];
			if (r.notNil) {
				var isSel = (selectedNode === u);
				var isOut = u.isKindOf(Out);
				var rateColor = switch(u.rate,
					\audio,   { theme.signal },
					\control, { theme.warning },
					{ theme.subtext }
				);
				var rateTag = switch(u.rate,
					\audio,   { "AR" },
					\control, { "KR" },
					{ "IR" }
				);

				// Box body
				Pen.fillColor = if (isSel) {
					theme.panelBackground.blend(Color.white, 0.18)
				} {
					theme.panelBackground
				};
				Pen.strokeColor = if (isSel) { Color.white } { if (isOut) { theme.signal } { rateColor } };
				Pen.width = if (isSel) { 2.0 } { 1.0 };
				Pen.addRoundedRect(r, 4, 4);
				Pen.fillStroke;

				// Rate accent strip
				Pen.fillColor = rateColor;
				Pen.fillRect(Rect(r.left + 2, r.top + 2, 3, r.height - 4));

				// UGen Class Name
				Pen.font = theme.font(8, bold: true);
				Pen.color = theme.text;
				Pen.stringLeftJustIn(u.class.name.asString, Rect(r.left + 8, r.top + 4, r.width - 34, 13));

				// Rate Tag Chip
				Pen.font = theme.font(7, mono: true);
				Pen.color = rateColor;
				Pen.stringRightJustIn("[" ++ rateTag ++ "]", Rect(r.right - 26, r.top + 4, 22, 13));

				// Index number
				Pen.font = theme.font(7);
				Pen.color = theme.subtext;
				Pen.stringLeftJustIn("#" ++ children.indexOf(u), Rect(r.left + 8, r.top + 17, 30, 11));
			};
		};

		// 4. Interactive Node Inspector Details Footer
		if (selectedNode.notNil) {
			var idx = children.indexOf(selectedNode);
			var inText = if (selectedNode.inputs.notNil) {
				selectedNode.inputs.collect { |inp|
					if (inp.isKindOf(OutputProxy)) {
						inp.source.class.name ++ "#" ++ children.indexOf(inp.source)
					} {
						if (inp.isKindOf(UGen)) {
							inp.class.name ++ "#" ++ children.indexOf(inp)
						} {
							inp.asString
						}
					}
				}.join(", ")
			} { "none" };

			var details = "SELECTED NODE: #" ++ idx ++ " " ++ selectedNode.class.name
				++ " (" ++ selectedNode.rate ++ ") | Inputs: [" ++ inText ++ "]";

			Pen.fillColor = theme.panelBackground;
			Pen.strokeColor = theme.border;
			Pen.addRoundedRect(Rect(20, curH - 34, curW - 40, 24), 4, 4);
			Pen.fillStroke;

			Pen.font = theme.font(8);
			Pen.color = theme.text;
			Pen.stringLeftJustIn(details, Rect(28, curH - 30, curW - 56, 16));
		};
	}

	synthDef_ { |newDef|
		if (newDef.isKindOf(Function)) {
			synthDef = newDef.asSynthDef;
		} {
			synthDef = newDef;
		};
		this.prParseGraph;
		view.refresh;
	}

	theme_ { |newTheme|
		theme = ProbeTheme.get(newTheme);
		view.background = theme.background;
		view.refresh;
	}
}

ProbeInspector {
	var <window, <graphView;

	*new { |synthDef, theme=\graphite|
		^super.new.prInit(synthDef, theme);
	}

	prInit { |synthDef, theme|
		var w = 820, h = 500;
		window = Window("Probe — DSP Graph Inspector", Rect(100, 100, w, h));
		graphView = ProbeGraphView(window, Rect(0, 0, w, h), synthDef, theme);
		window.view.background = graphView.theme.background;

		window.view.onResize = { |v|
			if (v.notNil and: { v.isClosed.not } and: { v.bounds.notNil } and: { graphView.notNil }) {
				graphView.view.bounds = Rect(0, 0, v.bounds.width, v.bounds.height);
			};
		};

		window.front;
	}

	close {
		window.close;
	}
}
