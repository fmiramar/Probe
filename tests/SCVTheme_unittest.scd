// SCVisuals — Unit Test: SCVTheme
// Verifies all 6 palettes, density controls, presets, and copy-on-write immutability.

(
var passed = 0, failed = 0;
var assert = { |cond, name|
	if (cond) {
		passed = passed + 1;
		("  [PASS] " ++ name).postln;
	} {
		failed = failed + 1;
		("  [FAIL] " ++ name).postln;
	};
};

"--- Testing SCVTheme ---".postln;

// 1. Verify all 6 themes exist and have non-nil colors
[\graphite, \phosphor, \ice, \amber, \paper, \monochrome].do { |name|
	var theme = SCVTheme.get(name);
	assert.value(theme.notNil, "Theme " ++ name ++ " exists");
	assert.value(theme.background.isKindOf(Color), name ++ " background is Color");
	assert.value(theme.panelBackground.isKindOf(Color), name ++ " panelBackground is Color");
	assert.value(theme.signal.isKindOf(Color), name ++ " signal is Color");
	assert.value(theme.warning.isKindOf(Color), name ++ " warning is Color");
	assert.value(theme.critical.isKindOf(Color), name ++ " critical is Color");
	assert.value(theme.text.isKindOf(Color), name ++ " text is Color");
	assert.value(theme.grid.isKindOf(Color), name ++ " grid is Color");
};

// 2. Default theme is graphite
assert.value(SCVTheme.current.name === \graphite, "Default theme is graphite");

// 3. Density controls
assert.value(SCVTheme.get(\graphite).density === \compact, "Graphite default density is compact");
assert.value(SCVTheme.get(\graphite).meterWidth == 16, "Compact meter width is 16");

// 4. Immutability / copy-on-write
{
	var original = SCVTheme.get(\graphite);
	var micro = original.withDensity(\micro);
	var extended = original.withDensity(\extended);

	assert.value(micro.density === \micro, "micro copy density is micro");
	assert.value(micro.meterWidth == 10, "micro meterWidth is 10");
	assert.value(micro.gapWidth == 2, "micro gapWidth is 2");

	assert.value(extended.density === \extended, "extended copy density is extended");
	assert.value(extended.meterWidth == 24, "extended meterWidth is 24");

	assert.value(original.density === \compact, "Original theme density remains compact (unmutated)");
	assert.value(original.meterWidth == 16, "Original meterWidth remains 16 (unmutated)");
}.value;

// 5. Presets
{
	var eco = SCVTheme.get(\graphite).withPreset(\eco);
	var detailed = SCVTheme.get(\graphite).withPreset(\detailed);

	assert.value(eco.updateFrequency == 10, "eco updateFrequency is 10");
	assert.value(eco.targetFPS == 15, "eco targetFPS is 15");
	assert.value(detailed.updateFrequency == 25, "detailed updateFrequency is 25");
	assert.value(detailed.targetFPS == 40, "detailed targetFPS is 40");
}.value;

// Summary
("--------------------------------------------------").postln;
("SCVTheme Tests: " ++ passed ++ " passed, " ++ failed ++ " failed.").postln;
if (failed == 0) {
	"ALL SCVTheme TESTS PASSED!".postln;
	0.exit;
} {
	"SOME TESTS FAILED!".postln;
	1.exit;
};
)
