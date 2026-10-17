// scripts/verify_system_install.scd
// Verifies system-wide installation of Probe and backwards-compatible SCVisuals.

(
var passed = 0, failed = 0;
var assert = { |cond, msg|
	if (cond) {
		passed = passed + 1;
		("  [PASS] " ++ msg).postln;
	} {
		failed = failed + 1;
		("  [FAIL] " ++ msg).postln;
	};
};

"========================================================================".postln;
"Testing System-Wide SuperCollider Installation of Probe (v0.2.0)...".postln;
"========================================================================".postln;

// 1. Probe Core Classes
[
	Probe,
	ProbeBandAnalyzer,
	ProbeBandAnalyzerView,
	ProbeSpectralDashboard,
	ProbeSpectralDashboardView,
	ProbeSpectralSource,
	ProbeDescriptorsView,
	ProbePitchTrackerView,
	ProbeChromagramView,
	ProbeSpectralCompareView,
	ProbeInspector,
	ProbeGraphView
].do { |cls|
	assert.value(cls.notNil, "Class available: " ++ cls.asString);
};

// 2. SCVisuals Backwards-Compatible Classes
[
	SCVisuals,
	SCVTheme,
	SCVMeasurementBroker,
	SCVLevelsSource,
	SCVSpectrumSource,
	SCVLoudnessSource,
	SCVVectorscopeSource,
	SCVMeter,
	SCVMeterView,
	SCVScope,
	SCVScopeView,
	SCVSpectrum,
	SCVSpectrumView,
	SCVSpectrogram,
	SCVSpectrogramView,
	SCVWaterfall,
	SCVWaterfallView,
	SCVHistory,
	SCVHistoryView,
	SCVLoudness,
	SCVLoudnessView,
	SCVVectorscope,
	SCVVectorscopeView,
	SCVDashboard,
	SCVDashboardView
].do { |cls|
	assert.value(cls.notNil, "Backwards-compatible class available: " ++ cls.asString);
};

// 3. Server Methods
assert.value(Server.findMethod(\meter2).notNil, "Server:meter2 extension registered");
assert.value(Server.findMethod(\scope2).notNil, "Server:scope2 extension registered");
assert.value(Server.findMethod(\freqscope2).notNil, "Server:freqscope2 extension registered");
assert.value(Server.findMethod(\dashboard).notNil, "Server:dashboard extension registered");
assert.value(Server.findMethod(\spectralDashboard).notNil, "Server:spectralDashboard extension registered");
assert.value(Server.findMethod(\bandAnalyzer).notNil, "Server:bandAnalyzer extension registered");
assert.value(Server.findMethod(\inspector).notNil, "Server:inspector extension registered");

// 4. In-Graph Tracing
assert.value(UGen.findMethod(\trace).notNil, "UGen:trace extension registered");

// 5. Versioning & Theme Integrity
assert.value(Probe.version == "0.2.0", "Probe.version is 0.2.0");
assert.value(SCVisuals.version == "0.2.0", "SCVisuals.version is 0.2.0");
assert.value(Probe.theme.name === \graphite, "Probe default theme is \\graphite");
assert.value(Probe.fontScale == 1.25, "Probe default fontScale is 1.25");

"========================================================================".postln;
("System Verification Results: " ++ passed ++ " Passed, " ++ failed ++ " Failed.").postln;
"========================================================================".postln;

if (failed == 0) {
	"SYSTEM-WIDE INSTALLATION VERIFIED SUCCESSFULLY!".postln;
	0.exit;
} {
	"SYSTEM-WIDE INSTALLATION VERIFICATION FAILED!".postln;
	1.exit;
};
)
