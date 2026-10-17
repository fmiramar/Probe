/*
    SCVTheme.sc
    SCVisuals — Theme Engine & Palettes
    Immutable / copy-on-write theme definitions with density and performance presets.
*/

SCVTheme {
	classvar <themes;
	classvar <>current;
	classvar <>defaultFontScale = 1.25;

	var <name;
	var <background, <panelBackground;
	var <signal, <secondarySignal;
	var <warning, <critical;
	var <text, <subtext;
	var <grid;
	var <density;       // \micro, \compact, \extended
	var <preset;        // \eco, \normal, \detailed
	var <meterWidth, <gapWidth;
	var <fontFamily, <fontSize;
	var <fontScale;

	*initClass {
		themes = IdentityDictionary.new;
		this.initDefaultThemes;
		current = this.get(\graphite);
	}

	*initDefaultThemes {
		// 1. Graphite (Default)
		this.register(\graphite, (
			background:        Color.fromHexString("#171A20"),
			panelBackground:   Color.fromHexString("#20242C"),
			signal:            Color.fromHexString("#7ED6BA"),
			secondarySignal:   Color.fromHexString("#6EDBE8"),
			warning:           Color.fromHexString("#E5B95F"),
			critical:          Color.fromHexString("#F07878"),
			text:              Color.fromHexString("#D0D4DC"),
			subtext:           Color.fromHexString("#8A92A0"),
			grid:              Color.fromHexString("#3A404D")
		));

		// 2. Phosphor
		this.register(\phosphor, (
			background:        Color.fromHexString("#09140F"),
			panelBackground:   Color.fromHexString("#112218"),
			signal:            Color.fromHexString("#78E68F"),
			secondarySignal:   Color.fromHexString("#A4F4B4"),
			warning:           Color.fromHexString("#DFCB73"),
			critical:          Color.fromHexString("#FF796C"),
			text:              Color.fromHexString("#D4ECD8"),
			subtext:           Color.fromHexString("#6B9E78"),
			grid:              Color.fromHexString("#244030")
		));

		// 3. Ice
		this.register(\ice, (
			background:        Color.fromHexString("#101A2A"),
			panelBackground:   Color.fromHexString("#18263D"),
			signal:            Color.fromHexString("#6EDBE8"),
			secondarySignal:   Color.fromHexString("#A3EEF7"),
			warning:           Color.fromHexString("#FFC078"),
			critical:          Color.fromHexString("#FF718E"),
			text:              Color.fromHexString("#DCE8F5"),
			subtext:           Color.fromHexString("#7892B0"),
			grid:              Color.fromHexString("#2C4262")
		));

		// 4. Amber
		this.register(\amber, (
			background:        Color.fromHexString("#1A1712"),
			panelBackground:   Color.fromHexString("#29231A"),
			signal:            Color.fromHexString("#F7BE54"),
			secondarySignal:   Color.fromHexString("#FFD988"),
			warning:           Color.fromHexString("#F28C45"),
			critical:          Color.fromHexString("#ED6066"),
			text:              Color.fromHexString("#F5E9DC"),
			subtext:           Color.fromHexString("#9E8668"),
			grid:              Color.fromHexString("#423828")
		));

		// 5. Paper
		this.register(\paper, (
			background:        Color.fromHexString("#F1EDE2"),
			panelBackground:   Color.fromHexString("#E4DFC8"),
			signal:            Color.fromHexString("#286B68"),
			secondarySignal:   Color.fromHexString("#3D918E"),
			warning:           Color.fromHexString("#B47B35"),
			critical:          Color.fromHexString("#AD4545"),
			text:              Color.fromHexString("#111312"),
			subtext:           Color.fromHexString("#3A403A"),
			grid:              Color.fromHexString("#B5AFA0")
		));

		// 6. Monochrome
		this.register(\monochrome, (
			background:        Color.fromHexString("#111111"),
			panelBackground:   Color.fromHexString("#1E1E1E"),
			signal:            Color.fromHexString("#E4E4E4"),
			secondarySignal:   Color.fromHexString("#CCCCCC"),
			warning:           Color.fromHexString("#999999"),
			critical:          Color.fromHexString("#FFFFFF"),
			text:              Color.fromHexString("#F0F0F0"),
			subtext:           Color.fromHexString("#888888"),
			grid:              Color.fromHexString("#444444")
		));
	}

	*register { |themeName, dict|
		var theme = this.prBuildTheme(themeName.asSymbol, dict);
		themes.put(themeName.asSymbol, theme);
		^theme;
	}

	*get { |themeOrName|
		if (themeOrName.isKindOf(SCVTheme)) { ^themeOrName };
		^themes[themeOrName.asSymbol] ?? { themes[\graphite] };
	}

	*prBuildTheme { |themeName, dict|
		var inst = super.new;
		inst.prInit(themeName, dict);
		^inst;
	}

	prInit { |themeName, dict|
		name            = themeName;
		background      = dict[\background]      ?? { Color.gray(0.1) };
		panelBackground = dict[\panelBackground] ?? { Color.gray(0.15) };
		signal          = dict[\signal]          ?? { Color.green };
		secondarySignal = dict[\secondarySignal] ?? { Color.cyan };
		warning         = dict[\warning]         ?? { Color.yellow };
		critical        = dict[\critical]        ?? { Color.red };
		text            = dict[\text]            ?? { Color.white };
		subtext         = dict[\subtext]         ?? { Color.gray(0.6) };
		grid            = dict[\grid]            ?? { Color.gray(0.25) };

		this.density_(dict[\density] ?? { \compact });
		preset = dict[\preset] ?? { \normal };
		fontFamily = dict[\fontFamily] ?? { Font.defaultSansFace };
		fontScale = (dict[\fontScale] ?? { defaultFontScale }).asFloat.clip(0.6, 2.5);
	}

	density_ { |aDensity|
		density = aDensity.asSymbol;
		switch(density,
			\micro, {
				meterWidth = 10;
				gapWidth   = 2;
				fontSize   = 7;
			},
			\compact, {
				meterWidth = 16;
				gapWidth   = 4;
				fontSize   = 8;
			},
			\extended, {
				meterWidth = 24;
				gapWidth   = 6;
				fontSize   = 10;
			},
			{
				meterWidth = 16;
				gapWidth   = 4;
				fontSize   = 8;
			}
		);
	}

	fontScale_ { |scale|
		fontScale = scale.asFloat.clip(0.6, 2.5);
	}

	withFontScale { |newScale|
		^this.copy((fontScale: newScale.asFloat.clip(0.6, 2.5)));
	}

	font { |baseSize=9, bold=false, mono=false|
		var effectiveScale = fontScale ?? { defaultFontScale };
		var size = (baseSize * effectiveScale).round.asInteger.max(6);
		var f = if (mono) {
			Font.monospace(size)
		} {
			Font.sansSerif(size)
		};
		^if (bold) { f.boldVariant } { f };
	}

	// Copy-on-write immutability
	copy { |overrides|
		var dict = IdentityDictionary.new;
		dict.put(\background,        background);
		dict.put(\panelBackground,   panelBackground);
		dict.put(\signal,            signal);
		dict.put(\secondarySignal,   secondarySignal);
		dict.put(\warning,           warning);
		dict.put(\critical,          critical);
		dict.put(\text,              text);
		dict.put(\subtext,           subtext);
		dict.put(\grid,              grid);
		dict.put(\density,           density);
		dict.put(\preset,            preset);
		dict.put(\fontFamily,        fontFamily);
		dict.put(\fontScale,         fontScale);

		if (overrides.notNil) {
			overrides.keysValuesDo { |k, v| dict.put(k, v) };
		};

		^SCVTheme.prBuildTheme(name, dict);
	}

	withDensity { |newDensity|
		^this.copy((density: newDensity));
	}

	withPreset { |newPreset|
		^this.copy((preset: newPreset));
	}

	updateFrequency {
		^switch(preset,
			\eco, { 10 },
			\normal, { 15 },
			\detailed, { 25 },
			{ 15 }
		);
	}

	targetFPS {
		^switch(preset,
			\eco, { 15 },
			\normal, { 25 },
			\detailed, { 40 },
			{ 25 }
		);
	}

	printOn { |stream|
		stream << "SCVTheme(" << name << ", density: " << density << ", preset: " << preset << ")";
	}
}
