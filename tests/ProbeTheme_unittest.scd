// Probe — Unit Test: ProbeTheme
// Verifies all 6 palettes, density controls, presets, copy-on-write immutability, and legacy SCVTheme compatibility.

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

"--- Testing ProbeTheme ---".postln;

// 1. Verify all 7 themes exist and have non-nil colors
[\graphite, \phosphor, \ice, \amber, \paper, \monochrome, \puredata].do { |name|
	var theme = ProbeTheme.get(name);
	assert.value(theme.notNil, "Theme " ++ name ++ " exists");
	assert.value(theme.background.isKindOf(Color), name ++ " background is Color");
	assert.value(theme.panelBackground.isKindOf(Color), name ++ " panelBackground is Color");
	assert.value(theme.signal.isKindOf(Color), name ++ " signal is Color");
	assert.value(theme.warning.isKindOf(Color), name ++ " warning is Color");
	assert.value(theme.critical.isKindOf(Color), name ++ " critical is Color");
	assert.value(theme.text.isKindOf(Color), name ++ " text is Color");
	assert.value(theme.grid.isKindOf(Color), name ++ " grid is Color");
	assert.value(theme.border.isKindOf(Color), name ++ " border is Color");
};

// 1b. Verify Pure Data theme specific characteristics
{
	var pd = ProbeTheme.get(\puredata);
	assert.value(pd.background.red == 1.0 and: { pd.background.green == 1.0 } and: { pd.background.blue == 1.0 }, "puredata background is pure white (#FFFFFF)");
	assert.value(pd.signal.red == 0.0 and: { pd.signal.green == 0.0 } and: { pd.signal.blue == 0.0 }, "puredata signal is pure black (#000000)");
	assert.value(pd.text.red == 0.0 and: { pd.text.green == 0.0 } and: { pd.text.blue == 0.0 }, "puredata text is pure black (#000000)");
	assert.value(pd.font(9).isKindOf(Font), "puredata produces valid Font instance");
}.value;

// 2. Default theme is graphite
assert.value(ProbeTheme.current.name === \graphite, "Default theme is graphite");

// 3. Density controls
assert.value(ProbeTheme.get(\graphite).density === \compact, "Graphite default density is compact");
assert.value(ProbeTheme.get(\graphite).meterWidth == 16, "Compact meter width is 16");

// 4. Immutability / copy-on-write
{
	var original = ProbeTheme.get(\graphite);
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
	var eco = ProbeTheme.get(\graphite).withPreset(\eco);
	var detailed = ProbeTheme.get(\graphite).withPreset(\detailed);

	assert.value(eco.updateFrequency == 10, "eco updateFrequency is 10");
	assert.value(eco.targetFPS == 15, "eco targetFPS is 15");
	assert.value(detailed.updateFrequency == 25, "detailed updateFrequency is 25");
	assert.value(detailed.targetFPS == 40, "detailed targetFPS is 40");
}.value;

// 6. Backwards compatibility with SCVTheme
assert.value(SCVTheme.get(\graphite).notNil, "SCVTheme.get returns non-nil theme");
assert.value(SCVTheme.current.name === \graphite, "SCVTheme.current matches ProbeTheme.current");

// Summary
("--------------------------------------------------").postln;
("ProbeTheme Tests: " ++ passed ++ " passed, " ++ failed ++ " failed.").postln;
if (failed == 0) {
	"ALL ProbeTheme TESTS PASSED!".postln;
	0.exit;
} {
	"SOME TESTS FAILED!".postln;
	1.exit;
};
)
